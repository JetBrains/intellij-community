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

import com.intellij.openapi.util.Couple;
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
                                              Couple.newOne("id", preloaderFiles),
                                              Couple.newOne("requiredFor", "preloader"),
                                              Couple.newOne("dir", tempDirPath),
                                              Couple.newOne("includes", preloaderJar)));

      allButPreloader = "all_but_preloader_" + artifactFileNameWithoutExtension;
      topLevelTagsCollector.add(new SimpleTag("fx:fileset", Couple.newOne("id", allButPreloader),
                                              Couple.newOne("dir", tempDirPath),
                                              Couple.newOne("excludes", preloaderJar),
                                              Couple.newOne("includes", "**/*.jar")));
    }

    final String allButSelf = "all_but_" + artifactFileNameWithoutExtension;
    final SimpleTag allButSelfAndPreloader = new SimpleTag("fx:fileset", Couple.newOne("id", allButSelf),
                                                           Couple.newOne("dir", tempDirPath),
                                                           Couple.newOne("includes", "**/*.jar"));
    allButSelfAndPreloader.add(new SimpleTag("exclude", Couple.newOne("name", artifactFileName)));
    if (preloaderJar != null) {
      allButSelfAndPreloader.add(new SimpleTag("exclude", Couple.newOne("name", preloaderJar)));
    }
    topLevelTagsCollector.add(allButSelfAndPreloader);

    final String all = "all_" + artifactFileNameWithoutExtension;
    final SimpleTag allIncluded = new SimpleTag("fx:fileset", Couple.newOne("id", all),
                                                Couple.newOne("dir", tempDirPath),
                                                Couple.newOne("includes", "**/*.jar"));
    topLevelTagsCollector.add(allIncluded);

    //register application
    final String appId = artifactFileNameWithoutExtension + "_id";
    final SimpleTag applicationTag = new SimpleTag("fx:application", Couple.newOne("id", appId),
                                                   Couple.newOne("name", artifactName),
                                                   Couple.newOne("mainClass", packager.getAppClass()));
    if (preloaderFiles != null) {
      applicationTag.addAttribute(Couple.newOne("preloaderClass", preloaderClass));
    }

    appendValuesFromPropertiesFile(applicationTag, packager.getHtmlParamFile(), "fx:htmlParam", false);
    //also loads fx:argument values
    appendValuesFromPropertiesFile(applicationTag, packager.getParamFile(), "fx:param", true);

    topLevelTagsCollector.add(applicationTag);

    if (packager.convertCss2Bin()) {
      final SimpleTag css2binTag = new SimpleTag("fx:csstobin", Couple.newOne("outdir", tempDirPath));
      css2binTag.add(new SimpleTag("fileset", Couple.newOne("dir", tempDirPath), Couple.newOne("includes", "**/*.css")));
      topLevelTagsCollector.add(css2binTag);
    }

    //create jar task
    final SimpleTag createJarTag = new SimpleTag("fx:jar",
                                                 Couple.newOne("destfile", tempDirPath + File.separator + artifactFileName));
    createJarTag.add(new SimpleTag("fx:application", Couple.newOne("refid", appId)));

    final List<Pair> fileset2Jar = new ArrayList<Pair>();
    fileset2Jar.add(Couple.newOne("dir", tempDirPath));
    fileset2Jar.add(Couple.newOne("excludes", "**/*.jar"));
    createJarTag.add(new SimpleTag("fileset", fileset2Jar.toArray(new Pair[fileset2Jar.size()])));

    createJarTag.add(createResourcesTag(preloaderFiles, false, allButPreloader, allButSelf, all));

    List<JavaFxManifestAttribute> manifestAttributes = packager.getCustomManifestAttributes();
    if (manifestAttributes != null) {
      final SimpleTag manifestTag = new SimpleTag("manifest");
      for (JavaFxManifestAttribute pair : manifestAttributes) {
        manifestTag.add(new SimpleTag("attribute",
                                      Couple.newOne("name", pair.getName()),
                                      Couple.newOne("value", pair.getValue())));
      }
      createJarTag.add(manifestTag);
    }

    topLevelTagsCollector.add(createJarTag);

    //deploy task
    final SimpleTag deployTag = new SimpleTag("fx:deploy",
                                              Couple.newOne("width", packager.getWidth()),
                                              Couple.newOne("height", packager.getHeight()),
                                              Couple.newOne("updatemode", packager.getUpdateMode()),
                                              Couple.newOne("outdir", tempDirPath + File.separator + "deploy"),
                                              Couple.newOne("outfile", artifactFileNameWithoutExtension));
    final JavaFxPackagerConstants.NativeBundles bundle = packager.getNativeBundle();
    if (bundle != JavaFxPackagerConstants.NativeBundles.none) {
      deployTag.addAttribute(Couple.newOne("nativeBundles", bundle.name()));
    }

    if (packager.isEnabledSigning()) {
      deployTag.add(new SimpleTag("fx:permissions", Couple.newOne("elevated", "true")));
    }

    deployTag.add(new SimpleTag("fx:application", Couple.newOne("refid", appId)));

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
      resourcesTag.add(new SimpleTag("fx:fileset", Couple.newOne("refid", preloaderFiles)));
      resourcesTag.add(new SimpleTag("fx:fileset", Couple.newOne("refid", includeSelf ? allButPreloader : allButSelf)));
    } else {
      resourcesTag.add(new SimpleTag("fx:fileset", Couple.newOne("refid", includeSelf ? all : allButSelf)));
    }
    return resourcesTag;
  }

  private static void appendIfNotEmpty(final List<Pair> pairs, final String propertyName, final String propValue) {
    if (!StringUtil.isEmptyOrSpaces(propValue)) {
      pairs.add(Couple.newOne(propertyName, propValue));
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
                .add(new SimpleTag(paramTagName, Couple.newOne("name", propName), Couple.newOne("value", propValue)));
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
