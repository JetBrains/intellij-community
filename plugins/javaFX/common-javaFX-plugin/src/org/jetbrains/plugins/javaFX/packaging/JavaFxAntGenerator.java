/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * User: anna
 * Date: 3/28/13
 */
public class JavaFxAntGenerator {
  public static List<SimpleTag> createJarAndDeployTasks(AbstractJavaFxPackager packager,
                                                        String artifactFileName,
                                                        String artifactName,
                                                        String tempDirPath) {
    final String artifactFileNameWithoutExtension = FileUtil.getNameWithoutExtension(artifactFileName);
    final List<SimpleTag> topLevelTagsCollector = new ArrayList<SimpleTag>(); 
    final String preloaderJar = packager.getPreloaderJar();
    final String preloaderClass = packager.getPreloaderClass();
    String preloaderFiles = null;
    String allButPreloader = null;
    
    if (!StringUtil.isEmptyOrSpaces(preloaderJar) && !StringUtil.isEmptyOrSpaces(preloaderClass)) {
      preloaderFiles = artifactFileNameWithoutExtension + "_preloader_files";
      topLevelTagsCollector.add(new SimpleTag("fx:fileset",
                                              Pair.create("id", preloaderFiles),
                                              Pair.create("requiredFor", "preloader"),
                                              Pair.create("dir", tempDirPath),
                                              Pair.create("includes", preloaderJar)));

      allButPreloader = "all_but_preloader_" + artifactFileNameWithoutExtension;
      topLevelTagsCollector.add(new SimpleTag("fx:fileset", Pair.create("id", allButPreloader),
                                              Pair.create("dir", tempDirPath),
                                              Pair.create("excludes", preloaderJar),
                                              Pair.create("includes", "**/*.jar")));
    }

    final String allButSelf = "all_but_" + artifactFileNameWithoutExtension;
    final SimpleTag allButSelfAndPreloader = new SimpleTag("fx:fileset", Pair.create("id", allButSelf),
                                                           Pair.create("dir", tempDirPath),
                                                           Pair.create("includes", "**/*.jar"));
    allButSelfAndPreloader.add(new SimpleTag("exclude", Pair.create("name", artifactFileName)));
    if (preloaderJar != null) {
      allButSelfAndPreloader.add(new SimpleTag("exclude", Pair.create("name", preloaderJar)));
    }
    topLevelTagsCollector.add(allButSelfAndPreloader);

    final String all = "all_" + artifactFileNameWithoutExtension;
    final SimpleTag allIncluded = new SimpleTag("fx:fileset", Pair.create("id", all),
                                                Pair.create("dir", tempDirPath),
                                                Pair.create("includes", "**/*.jar"));
    topLevelTagsCollector.add(allIncluded);

    //register application
    final String appId = artifactFileNameWithoutExtension + "_id";
    final SimpleTag applicationTag = new SimpleTag("fx:application", Pair.create("id", appId),
                                                   Pair.create("name", artifactName),
                                                   Pair.create("mainClass", packager.getAppClass()));
    if (preloaderFiles != null) {
      applicationTag.addAttribute(Pair.create("preloaderClass", preloaderClass));
    }

    appendValuesFromPropertiesFile(applicationTag, packager.getHtmlParamFile(), "fx:htmlParam", false);
    //also loads fx:argument values
    appendValuesFromPropertiesFile(applicationTag, packager.getParamFile(), "fx:param", true);

    topLevelTagsCollector.add(applicationTag);

    if (packager.convertCss2Bin()) {
      final SimpleTag css2binTag = new SimpleTag("fx:csstobin", Pair.create("outdir", tempDirPath));
      css2binTag.add(new SimpleTag("fileset", Pair.create("dir", tempDirPath), Pair.create("includes", "**/*.css")));
      topLevelTagsCollector.add(css2binTag);
    }

    //create jar task
    final SimpleTag createJarTag = new SimpleTag("fx:jar",
                                                 Pair.create("destfile", tempDirPath + File.separator + artifactFileName));
    createJarTag.add(new SimpleTag("fx:application", Pair.create("refid", appId)));

    final List<Pair> fileset2Jar = new ArrayList<Pair>();
    fileset2Jar.add(Pair.create("dir", tempDirPath));
    fileset2Jar.add(Pair.create("excludes", "**/*.jar"));
    createJarTag.add(new SimpleTag("fileset", fileset2Jar.toArray(new Pair[fileset2Jar.size()])));

    createJarTag.add(createResourcesTag(preloaderFiles, false, allButPreloader, allButSelf, all));

    List<JavaFxManifestAttribute> manifestAttributes = packager.getCustomManifestAttributes();
    if (manifestAttributes != null) {
      final SimpleTag manifestTag = new SimpleTag("manifest");
      for (JavaFxManifestAttribute pair : manifestAttributes) {
        manifestTag.add(new SimpleTag("attribute",
                                      Pair.create("name", pair.getName()),
                                      Pair.create("value", pair.getValue())));
      }
      createJarTag.add(manifestTag);
    }

    topLevelTagsCollector.add(createJarTag);

    //deploy task
    final SimpleTag deployTag = new SimpleTag("fx:deploy",
                                              Pair.create("width", packager.getWidth()),
                                              Pair.create("height", packager.getHeight()),
                                              Pair.create("updatemode", packager.getUpdateMode()),
                                              Pair.create("outdir", tempDirPath + File.separator + "deploy"),
                                              Pair.create("outfile", artifactFileNameWithoutExtension));
    final JavaFxPackagerConstants.NativeBundles bundle = packager.getNativeBundle();
    if (bundle != JavaFxPackagerConstants.NativeBundles.none) {
      deployTag.addAttribute(Pair.create("nativeBundles", bundle.name()));
    }

    if (packager.isEnabledSigning()) {
      deployTag.add(new SimpleTag("fx:permissions", Pair.create("elevated", "true")));
    }

    deployTag.add(new SimpleTag("fx:application", Pair.create("refid", appId)));

    final List<Pair> infoPairs = new ArrayList<Pair>();
    appendIfNotEmpty(infoPairs, "title", packager.getTitle());
    appendIfNotEmpty(infoPairs, "vendor", packager.getVendor());
    appendIfNotEmpty(infoPairs, "description", packager.getDescription());
    if (!infoPairs.isEmpty()) {
      deployTag.add(new SimpleTag("fx:info", infoPairs.toArray(new Pair[infoPairs.size()])));
    }
    deployTag.add(createResourcesTag(preloaderFiles, true, allButPreloader, allButSelf, all));

    topLevelTagsCollector.add(deployTag);
    return topLevelTagsCollector;
  }

