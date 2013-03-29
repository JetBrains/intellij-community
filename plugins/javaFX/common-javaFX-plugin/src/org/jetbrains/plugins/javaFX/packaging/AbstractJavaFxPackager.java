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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Base64Converter;
import com.intellij.util.PathUtilRt;
import com.intellij.util.io.ZipUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 3/12/13
 */
public abstract class AbstractJavaFxPackager {
  private static final Logger LOG = Logger.getInstance("#" + AbstractJavaFxPackager.class.getName());

  //artifact description
  protected String getArtifactRootName() {
    return PathUtilRt.getFileName(getArtifactOutputFilePath());
  }

  protected abstract String getArtifactOutputPath();

  protected abstract String getArtifactOutputFilePath();

  //artifact properties
  protected abstract String getAppClass();

  protected abstract String getTitle();

  protected abstract String getVendor();

  protected abstract String getDescription();

  protected abstract String getWidth();

  protected abstract String getHeight();

  protected abstract String getHtmlParamFile();

  protected abstract String getParamFile();

  protected abstract String getUpdateMode();

  protected abstract void registerJavaFxPackagerError(final String message);


  public void buildJavaFxArtifact(final String homePath) {
    if (!checkNotEmpty(getAppClass(), "Application class")) return;
    if (!checkNotEmpty(getWidth(), "Width")) return;
    if (!checkNotEmpty(getHeight(), "Height")) return;

    final String zipPath = getArtifactOutputFilePath();

    final File tempUnzippedArtifactOutput;
    try {
      tempUnzippedArtifactOutput = FileUtil.createTempDirectory("artifact", "unzipped");
      ZipUtil.extract(new File(zipPath), tempUnzippedArtifactOutput, null);
      copyLibraries(zipPath, tempUnzippedArtifactOutput);
    }
    catch (IOException e) {
      registerJavaFxPackagerError(e);
      return;
    }

    final File tempDirectory = new File(tempUnzippedArtifactOutput, "deploy");
    try {

      final StringBuilder buf = new StringBuilder();
      buf.append("<project default=\"build artifact\">\n");
      buf.append("<taskdef resource=\"com/sun/javafx/tools/ant/antlib.xml\" uri=\"javafx:com.sun.javafx.tools.ant\" ")
         .append("classpath=\"").append(homePath).append("/lib/ant-javafx.jar\"/>\n");
      buf.append("<target name=\"build artifact\" xmlns:fx=\"javafx:com.sun.javafx.tools.ant\">");
      final String artifactFileName = getArtifactRootName();
      final String artifactName = FileUtil.getNameWithoutExtension(artifactFileName);
      final List<JavaFxAntGenerator.SimpleTag> tags = 
        JavaFxAntGenerator.createJarAndDeployTasks(this, artifactFileName, artifactName, tempUnzippedArtifactOutput.getPath());
      for (JavaFxAntGenerator.SimpleTag tag : tags) {
        tag.generate(buf);
      }
      buf.append("</target>");
      buf.append("</project>");

      final int result = startAntTarget(buf.toString(), homePath);
      if (result == 0) {
        if (isEnabledSigning()) {
          signApp(homePath + File.separator + "bin", tempDirectory);
        }
      }
      else {
        registerJavaFxPackagerError("fx:deploy task has failed.");
      }
    }
    finally {
      copyResultsToArtifactsOutput(tempDirectory);
      FileUtil.delete(tempUnzippedArtifactOutput);
    }
  }

  private void copyLibraries(String zipPath, File tempUnzippedArtifactOutput) throws IOException {
    final String preloaderJar = getPreloaderJar();
    if (preloaderJar != null) {
      final File preloaderJarFile = new File(getArtifactOutputPath(), preloaderJar);
      if (preloaderJarFile.isFile()) {
        FileUtil.copy(preloaderJarFile, new File(tempUnzippedArtifactOutput, preloaderJarFile.getName()));
      }
    }
  }

  private boolean checkNotEmpty(final String text, final String title) {
    if (StringUtil.isEmptyOrSpaces(text)) {
      registerJavaFxPackagerError("Unable to build JavaFX artifact. " + title + " should be specified in artifact's settings.");
      return false;
    }
    return true;
  }

  private void signApp(String binPath, File tempDirectory) {
    final boolean selfSigning = isSelfSigning();
    final int genResult = selfSigning ? genKey(binPath) : 0;
    if (genResult == 0) {
      sign(binPath, selfSigning, tempDirectory.getPath() + File.separator + getArtifactRootName());
      final String preloaderJar = getPreloaderJar();
      if (preloaderJar != null) {
        sign(binPath, selfSigning, tempDirectory.getPath() + File.separator + preloaderJar);
      }
    } else {
      registerJavaFxPackagerError("JavaFX generate certificate task has failed.");
    }
  }

  private void sign(String binPath, boolean selfSigning, final String jar2Sign) {
    final List<String> signCommandLine = new ArrayList<String>();
    addParameter(signCommandLine, FileUtil.toSystemDependentName(binPath + File.separator + "jarsigner"));

    collectStoreParams(selfSigning, signCommandLine);

    addParameter(signCommandLine, jar2Sign);
    addParameter(signCommandLine, getAlias(selfSigning));

    final int signedResult = startProcess(signCommandLine);
    if (signedResult != 0) {
      registerJavaFxPackagerError("JavaFX sign task has failed.");
    }
  }

