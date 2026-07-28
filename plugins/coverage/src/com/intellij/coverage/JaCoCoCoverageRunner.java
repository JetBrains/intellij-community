// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.coverage.analysis.AnalysisUtils;
import com.intellij.coverage.analysis.CoverageOutputRoots;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.BranchData;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jacoco.agent.AgentJar;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.IExecutionDataVisitor;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.intellij.openapi.diagnostic.LoggerKt.rethrowControlFlowException;

public final class JaCoCoCoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance(JaCoCoCoverageRunner.class);
  private static final IExecutionDataVisitor NO_OP_VISITOR = new IExecutionDataVisitor() {
    @Override
    public void visitClassExecution(ExecutionData data) {
    }
  };

  @Override
  public @NotNull List<Integer> collectSrcLinesForUntouchedFile(@NotNull Path classFile, @NotNull CoverageSuitesBundle suite) {
    CoverageBuilder coverageBuilder = new CoverageBuilder();
    Analyzer analyzer = new Analyzer(new ExecutionDataStore(), coverageBuilder);
    try {
      byte[] classBytes = AnalysisUtils.loadClassBytes(classFile);
      if (classBytes == null) return List.of();
      analyzer.analyzeClass(classBytes, classFile.toString());
    }
    catch (Exception e) {
      rethrowControlFlowException(e);
      LOG.error("Fail to process class from: " + classFile, e);
      return List.of();
    }

    List<Integer> uncoveredLines = new ArrayList<>();
    for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
      int firstLine = classCoverage.getFirstLine();
      int lastLine = classCoverage.getLastLine();
      if (firstLine < 0 || lastLine < firstLine) continue;
      for (int line = firstLine; line <= lastLine; line++) {
        if (classCoverage.getLine(line).getStatus() != ICounter.EMPTY) {
          uncoveredLines.add(line - 1);
        }
      }
    }
    return uncoveredLines;
  }

  @Override
  public @NotNull CoverageLoadingResult loadCoverageData(
    @NotNull Path sessionDataFile,
    @Nullable CoverageSuite coverageSuite,
    @NotNull CoverageLoadErrorReporter reporter
  ) {
    if (!(coverageSuite instanceof JavaCoverageSuite javaSuite)) {
      return new FailedCoverageLoadingResult("Unsupported coverage suite: " + coverageSuite);
    }
    final Project project = javaSuite.getProject();
    if (project == null) {
      return new FailedCoverageLoadingResult("Failed to locate Project for coverage suite: " + coverageSuite);
    }
    final ProjectData data = new ProjectData();
    try {
      ExecFileLoader loader = new ExecFileLoader();
      final CoverageBuilder coverageBuilder = new CoverageBuilder();
      Module mainModule = findMainModule(javaSuite.getConfiguration());
      loadReportToCoverageBuilder(coverageBuilder, sessionDataFile, mainModule, project, loader, javaSuite, reporter);
      loadIntoProjectData(data, coverageBuilder);
    }
    catch (IOException e) {
      processError(sessionDataFile, e, reporter);
      return new FailedCoverageLoadingResult(e, true, data);
    }
    catch (Exception e) {
      rethrowControlFlowException(e);
      LOG.error(e);
      return new FailedCoverageLoadingResult(e, false, data);
    }
    return new SuccessCoverageLoadingResult(data);
  }

  private static void processError(@NotNull Path sessionDataFile, IOException e, @NotNull CoverageLoadErrorReporter reporter) {
    final String path = sessionDataFile.toAbsolutePath().toString();
    if ("Invalid execution data file.".equals(e.getMessage())) {
      Notifications.Bus.notify(new Notification("Coverage",
                                                CoverageBundle.message("coverage.error.loading.report"),
                                                JavaCoverageBundle.message("coverage.error.jacoco.report.format", path),
                                                NotificationType.ERROR));
      LOG.info(e);
      String message = CoverageBundle.message("coverage.error.loading.report") +
                       ": " +
                       JavaCoverageBundle.message("coverage.error.jacoco.report.format", path);
      reporter.reportWarning(message, e);
    }
    else if (e.getMessage() != null && e.getMessage().startsWith("Unknown block type")) {
      Notifications.Bus.notify(new Notification("Coverage",
                                                CoverageBundle.message("coverage.error.loading.report"),
                                                JavaCoverageBundle.message("coverage.error.jacoco.report.corrupted", path),
                                                NotificationType.ERROR));
      LOG.info(e);
      String message = CoverageBundle.message("coverage.error.loading.report") +
                       ": " +
                       JavaCoverageBundle.message("coverage.error.jacoco.report.corrupted", path);
      reporter.reportWarning(message, e);
    }
    else {
      LOG.error(e);
      reporter.reportError(e);
    }
  }

  private static void loadIntoProjectData(@NotNull ProjectData data, @NotNull CoverageBuilder coverageBuilder) {
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
          final LineData lineData = new LineData(i, desc);
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
                                                  @NotNull Path sessionDataFile,
                                                  @Nullable Module mainModule,
                                                  @NotNull Project project,
                                                  ExecFileLoader loader,
                                                  JavaCoverageSuite suite,
                                                  @NotNull CoverageLoadErrorReporter reporter) throws IOException {
    try (InputStream inputStream = Files.newInputStream(sessionDataFile)) {
      loader.load(inputStream);
    }

    final Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), coverageBuilder);

    final Module[] modules = getModules(mainModule, project);
    if (modules.length == 0) {
      String message = "Could not find modules in project, the coverage data will not be loaded";
      LOG.warn(message);
      reporter.reportWarning(message, null);
    }
    final CoverageDataManager manager = CoverageDataManager.getInstance(project);
    for (Module module : modules) {
      final VirtualFile[] roots = CoverageOutputRoots.getRoots(manager, module, true);
      if (roots.length == 0) {
        String message = "Could not find source roots for module " + module.getName() + ", the coverage data will not be loaded";
        LOG.warn(message);
        reporter.reportWarning(message, null);
        continue;
      }
      for (VirtualFile root : roots) {
        try {
          analyzeRoot(analyzer, suite, getLocalRootPath(root), reporter);
        }
        catch (NoSuchFileException e) {
          LOG.warn(e);
          reporter.reportWarning(e);
        }
      }
    }
  }

  private static @NotNull Path getLocalRootPath(@NotNull VirtualFile root) {
    VirtualFile archiveFile = JarFileSystem.getInstance().getVirtualFileForJar(root);
    VirtualFile rootFile = archiveFile != null ? archiveFile : root;
    return rootFile.toNioPath();
  }

  private static void analyzeRoot(@NotNull Analyzer analyzer,
                                  @NotNull JavaCoverageSuite suite,
                                  @NotNull Path root,
                                  @NotNull CoverageLoadErrorReporter reporter) throws IOException {
    if (Files.isDirectory(root)) {
      analyzeDirectory(analyzer, suite, root, reporter);
    }
    else if (Files.isRegularFile(root) && StringUtil.endsWithIgnoreCase(root.getFileName().toString(), ".jar")) {
      analyzeArchive(analyzer, suite, root, reporter);
    }
  }

  private static void analyzeDirectory(@NotNull Analyzer analyzer,
                                       @NotNull JavaCoverageSuite suite,
                                       @NotNull Path rootPath,
                                       @NotNull CoverageLoadErrorReporter reporter) throws IOException {
    Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        String relativePath = rootPath.relativize(path).toString();
        if (!relativePath.endsWith(".class")) return FileVisitResult.CONTINUE;

        String internalName = FileUtil.toSystemIndependentName(StringUtil.trimEnd(relativePath, ".class"));
        if (!isClassFiltered(suite, internalName)) return FileVisitResult.CONTINUE;
        try (InputStream inputStream = Files.newInputStream(path)) {
          analyzer.analyzeClass(inputStream, relativePath);
        }
        catch (Exception e) {
          LOG.info(e);
          reporter.reportWarning(e);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void analyzeArchive(@NotNull Analyzer analyzer,
                                     @NotNull JavaCoverageSuite suite,
                                     @NotNull Path archive,
                                     @NotNull CoverageLoadErrorReporter reporter) throws IOException {
    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;

        String entryName = entry.getName();
        if (!entryName.endsWith(".class")) continue;

        String internalName = StringUtil.trimEnd(entryName, ".class");
        if (!isClassFiltered(suite, internalName)) continue;
        try {
          analyzer.analyzeClass(zipInputStream, entryName);
        }
        catch (Exception e) {
          LOG.info(e);
          reporter.reportWarning(e);
        }
      }
    }
  }

  private static boolean isClassFiltered(@NotNull JavaCoverageSuite suite, @NotNull String internalName) {
    String fqn = AnalysisUtils.internalNameToFqn(internalName);
    return suite.isClassFiltered(fqn);
  }

  private static Module[] getModules(@Nullable Module mainModule,
                                     @NotNull Project project) {
    final Module[] modules;
    if (mainModule != null) {
      HashSet<Module> mainModuleWithDependencies = new HashSet<>();
      ReadAction.runBlocking(() -> ModuleUtilCore.getDependencies(mainModule, mainModuleWithDependencies));
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
    javaParameters.getTargetDependentParameters().asTargetParameters().add(_ -> {
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
    if (Registry.is("idea.jacoco.collect.coverage.for.classes.with.no.location")) {
      // JaCoCo engine ignores classes with no location.
      // Location is accessed with these methods:
      // * java.security.CodeSource.getLocation
      // * java.security.ProtectionDomain.getCodeSource
      // where protection domain is passed to JaCoCo transformer.
      //
      // IntelliJ provides no source (default is used) here:
      // com.intellij.util.lang.UrlClassLoader.consumeClassData(java.lang.String, java.nio.ByteBuffer)
      builder.fixed(",inclnolocationclasses=true");
    }
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
    Path targetDirectory = Path.of(settings.OUTPUT_DIRECTORY);
    Module module = findMainModule(suite.getRunConfiguration());

    ExecFileLoader loader = new ExecFileLoader();
    CoverageBuilder coverageBuilder = new CoverageBuilder();
    for (CoverageSuite aSuite : suite.getSuites()) {
      Path coverageFile = Path.of(aSuite.getCoverageDataFileName());
      try {
        loadReportToCoverageBuilder(coverageBuilder, coverageFile, module, project, loader, (JavaCoverageSuite)suite.getSuites()[0],
                                    new DummyCoverageLoadErrorReporter());
      }
      catch (IOException e) {
        processError(coverageFile, e, new DummyCoverageLoadErrorReporter());
      }
    }

    final IBundleCoverage bundleCoverage = coverageBuilder.getBundle(suite.getPresentableName());

    File file = targetDirectory.toFile();
    final IReportVisitor visitor = new HTMLFormatter().createVisitor(new FileMultiReportOutput(file));

    visitor.visitInfo(loader.getSessionInfoStore().getInfos(),
                      loader.getExecutionDataStore().getContents());

    int tabWidth = 4;
    MultiSourceFileLocator multiSourceFileLocator = new MultiSourceFileLocator(tabWidth);
    for (Module srcModule : getModules(module, project)) {
      VirtualFile[] roots = ModuleRootManager.getInstance(srcModule).getSourceRoots(true);
      for (VirtualFile root : roots) {
        multiSourceFileLocator.add(
          new DirectorySourceFileLocator(VfsUtilCore.virtualToIoFile(root), StandardCharsets.UTF_8.name(), tabWidth));
      }
    }
    visitor.visitBundle(bundleCoverage, multiSourceFileLocator);
    visitor.visitEnd();
  }

  private static @Nullable Module findMainModule(RunConfigurationBase<?> configuration) {
    return configuration instanceof ModuleBasedConfiguration<?, ?> moduleConfig
           ? moduleConfig.getConfigurationModule().getModule()
           : null;
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
  public boolean canBeLoaded(@NotNull Path candidate) {
    try {
      try (InputStream stream = new BufferedInputStream(Files.newInputStream(candidate))) {
        final ExecutionDataReader reader = new ExecutionDataReader(stream);
        var sessionInfoStore = new SessionInfoStore();
        reader.setSessionInfoVisitor(sessionInfoStore);
        reader.setExecutionDataVisitor(NO_OP_VISITOR);
        reader.read();
        return !sessionInfoStore.isEmpty();
      }
    }
    catch (IOException e) {
      LOG.debug(e);
      return false;
    }
  }

  @Override
  public @NotNull String getDataFileExtension() {
    return "exec";
  }

  @Override
  public String generateBriefReport(@NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    @NotNull TextRange range,
                                    @NotNull LineData lineData) {
    BranchData branchData = lineData.getBranchData();
    var lineCoverage = CoverageEngine.getLineCoverageStatus(lineData);
    if (branchData == null) return lineCoverage;
    var branchCoverage = JavaCoverageEngine.getBranchCoverageStatus(branchData);
    return lineCoverage + "\n" + branchCoverage;
  }
}
