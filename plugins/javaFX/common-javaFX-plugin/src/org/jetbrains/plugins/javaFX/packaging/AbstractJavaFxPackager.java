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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 3/12/13
 */
public abstract class AbstractJavaFxPackager {
  private static final Logger LOG = Logger.getInstance("#" + AbstractJavaFxPackager.class.getName());

  //artifact description
  protected abstract String getArtifactName();
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


  public void createJarAndDeploy(final String binPath) {
    final String zipPath = getArtifactOutputFilePath();

    final File tempUnzippedArtifactOutput;
    try {
      tempUnzippedArtifactOutput = FileUtil.createTempDirectory("artifact", "unzipped");
      ZipUtil.extract(new File(zipPath), tempUnzippedArtifactOutput, null);
    }
    catch (IOException e) {
      registerJavaFxPackagerError(e);
      return;
    }

    final List<String> commandLine = new ArrayList<String>();
    addParameter(commandLine, FileUtil.toSystemDependentName(binPath + File.separator + "javafxpackager"));

    addParameter(commandLine, "-createJar");
    addParameter(commandLine, "-appclass");
    addParameter(commandLine, getAppClass());

    addParameter(commandLine, "-srcdir");
    addParameter(commandLine, tempUnzippedArtifactOutput.getPath());
    addParameter(commandLine, "-outdir");

    final File tempDirWithJar;
    try {
      tempDirWithJar = FileUtil.createTempDirectory("javafxpackager", "out");
    }
    catch (IOException e) {
      registerJavaFxPackagerError(e);
      return;
    }
    addParameter(commandLine, tempDirWithJar.getPath());
    addParameter(commandLine, "-outfile");

    addParameter(commandLine, getArtifactName());
    addParameter(commandLine, "-v");

    addParameter(commandLine, "-nocss2bin");

    appendManifestProperties(commandLine);

    final int result = startProcess(commandLine);
    if (result == 0) {
      deploy(binPath, tempDirWithJar, tempUnzippedArtifactOutput);
    }
  }

  private void deploy(String binPath,
                      final File tempDirWithCreatedJar,
                      final File tempUnzippedArtifactOutput) {
    final List<String> commandLine = new ArrayList<String>();
    addParameter(commandLine, FileUtil.toSystemDependentName(binPath + File.separator + "javafxpackager"));

    addParameter(commandLine, "-deploy");

    appendIfNotEmpty(commandLine, "-title", getTitle());
    appendIfNotEmpty(commandLine, "-vendor", getVendor());
    appendIfNotEmpty(commandLine, "-description", getDescription());

    addParameter(commandLine, "-appclass");
    addParameter(commandLine, getAppClass());

    addParameter(commandLine, "-width");
    addParameter(commandLine, getWidth());
    addParameter(commandLine, "-height");
    addParameter(commandLine, getHeight());

    appendIfNotEmpty(commandLine, "-htmlparamfile", getHtmlParamFile());
    appendIfNotEmpty(commandLine, "-paramfile", getParamFile());

    addParameter(commandLine, "-updatemode");
    addParameter(commandLine, getUpdateMode());

    addParameter(commandLine, "-name");
    addParameter(commandLine, getArtifactName());


    addParameter(commandLine, "-outdir");

    final File tempDirectory;
    try {
      tempDirectory = FileUtil.createTempDirectory("javafxpackager", "out");
    }
    catch (IOException e) {
      registerJavaFxPackagerError(e);
      return;
    }
    addParameter(commandLine, tempDirectory.getPath());

    addParameter(commandLine, "-outfile");
    addParameter(commandLine, getArtifactName());

    addParameter(commandLine, "-srcdir");
    addParameter(commandLine, tempDirWithCreatedJar.getPath());

    addParameter(commandLine, "-v");

    final int result = startProcess(commandLine);
    FileUtil.delete(tempUnzippedArtifactOutput);
    FileUtil.delete(new File(getArtifactOutputFilePath()));
    copyResultsToArtifactsOutput(tempDirectory);
    copyResultsToArtifactsOutput(tempDirWithCreatedJar);
  }

  private void appendIfNotEmpty(List<String> commandLine, final String propName, String title) {
    if (!StringUtil.isEmptyOrSpaces(title)) {
      addParameter(commandLine, propName);
      addParameter(commandLine, title);
    }
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

  private String getManifestString() {
    final StringBuilder buf = new StringBuilder();
    final String title = getTitle();
    if (!StringUtil.isEmptyOrSpaces(title)) {
      buf.append("Implementation-Title=").append(title).append(";");
    }
    final String vendor = getVendor();
    if (!StringUtil.isEmptyOrSpaces(vendor)) {
      buf.append("Implementation-Vendor=").append(vendor).append(";");
    }
    final int lastIdx = buf.length() - 1;
    if (lastIdx > 0 && buf.charAt(lastIdx) == ';') {
      buf.deleteCharAt(lastIdx);
    }
    return buf.length() == 0 ? null : buf.toString();
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

  private void appendManifestProperties(List<String> commandLine) {
    final String manifestAttr = getManifestString();
    if (manifestAttr != null) {
      addParameter(commandLine, "-manifestAttrs");
      addParameter(commandLine, "\"" + manifestAttr + "\"");
    }
  }

  private int startProcess(List<String> commands) {
    try {
      final Process process = new ProcessBuilder(commands).start();
      return process.waitFor();
    }
    catch (Exception e) {
      registerJavaFxPackagerError(e);
      return -1;
    }
  }
}