  private int genKey(String binPath) {
    final String keyStorePath = getKeystore(true);
    final File keyStoreFile = new File(keyStorePath);
    if (keyStoreFile.isFile()) {
      FileUtil.delete(keyStoreFile);
    }

    final List<String> genCommandLine = new ArrayList<String>();
    addParameter(genCommandLine, FileUtil.toSystemDependentName(binPath + File.separator + "keytool"));

    addParameter(genCommandLine, "-genkeypair");

    addParameter(genCommandLine, "-dname");
    String vendor = getVendor();
    if (StringUtil.isEmptyOrSpaces(vendor)) {
      vendor = "jb-fx-build";
    }
    addParameter(genCommandLine, "CN=" + vendor.replaceAll(",", "\\\\,"));

    addParameter(genCommandLine, "-alias");
    addParameter(genCommandLine, getAlias(true));

    collectStoreParams(true, genCommandLine);

    return startProcess(genCommandLine);
  }

  private void collectStoreParams(boolean selfSigning, List<String> signCommandLine) {
    addParameter(signCommandLine, "-keyStore");
    addParameter(signCommandLine, getKeystore(selfSigning));

    addParameter(signCommandLine, "-storepass");
    addParameter(signCommandLine, getStorepass(selfSigning));

    addParameter(signCommandLine, "-keypass");
    addParameter(signCommandLine, getKeypass(selfSigning));
  }

  private void copyResultsToArtifactsOutput(final File tempDirectory) {
    try {
      final File resultedJar = new File(getArtifactOutputPath());
      FileUtil.copyDir(tempDirectory, resultedJar);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    FileUtil.delete(tempDirectory);
  }

  private void registerJavaFxPackagerError(Exception ex) {
    registerJavaFxPackagerError(ex.getMessage());
  }

  protected abstract String prepareParam(String param);
  private void addParameter(List<String> commandLine, String param) {
    if (!StringUtil.isEmptyOrSpaces(param)) {
      commandLine.add(prepareParam(param));
    }
  }

  private int startProcess(List<String> commands) {
    try {
      final Process process = new ProcessBuilder(commands).start();
      final String message = new String(FileUtil.loadBytes(process.getErrorStream()));
      if (!StringUtil.isEmptyOrSpaces(message)) {
        registerJavaFxPackagerError(message);
      }
      return process.waitFor();
    }
    catch (Exception e) {
      registerJavaFxPackagerError(e);
      return -1;
    }
  }

  private int startAntTarget(String buildText, String javaHome) {
    final String antHome = getAntHome();
    if (antHome == null) {
      registerJavaFxPackagerError("Bundled ant not found.");
      return -1;
    }
    final ArrayList<String> commands = new ArrayList<String>();
    commands.add(javaHome + File.separator + "bin" + File.separator + "java");

    commands.add("-Dant.home=" + antHome);

    commands.add("-classpath");
    commands.add(antHome + "/lib/ant.jar" + File.pathSeparator + 
                 antHome + "/lib/ant-launcher.jar" + File.pathSeparator +
                 javaHome + "/lib/ant-javafx.jar");
    commands.add("org.apache.tools.ant.launch.Launcher");
    commands.add("-f");
    try {
      File tempFile = FileUtil.createTempFile("build", ".xml");
      tempFile.deleteOnExit();
      OutputStream outputStream = new FileOutputStream(tempFile.getAbsolutePath());
      try {
        outputStream.write(buildText.getBytes(Charset.defaultCharset()));
      }
      finally {
        outputStream.close();
      }
      commands.add(tempFile.getCanonicalPath());
    }
    catch (IOException e) {
      registerJavaFxPackagerError(e);
      return -1;
    }
    return startProcess(commands);
  }

  private static String getAntHome() {
    final String appHome = PathManager.getHomePath();
    if (appHome == null) {
      return null;
    }

    File antHome = new File(appHome, "lib" + File.separator + "ant");
    if (!antHome.exists()) {
      File communityAntHome = new File(appHome, "community" + File.separator + "lib" + File.separator + "ant");
      if (communityAntHome.exists()) {
        antHome = communityAntHome;
      }
    }

    if (!antHome.exists()) {
      return null;
    }

    return antHome.getPath();
  }

  private String getAlias(boolean selfSigning) {
    return selfSigning ? "jb" : getAlias();
  }

  private String getKeypass(boolean selfSigning) {
    return selfSigning ? "keypass" : Base64Converter.decode(getKeypass());
  }

  private String getKeystore(boolean selfSigning) {
    return selfSigning ? getArtifactOutputPath() + File.separator + "jb-jfx.jks" : getKeystore();
  }

  private String getStorepass(boolean selfSigning) {
    return selfSigning ? "storepass" : Base64Converter.decode(getStorepass());
  }

  public abstract String getKeypass();

  public abstract String getStorepass();

  public abstract String getKeystore();

  public abstract String getAlias();

  public abstract boolean isSelfSigning();

  public abstract boolean isEnabledSigning();

  public abstract String getPreloaderClass();

  public abstract String getPreloaderJar();
}
