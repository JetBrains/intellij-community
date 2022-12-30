// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    doTest("""
             <fx:fileset id="all_but_jarDeployNoInfo" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployNoInfo.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployNoInfo" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployNoInfo_id" name="jarDeployNoInfo" mainClass="Main">
             </fx:application>
             <fx:jar destfile="temp/jarDeployNoInfo.jar">
             <fx:application refid="jarDeployNoInfo_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeployNoInfo">
             </fx:fileset>
             </fx:resources>
             </fx:jar>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployNoInfo">
             <fx:application refid="jarDeployNoInfo_id">
             </fx:application>
             <fx:resources>
             <fx:fileset refid="all_jarDeployNoInfo">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """, Collections.emptyMap());
  }

  public void testJarDeployTitle() {
    doTest("""
             <fx:fileset id="all_but_jarDeployTitle" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployTitle.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployTitle" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployTitle_id" name="jarDeployTitle" mainClass="Main">
             </fx:application>
             <fx:jar destfile="temp/jarDeployTitle.jar">
             <fx:application refid="jarDeployTitle_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeployTitle">
             </fx:fileset>
             </fx:resources>
             <manifest>
             <attribute name="Implementation-Title" value="My App">
             </attribute>
             </manifest>
             </fx:jar>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployTitle">
             <fx:application refid="jarDeployTitle_id">
             </fx:application>
             <fx:info title="My App">
             </fx:info>
             <fx:resources>
             <fx:fileset refid="all_jarDeployTitle">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """, Collections.singletonMap(TITLE, "My App"));
  }

  public void testJarDeployIcon() {
    doTest("""
             <fx:fileset id="all_but_jarDeployIcon" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployIcon.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployIcon" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployIcon_id" name="jarDeployIcon" mainClass="Main">
             </fx:application>
             <fx:jar destfile="temp/jarDeployIcon.jar">
             <fx:application refid="jarDeployIcon_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeployIcon">
             </fx:fileset>
             </fx:resources>
             </fx:jar>
             <condition property="app.icon.path" value="${basedir}/app_icon.png">
             <and>
             <os family="unix">
             </os>
             <not>
             <os family="mac">
             </os>
             </not>
             </and>
             </condition>
             <condition property="app.icon.path" value="${basedir}/app_icon.icns">
             <os family="mac">
             </os>
             </condition>
             <condition property="app.icon.path" value="${basedir}/app_icon.ico">
             <os family="windows">
             </os>
             </condition>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployIcon" nativeBundles="all">
             <fx:application refid="jarDeployIcon_id">
             </fx:application>
             <fx:info>
             <fx:icon href="${app.icon.path}">
             </fx:icon>
             </fx:info>
             <fx:resources>
             <fx:fileset refid="all_jarDeployIcon">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """, new ContainerUtil.ImmutableMapBuilder<String, String>()
             .put(ICONS, "/project_dir/app_icon.png,/project_dir/app_icon.icns,/project_dir/app_icon.ico")
             .put(BASE_DIR_PATH, "/project_dir")
             .build());
  }

  public void testJarDeployIconAbsolute() {
    doTest("""
             <fx:fileset id="all_but_jarDeployIconAbsolute" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployIconAbsolute.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployIconAbsolute" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployIconAbsolute_id" name="jarDeployIconAbsolute" mainClass="Main">
             </fx:application>
             <fx:jar destfile="temp/jarDeployIconAbsolute.jar">
             <fx:application refid="jarDeployIconAbsolute_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeployIconAbsolute">
             </fx:fileset>
             </fx:resources>
             </fx:jar>
             <condition property="app.icon.path" value="/project_dir/app_icon.png">
             <and>
             <os family="unix">
             </os>
             <not>
             <os family="mac">
             </os>
             </not>
             </and>
             </condition>
             <condition property="app.icon.path" value="/project_dir/app_icon.icns">
             <os family="mac">
             </os>
             </condition>
             <condition property="app.icon.path" value="/project_dir/app_icon.ico">
             <os family="windows">
             </os>
             </condition>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployIconAbsolute" nativeBundles="all">
             <fx:application refid="jarDeployIconAbsolute_id">
             </fx:application>
             <fx:info>
             <fx:icon href="${app.icon.path}">
             </fx:icon>
             </fx:info>
             <fx:resources>
             <fx:fileset refid="all_jarDeployIconAbsolute">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """,
           Collections.singletonMap(ICONS, "/project_dir/app_icon.png,/project_dir/app_icon.icns,/project_dir/app_icon.ico"));
  }

  public void testJarDeployVersion() {
    doTest("""
             <fx:fileset id="all_but_jarDeployVersion" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployVersion.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployVersion" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployVersion_id" name="jarDeployVersion" mainClass="Main" version="4.2">
             </fx:application>
             <fx:jar destfile="temp/jarDeployVersion.jar">
             <fx:application refid="jarDeployVersion_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeployVersion">
             </fx:fileset>
             </fx:resources>
             <manifest>
             <attribute name="Implementation-Version" value="4.2">
             </attribute>
             </manifest>
             </fx:jar>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployVersion">
             <fx:application refid="jarDeployVersion_id">
             </fx:application>
             <fx:resources>
             <fx:fileset refid="all_jarDeployVersion">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """, Collections.singletonMap(VERSION, "4.2"));
  }

  public void testJarDeployTemplate() {
    doTest("""
             <fx:fileset id="all_but_jarDeployTemplate" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployTemplate.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployTemplate" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployTemplate_id" name="jarDeployTemplate" mainClass="Main">
             </fx:application>
             <fx:jar destfile="temp/jarDeployTemplate.jar">
             <fx:application refid="jarDeployTemplate_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeployTemplate">
             </fx:fileset>
             </fx:resources>
             </fx:jar>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployTemplate" placeholderId="app-placeholder-id">
             <fx:application refid="jarDeployTemplate_id">
             </fx:application>
             <fx:resources>
             <fx:fileset refid="all_jarDeployTemplate">
             </fx:fileset>
             </fx:resources>
             <fx:template file="${basedir}/app_template.html" tofile="temp/deploy/app_template.html">
             </fx:template>
             </fx:deploy>
             """, new ContainerUtil.ImmutableMapBuilder<String, String>()
             .put(TEMPLATE, "/project_dir/app_template.html")
             .put(PLACEHOLDER, "app-placeholder-id")
             .put(BASE_DIR_PATH, "/project_dir")
             .build());
  }

  public void testJarDeploySigned() {
    doTest("""
             <fx:fileset id="all_but_jarDeploySigned" dir="temp" includes="**/*.jar">
             <exclude name="jarDeploySigned.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeploySigned" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeploySigned_id" name="jarDeploySigned" mainClass="Main">
             </fx:application>
             <fx:jar destfile="temp/jarDeploySigned.jar">
             <fx:application refid="jarDeploySigned_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeploySigned">
             </fx:fileset>
             </fx:resources>
             </fx:jar>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeploySigned">
             <fx:permissions elevated="true">
             </fx:permissions>
             <fx:application refid="jarDeploySigned_id">
             </fx:application>
             <fx:resources>
             <fx:fileset refid="all_jarDeploySigned">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """, Collections.singletonMap(SIGNED, "true"));
  }

  public void testJarDeployPreloader() {
    final HashMap<String, String> options = new HashMap<>();
    options.put(PRELOADER_CLASS, "MyPreloader");
    options.put(PRELOADER_JAR, "preloader.jar");
    doTest("""
             <fx:fileset id="jarDeployPreloader_preloader_files" requiredFor="preloader" dir="temp" includes="preloader.jar">
             </fx:fileset>
             <fx:fileset id="all_but_preloader_jarDeployPreloader" dir="temp" excludes="preloader.jar" includes="**/*.jar">
             </fx:fileset>
             <fx:fileset id="all_but_jarDeployPreloader" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployPreloader.jar">
             </exclude>
             <exclude name="preloader.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployPreloader" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployPreloader_id" name="jarDeployPreloader" mainClass="Main" preloaderClass="MyPreloader">
             </fx:application>
             <fx:jar destfile="temp/jarDeployPreloader.jar">
             <fx:application refid="jarDeployPreloader_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="jarDeployPreloader_preloader_files">
             </fx:fileset>
             <fx:fileset refid="all_but_jarDeployPreloader">
             </fx:fileset>
             </fx:resources>
             </fx:jar>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployPreloader">
             <fx:application refid="jarDeployPreloader_id">
             </fx:application>
             <fx:resources>
             <fx:fileset refid="jarDeployPreloader_preloader_files">
             </fx:fileset>
             <fx:fileset refid="all_but_preloader_jarDeployPreloader">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """, options);
  }

  public void testJarDeployVerbose() {
    doTest("""
             <fx:fileset id="all_but_jarDeployVerbose" dir="temp" includes="**/*.jar">
             <exclude name="jarDeployVerbose.jar">
             </exclude>
             </fx:fileset>
             <fx:fileset id="all_jarDeployVerbose" dir="temp" includes="**/*.jar">
             </fx:fileset>
             <fx:application id="jarDeployVerbose_id" name="jarDeployVerbose" mainClass="Main">
             </fx:application>
             <fx:jar destfile="temp/jarDeployVerbose.jar" verbose="true">
             <fx:application refid="jarDeployVerbose_id">
             </fx:application>
             <fileset dir="temp" excludes="**/*.jar">
             </fileset>
             <fx:resources>
             <fx:fileset refid="all_but_jarDeployVerbose">
             </fx:fileset>
             </fx:resources>
             </fx:jar>
             <fx:deploy width="800" height="400" updatemode="background" outdir="temp/deploy" outfile="jarDeployVerbose" verbose="true">
             <fx:application refid="jarDeployVerbose_id">
             </fx:application>
             <fx:resources>
             <fx:fileset refid="all_jarDeployVerbose">
             </fx:fileset>
             </fx:resources>
             </fx:deploy>
             """, Collections.singletonMap(VERBOSE, "true"));
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

  private static final class MockJavaFxPackager extends AbstractJavaFxPackager {

    private final String myOutputPath;
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
