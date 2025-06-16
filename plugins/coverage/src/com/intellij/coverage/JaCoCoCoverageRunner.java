// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.coverage.analysis.AnalysisUtils;
import com.intellij.coverage.analysis.JavaCoverageClassesEnumerator;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jacoco.agent.AgentJar;
import org.jacoco.core.analysis.*;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public final class JaCoCoCoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance(JaCoCoCoverageRunner.class);

  @Override
  public @NotNull CoverageLoadingResult loadCoverageData(
    @NotNull File sessionDataFile,
    @Nullable CoverageSuite baseCoverageSuite,
    @NotNull CoverageLoadErrorReporter reporter
  ) {
    final ProjectData data = new ProjectData();
    try {
      final Project project = baseCoverageSuite instanceof BaseCoverageSuite ? baseCoverageSuite.getProject() : null;
      if (project != null) {
        var configuration = ((BaseCoverageSuite)baseCoverageSuite).getConfiguration();

        Module mainModule = configuration instanceof ModuleBasedConfiguration
                            ? ((ModuleBasedConfiguration<?, ?>)configuration).getConfigurationModule().getModule()
                            : null;

        loadExecutionData(sessionDataFile, data, mainModule, project, baseCoverageSuite, reporter);
      }
    }
    catch (IOException e) {
      processError(sessionDataFile, e, reporter);
      return new FailedCoverageLoadingResult(e, true, data);
    }
    catch (Exception e) {
      if (e instanceof ControlFlowException) throw e;
      LOG.error(e);
      return new FailedCoverageLoadingResult(e, false, data);
    }
    return new SuccessCoverageLoadingResult(data);
  }

  private static void processError(@NotNull File sessionDataFile, IOException e,  @NotNull CoverageLoadErrorReporter reporter) {
    final String path = sessionDataFile.getAbsolutePath();
    if ("Invalid execution data file.".equals(e.getMessage())) {
      Notifications.Bus.notify(new Notification("Coverage",
                                                CoverageBundle.message("coverage.error.loading.report"),
                                                JavaCoverageBundle.message("coverage.error.jacoco.report.format", path),
                                                NotificationType.ERROR));
      LOG.info(e);
      String message = CoverageBundle.message("coverage.error.loading.report") + ": " + JavaCoverageBundle.message("coverage.error.jacoco.report.format", path);
      reporter.reportWarning(message, e);
    }
    else if (e.getMessage() != null && e.getMessage().startsWith("Unknown block type")) {
      Notifications.Bus.notify(new Notification("Coverage",
                                                CoverageBundle.message("coverage.error.loading.report"),
                                                JavaCoverageBundle.message("coverage.error.jacoco.report.corrupted", path),
                                                NotificationType.ERROR));
      LOG.info(e);
      String message = CoverageBundle.message("coverage.error.loading.report") + ": " + JavaCoverageBundle.message("coverage.error.jacoco.report.corrupted", path);
      reporter.reportWarning(message, e);
    }
    else {
      LOG.error(e);
      reporter.reportError(e);
    }
  }

  private static void loadExecutionData(final @NotNull File sessionDataFile,
                                        ProjectData data,
                                        @Nullable Module mainModule,
                                        @NotNull Project project,
                                        CoverageSuite suite,
                                        @NotNull CoverageLoadErrorReporter reporter) throws IOException {
    ExecFileLoader loader = new ExecFileLoader();
    final CoverageBuilder coverageBuilder = new CoverageBuilder();
    loadReportToCoverageBuilder(coverageBuilder, sessionDataFile, mainModule, project, loader, (JavaCoverageSuite)suite, reporter);

    for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
      String className = AnalysisUtils.internalNameToFqn(classCoverage.getName());
      final ClassData classData = data.getOrCreateClassData(className);
      final Collection<IMethodCoverage> methods = classCoverage.getMethods();
      LineData[] lines = new LineData[classCoverage.getLastLine() + 1];
      for (IMethodCoverage method : methods) {
        final String desc = method.getName() + method.getDesc();
        // Line numbers are 1-based here.
        final int firstLine = method.getFirstLine();
        final int lastLine = method.getLastLine();
        for (int i = firstLine; i <= lastLine; i++) {
          final ILine methodLine = method.getLine(i);
          final int methodLineStatus = methodLine.getStatus();
          if (methodLineStatus == ICounter.EMPTY) continue;
          final LineData lineData = new LineData(i , desc);
          switch (methodLineStatus) {
            case ICounter.FULLY_COVERED -> lineData.setStatus(LineCoverage.FULL);
            case ICounter.PARTLY_COVERED -> lineData.setStatus(LineCoverage.PARTIAL);
            default -> lineData.setStatus(LineCoverage.NONE);
          }

          lineData.setHits(methodLineStatus == ICounter.FULLY_COVERED || methodLineStatus == ICounter.PARTLY_COVERED ? 1 : 0);
          ICounter branchCounter = methodLine.getBranchCounter();
          if (branchCounter.getTotalCount() > 0) {
            final int[] keys = new int[branchCounter.getTotalCount()];
            for (int key = 0; key < keys.length; key++) {
              keys[key] = key;
            }
            final SwitchData switchData = lineData.addSwitch(0, keys);
            final int[] hits = switchData.getHits();
            Arrays.fill(hits, 0, branchCounter.getCoveredCount(), 1);
            switchData.setKeysAndHits(keys, hits);
            switchData.setDefaultHits(1);
          }

          classData.registerMethodSignature(lineData);
          lineData.fillArrays();
          lines[i] = lineData;
        }
      }
      classData.setLines(lines);
    }
  }

  private static void loadReportToCoverageBuilder(@NotNull CoverageBuilder coverageBuilder,
                                                  @NotNull File sessionDataFile,
                                                  @Nullable Module mainModule,
                                                  @NotNull Project project,
                                                  ExecFileLoader loader,
                                                  JavaCoverageSuite suite,
                                                  @NotNull CoverageLoadErrorReporter reporter) throws IOException {
    loader.load(sessionDataFile);

    final Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), coverageBuilder);

    final Module[] modules = getModules(mainModule, project);
    if (modules.length == 0) {
      String message = "Could not find modules in project, the coverage data will not be loaded";
      LOG.warn(message);
      reporter.reportWarning(message, null);
    }
    final CoverageDataManager manager = CoverageDataManager.getInstance(project);
    for (Module module : modules) {
      final VirtualFile[] roots = JavaCoverageClassesEnumerator.getRoots(manager, module, true);
      if (roots.length == 0) {
        String message = "Could not find source roots for module " + module.getName() + ", the coverage data will not be loaded";
        LOG.warn(message);
        reporter.reportWarning(message, null);
        continue;
      }
      for (VirtualFile root : roots) {
        try {
          Path rootPath = Paths.get(new File(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(root.getUrl()))).toURI());
          Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
              String internalName = StringUtil.trimEnd(rootPath.relativize(path).toString(), ".class");
              String fqn = AnalysisUtils.internalNameToFqn(internalName);
              if (!suite.isClassFiltered(fqn)) return FileVisitResult.CONTINUE;
              File file = path.toFile();
              try {
                analyzer.analyzeAll(file);
              }
              catch (Exception e) {
                LOG.info(e);
                reporter.reportWarning(e);
              }
              return FileVisitResult.CONTINUE;
            }
          });
        }
        catch (NoSuchFileException e) {
          LOG.warn(e);
          reporter.reportWarning(e);
        }
      }
    }
  }

  private static Module[] getModules(@Nullable Module mainModule,
                                     @NotNull Project project) {
    final Module[] modules;
    if (mainModule != null) {
      HashSet<Module> mainModuleWithDependencies = new HashSet<>();
      ReadAction.run(() -> ModuleUtilCore.getDependencies(mainModule, mainModuleWithDependencies));
      modules = mainModuleWithDependencies.toArray(Module.EMPTY_ARRAY);
    }
    else {
      modules = ModuleManager.getInstance(project).getModules();
    }
    return modules;
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
  public void appendCoverageArgument(String sessionDataFilePath,
                                     String @Nullable [] patterns,
                                     String[] excludePatterns,
                                     SimpleJavaParameters javaParameters,
                                     boolean testTracking,
                                     boolean branchCoverage,
                                     String sourceMapPath,
                                     @Nullable Project project) {
    String path;
    try {
      path = AgentJar.extractToTempLocation().getAbsolutePath();
    }
    catch (IOException e) {
      return;
    }
    final String agentPath = handleSpacesInAgentPath(path);
    if (agentPath == null) return;
    javaParameters.getTargetDependentParameters().asTargetParameters().add(request -> {
      return createArgumentTargetValue(agentPath, sessionDataFilePath, patterns, excludePatterns);
    });
  }

  public JavaTargetParameter createArgumentTargetValue(String agentPath,
                                                       String sessionDataFilePath,
                                                       String @Nullable [] patterns,
                                                       String[] excludePatterns) {
    HashSet<String> uploadPaths = ContainerUtil.newHashSet(agentPath);
    HashSet<String> downloadPaths = ContainerUtil.newHashSet(sessionDataFilePath);
    var builder = new JavaTargetParameter.Builder(uploadPaths, downloadPaths);
    return doCreateCoverageArgument(builder, patterns, excludePatterns, sessionDataFilePath, agentPath);
  }

  private static @NotNull JavaTargetParameter doCreateCoverageArgument(@NotNull JavaTargetParameter.Builder builder,
                                                                       String @Nullable [] patterns,
                                                                       String[] excludePatterns,
                                                                       String sessionDataFilePath,
                                                                       String agentPath) {
    builder
      .fixed("-javaagent:")
      .resolved(agentPath)
      .fixed("=destfile=")
      .resolved(sessionDataFilePath)
      .fixed(",append=false");
    if (!ArrayUtil.isEmpty(patterns)) {
      builder.fixed(",includes=").fixed(StringUtil.join(patterns, ":"));
    }
    if (!ArrayUtil.isEmpty(excludePatterns)) {
      builder.fixed(",excludes=").fixed(StringUtil.join(excludePatterns, ":"));
    }
    return builder.build();
  }

  @Override
  public boolean isBranchInfoAvailable(boolean branchCoverage) {
    return true;
  }

  @Override
  public void generateReport(CoverageSuitesBundle suite, Project project) throws IOException {
    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    File targetDirectory = new File(settings.OUTPUT_DIRECTORY);
    var runConfiguration = suite.getRunConfiguration();
    Module module = runConfiguration instanceof ModuleBasedConfiguration
                    ? ((ModuleBasedConfiguration<?, ?>)runConfiguration).getConfigurationModule().getModule()
                    : null;

    ExecFileLoader loader = new ExecFileLoader();
    CoverageBuilder coverageBuilder = new CoverageBuilder();
    for (CoverageSuite aSuite : suite.getSuites()) {
      File coverageFile = new File(aSuite.getCoverageDataFileName());
      try {
        loadReportToCoverageBuilder(coverageBuilder, coverageFile, module, project, loader, (JavaCoverageSuite)suite.getSuites()[0], new DummyCoverageLoadErrorReporter());
      } catch (IOException e) {
        processError(coverageFile, e, new DummyCoverageLoadErrorReporter());
      }
    }

    final IBundleCoverage bundleCoverage = coverageBuilder.getBundle(suite.getPresentableName());

    final IReportVisitor visitor = new HTMLFormatter().createVisitor(new FileMultiReportOutput(targetDirectory));

    visitor.visitInfo(loader.getSessionInfoStore().getInfos(),
                      loader.getExecutionDataStore().getContents());

    int tabWidth = 4;
    MultiSourceFileLocator multiSourceFileLocator = new MultiSourceFileLocator(tabWidth);
    for (Module srcModule : getModules(module, project)) {
      VirtualFile[] roots = ModuleRootManager.getInstance(srcModule).getSourceRoots(true);
      for (VirtualFile root : roots) {
        multiSourceFileLocator.add(new DirectorySourceFileLocator(VfsUtilCore.virtualToIoFile(root), StandardCharsets.UTF_8.name(), tabWidth));
      }
    }
    visitor.visitBundle(bundleCoverage, multiSourceFileLocator);
    visitor.visitEnd();
  }

  @Override
  public @NotNull String getPresentableName() {
    return "JaCoCo";
  }

  @Override
  public @NotNull String getId() {
    return "jacoco";
  }

  @Override
  public @NotNull String getDataFileExtension() {
    return "exec";
  }
}
