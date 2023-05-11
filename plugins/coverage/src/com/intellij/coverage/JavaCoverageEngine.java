// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.CommonBundle;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.coverage.listeners.java.CoverageListener;
import com.intellij.coverage.view.CoverageViewExtension;
import com.intellij.coverage.view.CoverageViewManager;
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
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.java.coverage.JavaCoverageBundle;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.task.ProjectTaskManager;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.coverage.report.ReportGenerationFailedException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageEngine extends CoverageEngine {
  private static final Logger LOG = Logger.getInstance(JavaCoverageEngine.class.getName());

  public static JavaCoverageEngine getInstance() {
    return EP_NAME.findExtensionOrFail(JavaCoverageEngine.class);
  }

  @Override
  public boolean isApplicableTo(@NotNull final RunConfigurationBase conf) {
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

  private static boolean willRunOnTarget(@NotNull final TargetEnvironmentAwareRunProfile configuration) {
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
  public Set<String> getTestsForLine(Project project, String classFQName, int lineNumber) {
    return extractTracedTests(project, classFQName, lineNumber);
  }

  @Override
  public boolean wasTestDataCollected(Project project) {
    File[] files = getTraceFiles(project);
    return files != null && files.length > 0;
  }

  private static Set<String> extractTracedTests(Project project, final String classFQName, final int lineNumber) {
    Set<String> tests = new HashSet<>();
    final File[] traceFiles = getTraceFiles(project);
    if (traceFiles == null) return tests;
    for (File traceFile : traceFiles) {
      DataInputStream in = null;
      try {
        in = new DataInputStream(new FileInputStream(traceFile));
        extractTests(traceFile, in, tests, classFQName, lineNumber);
      }
      catch (Exception ex) {
        LOG.error(traceFile.getName(), ex);
      }
      finally {
        try {
          if (in != null) {
            in.close();
          }
        }
        catch (IOException ex) {
          LOG.error(ex);
        }
      }
    }
    return tests;
  }

  private static void extractTests(final File traceFile,
                                   final DataInputStream in,
                                   final Set<? super String> tests,
                                   final String classFQName,
                                   final int lineNumber) throws IOException {
    long traceSize = in.readInt();
    for (int i = 0; i < traceSize; i++) {
      final String className = in.readUTF();
      final int linesSize = in.readInt();
      for(int l = 0; l < linesSize; l++) {
        final int line = in.readInt();
        if (Comparing.strEqual(className, classFQName)) {
          if (lineNumber == line) {
            tests.add(FileUtilRt.getNameWithoutExtension(traceFile.getName()));
            return;
          }
        }
      }
    }
  }

  private static File @Nullable [] getTraceFiles(Project project) {
    final CoverageSuitesBundle currentSuite = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
    if (currentSuite == null) return null;
    final List<File> files = new ArrayList<>();
    for (CoverageSuite coverageSuite : currentSuite.getSuites()) {
      final File tracesDir = getTracesDirectory(coverageSuite);
      final File[] suiteFiles = tracesDir.listFiles();
      if (suiteFiles != null) {
        Collections.addAll(files, suiteFiles);
      }
    }

    return files.isEmpty() ? null : files.toArray(new File[0]);
  }

  private static File getTracesDirectory(CoverageSuite coverageSuite) {
    final String filePath = coverageSuite.getCoverageDataFileName();
    final String dirName = FileUtilRt.getNameWithoutExtension(new File(filePath).getName());

    final File parentDir = new File(filePath).getParentFile();
    return new File(parentDir, dirName);
  }


  @Override
  public void collectTestLines(List<String> sanitizedTestNames,
                               CoverageSuite suite,
                               Map<String, Set<Integer>> executionTrace) {
    final File tracesDir = getTracesDirectory(suite);
    for (String testName : sanitizedTestNames) {
      final File file = new File(tracesDir, testName + ".tr");
      if (file.exists()) {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
          int traceSize = in.readInt();
          for (int i = 0; i < traceSize; i++) {
            final String className = in.readUTF();
            final int linesSize = in.readInt();
            final Set<Integer> lines = executionTrace.computeIfAbsent(className, k -> new HashSet<>());
            for(int l = 0; l < linesSize; l++) {
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
    if (suite.isTracingEnabled()) {
      File tracesDirectory = getTracesDirectory(suite);
      if (tracesDirectory.exists()) {
        FileUtil.delete(tracesDirectory);
      }
    }
  }

  @NotNull
  @Override
  public CoverageEnabledConfiguration createCoverageEnabledConfiguration(@NotNull final RunConfigurationBase conf) {
    return new JavaCoverageEnabledConfiguration(conf, this);
  }

  @Nullable
  @Override
  public CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                           @NotNull final String name,
                                           @NotNull final CoverageFileProvider coverageDataFileProvider,
                                           String[] filters,
                                           long lastCoverageTimeStamp,
                                           String suiteToMerge,
                                           boolean coverageByTestEnabled,
                                           boolean branchCoverage,
                                           boolean trackTestFolders, Project project) {

    return createSuite(covRunner, name, coverageDataFileProvider, filters, null, lastCoverageTimeStamp, coverageByTestEnabled,
                       branchCoverage, trackTestFolders, project);
  }

  @Override
  public CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                           @NotNull final String name,
                                           @NotNull final CoverageFileProvider coverageDataFileProvider,
                                           @NotNull final CoverageEnabledConfiguration config) {
    if (config instanceof JavaCoverageEnabledConfiguration javaConfig) {
      return createSuite(covRunner, name, coverageDataFileProvider,
                         javaConfig.getPatterns(),
                         javaConfig.getExcludePatterns(),
                         new Date().getTime(),
                         javaConfig.isTrackPerTestCoverage() && javaConfig.isTracingEnabled(),
                         javaConfig.isTracingEnabled(),
                         javaConfig.isTrackTestFolders(), config.getConfiguration().getProject());
    }
    return null;
  }

  @Nullable
  @Override
  public CoverageSuite createEmptyCoverageSuite(@NotNull CoverageRunner coverageRunner) {
    return new JavaCoverageSuite(this);
  }

  @NotNull
  @Override
  public CoverageAnnotator getCoverageAnnotator(@NotNull Project project) {
    return JavaCoverageAnnotator.getInstance(project);
  }

  /**
   * Determines if coverage information should be displayed for given file
   */
  @Override
  public boolean coverageEditorHighlightingApplicableTo(@NotNull final PsiFile psiFile) {
    if (!(psiFile instanceof PsiClassOwner)) {
      return false;
    }
    // let's show coverage only for module files
    final Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(psiFile));
    return module != null;
  }

  @Override
  public boolean acceptedByFilters(@NotNull final PsiFile psiFile, @NotNull final CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    final Project project = psiFile.getProject();
    if (!suite.isTrackTestFolders() && ReadAction.compute(() -> TestSourcesFilter.isTestSources(virtualFile, project))) {
      return false;
    }

    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;

      if (psiFile instanceof PsiClassOwner && javaSuite.isPackageFiltered(ReadAction.compute(() -> ((PsiClassOwner)psiFile).getPackageName()))) {
        return true;
      } else {
        final List<PsiClass> classes = javaSuite.getCurrentSuiteClasses(project);
        for (PsiClass aClass : classes) {
          final PsiFile containingFile = ReadAction.compute(aClass::getContainingFile);
          if (psiFile.equals(containingFile)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean recompileProjectAndRerunAction(@NotNull Module module, @NotNull final CoverageSuitesBundle suite,
                                                @NotNull final Runnable chooseSuiteAction) {
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
        ProjectTaskManager taskManager = ProjectTaskManager.getInstance(project);
        Promise<ProjectTaskManager.Result> promise = taskManager.buildAllModules();
        promise.onSuccess(result -> ApplicationManager.getApplication().invokeLater(() -> {
                            CoverageDataManager.getInstance(project).chooseSuitesBundle(suite);
                          }, o -> project.isDisposed())
        );
      }));
      CoverageNotifications.getInstance(project).addNotification(notification);
      notification.notify(project);
    }
    return false;
  }

  @Nullable
  private static File getOutputpath(CompilerModuleExtension compilerModuleExtension) {
    final @Nullable String outputpathUrl = compilerModuleExtension.getCompilerOutputUrl();
    final @Nullable File outputpath = outputpathUrl != null ? new File(VfsUtilCore.urlToPath(outputpathUrl)) : null;
    return outputpath;
  }

  @Nullable
  private static File getTestOutputpath(CompilerModuleExtension compilerModuleExtension) {
    final @Nullable String outputpathUrl = compilerModuleExtension.getCompilerOutputUrlForTests();
    final @Nullable File outputpath = outputpathUrl != null ? new File(VfsUtilCore.urlToPath(outputpathUrl)) : null;
    return outputpath;
  }

  @Override
  @Nullable
  public List<Integer> collectSrcLinesForUntouchedFile(@NotNull final File classFile, @NotNull final CoverageSuitesBundle suite) {
    final byte[] content;
    try {
      content = FileUtil.loadFileBytes(classFile);
    }
    catch (IOException e) {
      return null;
    }

    final List<Integer> uncoveredLines = new ArrayList<>();
    try {
      SourceLineCounterUtil.collectSrcLinesForUntouchedFiles(uncoveredLines, content, suite.getProject());
    }
    catch (Exception e) {
      if (e instanceof ControlFlowException) throw e;
      LOG.error("Fail to process class from: " + classFile.getPath(), e);
    }
    return uncoveredLines;
  }

  @Override
  public boolean includeUntouchedFileInCoverage(@NotNull final String qualifiedName,
                                                @NotNull final File outputFile,
                                                @NotNull final PsiFile sourceFile, @NotNull CoverageSuitesBundle suite) {
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      if (javaSuite.isClassFiltered(qualifiedName) || javaSuite.isPackageFiltered(getPackageName(sourceFile))) return true;
    }
    return false;
  }


  @Override
  @NotNull
  public String getQualifiedName(@NotNull final File outputFile, @NotNull final PsiFile sourceFile) {
    final String packageFQName = getPackageName(sourceFile);
    return StringUtil.getQualifiedName(packageFQName, FileUtilRt.getNameWithoutExtension(outputFile.getName()));
  }

  @NotNull
  @Override
  public Set<String> getQualifiedNames(@NotNull final PsiFile sourceFile) {
    final PsiClass[] classes = ReadAction.compute(() -> ((PsiClassOwner)sourceFile).getClasses());
    final Set<String> qNames = new HashSet<>();
    for (final JavaCoverageEngineExtension nameExtension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      if (ReadAction.compute(() -> nameExtension.suggestQualifiedName(sourceFile, classes, qNames))) {
        return qNames;
      }
    }
    for (final PsiClass aClass : classes) {
      final String qName = ReadAction.compute(() -> aClass.getQualifiedName());
      if (qName == null) continue;
      qNames.add(qName);
    }
    return qNames;
  }

  @Override
  @NotNull
  public Set<File> getCorrespondingOutputFiles(@NotNull final PsiFile srcFile,
                                               @Nullable final Module module,
                                               @NotNull final CoverageSuitesBundle suite) {
    if (module == null) {
      return Collections.emptySet();
    }
    final Set<File> classFiles = new HashSet<>();
    final CompilerModuleExtension moduleExtension = Objects.requireNonNull(CompilerModuleExtension.getInstance(module));
    final @Nullable File outputpath = getOutputpath(moduleExtension);
    final @Nullable File testOutputpath = getTestOutputpath(moduleExtension);

    final @Nullable VirtualFile outputpathVirtualFile = fileToVirtualFileWithRefresh(outputpath);
    final @Nullable VirtualFile testOutputpathVirtualFile = fileToVirtualFileWithRefresh(testOutputpath);

    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      if (extension.collectOutputFiles(srcFile, outputpathVirtualFile, testOutputpathVirtualFile, suite, classFiles)) return classFiles;
    }

    final Project project = module.getProject();
    final CoverageDataManager dataManager = CoverageDataManager.getInstance(project);
    boolean includeTests = suite.isTrackTestFolders();
    final VirtualFile[] roots = JavaCoverageClassesEnumerator.getRoots(dataManager, module, includeTests);


    final String packageFQName = getPackageName(srcFile);
    final String packageVmName = packageFQName.replace('.', '/');

    final List<File> children = new ArrayList<>();
    for (VirtualFile root : roots) {
      if (root == null) continue;
      final VirtualFile packageDir = root.findFileByRelativePath(packageVmName);
      if (packageDir == null) continue;
      final File dir = VfsUtilCore.virtualToIoFile(packageDir);
      if (!dir.exists()) continue;
      final File[] files = dir.listFiles();
      if (files == null) continue;
      Collections.addAll(children, files);
    }

    final PsiClass[] classes = ReadAction.compute(() -> ((PsiClassOwner)srcFile).getClasses());
    for (final PsiClass psiClass : classes) {
      final String className = ReadAction.compute(() -> psiClass.getName());
      for (File child : children) {
        if (FileUtilRt.extensionEquals(child.getName(), JavaClassFileType.INSTANCE.getDefaultExtension())) {
          final String childName = FileUtilRt.getNameWithoutExtension(child.getName());
          if (childName.equals(className) ||  //class or inner
              childName.startsWith(className) && childName.charAt(className.length()) == '$') {
            classFiles.add(child);
          }
        }
      }
    }
    return classFiles;
  }

  @Nullable
  private static VirtualFile fileToVirtualFileWithRefresh(@Nullable File file) {
    if (file == null) return null;
    return WriteAction.computeAndWait(() -> VfsUtil.findFileByIoFile(file, true));
  }

  @Override
  public String generateBriefReport(@NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    int lineNumber,
                                    int startOffset,
                                    int endOffset,
                                    @Nullable LineData lineData) {

    final StringBuilder buf = new StringBuilder();
    buf.append(CoverageBundle.message("hits.title", ""));
    if (lineData == null) {
      buf.append(0);
      return buf.toString();
    }
    buf.append(lineData.getHits()).append("\n");


    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      String report = extension.generateBriefReport(editor, psiFile, lineNumber, startOffset, endOffset, lineData);
      if (report != null) {
        buf.append(report);
        return report;
      }
    }

    final List<PsiExpression> expressions = new ArrayList<>();

    final Project project = editor.getProject();
    for(int offset = startOffset; offset < endOffset; offset++) {
      PsiElement parent = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiStatement.class);
      PsiElement condition = null;
      if (parent instanceof PsiIfStatement) {
        condition = ((PsiIfStatement)parent).getCondition();
      }
      else if (parent instanceof PsiSwitchStatement) {
        condition = ((PsiSwitchStatement)parent).getExpression();
      }
      else if (parent instanceof PsiConditionalLoopStatement) {
        condition = ((PsiConditionalLoopStatement)parent).getCondition();
      }
      else if (parent instanceof PsiForeachStatement) {
        condition = ((PsiForeachStatement)parent).getIteratedValue();
      }
      else if (parent instanceof PsiAssertStatement) {
        condition = ((PsiAssertStatement)parent).getAssertCondition();
      }
      if (PsiTreeUtil.isAncestor(condition, Objects.requireNonNull(psiFile.findElementAt(offset)), false)) {
        try {
          final ControlFlow controlFlow = ControlFlowFactory.getInstance(Objects.requireNonNull(project)).getControlFlow(
            parent, AllVariablesControlFlowPolicy.getInstance());
          for (Instruction instruction : controlFlow.getInstructions()) {
            if (instruction instanceof ConditionalBranchingInstruction) {
              final PsiExpression expression = ((ConditionalBranchingInstruction)instruction).expression;
              if (!expressions.contains(expression)) {
                expressions.add(expression);
              }
            }
          }
        }
        catch (AnalysisCanceledException e) {
          return buf.toString();
        }
      }
    }

    try {
      int idx = 0;
      int hits = 0;
      final String indent = "    ";
      if (lineData.getJumps() != null) {
        for (JumpData jumpData : lineData.getJumps()) {
          if (jumpData.getTrueHits() + jumpData.getFalseHits() > 0) {
            final PsiExpression expression = expressions.get(idx++);
            final PsiElement parentExpression = expression.getParent();
            boolean reverse = parentExpression instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parentExpression).getOperationTokenType() == JavaTokenType.OROR
                              || parentExpression instanceof PsiDoWhileStatement || parentExpression instanceof PsiAssertStatement;
            buf.append(indent).append(expression.getText()).append("\n");
            buf.append(indent).append(indent).append(PsiKeyword.TRUE).append(" ").append(CoverageBundle.message("hits.message", reverse ? jumpData.getFalseHits() : jumpData
              .getTrueHits())).append("\n");
            buf.append(indent).append(indent).append(PsiKeyword.FALSE).append(" ").append(CoverageBundle.message("hits.message", reverse ? jumpData
              .getTrueHits() : jumpData.getFalseHits())).append("\n");
            hits += jumpData.getTrueHits() + jumpData.getFalseHits();
          }
        }
      }

      if (lineData.getSwitches() != null) {
        for (SwitchData switchData : lineData.getSwitches()) {
          final PsiExpression conditionExpression = expressions.get(idx++);
          buf.append(indent).append(conditionExpression.getText()).append("\n");
          int i = 0;
          for (int key : switchData.getKeys()) {
            final int switchHits = switchData.getHits()[i++];
            buf.append(indent).append(indent).append("case ").append(key).append(": ").append(switchHits).append("\n");
            hits += switchHits;
          }
          int defaultHits = switchData.getDefaultHits();
          final boolean hasDefaultLabel = hasDefaultLabel(conditionExpression);
          if (hasDefaultLabel || defaultHits > 0) {
            if (!hasDefaultLabel) {
              defaultHits -= hits;
            }

            if (hasDefaultLabel || defaultHits > 0) {
              buf.append(indent).append(indent).append("default: ").append(defaultHits).append("\n");
              hits += defaultHits;
            }
          }
        }
      }
      if (lineData.getHits() > hits && hits > 0) {
        buf.append(JavaCoverageBundle.message("report.unknown.outcome",lineData.getHits() - hits));
      }
    }
    catch (Exception e) {
      LOG.info(e);
      return CoverageBundle.message("hits.title", lineData.getHits());
    }
    return buf.toString();
  }

  @Override
  @Nullable
  public String getTestMethodName(@NotNull final PsiElement element,
                                  @NotNull final AbstractTestProxy testProxy) {
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
  @NotNull
  public List<PsiElement> findTestsByNames(String @NotNull [] testNames, @NotNull Project project) {
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
    PsiClass psiClass = ClassUtil.findPsiClass(psiManager, className);
    if (psiClass == null) return;
    TestFramework testFramework = TestFrameworks.detectFramework(psiClass);
    if (testFramework == null) return;
    Arrays.stream(psiClass.getAllMethods())
      .filter(method -> testFramework.isTestMethod(method) &&
                        testName.equals(CoverageListener.sanitize(method.getName(), className.length())))
      .forEach(elements::add);
  }


  private static boolean hasDefaultLabel(final PsiElement conditionExpression) {
    boolean hasDefault = false;
    final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(conditionExpression, PsiSwitchStatement.class);
    final PsiCodeBlock body = ((PsiSwitchStatementImpl)conditionExpression.getParent()).getBody();
    if (body != null) {
      final PsiElement bodyElement = body.getFirstBodyElement();
      if (bodyElement != null) {
        PsiSwitchLabelStatement label = PsiTreeUtil.getNextSiblingOfType(bodyElement, PsiSwitchLabelStatement.class);
        while (label != null) {
          if (label.getEnclosingSwitchStatement() == switchStatement) {
            hasDefault |= label.isDefaultCase();
          }
          label = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatement.class);
        }
      }
    }
    return hasDefault;
  }

  protected JavaCoverageSuite createSuite(CoverageRunner acceptedCovRunner,
                                          String name, CoverageFileProvider coverageDataFileProvider,
                                          String[] filters,
                                          String[] excludePatterns,
                                          long lastCoverageTimeStamp,
                                          boolean coverageByTestEnabled,
                                          boolean branchCoverage,
                                          boolean trackTestFolders, Project project) {
    return new JavaCoverageSuite(name, coverageDataFileProvider, filters, excludePatterns, lastCoverageTimeStamp, coverageByTestEnabled, branchCoverage,
                                 trackTestFolders, acceptedCovRunner, this, project);
  }

  @NotNull
  protected static String getPackageName(final PsiFile sourceFile) {
    return ReadAction.compute(() -> ((PsiClassOwner)sourceFile).getPackageName());
  }

  @Override
  public boolean isReportGenerationAvailable(@NotNull Project project,
                                             @NotNull DataContext dataContext,
                                             @NotNull CoverageSuitesBundle currentSuite) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    return projectSdk != null;
  }

  @Override
  public final void generateReport(@NotNull final Project project,
                                   @NotNull final DataContext dataContext,
                                   @NotNull final CoverageSuitesBundle currentSuite) {

    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, JavaCoverageBundle.message("generating.coverage.report")) {
      final Exception[] myExceptions = new Exception[1];

      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
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
          BrowserUtil.browse(new File(settings.OUTPUT_DIRECTORY, "index.html"));
        }
      }
    });
  }

  @Override
  public @Nls String getPresentableText() {
    return JavaCoverageBundle.message("java.coverage.engine.presentable.text");
  }

  @Override
  public boolean isGeneratedCode(Project project, String qualifiedName, Object lineData) {
    if (JavaCoverageOptionsProvider.getInstance(project).isGeneratedConstructor(qualifiedName, ((LineData)lineData).getMethodSignature())) return true;
    return super.isGeneratedCode(project, qualifiedName, lineData);
  }

  @Override
  public CoverageViewExtension createCoverageViewExtension(Project project,
                                                           CoverageSuitesBundle suiteBundle,
                                                           CoverageViewManager.StateBean stateBean) {
    return new JavaCoverageViewExtension((JavaCoverageAnnotator)getCoverageAnnotator(project), project, suiteBundle, stateBean);
  }

  public static boolean isSourceMapNeeded(RunConfigurationBase configuration) {
    for (final JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      if (extension.isSourceMapNeeded(configuration)) {
        return true;
      }
    }
    return false;
  }
}
