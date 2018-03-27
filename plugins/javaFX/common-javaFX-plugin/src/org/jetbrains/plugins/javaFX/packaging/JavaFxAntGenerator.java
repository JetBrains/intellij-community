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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class JavaFxAntGenerator {
  public static List<SimpleTag> createJarAndDeployTasks(AbstractJavaFxPackager packager,
                                                        String artifactFileName,
                                                        String artifactName,
                                                        String tempDirPath,
                                                        String tempDirDeployPath,
                                                        String relativeToBaseDirPath) {
    final String artifactFileNameWithoutExtension = FileUtil.getNameWithoutExtension(artifactFileName);
    final List<SimpleTag> topLevelTagsCollector = new ArrayList<>();
    final String preloaderJar = packager.getPreloaderJar();
    final String preloaderClass = packager.getPreloaderClass();
    String preloaderFiles = null;
    String allButPreloader = null;
    
    if (!StringUtil.isEmptyOrSpaces(preloaderJar) && !StringUtil.isEmptyOrSpaces(preloaderClass)) {
      preloaderFiles = artifactFileNameWithoutExtension + "_preloader_files";
      topLevelTagsCollector.add(new SimpleTag("fx:fileset",
                                              Couple.of("id", preloaderFiles),
                                              Couple.of("requiredFor", "preloader"),
                                              Couple.of("dir", tempDirPath),
                                              Couple.of("includes", preloaderJar)));

      allButPreloader = "all_but_preloader_" + artifactFileNameWithoutExtension;
      topLevelTagsCollector.add(new SimpleTag("fx:fileset", Couple.of("id", allButPreloader),
                                              Couple.of("dir", tempDirPath),
                                              Couple.of("excludes", preloaderJar),
                                              Couple.of("includes", "**/*.jar")));
    }

    final String allButSelf = "all_but_" + artifactFileNameWithoutExtension;
    final SimpleTag allButSelfAndPreloader = new SimpleTag("fx:fileset", Couple.of("id", allButSelf),
                                                           Couple.of("dir", tempDirPath),
                                                           Couple.of("includes", "**/*.jar"));
    allButSelfAndPreloader.add(new SimpleTag("exclude", Couple.of("name", artifactFileName)));
    if (preloaderJar != null) {
      allButSelfAndPreloader.add(new SimpleTag("exclude", Couple.of("name", preloaderJar)));
    }
    topLevelTagsCollector.add(allButSelfAndPreloader);

    final String all = "all_" + artifactFileNameWithoutExtension;
    final SimpleTag allIncluded = new SimpleTag("fx:fileset", Couple.of("id", all),
                                                Couple.of("dir", tempDirPath),
                                                Couple.of("includes", "**/*.jar"));
    topLevelTagsCollector.add(allIncluded);

    //register application
    final String appId = artifactFileNameWithoutExtension + "_id";
    final SimpleTag applicationTag = new SimpleTag("fx:application", Couple.of("id", appId),
                                                   Couple.of("name", artifactName),
                                                   Couple.of("mainClass", packager.getAppClass()));
    if (preloaderFiles != null) {
      applicationTag.addAttribute(Couple.of("preloaderClass", preloaderClass));
    }
    final String version = packager.getVersion();
    if (!StringUtil.isEmptyOrSpaces(version)) {
      applicationTag.addAttribute(Couple.of("version", version.trim().replaceAll("\\s", "-")));
    }

    appendValuesFromPropertiesFile(applicationTag, packager.getHtmlParamFile(), "fx:htmlParam", false);
    //also loads fx:argument values
    appendValuesFromPropertiesFile(applicationTag, packager.getParamFile(), "fx:param", true);

    topLevelTagsCollector.add(applicationTag);

    if (packager.convertCss2Bin()) {
      final SimpleTag css2binTag = new SimpleTag("fx:csstobin", Couple.of("outdir", tempDirPath));
      css2binTag.add(new SimpleTag("fileset", Couple.of("dir", tempDirPath), Couple.of("includes", "**/*.css")));
      topLevelTagsCollector.add(css2binTag);
    }

    //create jar task
    final SimpleTag createJarTag = new SimpleTag("fx:jar",
                                                 Couple.of("destfile", tempDirPath + "/" + artifactFileName));
    addVerboseAttribute(createJarTag, packager);
    createJarTag.add(new SimpleTag("fx:application", Couple.of("refid", appId)));

    final List<Pair> fileset2Jar = new ArrayList<>();
    fileset2Jar.add(Couple.of("dir", tempDirPath));
    fileset2Jar.add(Couple.of("excludes", "**/*.jar"));
    createJarTag.add(new SimpleTag("fileset", fileset2Jar.toArray(new Pair[fileset2Jar.size()])));

    createJarTag.add(createResourcesTag(preloaderFiles, false, allButPreloader, allButSelf, all));

    final List<JavaFxManifestAttribute> manifestAttributes = getManifestAttributes(packager);
    if (!manifestAttributes.isEmpty()) {
      final SimpleTag manifestTag = new SimpleTag("manifest");
      for (JavaFxManifestAttribute pair : manifestAttributes) {
        manifestTag.add(new SimpleTag("attribute",
                                      Couple.of("name", pair.getName()),
                                      Couple.of("value", pair.getValue())));
      }
      createJarTag.add(manifestTag);
    }

    topLevelTagsCollector.add(createJarTag);

    final JavaFxPackagerConstants.NativeBundles bundle = packager.getNativeBundle();
    final SimpleTag iconTag = appendApplicationIconPath(topLevelTagsCollector, bundle, packager.getIcons(), relativeToBaseDirPath);

    //deploy task
    final SimpleTag deployTag = new SimpleTag("fx:deploy",
                                              Couple.of("width", packager.getWidth()),
                                              Couple.of("height", packager.getHeight()),
                                              Couple.of("updatemode", packager.getUpdateMode()),
                                              Couple.of("outdir", tempDirDeployPath),
                                              Couple.of("outfile", artifactFileNameWithoutExtension));
    if (bundle != null && bundle != JavaFxPackagerConstants.NativeBundles.none) {
      deployTag.addAttribute(Couple.of("nativeBundles", bundle.name()));
    }
    if (!StringUtil.isEmpty(packager.getHtmlPlaceholderId())) {
      deployTag.addAttribute(Couple.of("placeholderId", packager.getHtmlPlaceholderId()));
    }
    addVerboseAttribute(deployTag, packager);

    if (packager.isEnabledSigning()) {
      deployTag.add(new SimpleTag("fx:permissions", Couple.of("elevated", "true")));
    }

    deployTag.add(new SimpleTag("fx:application", Couple.of("refid", appId)));

    final List<Pair> infoPairs = new ArrayList<>();
    appendIfNotEmpty(infoPairs, "title", packager.getTitle());
    appendIfNotEmpty(infoPairs, "vendor", packager.getVendor());
    appendIfNotEmpty(infoPairs, "description", packager.getDescription());
    if (!infoPairs.isEmpty() || iconTag != null) {
      final SimpleTag infoTag = new SimpleTag("fx:info", infoPairs);
      deployTag.add(infoTag);
      if (iconTag != null) infoTag.add(iconTag);
    }
    deployTag.add(createResourcesTag(preloaderFiles, true, allButPreloader, allButSelf, all));
    final SimpleTag templateTag = createTemplateTag(packager, tempDirDeployPath, relativeToBaseDirPath);
    if (templateTag != null) deployTag.add(templateTag);

    topLevelTagsCollector.add(deployTag);
    return topLevelTagsCollector;
  }

  private static void addVerboseAttribute(SimpleTag tag, @NotNull AbstractJavaFxPackager packager) {
    JavaFxPackagerConstants.MsgOutputLevel msgOutputLevel = packager.getMsgOutputLevel();
    if (msgOutputLevel != null && msgOutputLevel.isVerbose()) {
      tag.addAttribute(Couple.of("verbose", "true"));
    }
  }

  @NotNull
  private static List<JavaFxManifestAttribute> getManifestAttributes(@NotNull AbstractJavaFxPackager packager) {
    final List<JavaFxManifestAttribute> manifestAttributes = new ArrayList<>();
    final String title = packager.getTitle();
    if (title != null) {
      manifestAttributes.add(new JavaFxManifestAttribute("Implementation-Title", title));
    }
    final String version = packager.getVersion();
    if (version != null) {
      manifestAttributes.add(new JavaFxManifestAttribute("Implementation-Version", version));
    }
    final String vendor = packager.getVendor();
    if (vendor != null) {
      manifestAttributes.add(new JavaFxManifestAttribute("Implementation-Vendor", vendor));
    }
    final List<JavaFxManifestAttribute> customManifestAttributes = packager.getCustomManifestAttributes();
    if (customManifestAttributes != null) {
      manifestAttributes.addAll(customManifestAttributes);
    }
    return manifestAttributes;
  }

  private static SimpleTag appendApplicationIconPath(List<SimpleTag> topLevelTagsCollector,
                                                     JavaFxPackagerConstants.NativeBundles bundle,
                                                     JavaFxApplicationIcons appIcons,
                                                     String relativeToPath) {
    boolean haveAppIcon = false;
    if (appIcons == null || bundle == null || appIcons.isEmpty()) return null;
    if (bundle.isOnLinux()) {
      String iconPath = appIcons.getLinuxIcon(relativeToPath);
      if (!StringUtil.isEmpty(iconPath)) {
        final SimpleTag and = new SimpleTag("and");
        and.add(new SimpleTag("os", Couple.of("family", "unix")));
        final SimpleTag not = new SimpleTag("not");
        not.add(new SimpleTag("os", Couple.of("family", "mac")));
        and.add(not);
        appendIconPropertyTag(topLevelTagsCollector, iconPath, relativeToPath != null, and);
        haveAppIcon = true;
      }
    }
    if (bundle.isOnMac()) {
      String iconPath = appIcons.getMacIcon(relativeToPath);
      if (!StringUtil.isEmpty(iconPath)) {
        appendIconPropertyTag(topLevelTagsCollector, iconPath, relativeToPath != null, new SimpleTag("os", Couple.of("family", "mac")));
        haveAppIcon = true;
      }
    }
    if (bundle.isOnWindows()) {
      String iconPath = appIcons.getWindowsIcon(relativeToPath);
      if (!StringUtil.isEmpty(iconPath)) {
        appendIconPropertyTag(topLevelTagsCollector, iconPath, relativeToPath != null, new SimpleTag("os", Couple.of("family", "windows")));
        haveAppIcon = true;
      }
    }
    if (haveAppIcon) {
      return new SimpleTag("fx:icon", Couple.of("href", "${app.icon.path}"));
    }
    return null;
  }

  private static void appendIconPropertyTag(List<SimpleTag> tagsCollector,
                                            String iconPath,
                                            boolean isRelativeIconPath,
                                            SimpleTag osFamily) {
    final SimpleTag condition = new SimpleTag("condition",
                                              Couple.of("property", "app.icon.path"),
                                              Couple.of("value", isRelativeIconPath ? "${basedir}/" + iconPath : iconPath));
    condition.add(osFamily);
    tagsCollector.add(condition);
  }

  private static SimpleTag createResourcesTag(String preloaderFiles, boolean includeSelf,
                                              String allButPreloader,
                                              String allButSelf,
                                              String all) {
    final SimpleTag resourcesTag = new SimpleTag("fx:resources");
    if (preloaderFiles != null) {
      resourcesTag.add(new SimpleTag("fx:fileset", Couple.of("refid", preloaderFiles)));
      resourcesTag.add(new SimpleTag("fx:fileset", Couple.of("refid", includeSelf ? allButPreloader : allButSelf)));
    } else {
      resourcesTag.add(new SimpleTag("fx:fileset", Couple.of("refid", includeSelf ? all : allButSelf)));
    }
    return resourcesTag;
  }

  private static SimpleTag createTemplateTag(AbstractJavaFxPackager packager, String deployOutDir, String relativeToBaseDirPath) {
    String htmlTemplate = packager.getHtmlTemplateFile();
    if (!StringUtil.isEmpty(htmlTemplate)) {
      final String shortName = new File(htmlTemplate).getName();
      if (!StringUtil.isEmpty(relativeToBaseDirPath)) {
        htmlTemplate = "${basedir}/" + FileUtil.getRelativePath(relativeToBaseDirPath, htmlTemplate, '/');
      }
      return new SimpleTag("fx:template",
                           Couple.of("file", htmlTemplate),
                           Couple.of("tofile", deployOutDir + "/" + shortName));
    }
    return null;
  }

  private static void appendIfNotEmpty(final List<Pair> pairs, final String propertyName, final String propValue) {
    if (!StringUtil.isEmptyOrSpaces(propValue)) {
      pairs.add(Couple.of(propertyName, propValue));
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
                .add(new SimpleTag(paramTagName, Couple.of("name", propName), Couple.of("value", propValue)));
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
    private final List<SimpleTag> mySubTags = new ArrayList<>();
    private final String myValue;

    public SimpleTag(String name, Pair... pairs) {
      myName = name;
      myPairs = new ArrayList<>(Arrays.asList(pairs));
      myValue = null;
    }

    public SimpleTag(String name, Collection<Pair> pairs) {
      myName = name;
      myPairs = new ArrayList<>(pairs);
      myValue = null;
    }

    public SimpleTag(String name, String value) {
      myName = name;
      myPairs = new ArrayList<>();
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
