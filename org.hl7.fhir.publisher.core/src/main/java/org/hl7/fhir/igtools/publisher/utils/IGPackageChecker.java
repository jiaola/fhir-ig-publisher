package org.hl7.fhir.igtools.publisher.utils;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.SPDXLicense;
import org.hl7.fhir.r5.test.utils.ToolsHelper;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.NPMPackageGenerator.Category;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.cache.NpmPackageIndexBuilder;
import org.hl7.fhir.utilities.cache.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class IGPackageChecker {

  private String folder;
  private String canonical;
  private String vpath;
  private String packageId;

  public IGPackageChecker(String folder, String canonical, String vpath, String packageId) {
    this.folder = folder; 
    this.canonical = canonical;
    this.vpath = vpath;
    this.packageId = packageId;
  }

  public void check(String ver, String pckId, String fhirversion, String name, Date date, String url, String canonical) throws IOException, FHIRException {
    String pf = Utilities.path(folder, "package.tgz");
    File f = new File(pf);
    if (!f.exists()) {
      makePackage(pf, name, ver, fhirversion, date);
    } else {
      NpmPackage pck = NpmPackage.fromPackage(new FileInputStream(f));
      JsonObject json = pck.getNpm();
      checkJsonProp(pf, json, "version", ver);
      checkJsonProp(pf, json, "name", pckId);
      checkJsonProp(pf, json, "url", url);
      checkJsonProp(pf, json, "canonical", canonical);
      if (!json.has("fhirVersions")) {
        System.out.println("Problem with "+pf+": missing fhirVersions");
      } else {
        if (json.getAsJsonArray("fhirVersions").size() == 0) {
          System.out.println("Problem with "+pf+": fhirVersions size = "+json.getAsJsonArray("fhirVersions").size());          
        }
        if (!json.getAsJsonArray("fhirVersions").get(0).getAsString().equals(fhirversion)) {
          System.out.println("Problem with "+pf+": fhirVersions value mismatch (expected "+fhirversion+", found "+json.getAsJsonArray("fhirVersions").get(0).getAsString());
        }
      }
      if (json.has("dependencies")) {
        JsonObject dep = json.getAsJsonObject("dependencies");
        if (dep.has("hl7.fhir.core")) {
          System.out.println("Problem with "+pf+": found hl7.fhir.core in dependencies");
        }  
        if (fhirversion.startsWith("1.0")) {
          if (!dep.has("hl7.fhir.r2.core")) {
            System.out.println("Problem with "+pf+": R2 guide doesn't list R2 in it's dependencies");
          } else if (!fhirversion.equals(JSONUtil.str(dep, "hl7.fhir.r2.core"))) {
            System.out.println("Problem with "+pf+": fhirVersions value mismatch on hl7.fhir.r2.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r2.core"));
          }
        } else if (fhirversion.startsWith("1.4")) {
          if (!dep.has("hl7.fhir.r2b.core")) {
            System.out.println("Problem with "+pf+": R2B guide doesn't list R2B in it's dependencies");
          } else if (!fhirversion.equals(JSONUtil.str(dep, "hl7.fhir.r2b.core"))) {
            System.out.println("Problem with "+pf+": fhirVersions value mismatch on hl7.fhir.r2b.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r2b.core"));
          }          
        } else if (fhirversion.startsWith("3.0")) {
          if (!dep.has("hl7.fhir.r3.core")) {
            System.out.println("Problem with "+pf+": R3 guide doesn't list R3 in it's dependencies");
          } else if (!fhirversion.equals(JSONUtil.str(dep, "hl7.fhir.r3.core"))) {
            System.out.println("Problem with "+pf+": fhirVersions value mismatch on hl7.fhir.r3.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r3.core"));
          }
        } else if (fhirversion.startsWith("4.0")) {
          if (!dep.has("hl7.fhir.r4.core")) {
            System.out.println("Problem with "+pf+": R4 guide doesn't list R4 in it's dependencies");
          } else if (!fhirversion.equals(JSONUtil.str(dep, "hl7.fhir.r4.core"))) {
            System.out.println("Problem with "+pf+": fhirVersions value mismatch on hl7.fhir.r4.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r4.core"));
          }
        }
      }
      if (pck.isChangedByLoader()) {
        pck.save(new FileOutputStream(f));
      }
    }
  }

  public void checkJsonProp(String pf, JsonObject json, String propName, String value) {
    if (!json.has(propName)) {
      System.out.println("Problem with "+pf+": missing "+propName);
    } else if (!json.get(propName).getAsString().equals(value)) {
      System.out.println("Problem with "+pf+": expected "+propName+" "+value+" but found "+json.get(propName).getAsString());
    }
  }

  private String tail(String s) {
    return s.substring(s.lastIndexOf("/")+1);
  }

  private void makePackage(String file, String name, String ver, String fhirversion, Date date) throws FHIRException, IOException {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setUrl(Utilities.pathURL(canonical, "ImplementationGuide", "ig"));
    ig.setName(name);
    ig.setTitle(Utilities.titleize(name));
    ig.setVersion(ver);
    ig.getDateElement().setValue(date);
    ig.setPackageId(packageId);
    ig.setLicense(SPDXLicense.CC01_0);
    ig.getManifest().setRendering(vpath);
    if (FHIRVersion.isValidCode(fhirversion))
      ig.addFhirVersion(FHIRVersion.fromCode(fhirversion));
    List<String> fhirversions = new ArrayList<>();
    if (fhirversion.contains("|")) {
      for (String v : fhirversion.split("\\|")) {
        fhirversions.add(v);
      }
    } else {
      fhirversions.add(fhirversion);
    }
    NPMPackageGenerator npm = new NPMPackageGenerator(file, canonical, vpath, PackageType.IG, ig, date, fhirversions);
    for (File f : new File(folder).listFiles()) {
      if (f.getName().endsWith(".openapi.json")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        npm.addFile(Category.OPENAPI, f.getName(), src);
      } else if (f.getName().endsWith(".json")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        String s = TextFile.bytesToString(src);
        if (s.contains("\"resourceType\"")) {
          JsonObject json = JsonTrackingParser.parseJson(s);
          if (json.has("resourceType") && json.has("id") && json.get("id").isJsonPrimitive()) {
            String rt = json.get("resourceType").getAsString();
            String id = json.get("id").getAsString();
            npm.addFile(Category.RESOURCE, rt+"-"+id+".json", src);
          }
        }
      }
      if (f.getName().endsWith(".sch")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        npm.addFile(Category.SCHEMATRON, f.getName(), src);
      }
      if (f.getName().equals("spec.internals")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        npm.addFile(Category.OTHER, f.getName(), src);
      }
    }
    npm.finish();    
  }

}
