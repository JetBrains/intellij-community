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
import com.intellij.testFramework.UsefulTestCase;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 3/28/13
 */
public class JavaFxAntTaskTest extends UsefulTestCase{

  private static final String PRELOADER_CLASS = "preloaderClass";
  private static final String TITLE = "title";
  private static final String PRELOADER_JAR = "preloaderJar";
  private static final String SIGNED = "signed";

  public void testJarDeployNoInfo() throws Exception {
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
           "</fx:deploy>\n", Collections.<String, String>emptyMap());
  }

  public void testJarDeployTitle() throws Exception {
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

  public void testJarDeploySigned() throws Exception {
    doTest("<fx:fileset id=\"all_but_jarDeploySigned\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "<exclude name=\"jarDeploySigned.jar\">\n" +
           "</exclude>\n" +
           "</fx:fileset>\n" +
           "<fx:fileset id=\"all_jarDeploySigned\" dir=\"temp\" includes=\"**/*.jar\">\n" +
           "</fx:fileset>\n" +
           "<fx:application id=\"jarDeploySigned_id\" name=\"jarDeploySigned\" mainClass=\"Main\">\n" +
           "</fx:application>\n" +
           "<fx:jar destfile=\"temp" + File.separator + "jarDeploySigned.jar\">\n" +
           "<fx:application refid=\"jarDeploySigned_id\">\n" +
           "</fx:application>\n" +
           "<fileset dir=\"temp\" excludes=\"**/*.jar\">\n" +
           "</fileset>\n" +
           "<fx:resources>\n" +
           "<fx:fileset refid=\"all_but_jarDeploySigned\">\n" +
           "</fx:fileset>\n" +
           "</fx:resources>\n" +
           "</fx:jar>\n" +
           "<fx:deploy width=\"800\" height=\"400\" updatemode=\"background\" outdir=\"temp" + File.separator + "deploy\" outfile=\"jarDeploySigned\">\n" +
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

  public void testJarDeployPreloader() throws Exception {
    final HashMap<String, String> options = new HashMap<String, String>();
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

  private void doTest(final String expected, Map<String, String> options) {
    final String artifactName = getTestName(true);
    final String artifactFileName = artifactName + ".jar";
    final MockJavaFxPackager packager = new MockJavaFxPackager(artifactName + File.separator + artifactFileName);

    final String title = options.get(TITLE);
    if (title != null) {
      packager.setTitle(title);
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

    final List<JavaFxAntGenerator.SimpleTag> temp = JavaFxAntGenerator
      .createJarAndDeployTasks(packager, artifactFileName, artifactName, "temp");
    final StringBuilder buf = new StringBuilder();
    for (JavaFxAntGenerator.SimpleTag tag : temp) {
      tag.generate(buf);
    }
    assertEquals(expected
                   .replaceAll("temp/deploy", "temp\\" + File.separator + "deploy")
                   .replaceAll("temp/" + artifactFileName, "temp\\" + File.separator + artifactFileName),
                 buf.toString());
  }

  private static class MockJavaFxPackager extends AbstractJavaFxPackager {

    private String myOutputPath;
    private String myTitle;
    private String myVendor;
    private String myDescription;
    private String myHtmlParams;
    private String myParams;
    private String myPreloaderClass;
    private String myPreloaderJar;
    private boolean myConvertCss2Bin;
    private boolean mySigned;
    private List<JavaFxManifestAttribute> myCustomManifestAttributes;

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
    protected String getWidth() {
      return "800";
    }

    @Override
    protected String getHeight() {
      return "400";
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
      return JavaFxPackagerConstants.NativeBundles.none;
    }

    @Override
    protected void registerJavaFxPackagerError(String message) {
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
  }
}
