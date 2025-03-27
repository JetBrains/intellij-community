// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.execution.target.java.TargetPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.CoverageReport;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class IDEACoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance(IDEACoverageRunner.class);
  public static final String INNER_CLASS_REGEX = "(\\$.*)*";
  private static final String COVERAGE_AGENT_PATH_PROPERTY = "idea.coverage.agent.path";

  @Override
  public @NotNull CoverageLoadingResult loadCoverageData(
    final @NotNull File sessionDataFile,
    final @Nullable CoverageSuite coverageSuite,
    final @NotNull CoverageLoadErrorReporter reporter
  ) {
    ProjectData projectData = ProjectDataLoader.load(sessionDataFile);
    File sourceMapFile = new File(JavaCoverageEnabledConfiguration.getSourceMapPath(sessionDataFile.getPath()));
    if (sourceMapFile.exists()) {
      try {
        CoverageReport.loadAndApplySourceMap(projectData, sourceMapFile);
      }
      catch (IOException e) {
        LOG.warn("Error reading source map associated with coverage data", e);
        reporter.reportWarning("Error reading source map associated with coverage data: " + e.getMessage(), e);
      }
    }
    if (coverageSuite instanceof JavaCoverageSuite javaSuite) {
      // Do not overwrite existing patterns if present, as they are written in the format expected by IDEA,
      // while the patterns passed to the agent are converted to the regex format.
      if (ArrayUtil.isEmpty(javaSuite.getIncludeFilters())) {
        String[] includeFilters = extractPatterns(projectData.getIncludePatterns());
        javaSuite.setIncludeFilters(includeFilters);
      }
      if (ArrayUtil.isEmpty(javaSuite.getExcludePatterns())) {
        String[] excludePatterns = extractPatterns(projectData.getExcludePatterns());
        javaSuite.setExcludePatterns(excludePatterns);
      }
    }
    return new SuccessCoverageLoadingResult(projectData);
  }

  @Override
  public void appendCoverageArgument(String sessionDataFilePath,
                                     String @Nullable [] patterns,
                                     SimpleJavaParameters parameters,
                                     boolean testTracking,
                                     boolean branchCoverage) {
    appendCoverageArgument(sessionDataFilePath, patterns, null, parameters, testTracking, branchCoverage, null, null);
  }

  @Override
  public void appendCoverageArgument(final String sessionDataFilePath,
                                     final String[] patterns,
                                     final String[] excludePatterns,
                                     final SimpleJavaParameters javaParameters,
                                     final boolean testTracking,
                                     final boolean branchCoverage,
                                     @Nullable String sourceMapPath,
                                     final @Nullable Project project) {
    final String agentPath = getAgentPath();
    if (agentPath == null) return;
    final String[] excludeAnnotations = getExcludeAnnotations(project);
    List<Function<? super TargetEnvironmentRequest, ? extends JavaTargetParameter>> targetParameters =
      javaParameters.getTargetDependentParameters().asTargetParameters();
    targetParameters.add(request -> createArgumentTargetParameter(agentPath, sessionDataFilePath,
                                                                  patterns, excludePatterns, excludeAnnotations,
                                                                  testTracking,
                                                                  branchCoverage, sourceMapPath));
    if (!Registry.is("idea.coverage.new.tracing.enabled")) {
      targetParameters.add(request -> JavaTargetParameter.fixed("-Didea.new.tracing.coverage=false"));
    }
    if (testTracking && !Registry.is("idea.coverage.new.test.tracking.enabled")) {
      targetParameters.add(request -> JavaTargetParameter.fixed("-Didea.new.test.tracking.coverage=false"));
    }
    if (Registry.is("idea.coverage.calculate.exact.hits")) {
      targetParameters.add(request -> JavaTargetParameter.fixed("-Didea.coverage.calculate.hits=true"));
    }
  }

  private static @Nullable String getAgentPath() {
    final String userDefinedAgentPath = System.getProperty(COVERAGE_AGENT_PATH_PROPERTY);
    final String bundledAgentPath = PathUtil.getJarPathForClass(ProjectData.class);
    final String agentPath = userDefinedAgentPath != null ? userDefinedAgentPath : bundledAgentPath;
    return handleSpacesInAgentPath(agentPath);
  }

  private static @Nullable JavaTargetParameter createArgumentTargetParameter(String agentPath,
                                                                             String sessionDataFilePath,
                                                                             String @Nullable [] patterns,
                                                                             String[] excludePatterns,
                                                                             String[] excludeAnnotations,
                                                                             boolean testTracking,
                                                                             boolean branchCoverage,
                                                                             String sourceMapPath) {
    try {
      final File tempFile = createTempFile();
      tempFile.deleteOnExit();
      Ref<Boolean> writeOnceRef = new Ref<>(false);
      String tempFilePath = tempFile.getAbsolutePath();

      TargetPaths targetPaths = TargetPaths.ordered(builder -> {
        builder
          .download(sessionDataFilePath,
                         __ -> {
                           createFileOrClearExisting(sessionDataFilePath);
                           return Unit.INSTANCE;
                         },
                         targetSessionDataPath -> {
                           if (!writeOnceRef.get()) {
                             try {
                               writeOptionsToFile(tempFile, targetSessionDataPath,
                                                  patterns, excludePatterns, excludeAnnotations,
                                                  testTracking, branchCoverage, sourceMapPath);
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
        .upload(agentPath, __ -> Unit.INSTANCE, __ -> Unit.INSTANCE)
        .upload(tempFilePath, __ -> Unit.INSTANCE, __ -> Unit.INSTANCE);
        return Unit.INSTANCE;
      });

      return new JavaTargetParameter.Builder(targetPaths)
        .fixed("-javaagent:")
        .resolved(agentPath)
        .fixed("=")
        .resolved(tempFilePath)
        .build();
    }
    catch (IOException e) {
      LOG.info("Coverage was not enabled", e);
      return null;
    }
  }

  private static void createFileOrClearExisting(@NotNull String sessionDataFilePath) {
    File file = new File(sessionDataFilePath);
    FileUtil.createIfDoesntExist(file);
    try {
      new FileOutputStream(file).close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void writeOptionsToFile(File file,
                                         String sessionDataFilePath,
                                         String @Nullable [] patterns,
                                         String[] excludePatterns,
                                         String[] excludeAnnotations,
                                         boolean testTracking,
                                         boolean branchCoverage,
                                         String sourceMapPath) throws IOException {
    write2file(file, sessionDataFilePath);
    write2file(file, String.valueOf(testTracking));
    write2file(file, Boolean.FALSE.toString()); //append unloaded
    write2file(file, String.valueOf(Registry.is("idea.coverage.merge.report"))); //merge with existing
    write2file(file, String.valueOf(!branchCoverage));
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
    if (!ArrayUtil.isEmpty(excludeAnnotations)) {
      write2file(file, "-excludeAnnotations");
      writePatterns(file, excludeAnnotations);
    }
  }

  static String @NotNull [] convertToPatterns(String @NotNull [] patterns) {
    final String[] result = new String[patterns.length];
    for (int i = 0; i < patterns.length; i++) {
      String coveragePattern = patterns[i];
      coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
      if (!coveragePattern.endsWith(".*")) { //include inner classes
        coveragePattern += INNER_CLASS_REGEX;
      }
      result[i] = coveragePattern;
    }
    return result;
  }

  static String @NotNull [] convertFromPatterns(String @NotNull [] patterns) {
    final String[] result = new String[patterns.length];
    for (int i = 0; i < patterns.length; i++) {
      String coveragePattern = patterns[i];
      if (coveragePattern.endsWith(INNER_CLASS_REGEX)) {
        coveragePattern = coveragePattern.substring(0, coveragePattern.length() - INNER_CLASS_REGEX.length());
      }
      coveragePattern = coveragePattern.replace(".*", "*").replace("\\.", ".").replace("\\$", "$");
      result[i] = coveragePattern;
    }
    return result;
  }

  private static void writePatterns(File tempFile, String[] patterns) throws IOException {
    for (String coveragePattern : convertToPatterns(patterns)) {
      write2file(tempFile, coveragePattern);
    }
  }

  private static String @Nullable [] extractPatterns(@Nullable List<Pattern> patterns) {
    if (patterns == null) return null;
    return convertFromPatterns(ArrayUtilRt.toStringArray(ContainerUtil.map(patterns, Pattern::pattern)));
  }

  private static String[] getExcludeAnnotations(@Nullable Project project) {
    if (project == null) return null;
    final JavaCoverageOptionsProvider optionsProvider = JavaCoverageOptionsProvider.getInstance(project);
    return ArrayUtil.toStringArray(optionsProvider.getExcludeAnnotationPatterns());
  }


  @Override
  public @NotNull String getPresentableName() {
    return "IntelliJ IDEA";
  }

  @Override
  public @NotNull String getId() {
    return "idea";
  }

  @Override
  public @NotNull String getDataFileExtension() {
    return "ic";
  }

  @Override
  public boolean isCoverageByTestApplicable() {
    return true;
  }

  public static void setExcludeAnnotations(Project project, ProjectData projectData) {
    final JavaCoverageOptionsProvider optionsProvider = JavaCoverageOptionsProvider.getInstance(project);
    try {
      final String[] patterns = ArrayUtil.toStringArray(optionsProvider.getExcludeAnnotationPatterns());
      final String[] regexps = convertToPatterns(patterns);
      projectData.setAnnotationsToIgnore(Stream.of(regexps).map((s) -> Pattern.compile(s)).toList());
    }
    catch (PatternSyntaxException e) {
      LOG.info("Failed to collect exclude annotation patterns", e);
    }
  }
}