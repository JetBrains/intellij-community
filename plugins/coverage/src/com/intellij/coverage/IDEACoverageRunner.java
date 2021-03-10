// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class IDEACoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance(IDEACoverageRunner.class);

  @Override
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
                                     String @Nullable [] patterns,
                                     SimpleJavaParameters parameters,
                                     boolean collectLineInfo,
                                     boolean isSampling) {
    appendCoverageArgument(sessionDataFilePath, patterns, null, parameters, collectLineInfo, isSampling, null);
  }

  @Override
  public void appendCoverageArgument(final String sessionDataFilePath,
                                     final String[] patterns,
                                     final String[] excludePatterns,
                                     final SimpleJavaParameters javaParameters,
                                     final boolean collectLineInfo,
                                     final boolean isSampling,
                                     @Nullable String sourceMapPath) {
    String agentPath = handleSpacesInAgentPath(PathUtil.getJarPathForClass(ProjectData.class));
    if (agentPath == null) return;
    List<Function<TargetEnvironmentRequest, JavaTargetParameter>> targetParameters =
      javaParameters.getTargetDependentParameters().asTargetParameters();
    targetParameters.add(request -> {
      return createArgumentTargetParameter(agentPath, sessionDataFilePath,
                                           patterns, excludePatterns,
                                           collectLineInfo, isSampling, sourceMapPath);
    });
    if (!Registry.is("idea.coverage.thread.safe.enabled")) {
      targetParameters.add(request -> {
        return JavaTargetParameter.fixed("-Didea.coverage.thread-safe.enabled=false");
      });
    }
    if (Registry.is("idea.coverage.new.sampling.enabled")) {
      targetParameters.add(request -> {
        return JavaTargetParameter.fixed("-Didea.new.sampling.coverage=true");
      });
    }
  }

  @Nullable
  private static JavaTargetParameter createArgumentTargetParameter(String agentPath,
                                                                   String sessionDataFilePath,
                                                                   String @Nullable [] patterns,
                                                                   String[] excludePatterns,
                                                                   boolean collectLineInfo,
                                                                   boolean isSampling,
                                                                   String sourceMapPath) {
    HashSet<String> uploadPaths = ContainerUtil.newHashSet(sessionDataFilePath, agentPath);
    HashSet<String> downloadPaths = ContainerUtil.newHashSet(sessionDataFilePath);
    try {
      final File tempFile = createTempFile();
      tempFile.deleteOnExit();
      Ref<Boolean> writeOnceRef = new Ref<>(false);
      uploadPaths.add(tempFile.getCanonicalPath());

      return new JavaTargetParameter.Builder(uploadPaths, downloadPaths)
        .fixed("-javaagent:")
        .resolved(agentPath)
        .fixed("=")
        .resolved(tempFile.getCanonicalPath())
        .doWithResolved(sessionDataFilePath, targetSessionDataPath -> {
          if (!writeOnceRef.get()) {
            try {
              writeOptionsToFile(tempFile, targetSessionDataPath, patterns, excludePatterns, collectLineInfo, isSampling, sourceMapPath);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
            finally {
              writeOnceRef.set(true);
            }
          }
          return Unit.INSTANCE;
        })
        .build();
    }
    catch (IOException e) {
      LOG.info("Coverage was not enabled", e);
      return null;
    }
  }

  private static void writeOptionsToFile(File file,
                                         String sessionDataFilePath,
                                         String @Nullable [] patterns,
                                         String[] excludePatterns,
                                         boolean collectLineInfo,
                                         boolean isSampling,
                                         String sourceMapPath) throws IOException {
    write2file(file, sessionDataFilePath);
    write2file(file, String.valueOf(collectLineInfo));
    write2file(file, Boolean.FALSE.toString()); //append unloaded
    write2file(file, Boolean.FALSE.toString());//merge with existing
    write2file(file, String.valueOf(isSampling));
    if (sourceMapPath != null) {
      write2file(file, Boolean.TRUE.toString());
      write2file(file, sourceMapPath);
    }
    if (patterns != null) {
      writePatterns(file, patterns);
    }
    if (excludePatterns != null) {
      write2file(file, "-exclude");
      writePatterns(file, excludePatterns);
    }
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


  @Override
  @NotNull
  public String getPresentableName() {
    return "IntelliJ IDEA";
  }

  @Override
  @NotNull
  public String getId() {
    return "idea";
  }

  @Override
  @NotNull
  public String getDataFileExtension() {
    return "ic";
  }

  @Override
  public boolean isCoverageByTestApplicable() {
    return true;
  }
}