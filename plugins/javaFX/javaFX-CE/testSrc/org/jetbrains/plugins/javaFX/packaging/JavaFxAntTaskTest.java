/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaFxAntTaskTest extends TestCase {

  private static final String PRELOADER_CLASS = "preloaderClass";
  private static final String TITLE = "title";
  private static final String VERSION = "version";
  private static final String ICONS = "icons";
  private static final String BASE_DIR_PATH = "baseDirPath";
  private static final String TEMPLATE = "template";
  private static final String PLACEHOLDER = "placeholder";
  private static final String PRELOADER_JAR = "preloaderJar";
  private static final String SIGNED = "signed";
  private static final String VERBOSE = "verbose";

  public void testJarDeployNoInfo() {
    doTest("<fx:fileset id=\"all_but_jarDeployNoInfo\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployNoInfo.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployNoInfo\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployNoInfo_id\" name=\"jarDeployNoInfo\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployNoInfo.jar\">\n" +
           "<fx:application refid=\"jarDeployNoInfo_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeployNoInfo\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployNoInfo\">\n" +
           "<fx:application refid=\"jarDeployNoInfo_id\">\n" +
           "</fx:application>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeployNoInfo\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n", Collections.emptyMap());
  }

  public void testJarDeployTitle() {
    doTest("<fx:fileset id=\"all_but_jarDeployTitle\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployTitle.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployTitle\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployTitle_id\" name=\"jarDeployTitle\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployTitle.jar\">\n" +
           "<fx:application refid=\"jarDeployTitle_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeployTitle\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "<manifest>\n" +
           "<attribute name=\"Implementation-Title\" value=\"My App\">\n" +
           "</attribute>\n" +
           "</manifest>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployTitle\">\n" +
           "<fx:application refid=\"jarDeployTitle_id\">\n" +
           "</fx:application>\n" +
           "<fx:info title=\"My App\">\n" +
           "</fx:info>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeployTitle\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n", Collections.singletonMap(TITLE, "My App"));
  }

  public void testJarDeployIcon() {
    doTest("<fx:fileset id=\"all_but_jarDeployIcon\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployIcon.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployIcon\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployIcon_id\" name=\"jarDeployIcon\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployIcon.jar\">\n" +
           "<fx:application refid=\"jarDeployIcon_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeployIcon\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<condition property=\"app.icon.path\" value=\"${basedir}/app_icon.png\">\n" +
           "<and>\n" +
           "<os family=\"unix\">\n" +
           "</os>\n" +
           "<not>\n" +
           "<os family=\"mac\">\n" +
           "</os>\n" +
           "</not>\n" +
           "</and>\n" +
           "</condition>\n" +
           "<condition property=\"app.icon.path\" value=\"${basedir}/app_icon.icns\">\n" +
           "<os family=\"mac\">\n" +
           "</os>\n" +
           "</condition>\n" +
           "<condition property=\"app.icon.path\" value=\"${basedir}/app_icon.ico\">\n" +
           "<os family=\"windows\">\n" +
           "</os>\n" +
           "</condition>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployIcon\" nativeBundles=\"all\">\n" +
           "<fx:application refid=\"jarDeployIcon_id\">\n" +
           "</fx:application>\n" +
           "<fx:info>\n" +
           "<fx:icon href=\"${app.icon.path}\">\n" +
           "</fx:icon>\n" +
           "</fx:info>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeployIcon\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n", new ContainerUtil.ImmutableMapBuilder<String, String>()
             .put(ICONS, "/project_dir/app_icon.png,/project_dir/app_icon.icns,/project_dir/app_icon.ico")
             .put(BASE_DIR_PATH, "/project_dir")
             .build());
  }

  public void testJarDeployIconAbsolute() {
    doTest("<fx:fileset id=\"all_but_jarDeployIconAbsolute\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployIconAbsolute.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployIconAbsolute\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployIconAbsolute_id\" name=\"jarDeployIconAbsolute\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployIconAbsolute.jar\">\n" +
           "<fx:application refid=\"jarDeployIconAbsolute_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeployIconAbsolute\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<condition property=\"app.icon.path\" value=\"/project_dir/app_icon.png\">\n" +
           "<and>\n" +
           "<os family=\"unix\">\n" +
           "</os>\n" +
           "<not>\n" +
           "<os family=\"mac\">\n" +
           "</os>\n" +
           "</not>\n" +
           "</and>\n" +
           "</condition>\n" +
           "<condition property=\"app.icon.path\" value=\"/project_dir/app_icon.icns\">\n" +
           "<os family=\"mac\">\n" +
           "</os>\n" +
           "</condition>\n" +
           "<condition property=\"app.icon.path\" value=\"/project_dir/app_icon.ico\">\n" +
           "<os family=\"windows\">\n" +
           "</os>\n" +
           "</condition>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployIconAbsolute\" nativeBundles=\"all\">\n" +
           "<fx:application refid=\"jarDeployIconAbsolute_id\">\n" +
           "</fx:application>\n" +
           "<fx:info>\n" +
           "<fx:icon href=\"${app.icon.path}\">\n" +
           "</fx:icon>\n" +
           "</fx:info>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeployIconAbsolute\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n",
           Collections.singletonMap(ICONS, "/project_dir/app_icon.png,/project_dir/app_icon.icns,/project_dir/app_icon.ico"));
  }

  public void testJarDeployVersion() {
    doTest("<fx:fileset id=\"all_but_jarDeployVersion\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployVersion.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployVersion\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployVersion_id\" name=\"jarDeployVersion\" mainClass=\"Main\" version=\"4.2\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployVersion.jar\">\n" +
           "<fx:application refid=\"jarDeployVersion_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeployVersion\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "<manifest>\n" +
           "<attribute name=\"Implementation-Version\" value=\"4.2\">\n" +
           "</attribute>\n" +
           "</manifest>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployVersion\">\n" +
           "<fx:application refid=\"jarDeployVersion_id\">\n" +
           "</fx:application>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeployVersion\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n", Collections.singletonMap(VERSION, "4.2"));
  }

  public void testJarDeployTemplate() {
    doTest("<fx:fileset id=\"all_but_jarDeployTemplate\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployTemplate.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployTemplate\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployTemplate_id\" name=\"jarDeployTemplate\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployTemplate.jar\">\n" +
           "<fx:application refid=\"jarDeployTemplate_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeployTemplate\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployTemplate\" placeholderId=\"app-placeholder-id\">\n" +
           "<fx:application refid=\"jarDeployTemplate_id\">\n" +
           "</fx:application>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeployTemplate\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "<fx:template file=\"${basedir}/app_template.html\" tofile=\"temp/deploy/app_template.html\">\n" +
           "</fx:template>\n" +
           "</fx:deploy>\n", new ContainerUtil.ImmutableMapBuilder<String, String>()
             .put(TEMPLATE, "/project_dir/app_template.html")
             .put(PLACEHOLDER, "app-placeholder-id")
             .put(BASE_DIR_PATH, "/project_dir")
             .build());
  }

  public void testJarDeploySigned() {
    doTest("<fx:fileset id=\"all_but_jarDeploySigned\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeploySigned.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeploySigned\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeploySigned_id\" name=\"jarDeploySigned\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp" + "/" + "jarDeploySigned.jar\">\n" +
           "<fx:application refid=\"jarDeploySigned_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeploySigned\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp" + "/" + "deploy\" outfile=\"jarDeploySigned\">\n" +
           "<fx:permissions elevated=\"true\">\n" +
           "</fx:permissions>\n" +
           "<fx:application refid=\"jarDeploySigned_id\">\n" +
           "</fx:application>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeploySigned\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n", Collections.singletonMap(SIGNED, "true"));
  }

  public void testJarDeployPreloader() {
    final HashMap<String, String> options = new HashMap<>();
    options.put(PRELOADER_CLASS, "MyPreloader");
    options.put(PRELOADER_JAR, "preloader.jar");
    doTest("<fx:fileset id=\"jarDeployPreloader_preloader_files\" requiredFor=\"preloader\" dir=\"temp\" includes=\"preloader.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_but_preloader_jarDeployPreloader\" dir=\"temp\" excludes=\"preloader.jar\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_but_jarDeployPreloader\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployPreloader.jar\">\n" +
           "</exclude>\n" +
           "<exclude name=\"preloader.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployPreloader\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployPreloader_id\" name=\"jarDeployPreloader\" mainClass=\"Main\" preloaderClass=\"MyPreloader\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployPreloader.jar\">\n" +
           "<fx:application refid=\"jarDeployPreloader_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"jarDeployPreloader_preloader_files\">\n" +
           "</fx:fileset>\n" +
           "<fx:fileset refid=\"all_but_jarDeployPreloader\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployPreloader\">\n" +
           "<fx:application refid=\"jarDeployPreloader_id\">\n" +
           "</fx:application>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"jarDeployPreloader_preloader_files\">\n" +
           "</fx:fileset>\n" +
           "<fx:fileset refid=\"all_but_preloader_jarDeployPreloader\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n", options);
  }

  public void testJarDeployVerbose() {
    doTest("<fx:fileset id=\"all_but_jarDeployVerbose\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeployVerbose.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeployVerbose\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeployVerbose_id\" name=\"jarDeployVerbose\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp/jarDeployVerbose.jar\" verbose=\"true\">\n" +
           "<fx:application refid=\"jarDeployVerbose_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeployVerbose\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp/deploy\" outfile=\"jarDeployVerbose\" verbose=\"true\">\n" +
           "<fx:application refid=\"jarDeployVerbose_id\">\n" +
           "</fx:application>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_jarDeployVerbose\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:deploy>\n", Collections.singletonMap(VERBOSE, "true"));
  }

  private void doTest(final String expected, Map<String, String> options) {
    final String artifactName = UsefulTestCase.getTestName(getName(), true);
    final String artifactFileName = artifactName + ".jar";
    final MockJavaFxPackager packager = new MockJavaFxPackager(artifactName + "/" + artifactFileName);

    final String title = options.get(TITLE);
    if (title != null) {
      packager.setTitle(title);
    }

    final String version = options.get(VERSION);
    if (version != null) {
      packager.setVersion(version);
    }

    String relativeToBaseDirPath = options.get(BASE_DIR_PATH);
    final String icon = options.get(ICONS);
    if (icon != null) {
      final String[] icons = icon.split(",");
      final JavaFxApplicationIcons appIcons = new JavaFxApplicationIcons();
      appIcons.setLinuxIcon(icons[0]);
      appIcons.setMacIcon(icons[1]);
      appIcons.setWindowsIcon(icons[2]);
      packager.setIcons(appIcons);
      packager.setNativeBundle(JavaFxPackagerConstants.NativeBundles.all);
    }

    final String template = options.get(TEMPLATE);
    if (template != null) {
      packager.setHtmlTemplate(template);
    }
    final String placeholder = options.get(PLACEHOLDER);
    if (placeholder != null) {
      packager.setHtmlPlaceholderId(placeholder);
    }

    final String preloaderClass = options.get(PRELOADER_CLASS);
    if (preloaderClass != null) {
      packager.setPreloaderClass(preloaderClass);
    }

    final String preloaderJar = options.get(PRELOADER_JAR);
    if (preloaderJar != null) {
      packager.setPreloaderJar(preloaderJar);
    }

    if (options.containsKey(SIGNED)) {
      packager.setSigned(true);
    }

    if (options.containsKey(VERBOSE)) {
      packager.setMsgOutputLevel(JavaFxPackagerConstants.MsgOutputLevel.Verbose);
    }

    final List<JavaFxAntGenerator.SimpleTag> temp = JavaFxAntGenerator
      .createJarAndDeployTasks(packager, artifactFileName, artifactName, "temp", "temp" + "/" + "deploy", relativeToBaseDirPath);
    final StringBuilder buf = new StringBuilder();
    for (JavaFxAntGenerator.SimpleTag tag : temp) {
      tag.generate(buf);
    }
    assertEquals(expected, buf.toString());
  }

  private static class MockJavaFxPackager extends AbstractJavaFxPackager {

    private String myOutputPath;
    private String myTitle;
    private String myVendor;
    private String myDescription;
    private String myVersion;
    private String myHtmlTemplate;
    private String myHtmlPlaceholderId;
    private String myHtmlParams;
    private String myParams;
    private String myPreloaderClass;
    private String myPreloaderJar;
    private boolean myConvertCss2Bin;
    private boolean mySigned;
    private List<JavaFxManifestAttribute> myCustomManifestAttributes;
    private JavaFxApplicationIcons myIcons;
    private JavaFxPackagerConstants.NativeBundles myNativeBundle = JavaFxPackagerConstants.NativeBundles.none;
    private JavaFxPackagerConstants.MsgOutputLevel myMsgOutputLevel = JavaFxPackagerConstants.MsgOutputLevel.Default;

    private MockJavaFxPackager(String outputPath) {
      myOutputPath = outputPath;
    }

    private void setTitle(String title) {
      myTitle = title;
    }

    private void setVendor(String vendor) {
      myVendor = vendor;
    }

    private void setDescription(String description) {
      myDescription = description;
    }

    private void setVersion(String version) {
      myVersion = version;
    }

    private void setHtmlTemplate(String htmlTemplate) {
      myHtmlTemplate = htmlTemplate;
    }

    private void setHtmlPlaceholderId(String htmlPlaceholderId) {
      myHtmlPlaceholderId = htmlPlaceholderId;
    }

    private void setHtmlParams(String htmlParams) {
      myHtmlParams = htmlParams;
    }

    private void setParams(String params) {
      myParams = params;
    }

    private void setPreloaderClass(String preloaderClass) {
      myPreloaderClass = preloaderClass;
    }

    private void setPreloaderJar(String preloaderJar) {
      myPreloaderJar = preloaderJar;
    }

    public void setSigned(boolean signed) {
      mySigned = signed;
    }

    public void setIcons(JavaFxApplicationIcons icons) {
      myIcons = icons;
    }

    public void setNativeBundle(JavaFxPackagerConstants.NativeBundles nativeBundle) {
      myNativeBundle = nativeBundle;
    }

    public void setMsgOutputLevel(JavaFxPackagerConstants.MsgOutputLevel msgOutputLevel) {
      myMsgOutputLevel = msgOutputLevel;
    }

    @Override
    protected String getArtifactName() {
      return getArtifactRootName();
    }

    @Override
    protected String getArtifactOutputPath() {
      return new File(myOutputPath).getParent();
    }

    @Override
    protected String getArtifactOutputFilePath() {
      return myOutputPath;
    }

    @Override
    protected String getAppClass() {
      return "Main";
    }

    @Override
    protected String getTitle() {
      return myTitle;
    }

    @Override
    protected String getVendor() {
      return myVendor;
    }

    @Override
    protected String getDescription() {
      return myDescription;
    }

    @Override
    public String getVersion() {
      return myVersion;
    }

    @Override
    protected String getWidth() {
      return "800";
    }

    @Override
    protected String getHeight() {
      return "400";
    }

    @Override
    public String getHtmlTemplateFile() {
      return myHtmlTemplate;
    }

    @Override
    public String getHtmlPlaceholderId() {
      return myHtmlPlaceholderId;
    }

    @Override
    protected String getHtmlParamFile() {
      return myHtmlParams;
    }

    @Override
    protected String getParamFile() {
      return myParams;
    }

    @Override
    protected String getUpdateMode() {
      return JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND;
    }

    @Override
    protected JavaFxPackagerConstants.NativeBundles getNativeBundle() {
      return myNativeBundle;
    }

    @Override
    protected void registerJavaFxPackagerError(String message) {
    }

    @Override
    protected void registerJavaFxPackagerInfo(String message) {
    }

    @Override
    public String getKeypass() {
      return null;
    }

    @Override
    public String getStorepass() {
      return null;
    }

    @Override
    public String getKeystore() {
      return null;
    }

    @Override
    public String getAlias() {
      return null;
    }

    @Override
    public boolean isSelfSigning() {
      return true;
    }

    @Override
    public boolean isEnabledSigning() {
      return mySigned;
    }

    @Override
    public String getPreloaderClass() {
      return myPreloaderClass;
    }

    @Override
    public String getPreloaderJar() {
      return myPreloaderJar;
    }

    @Override
    public boolean convertCss2Bin() {
      return myConvertCss2Bin;
    }

    @Override
    public List<JavaFxManifestAttribute> getCustomManifestAttributes() {
      return myCustomManifestAttributes;
    }

    @Override
    public JavaFxApplicationIcons getIcons() {
      return myIcons;
    }

    @Override
    public JavaFxPackagerConstants.MsgOutputLevel getMsgOutputLevel() {
      return myMsgOutputLevel;
    }
  }
}
