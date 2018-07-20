// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.coverage;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IDEACoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance(IDEACoverageRunner.class);

  public ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable final CoverageSuite coverageSuite) {
    ProjectData projectData = ProjectDataLoader.load(sessionDataFile);
    File sourceMapFile = new File(JavaCoverageEnabledConfiguration.getSourceMapPath(sessionDataFile.getPath()));
    if (sourceMapFile.exists()) {
      try {
        loadSourceMap(projectData, sourceMapFile);
      }
      catch (IOException e) {
        LOG.warn("Error reading source map associated with coverage data", e);
      }
    }
    return projectData;
  }

  public void loadSourceMap(ProjectData projectData, File sourceMapFile) throws IOException {
    Map map = SaveHook.loadSourceMapFromFile(new HashMap(), sourceMapFile);
    for (Object o : map.entrySet()) {
      @SuppressWarnings("unchecked") Map.Entry<String, String> entry = (Map.Entry<String, String>)o;
      String className = entry.getKey();
      String source = entry.getValue();
      ClassData data = projectData.getClassData(className);
      if (data != null) {
        data.setSource(source);
      }
    }
  }

  @Override
  public void appendCoverageArgument(String sessionDataFilePath,
                                     @Nullable String[] patterns,
                                     SimpleJavaParameters parameters,
                                     boolean collectLineInfo,
                                     boolean isSampling) {
    appendCoverageArgument(sessionDataFilePath, patterns, null, parameters, collectLineInfo, isSampling, null);
  }

  public void appendCoverageArgument(final String sessionDataFilePath,
                                     final String[] patterns,
                                     final String[] excludePatterns,
                                     final SimpleJavaParameters javaParameters,
                                     final boolean collectLineInfo,
                                     final boolean isSampling,
                                     @Nullable String sourceMapPath) {
    StringBuilder argument = new StringBuilder("-javaagent:");
    String agentPath = handleSpacesInAgentPath(PathUtil.getJarPathForClass(ProjectData.class));
    if (agentPath == null) return;
    argument.append(agentPath);
    argument.append("=");
    try {
      final File tempFile = createTempFile();
      tempFile.deleteOnExit();
      write2file(tempFile, sessionDataFilePath);
      write2file(tempFile, String.valueOf(collectLineInfo));
      write2file(tempFile, Boolean.FALSE.toString()); //append unloaded
      write2file(tempFile, Boolean.FALSE.toString());//merge with existing
      write2file(tempFile, String.valueOf(isSampling));
      if (sourceMapPath != null) {
        write2file(tempFile, Boolean.TRUE.toString());
        write2file(tempFile, sourceMapPath);
      }
      if (patterns != null) {
        writePatterns(tempFile, patterns);
      }
      if (excludePatterns != null) {
        write2file(tempFile, "-exclude");
        writePatterns(tempFile, excludePatterns);
      }
      argument.append(tempFile.getCanonicalPath());
    }
    catch (IOException e) {
      LOG.info("Coverage was not enabled", e);
      return;
    }

    javaParameters.getVMParametersList().add(argument.toString());
  }

  private static void writePatterns(File tempFile, String[] patterns) throws IOException {
    for (String coveragePattern : patterns) {
      coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
      if (!coveragePattern.endsWith(".*")) { //include inner classes
        coveragePattern += "(\\$.*)*";
      }
      write2file(tempFile, coveragePattern);
    }
  }


  public String getPresentableName() {
    return "IntelliJ IDEA";
  }

  public String getId() {
    return "idea";
  }

  public String getDataFileExtension() {
    return "ic";
  }

  @Override
  public boolean isCoverageByTestApplicable() {
    return true;
  }
}