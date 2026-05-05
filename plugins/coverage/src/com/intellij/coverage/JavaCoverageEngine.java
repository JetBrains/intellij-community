// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.CommonBundle;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.coverage.analysis.AnalysisUtils;
import com.intellij.coverage.analysis.JavaCoverageAnnotator;
import com.intellij.coverage.analysis.JavaCoverageClassesEnumerator;
import com.intellij.coverage.analysis.PackageAnnotator;
import com.intellij.coverage.listeners.java.CoverageListener;
import com.intellij.coverage.view.CoverageViewExtension;
import com.intellij.coverage.view.JavaCoverageViewExtension;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.target.RunTargetsEnabled;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfigurations;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.BrowserUtil;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.rt.coverage.data.BranchData;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.task.ProjectTaskManager;
import com.intellij.task.impl.ProjectTaskManagerImpl;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.coverage.report.ReportGenerationFailedException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageEngine extends CoverageEngine {
  private static final Logger LOG = Logger.getInstance(JavaCoverageEngine.class.getName());
  private static final String indent = "  ";
  private static final int MAX_EXPRESSION_LENGTH = 100;

  public static JavaCoverageEngine getInstance() {
    return EP_NAME.findExtensionOrFail(JavaCoverageEngine.class);
  }

  @Override
  public boolean isApplicableTo(final @NotNull RunConfigurationBase conf) {
    if (conf instanceof CommonJavaRunConfigurationParameters) {
      return true;
    }

    if (RunTargetsEnabled.get()
        && conf instanceof TargetEnvironmentAwareRunProfile
        && willRunOnTarget((TargetEnvironmentAwareRunProfile)conf)) {
      return false;
    }

    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      if (extension.isApplicableTo(conf)) {
        return true;
      }
    }
    return false;
  }

  private static boolean willRunOnTarget(final @NotNull TargetEnvironmentAwareRunProfile configuration) {
    Project project = ((RunConfigurationBase<?>)configuration).getProject();
    return TargetEnvironmentConfigurations.getEffectiveTargetName(configuration, project) != null || isProjectUnderWsl(project);
  }

  private static boolean isProjectUnderWsl(@NotNull Project project) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk == null) {
      return false;
    }
    String projectSdkHomePath = projectSdk.getHomePath();
    return projectSdkHomePath != null && WslPath.isWslUncPath(projectSdkHomePath);
  }

  @Override
  public boolean canHavePerTestCoverage(@NotNull RunConfigurationBase conf) {
    return !(conf instanceof ApplicationConfiguration) && conf instanceof CommonJavaRunConfigurationParameters;
  }

  @Override
  public Set<String> getTestsForLine(Project project, CoverageSuitesBundle bundle, String classFQName, int lineNumber) {
    return extractTracedTests(bundle, classFQName, lineNumber);
  }

  @Override
  public boolean wasTestDataCollected(Project project, CoverageSuitesBundle bundle) {
    return !getTraceFiles(bundle).isEmpty();
  }

  private static Set<String> extractTracedTests(CoverageSuitesBundle bundle, final String classFQName, final int lineNumber) {
    Set<String> tests = new HashSet<>();
    final List<Path> traceFiles = getTraceFiles(bundle);
    for (Path traceFile : traceFiles) {
      try (DataInputStream in = new DataInputStream(Files.newInputStream(traceFile))) {
        extractTests(traceFile, in, tests, classFQName, lineNumber);
      }
      catch (Exception ex) {
        LOG.error(traceFile.getFileName().toString(), ex);
      }
    }
    return tests;
  }

  private static void extractTests(final Path traceFile,
                                   final DataInputStream in,
                                   final Set<? super String> tests,
                                   final String classFQName,
                                   final int lineNumber) throws IOException {
    long traceSize = in.readInt();
    for (int i = 0; i < traceSize; i++) {
      final String className = in.readUTF();
      final int linesSize = in.readInt();
      for (int l = 0; l < linesSize; l++) {
        final int line = in.readInt();
        if (Comparing.strEqual(className, classFQName)) {
          if (lineNumber == line) {
            tests.add(FileUtilRt.getNameWithoutExtension(traceFile.getFileName().toString()));
            return;
          }
        }
      }
    }
  }

  private static @NotNull List<Path> getTraceFiles(CoverageSuitesBundle bundle) {
    final List<Path> files = new ArrayList<>();
    for (CoverageSuite coverageSuite : bundle.getSuites()) {
      final Path tracesDir = getTracesDirectory(coverageSuite);
      if (Files.isDirectory(tracesDir)) {
        try (var suiteFiles = Files.list(tracesDir)) {
          suiteFiles.forEach(files::add);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    return files;
  }

  private static Path getTracesDirectory(CoverageSuite coverageSuite) {
    final String filePath = coverageSuite.getCoverageDataFileName();
    final Path dataFilePath = Path.of(filePath);
    final Path fileName = dataFilePath.getFileName();
    final String dirName = FileUtilRt.getNameWithoutExtension(fileName != null ? fileName.toString() : "");

    final Path parentDir = dataFilePath.getParent();
    return parentDir != null ? parentDir.resolve(dirName) : Path.of(dirName);
  }


  @Override
  public void collectTestLines(List<String> sanitizedTestNames,
                               CoverageSuite suite,
                               Map<String, Set<Integer>> executionTrace) {
    final Path tracesDir = getTracesDirectory(suite);
    for (String testName : sanitizedTestNames) {
      final Path file = tracesDir.resolve(testName + ".tr");
      if (Files.exists(file)) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
          int traceSize = in.readInt();
          for (int i = 0; i < traceSize; i++) {
            final String className = in.readUTF();
            final int linesSize = in.readInt();
            final Set<Integer> lines = executionTrace.computeIfAbsent(className, _ -> new HashSet<>());
            for (int l = 0; l < linesSize; l++) {
              lines.add(in.readInt());
            }
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  protected void deleteAssociatedTraces(CoverageSuite suite) {
    if (suite.isCoverageByTestEnabled()) {
      Path tracesDirectory = getTracesDirectory(suite);
      try {
        NioFiles.deleteRecursively(tracesDirectory);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public @NotNull CoverageEnabledConfiguration createCoverageEnabledConfiguration(final @NotNull RunConfigurationBase conf) {
    return new JavaCoverageEnabledConfiguration(conf);
  }

  @Override
  public @Nullable CoverageSuite createCoverageSuite(@NotNull String name,
                                                     @NotNull Project project,
                                                     @NotNull CoverageRunner runner,
                                                     @NotNull CoverageFileProvider fileProvider,
                                                     long timestamp) {
    JavaCoverageOptionsProvider optionsProvider = JavaCoverageOptionsProvider.getInstance(project);
    return createSuite(runner, name, fileProvider, null, null, timestamp, false,
                       optionsProvider.getBranchCoverage(), optionsProvider.getTestModulesCoverage(), project);
  }

  @Override
  public @Nullable CoverageSuite createCoverageSuite(@NotNull CoverageEnabledConfiguration config) {
    Project project = config.getConfiguration().getProject();
    CoverageRunner runner = JavaCoverageOptionsProvider.getInstance(project).getCoverageRunner();
    if (runner == null) return null;
    // set here for correct createFileProvider call
    config.setCoverageRunner(runner);
    return super.createCoverageSuite(config);
  }

  @Override
  public @Nullable CoverageSuite createCoverageSuite(@NotNull String name,
                                                     @NotNull Project project,
                                                     @NotNull CoverageRunner runner,
                                                     @NotNull CoverageFileProvider fileProvider,
                                                     long timestamp,
                                                     @NotNull CoverageEnabledConfiguration config) {
    if (config instanceof JavaCoverageEnabledConfiguration javaConfig) {
      JavaCoverageOptionsProvider optionsProvider = JavaCoverageOptionsProvider.getInstance(project);
      return createSuite(runner,
                         name, fileProvider,
                         javaConfig.getPatterns(),
                         javaConfig.getExcludePatterns(),
                         timestamp,
                         optionsProvider.getTestTracking() && canHavePerTestCoverage(config.getConfiguration()),
                         optionsProvider.getBranchCoverage(),
                         optionsProvider.getTestModulesCoverage(),
                         project);
    }
    return null;
  }

  @Override
  public @Nullable CoverageSuite createEmptyCoverageSuite(@NotNull CoverageRunner coverageRunner) {
    return new JavaCoverageSuite(this);
  }

  @Override
  public @NotNull CoverageAnnotator getCoverageAnnotator(@NotNull Project project) {
    return JavaCoverageAnnotator.getInstance(project);
  }

  /**
   * Determines if coverage information should be displayed for given file
   */
  @Override
  public boolean coverageEditorHighlightingApplicableTo(final @NotNull PsiFile psiFile) {
    if (!(psiFile instanceof PsiClassOwner)) {
      return false;
    }
    // let's show coverage only for module files
    final Module module = ReadAction.computeBlocking(() -> ModuleUtilCore.findModuleForPsiElement(psiFile));
    return module != null;
  }

  @Override
  public boolean acceptedByFilters(final @NotNull PsiFile psiFile, final @NotNull CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    final Project project = psiFile.getProject();
    if (!suite.isTrackTestFolders() && ReadAction.computeBlocking(() -> TestSourcesFilter.isTestSources(virtualFile, project))) {
      return false;
    }

    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;

      if (psiFile instanceof PsiClassOwner psiClassOwner &&
          javaSuite.isPackageFiltered(ReadAction.computeBlocking(() -> psiClassOwner.getPackageName()))) {
        return true;
      }
      else {
        final List<PsiClass> classes = javaSuite.getCurrentSuiteClasses(project);
        for (PsiClass aClass : classes) {
          final PsiFile containingFile = ReadAction.computeBlocking(aClass::getContainingFile);
          final VirtualFile classVirtualFile = containingFile.getVirtualFile();
          if (virtualFile.equals(classVirtualFile)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean recompileProjectAndRerunAction(@NotNull Module module, final @NotNull CoverageSuitesBundle suite,
                                                final @NotNull Runnable chooseSuiteAction) {
    if (suite.isModuleChecked(module)) return false;
    for (final JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      Module moduleCandidate = extension.getModuleWithOutput(module);
      if (moduleCandidate != null) {
        module = moduleCandidate;
        break;
      }
    }
    final CoverageDataManager dataManager = CoverageDataManager.getInstance(module.getProject());
    final boolean includeTests = suite.isTrackTestFolders();
    final VirtualFile[] roots = JavaCoverageClassesEnumerator.getRoots(dataManager, module, includeTests);
    final boolean rootsExist = roots.length >= (includeTests ? 2 : 1) && ContainerUtil.all(roots, (root) -> root != null && root.exists());
    if (!rootsExist) {
      final Project project = module.getProject();
      suite.checkModule(module);
      LOG.debug("Going to ask to rebuild project. Include tests:" + includeTests +
                ". Module: " + module.getName() + ".  Output roots are: ");
      for (VirtualFile root : roots) {
        LOG.debug(root.getPath() + " exists: " + root.exists());
      }
      final Notification notification = new Notification("Coverage",
                                                         JavaCoverageBundle.message("project.is.out.of.date"),
                                                         JavaCoverageBundle.message("project.class.files.are.out.of.date"),
                                                         NotificationType.INFORMATION);
      notification.addAction(NotificationAction.createSimpleExpiring(JavaCoverageBundle.message("coverage.recompile"), () -> {
        ProjectTaskManagerImpl.putBuildOriginator(project, this.getClass());
        ProjectTaskManager taskManager = ProjectTaskManager.getInstance(project);
        Promise<ProjectTaskManager.Result> promise = taskManager.buildAllModules();
        promise.onSuccess(_ -> ApplicationManager.getApplication().invokeLater(() -> {
                            CoverageDataManager.getInstance(project).chooseSuitesBundle(suite);
                          }, _ -> project.isDisposed())
        );
      }));
      CoverageNotifications.getInstance(project).addNotification(notification);
      notification.notify(project);
    }
    return false;
  }

  private static @Nullable Path getOutputpath(CompilerModuleExtension compilerModuleExtension) {
    final @Nullable String outputpathUrl = compilerModuleExtension.getCompilerOutputUrl();
    return outputpathUrl != null ? Path.of(VfsUtilCore.urlToPath(outputpathUrl)) : null;
  }

  private static @Nullable Path getTestOutputpath(CompilerModuleExtension compilerModuleExtension) {
    final @Nullable String outputpathUrl = compilerModuleExtension.getCompilerOutputUrlForTests();
    return outputpathUrl != null ? Path.of(VfsUtilCore.urlToPath(outputpathUrl)) : null;
  }

  @Override
  public @Nullable List<Integer> collectSrcLinesForUntouchedFile(final @NotNull Path classFile, final @NotNull CoverageSuitesBundle suite) {
    final ProjectData projectData = new ProjectData();
    final PackageAnnotator annotator = new PackageAnnotator(suite, suite.getProject(), projectData);

    try {
      ClassData classData = annotator.collectNonCoveredClassInfo(classFile, projectData);
      if (classData == null) return null;
      return SourceLineCounterUtil.collectSrcLinesForUntouchedFiles(classData, suite.getProject());
    }
    catch (Exception e) {
      if (e instanceof ControlFlowException) throw e;
      LOG.error("Fail to process class from: " + classFile, e);
    }
    finally {
      annotator.close();
    }
    return null;
  }

  @Override
  public boolean includeUntouchedFileInCoverage(final @NotNull String qualifiedName,
                                                final @NotNull Path outputFile,
                                                final @NotNull PsiFile sourceFile, @NotNull CoverageSuitesBundle suite) {
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      if (javaSuite.isClassFiltered(qualifiedName)) return true;
    }
    return false;
  }


  @Override
  protected @NotNull String getQualifiedName(final @NotNull Path outputFile, final @NotNull PsiFile sourceFile) {
    final String packageFQName = getPackageName(sourceFile);
    return StringUtil.getQualifiedName(packageFQName, FileUtilRt.getNameWithoutExtension(outputFile.getFileName().toString()));
  }

  @Override
  public @NotNull Set<String> getQualifiedNames(final @NotNull PsiFile sourceFile) {
    return ReadAction.nonBlocking(() -> {
      final PsiClass[] classes = ((PsiClassOwner)sourceFile).getClasses();
      final Set<String> qNames = new HashSet<>();
      for (final JavaCoverageEngineExtension nameExtension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
        if (nameExtension.suggestQualifiedName(sourceFile, classes, qNames)) {
          return qNames;
        }
      }
      for (final PsiClass aClass : classes) {
        collectClassQualifiedNames(aClass, qNames);
      }
      return qNames;
    }).executeSynchronously();
  }

  private static void collectClassQualifiedNames(final @NotNull PsiClass psiClass, final @NotNull Set<? super String> qNames) {
    final String qName = ClassUtil.getJVMClassName(psiClass);
    if (qName != null) {
      qNames.add(qName);
    }
    for (PsiClass innerClass : psiClass.getInnerClasses()) {
      collectClassQualifiedNames(innerClass, qNames);
    }
  }

  @Override
  public @NotNull Set<Path> getCorrespondingOutputPaths(final @NotNull PsiFile srcFile,
                                                        final @Nullable Module module,
                                                        final @NotNull CoverageSuitesBundle suite) {
    if (module == null) {
      return Collections.emptySet();
    }
    final Set<Path> classFiles = new HashSet<>();
    final CompilerModuleExtension moduleExtension = Objects.requireNonNull(CompilerModuleExtension.getInstance(module));
    final @Nullable Path outputpath = getOutputpath(moduleExtension);
    final @Nullable Path testOutputpath = getTestOutputpath(moduleExtension);

    final @Nullable VirtualFile outputpathVirtualFile = fileToVirtualFileWithRefresh(outputpath);
    final @Nullable VirtualFile testOutputpathVirtualFile = fileToVirtualFileWithRefresh(testOutputpath);

    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      if (extension.collectOutputPaths(srcFile, outputpathVirtualFile, testOutputpathVirtualFile, suite, classFiles)) return classFiles;
    }

    final Project project = module.getProject();
    final CoverageDataManager dataManager = CoverageDataManager.getInstance(project);
    boolean includeTests = suite.isTrackTestFolders();
    final VirtualFile[] roots = JavaCoverageClassesEnumerator.getRoots(dataManager, module, includeTests);


    String packageVmName = AnalysisUtils.fqnToInternalName(getPackageName(srcFile));

    final Set<String> classNames = new HashSet<>();
    final PsiClass[] classes = ReadAction.computeBlocking(() -> ((PsiClassOwner)srcFile).getClasses());
    for (final PsiClass psiClass : classes) {
      final String className = ReadAction.computeBlocking(() -> psiClass.getName());
      if (className != null) {
        classNames.add(className);
      }
    }

    for (VirtualFile root : roots) {
      if (root == null) continue;
      classFiles.addAll(JavaCoverageClassesEnumerator.collectClassFiles(root.toNioPath(), packageVmName, classNames));
    }
    return classFiles;
  }

  private static @Nullable VirtualFile fileToVirtualFileWithRefresh(@Nullable Path file) {
    if (file == null) return null;
    return WriteAction.computeAndWait(() -> VfsUtil.findFile(file, true));
  }

  @Override
  public String generateBriefReport(@NotNull CoverageSuitesBundle bundle,
                                    @NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    @NotNull TextRange range,
                                    @Nullable LineData lineData) {
    if (lineData == null) {
      return CoverageBundle.message("coverage.next.change.uncovered");
    }
    var suites = bundle.getSuites();
    assert suites.length > 0 : "Suites list should not be empty";

    var firstRunner = suites[0].getRunner();
    assert firstRunner instanceof JavaCoverageRunner : "Runner should be JavaCoverageRunner";
    JavaCoverageRunner uniqueRunner = (JavaCoverageRunner)firstRunner;
    for (var suite : suites) {
      if (suite.getRunner() != uniqueRunner) {
        return createDefaultBriefReport(lineData);
      }
    }

    return uniqueRunner.generateBriefReport(editor, psiFile, range, lineData);
  }

  public static @NotNull String createBriefReport(@NotNull LineData lineData,
                                                  List<ConditionCoverageExpression> conditions,
                                                  List<SwitchCoverageExpression> switches) {
    StringBuilder buf = new StringBuilder();
    buf.append(CoverageBundle.message("hits.title", lineData.getHits()));
    int idx = 0;
    int hits = 0;

    if (lineData.getJumps() != null) {
      for (JumpData jumpData : lineData.getJumps()) {
        if (idx >= conditions.size()) {
          LOG.info("Cannot map coverage report data with PSI: there are more branches in report then in PSI");
          return createDefaultBriefReport(lineData);
        }
        ConditionCoverageExpression expression = conditions.get(idx++);
        addJumpDataInfo(buf, jumpData, expression);
        hits += jumpData.getTrueHits() + jumpData.getFalseHits();
      }
    }

    if (lineData.getSwitches() != null) {
      for (SwitchData switchData : lineData.getSwitches()) {
        if (idx >= switches.size()) {
          LOG.info("Cannot map coverage report data with PSI: there are more switches in report then in PSI");
          return createDefaultBriefReport(lineData);
        }
        SwitchCoverageExpression expression = switches.get(idx++);
        addSwitchDataInfo(buf, switchData, expression, lineData.getStatus());
        hits += IntStream.of(switchData.getHits()).sum() + switchData.getDefaultHits();
      }
    }
    if (lineData.getHits() > hits && hits > 0) {
      buf.append("\n").append(JavaCoverageBundle.message("report.unknown.outcome", lineData.getHits() - hits));
    }

    return buf.toString();
  }

  /**
   * Try to remove line breaks from expression for better visibility.
   * As the resulting expression can become too long, the modification is made only for short expressions.
   */
  private static String preprocessExpression(String expression) {
    String preprocessed = expression.replaceAll("[\\s\n]+", " ");
    return preprocessed.length() > MAX_EXPRESSION_LENGTH ? expression : preprocessed;
  }

  private static void addJumpDataInfo(StringBuilder buf, JumpData jumpData, ConditionCoverageExpression expression) {
    buf.append("\n").append(indent).append(preprocessExpression(expression.getExpression()));
    boolean reverse = expression.isReversed();
    int trueHits = reverse ? jumpData.getFalseHits() : jumpData.getTrueHits();
    buf.append("\n").append(indent).append(indent).append(JavaKeywords.TRUE).append(" ")
      .append(CoverageBundle.message("hits.message", trueHits));

    int falseHits = reverse ? jumpData.getTrueHits() : jumpData.getFalseHits();
    buf.append("\n").append(indent).append(indent).append(JavaKeywords.FALSE).append(" ")
      .append(CoverageBundle.message("hits.message", falseHits));
  }

  private static void addSwitchDataInfo(StringBuilder buf, SwitchData switchData, SwitchCoverageExpression expression, int coverageStatus) {
    buf.append("\n").append(indent).append(preprocessExpression(expression.getExpression()));
    boolean allBranchesHit = true;
    for (int i = 0; i < switchData.getKeys().length; i++) {
      String key = expression.getCases() != null && i < expression.getCases().size()
                   ? expression.getCases().get(i)
                   : Integer.toString(switchData.getKeys()[i]);
      int switchHits = switchData.getHits()[i];
      allBranchesHit &= switchHits > 0;
      buf.append("\n").append(indent).append(indent).append(JavaKeywords.CASE).append(" ").append(key).append(": ").append(switchHits);
    }
    int defaultHits = switchData.getDefaultHits();
    boolean defaultCausesLinePartiallyCovered = allBranchesHit && coverageStatus != LineCoverage.FULL;
    if (expression.getHasDefault() || defaultCausesLinePartiallyCovered || defaultHits > 0) {
      buf.append("\n").append(indent).append(indent).append(JavaKeywords.DEFAULT).append(": ").append(defaultHits);
    }
  }

  static @NotNull String createDefaultBriefReport(@NotNull LineData lineData) {
    BranchData branchData = lineData.getBranchData();
    var lineCoverage = CoverageBundle.message("hits.title", lineData.getHits());
    if (branchData == null) return lineCoverage;
    var branchCoverage = getBranchCoverageStatus(branchData);
    return lineCoverage + "\n" + branchCoverage;
  }

  static @Nls @NotNull String getBranchCoverageStatus(BranchData branchData) {
    return CoverageBundle.message("branch.coverage.message", branchData.getCoveredBranches(), branchData.getTotalBranches());
  }

  @Override
  public @Nullable String getTestMethodName(final @NotNull PsiElement element,
                                            final @NotNull AbstractTestProxy testProxy) {
    if (element instanceof PsiMethod method) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        String qualifiedName = ClassUtil.getJVMClassName(aClass);
        if (qualifiedName != null) {
          return qualifiedName + "," + CoverageListener.sanitize(method.getName(), qualifiedName.length());
        }
      }
    }
    return testProxy.toString();
  }


  @Override
  public @NotNull List<PsiElement> findTestsByNames(String @NotNull [] testNames, @NotNull Project project) {
    final List<PsiElement> elements = new ArrayList<>();
    PsiManager psiManager = PsiManager.getInstance(project);
    for (String testName : testNames) {
      int index = testName.indexOf(",");
      if (index <= 0) return elements;
      collectTestsByName(elements, testName.substring(index + 1), testName.substring(0, index), psiManager);
    }
    return elements;
  }

  private static void collectTestsByName(List<? super PsiElement> elements,
                                         String testName,
                                         String className,
                                         PsiManager psiManager) {
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = ClassUtil.findPsiClass(psiManager, className);
      if (psiClass == null) return;
      TestFramework testFramework = TestFrameworks.detectFramework(psiClass);
      if (testFramework == null) return;
      Arrays.stream(psiClass.getAllMethods())
        .filter(method -> testFramework.isTestMethod(method) &&
                          testName.equals(CoverageListener.sanitize(method.getName(), className.length())))
        .forEach(elements::add);
    });
  }

  public JavaCoverageSuite createSuite(CoverageRunner acceptedCovRunner,
                                       String name, CoverageFileProvider coverageDataFileProvider,
                                       String[] filters,
                                       String[] excludePatterns,
                                       long lastCoverageTimeStamp,
                                       boolean coverageByTestEnabled,
                                       boolean branchCoverage,
                                       boolean trackTestFolders, Project project) {
    return new JavaCoverageSuite(name, coverageDataFileProvider, filters, excludePatterns, lastCoverageTimeStamp,
                                 coverageByTestEnabled, branchCoverage, trackTestFolders, acceptedCovRunner, this, project);
  }

  protected static @NotNull String getPackageName(final PsiFile sourceFile) {
    return ReadAction.computeBlocking(() -> ((PsiClassOwner)sourceFile).getPackageName());
  }

  @Override
  public boolean isReportGenerationAvailable(@NotNull Project project,
                                             @NotNull DataContext dataContext,
                                             @NotNull CoverageSuitesBundle currentSuite) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    return projectSdk != null;
  }

  @Override
  public final void generateReport(final @NotNull Project project,
                                   final @NotNull DataContext dataContext,
                                   final @NotNull CoverageSuitesBundle currentSuite) {

    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, JavaCoverageBundle.message("generating.coverage.report")) {
      final Exception[] myExceptions = new Exception[1];

      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
        try {
          ((JavaCoverageRunner)currentSuite.getSuites()[0].getRunner()).generateReport(currentSuite, project);
        }
        catch (IOException e) {
          LOG.error(e);
        }
        catch (ReportGenerationFailedException e) {
          myExceptions[0] = e;
        }
      }


      @Override
      public void onSuccess() {
        if (myExceptions[0] != null) {
          Messages.showErrorDialog(project, myExceptions[0].getMessage(), CommonBundle.getErrorTitle());
          return;
        }
        if (settings.OPEN_IN_BROWSER) {
          BrowserUtil.browse(Path.of(settings.OUTPUT_DIRECTORY, "index.html"));
        }
      }
    });
  }

  @Override
  public @Nls String getPresentableText() {
    return JavaCoverageBundle.message("java.coverage.engine.presentable.text");
  }

  @Override
  protected boolean isGeneratedCode(Project project, String qualifiedName, Object lineData) {
    if (JavaCoverageOptionsProvider.getInstance(project).isGeneratedConstructor(qualifiedName, ((LineData)lineData).getMethodSignature())) {
      return true;
    }
    return super.isGeneratedCode(project, qualifiedName, lineData);
  }

  @Override
  public CoverageViewExtension createCoverageViewExtension(Project project,
                                                           CoverageSuitesBundle suiteBundle) {
    return new JavaCoverageViewExtension((JavaCoverageAnnotator)getCoverageAnnotator(project), project, suiteBundle);
  }

  public static boolean isSourceMapNeeded(RunConfigurationBase<?> configuration) {
    for (final JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      if (extension.isSourceMapNeeded(configuration)) {
        return true;
      }
    }
    return false;
  }
}