  private static SimpleTag createResourcesTag(String preloaderFiles, boolean includeSelf,
                                              String allButPreloader,
                                              String allButSelf,
                                              String all) {
    final SimpleTag resourcesTag = new SimpleTag("fx:resources");
    if (preloaderFiles != null) {
      resourcesTag.add(new SimpleTag("fx:fileset", Pair.create("refid", preloaderFiles)));
      resourcesTag.add(new SimpleTag("fx:fileset", Pair.create("refid", includeSelf ? allButPreloader : allButSelf)));
    } else {
      resourcesTag.add(new SimpleTag("fx:fileset", Pair.create("refid", includeSelf ? all : allButSelf)));
    }
    return resourcesTag;
  }

  private static void appendIfNotEmpty(final List<Pair> pairs, final String propertyName, final String propValue) {
    if (!StringUtil.isEmptyOrSpaces(propValue)) {
      pairs.add(Pair.create(propertyName, propValue));
    }
  }

  private static void appendValuesFromPropertiesFile(final SimpleTag applicationTag,
                                                     final String paramFile,
                                                     final String paramTagName,
                                                     final boolean allowNoNamed) {
    if (!StringUtil.isEmptyOrSpaces(paramFile)) {
      final Properties properties = new Properties();
      try {
        final FileInputStream paramsInputStream = new FileInputStream(new File(paramFile));
        try {
          properties.load(paramsInputStream);
          for (Object o : properties.keySet()) {
            final String propName = (String)o;
            final String propValue = properties.getProperty(propName);
            if (!StringUtil.isEmptyOrSpaces(propValue)) {
              applicationTag
                .add(new SimpleTag(paramTagName, Pair.create("name", propName), Pair.create("value", propValue)));
            }
            else if (allowNoNamed) {
              applicationTag.add(new SimpleTag("fx:argument", propName) {
                @Override
                public void generate(StringBuilder buf) {
                  buf.append("<").append(getName()).append(">").append(propName).append("</").append(getName()).append(">");
                }
              });
            }
          }
        }
        finally {
          paramsInputStream.close();
        }
      }
      catch (IOException ignore) {
      }
    }
  }

  public static class SimpleTag {
    private final String myName;
    private final List<Pair> myPairs;
    private final List<SimpleTag> mySubTags = new ArrayList<SimpleTag>();
    private final String myValue;

    public SimpleTag(String name, Pair... pairs) {
      myName = name;
      myPairs = new ArrayList<Pair>(Arrays.asList(pairs));
      myValue = null;
    }

    public SimpleTag(String name, String value) {
      myName = name;
      myPairs = new ArrayList<Pair>();
      myValue = value;
    }

    public void addAttribute(Pair attr) {
      myPairs.add(attr);
    }
    
    public void add(SimpleTag tag) {
      mySubTags.add(tag);
    }

    public String getName() {
      return myName;
    }

    public Pair[] getPairs() {
      return myPairs.toArray(new Pair[myPairs.size()]);
    }

    public String getValue() {
      return myValue;
    }

    public List<SimpleTag> getSubTags() {
      return mySubTags;
    }

    public void generate(StringBuilder buf) {
      buf.append("<").append(getName());
      for (Pair pair : getPairs()) {
        buf.append(" ").append(pair.first).append("=\"").append(pair.second).append("\"");
      }
      buf.append(">\n");
      for (SimpleTag tag : getSubTags()) {
        tag.generate(buf);
      }
      buf.append("</").append(getName()).append(">\n");
    }
  }
}
