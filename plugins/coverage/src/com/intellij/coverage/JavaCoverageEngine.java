/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.coverage;

import com.intellij.CommonBundle;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.coverage.view.CoverageViewExtension;
import com.intellij.coverage.view.CoverageViewManager;
import com.intellij.coverage.view.JavaCoverageViewExtension;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import com.intellij.util.containers.HashSet;
import jetbrains.coverage.report.ClassInfo;
import jetbrains.coverage.report.ReportBuilderFactory;
import jetbrains.coverage.report.ReportGenerationFailedException;
import jetbrains.coverage.report.SourceCodeProvider;
import jetbrains.coverage.report.html.HTMLReportBuilder;
import jetbrains.coverage.report.idea.IDEACoverageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageEngine extends CoverageEngine {
  private static final Logger LOG = Logger.getInstance(JavaCoverageEngine.class.getName());

  public static JavaCoverageEngine getInstance() {
    return Extensions.findExtension(EP_NAME, JavaCoverageEngine.class);
  }

  @Override
  public boolean isApplicableTo(@Nullable final RunConfigurationBase conf) {
    if (conf instanceof CommonJavaRunConfigurationParameters) {
      return true;
    }
    for (JavaCoverageEngineExtension extension : Extensions.getExtensions(JavaCoverageEngineExtension.EP_NAME)) {
      if (extension.isApplicableTo(conf)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canHavePerTestCoverage(@Nullable RunConfigurationBase conf) {
    return !(conf instanceof ApplicationConfiguration) && conf instanceof CommonJavaRunConfigurationParameters;
  }

  @NotNull
  @Override
  public CoverageEnabledConfiguration createCoverageEnabledConfiguration(@Nullable final RunConfigurationBase conf) {
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
                                           boolean tracingEnabled,
                                           boolean trackTestFolders, Project project) {

    return createSuite(covRunner, name, coverageDataFileProvider, filters, null, lastCoverageTimeStamp, coverageByTestEnabled,
                       tracingEnabled, trackTestFolders, project);
  }

  @Override
  public CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                           @NotNull final String name,
                                           @NotNull final CoverageFileProvider coverageDataFileProvider,
                                           @NotNull final CoverageEnabledConfiguration config) {
    if (config instanceof JavaCoverageEnabledConfiguration) {
      final JavaCoverageEnabledConfiguration javaConfig = (JavaCoverageEnabledConfiguration)config;
      return createSuite(covRunner, name, coverageDataFileProvider,
                         javaConfig.getPatterns(),
                         javaConfig.getExcludePatterns(),
                         new Date().getTime(),
                         javaConfig.isTrackPerTestCoverage() && !javaConfig.isSampling(),
                         !javaConfig.isSampling(),
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
  public CoverageAnnotator getCoverageAnnotator(Project project) {
    return JavaCoverageAnnotator.getInstance(project);
  }

  /**
   * Determines if coverage information should be displayed for given file
   * @param psiFile
   * @return
   */
  public boolean coverageEditorHighlightingApplicableTo(@NotNull final PsiFile psiFile) {
    if (!(psiFile instanceof PsiClassOwner)) {
      return false;
    }
    // let's show coverage only for module files
    final Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(psiFile));
    return module != null;
  }

  public boolean acceptedByFilters(@NotNull final PsiFile psiFile, @NotNull final CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    final Project project = psiFile.getProject();
    if (!suite.isTrackTestFolders() && TestSourcesFilter.isTestSources(virtualFile, project)) {
      return false;
    }

    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;

      if (javaSuite.isPackageFiltered(ReadAction.compute(() ->((PsiClassOwner)psiFile).getPackageName()))) {
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
  public boolean recompileProjectAndRerunAction(@NotNull final Module module, @NotNull final CoverageSuitesBundle suite,
                                                @NotNull final Runnable chooseSuiteAction) {
    final VirtualFile outputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    final VirtualFile testOutputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests();

    if ((outputpath == null && isModuleOutputNeeded(module, JavaSourceRootType.SOURCE))
        || (suite.isTrackTestFolders() && testOutputpath == null && isModuleOutputNeeded(module, JavaSourceRootType.TEST_SOURCE))) {
      final Project project = module.getProject();
      if (suite.isModuleChecked(module)) return false;
      suite.checkModule(module);
      final Runnable runnable = () -> {
        if (Messages.showOkCancelDialog(
          "Project class files are out of date. Would you like to recompile? The refusal to do it will result in incomplete coverage information",
          "Project is out of date", Messages.getWarningIcon()) == Messages.OK) {
          final CompilerManager compilerManager = CompilerManager.getInstance(project);
          compilerManager.make(compilerManager.createProjectCompileScope(project), new CompileStatusNotification() {
            public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
              if (aborted || errors != 0) return;
              ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) return;
                CoverageDataManager.getInstance(project).chooseSuitesBundle(suite);
              });
            }
          });
        } else if (!project.isDisposed()) {
          CoverageDataManager.getInstance(project).chooseSuitesBundle(null);
        }
      };
      ApplicationManager.getApplication().invokeLater(runnable);
      return true;
    }
    return false;
  }

  private static boolean isModuleOutputNeeded(Module module, final JavaSourceRootType rootType) {
    CompilerManager compilerManager = CompilerManager.getInstance(module.getProject());
    return ModuleRootManager.getInstance(module).getSourceRoots(rootType).stream().anyMatch(vFile -> !compilerManager.isExcludedFromCompilation(vFile));
  }

  @Nullable
  public List<Integer> collectSrcLinesForUntouchedFile(@NotNull final File classFile, @NotNull final CoverageSuitesBundle suite) {
    final List<Integer> uncoveredLines = new ArrayList<>();

    final byte[] content;
    try {
      content = FileUtil.loadFileBytes(classFile);
    }
    catch (IOException e) {
      return null;
    }

    try {
      SourceLineCounterUtil.collectSrcLinesForUntouchedFiles(uncoveredLines, content, suite.isTracingEnabled(), suite.getProject());
    }
    catch (Exception e) {
      LOG.error("Fail to process class from: " + classFile.getPath(), e);
    }
    return uncoveredLines;
  }

  public boolean includeUntouchedFileInCoverage(@NotNull final String qualifiedName,
                                                @NotNull final File outputFile,
                                                @NotNull final PsiFile sourceFile, @NotNull CoverageSuitesBundle suite) {
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      if (javaSuite.isClassFiltered(qualifiedName) || javaSuite.isPackageFiltered(getPackageName(sourceFile))) return true;
    }
    return false;
  }


  @NotNull
  public String getQualifiedName(@NotNull final File outputFile, @NotNull final PsiFile sourceFile) {
    final String packageFQName = getPackageName(sourceFile);
    return StringUtil.getQualifiedName(packageFQName, FileUtil.getNameWithoutExtension(outputFile));
  }

  @NotNull
  @Override
  public Set<String> getQualifiedNames(@NotNull final PsiFile sourceFile) {
    final PsiClass[] classes = ReadAction.compute(() -> ((PsiClassOwner)sourceFile).getClasses());
    final Set<String> qNames = new HashSet<>();
    for (final JavaCoverageEngineExtension nameExtension : Extensions.getExtensions(JavaCoverageEngineExtension.EP_NAME)) {
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

  @NotNull
  public Set<File> getCorrespondingOutputFiles(@NotNull final PsiFile srcFile,
                                               @Nullable final Module module,
                                               @NotNull final CoverageSuitesBundle suite) {
    if (module == null) {
      return Collections.emptySet();
    }
    final Set<File> classFiles = new HashSet<>();
    final VirtualFile outputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    final VirtualFile testOutputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests();

    for (JavaCoverageEngineExtension extension : Extensions.getExtensions(JavaCoverageEngineExtension.EP_NAME)) {
      if (extension.collectOutputFiles(srcFile, outputpath, testOutputpath, suite, classFiles)) return classFiles;
    }

    final String packageFQName = getPackageName(srcFile);
    final String packageVmName = packageFQName.replace('.', '/');

    final List<File> children = new ArrayList<>();
    final File vDir =
      outputpath == null
      ? null : packageVmName.length() > 0
               ? new File(outputpath.getPath() + File.separator + packageVmName) : VfsUtilCore.virtualToIoFile(outputpath);
    if (vDir != null && vDir.exists()) {
      Collections.addAll(children, vDir.listFiles());
    }

    if (suite.isTrackTestFolders()) {
      final File testDir =
        testOutputpath == null
        ? null : packageVmName.length() > 0
                 ? new File(testOutputpath.getPath() + File.separator + packageVmName) : VfsUtilCore.virtualToIoFile(testOutputpath);
      if (testDir != null && testDir.exists()) {
        Collections.addAll(children, testDir.listFiles());
      }
    }

    final PsiClass[] classes = ReadAction.compute(() -> ((PsiClassOwner)srcFile).getClasses());
    for (final PsiClass psiClass : classes) {
      final String className = ReadAction.compute(() -> psiClass.getName());
      for (File child : children) {
        if (FileUtilRt.extensionEquals(child.getName(), StdFileTypes.CLASS.getDefaultExtension())) {
          final String childName = FileUtil.getNameWithoutExtension(child);
          if (childName.equals(className) ||  //class or inner
              childName.startsWith(className) && childName.charAt(className.length()) == '$') {
            classFiles.add(child);
          }
        }
      }
    }
    return classFiles;
  }

  public String generateBriefReport(@NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    int lineNumber,
                                    int startOffset,
                                    int endOffset,
                                    @Nullable LineData lineData) {

    final StringBuffer buf = new StringBuffer();
    buf.append("Hits: ");
    if (lineData == null) {
      buf.append(0);
      return buf.toString();
    }
    buf.append(lineData.getHits()).append("\n");


    for (JavaCoverageEngineExtension extension : Extensions.getExtensions(JavaCoverageEngineExtension.EP_NAME)) {
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
      } else if (parent instanceof PsiSwitchStatement) {
        condition = ((PsiSwitchStatement)parent).getExpression();
      } else if (parent instanceof PsiDoWhileStatement) {
        condition = ((PsiDoWhileStatement)parent).getCondition();
      } else if (parent instanceof PsiForStatement) {
        condition = ((PsiForStatement)parent).getCondition();
      } else if (parent instanceof PsiWhileStatement) {
        condition = ((PsiWhileStatement)parent).getCondition();
      } else if (parent instanceof PsiForeachStatement) {
        condition = ((PsiForeachStatement)parent).getIteratedValue();
      } else if (parent instanceof PsiAssertStatement) {
        condition = ((PsiAssertStatement)parent).getAssertCondition();
      }
      if (condition != null && PsiTreeUtil.isAncestor(condition, psiFile.findElementAt(offset), false)) {
        try {
          final ControlFlow controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(
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

    final String indent = "    ";
    try {
      int idx = 0;
      int hits = 0;
      if (lineData.getJumps() != null) {
        for (Object o : lineData.getJumps()) {
          final JumpData jumpData = (JumpData)o;
          if (jumpData.getTrueHits() + jumpData.getFalseHits() > 0) {
            final PsiExpression expression = expressions.get(idx++);
            final PsiElement parentExpression = expression.getParent();
            boolean reverse = parentExpression instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parentExpression).getOperationTokenType() == JavaTokenType.OROR
                              || parentExpression instanceof PsiDoWhileStatement || parentExpression instanceof PsiAssertStatement;
            buf.append(indent).append(expression.getText()).append("\n");
            buf.append(indent).append(indent).append("true hits: ").append(reverse ? jumpData.getFalseHits() : jumpData.getTrueHits()).append("\n");
            buf.append(indent).append(indent).append("false hits: ").append(reverse ? jumpData.getTrueHits() : jumpData.getFalseHits()).append("\n");
            hits += jumpData.getTrueHits() + jumpData.getFalseHits();
          }
        }
      }

      if (lineData.getSwitches() != null) {
        for (Object o : lineData.getSwitches()) {
          final SwitchData switchData = (SwitchData)o;
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
        buf.append("Unknown outcome: ").append(lineData.getHits() - hits);
      }
    }
    catch (Exception e) {
      LOG.info(e);
      return "Hits: " + lineData.getHits();
    }
    return buf.toString();
  }

  @Nullable
  public String getTestMethodName(@NotNull final PsiElement element,
                                  @NotNull final AbstractTestProxy testProxy) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName != null) {
          return qualifiedName + "." + method.getName();
        }
      }
    }
    return testProxy.toString();
  }


  @NotNull
  public List<PsiElement> findTestsByNames(@NotNull String[] testNames, @NotNull Project project) {
    final List<PsiElement> elements = new ArrayList<>();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    for (String testName : testNames) {
      PsiClass psiClass =
          facade.findClass(StringUtil.getPackageName(testName, '_').replaceAll("\\_", "\\."), projectScope);
      int lastIdx = testName.lastIndexOf("_");
      if (psiClass != null) {
        collectTestsByName(elements, testName, psiClass, lastIdx);
      } else {
        String className = testName;
        while (lastIdx > 0) {
          className = className.substring(0, lastIdx);
          psiClass = facade.findClass(StringUtil.getPackageName(className, '_').replaceAll("\\_", "\\."), projectScope);
          lastIdx = className.lastIndexOf("_");
          if (psiClass != null) {
            collectTestsByName(elements, testName, psiClass, lastIdx);
            break;
          }
        }
      }
    }
    return elements;
  }

  private static void collectTestsByName(List<PsiElement> elements, String testName, PsiClass psiClass, int lastIdx) {
    final PsiMethod[] testsByName = psiClass.findMethodsByName(testName.substring(lastIdx + 1), true);
    if (testsByName.length == 1) {
      elements.add(testsByName[0]);
    }
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
                                          boolean tracingEnabled,
                                          boolean trackTestFolders, Project project) {
    return new JavaCoverageSuite(name, coverageDataFileProvider, filters, excludePatterns, lastCoverageTimeStamp, coverageByTestEnabled, tracingEnabled,
                                 trackTestFolders, acceptedCovRunner, this, project);
  }

  @NotNull
  protected static String getPackageName(final PsiFile sourceFile) {
    return ReadAction.compute(() -> ((PsiClassOwner)sourceFile).getPackageName());
  }

  protected static void generateJavaReport(@NotNull final Project project,
                                           final File tempFile,
                                           final CoverageSuitesBundle currentSuite) {
    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    final ProjectData projectData = currentSuite.getCoverageData();
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating coverage report ...") {
      final Exception[] myExceptions = new Exception[1];

      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          new SaveHook(tempFile, true, new IdeaClassFinder(project, currentSuite)).save(projectData);
          final HTMLReportBuilder builder = ReportBuilderFactory.createHTMLReportBuilder();
          builder.setReportDir(new File(settings.OUTPUT_DIRECTORY));
          final SourceCodeProvider sourceCodeProvider = new SourceCodeProvider() {
            public String getSourceCode(@NotNull final String classname) throws IOException {
              return DumbService.getInstance(project).runReadActionInSmartMode(() -> {
                if (project.isDisposed()) return "";
                final PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), classname);
                return psiClass != null ? psiClass.getNavigationElement().getContainingFile().getText() : "";
              });
            }
          };
          builder.generateReport(new IDEACoverageData(projectData, sourceCodeProvider) {
            @NotNull
            @Override
            public Collection<ClassInfo> getClasses() {
              final Collection<ClassInfo> classes = super.getClasses();
              if (!currentSuite.isTrackTestFolders()) {
                final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
                final GlobalSearchScope productionScope = GlobalSearchScopesCore.projectProductionScope(project);
                for (Iterator<ClassInfo> iterator = classes.iterator(); iterator.hasNext();) {
                  final ClassInfo aClass = iterator.next();
                  final PsiClass psiClass = DumbService.getInstance(project).runReadActionInSmartMode(() -> {
                    if (project.isDisposed()) return null;
                    return psiFacade.findClass(aClass.getFQName(), productionScope);
                  });
                  if (psiClass == null) {
                    iterator.remove();
                  }
                }
              }
              return classes;
            }
          });
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
    try {
      final File tempFile = FileUtil.createTempFile("temp", "");
      tempFile.deleteOnExit();
      generateJavaReport(project, tempFile, currentSuite);
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  @Override
  public String getPresentableText() {
    return "Java Coverage";
  }

  @Override
  public boolean isGeneratedCode(Project project, String qualifiedName, Object lineData) {
    PsiClass psiClass = ReadAction.compute(() -> ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), qualifiedName));
    return PackageAnnotator.isGeneratedDefaultConstructor(psiClass, ((LineData)lineData).getMethodSignature());
  }

  @Override
  public CoverageViewExtension createCoverageViewExtension(Project project,
                                                           CoverageSuitesBundle suiteBundle,
                                                           CoverageViewManager.StateBean stateBean) {
    return new JavaCoverageViewExtension((JavaCoverageAnnotator)getCoverageAnnotator(project), project, suiteBundle, stateBean);
  }

  public boolean isSourceMapNeeded(RunConfigurationBase configuration) {
    for (final JavaCoverageEngineExtension extension : Extensions.getExtensions(JavaCoverageEngineExtension.EP_NAME)) {
      if (extension.isSourceMapNeeded(configuration)) {
        return true;
      }
    }
    return false;
  }
}
