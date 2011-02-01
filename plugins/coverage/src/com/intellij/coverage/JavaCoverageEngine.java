package com.intellij.coverage;

import com.intellij.CommonBundle;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.ide.BrowserUtil;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import com.intellij.rt.coverage.instrumentation.SourceLineCounter;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import jetbrains.coverage.report.ClassInfo;
import jetbrains.coverage.report.ReportBuilderFactory;
import jetbrains.coverage.report.ReportGenerationFailedException;
import jetbrains.coverage.report.SourceCodeProvider;
import jetbrains.coverage.report.html.HTMLReportBuilder;
import jetbrains.coverage.report.idea.IDEACoverageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.EmptyVisitor;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageEngine extends CoverageEngine {
  private static final Logger LOG = Logger.getInstance(JavaCoverageEngine.class.getName());

  @Override
  public boolean isApplicableTo(@Nullable final RunConfigurationBase conf) {
    return conf instanceof CommonJavaRunConfigurationParameters;
  }

  @Override
  public boolean canHavePerTestCoverage(@Nullable RunConfigurationBase conf) {
    return !(conf instanceof ApplicationConfiguration);
  }

  @Override
  public CoverageEnabledConfiguration createCoverageEnabledConfiguration(@Nullable final RunConfigurationBase conf) {
    if (isApplicableTo(conf)) {
      return new JavaCoverageEnabledConfiguration(conf, this);
    }
    return null;
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
                                           boolean trackTestFolders) {

    return createSuite(covRunner, name, coverageDataFileProvider, filters, lastCoverageTimeStamp, coverageByTestEnabled,
                       tracingEnabled, trackTestFolders);
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
                         new Date().getTime(),
                         javaConfig.isTrackPerTestCoverage() && !javaConfig.isSampling(),
                         !javaConfig.isSampling(),
                         javaConfig.isTrackTestFolders());
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
    final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Nullable
      public Module compute() {
        return ModuleUtil.findModuleForPsiElement(psiFile);
      }
    });
    return module != null;
  }

  public boolean acceptedByFilters(@NotNull final PsiFile psiFile, @NotNull final CoverageSuitesBundle suite) {
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;

      final Project project = psiFile.getProject();
      final List<PsiPackage> packages = javaSuite.getCurrentSuitePackages(project);
      if (isUnderFilteredPackages((PsiClassOwner)psiFile, packages)) {
        return true;
      } else {
        final List<PsiClass> classes = javaSuite.getCurrentSuiteClasses(project);
        for (PsiClass aClass : classes) {
          final PsiFile containingFile = aClass.getContainingFile();
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

    if ((outputpath == null && isModuleOutputNeeded(module, false)) || (suite.isTrackTestFolders() && testOutputpath == null && isModuleOutputNeeded(module, true))) {
      final Project project = module.getProject();
      if (suite.isModuleChecked(module)) return false;
      suite.checkModule(module);
      final Runnable runnable = new Runnable() {
        public void run() {
          if (Messages.showOkCancelDialog(
            "Project class files are out of date. Would you like to recompile? The refusal to do it will result in incomplete coverage information",
            "Project is out of date", Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE) {
            final CompilerManager compilerManager = CompilerManager.getInstance(project);
            compilerManager.make(compilerManager.createProjectCompileScope(project), new CompileStatusNotification() {
              public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
                if (aborted || errors != 0) return;
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    if (project.isDisposed()) return;
                    CoverageDataManager.getInstance(project).chooseSuitesBundle(suite);
                  }
                });
              }
            });
          } else if (!project.isDisposed()) {
            CoverageDataManager.getInstance(project).chooseSuitesBundle(null);
          }
        }
      };
      ApplicationManager.getApplication().invokeLater(runnable);
      return true;
    }
    return false;
  }

  private static boolean isModuleOutputNeeded(Module module, boolean checkTestRoots) {
    for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      for (SourceFolder sourceFolder : entry.getSourceFolders()) {
        if (checkTestRoots) {
          if (sourceFolder.isTestSource()) return true;
        } else {
          if (!sourceFolder.isTestSource()) return true;
        }
      }
    }
    return false;
  }

  public static boolean isUnderFilteredPackages(final PsiClassOwner javaFile, final List<PsiPackage> packages) {
    final String hisPackageName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return javaFile.getPackageName();
      }
    });
    PsiPackage hisPackage = JavaPsiFacade.getInstance(javaFile.getProject()).findPackage(hisPackageName);
    if (hisPackage == null) return false;
    for (PsiPackage aPackage : packages) {
      if (PsiTreeUtil.isAncestor(aPackage, hisPackage, false)) return true;
    }
    return false;
  }

  @Nullable
  public List<Integer> collectSrcLinesForUntouchedFile(@NotNull final VirtualFile classFile, @NotNull final CoverageSuitesBundle suite) {
    final List<Integer> uncoveredLines = new ArrayList<Integer>();

    final byte[] content;
    try {
      content = classFile.contentsToByteArray();
    }
    catch (IOException e) {
      return null;
    }

    final ClassReader reader = new ClassReader(content, 0, content.length);
    final boolean excludeLines = suite.isTracingEnabled();
    final SourceLineCounter collector = new SourceLineCounter(new EmptyVisitor(), null, excludeLines);
    reader.accept(collector, 0);
    final TIntObjectHashMap lines = collector.getSourceLines();
    lines.forEachKey(new TIntProcedure() {
      public boolean execute(int line) {
        line--;
        uncoveredLines.add(line);
        return true;
      }
    });
    return uncoveredLines;
  }

  public boolean includeUntouchedFileInCoverage(@NotNull final String qualifiedName,
                                                @NotNull final VirtualFile outputFile,
                                                @NotNull final PsiFile sourceFile, @NotNull CoverageSuitesBundle suite) {
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      if (javaSuite.isClassFiltered(qualifiedName) || javaSuite.isPackageFiltered(getPackageName(sourceFile))) return true;
    }
    return false;
  }


  public String getQualifiedName(@NotNull final VirtualFile outputFile, @NotNull final PsiFile sourceFile) {
    final String packageFQName = getPackageName(sourceFile);
    return StringUtil.getQualifiedName(packageFQName, outputFile.getNameWithoutExtension());
  }

  @NotNull
  @Override
  public Set<String> getQualifiedNames(@NotNull final PsiFile sourceFile) {
    final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
      public PsiClass[] compute() {
        return ((PsiClassOwner)sourceFile).getClasses();
      }
    });
    final Set<String> qNames = new HashSet<String>();
    for (final PsiClass aClass : classes) {
      final String qName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        public String compute() {
          return aClass.getQualifiedName();
        }
      });
      if (qName == null) continue;
      qNames.add(qName);
    }
    if (qNames.isEmpty()) {
      final VirtualFile virtualFile = sourceFile.getVirtualFile();
      if (virtualFile != null) {
        qNames.add(getQualifiedName(virtualFile, sourceFile));
      }
    }
    return qNames;
  }

  @NotNull
  public Set<VirtualFile> getCorrespondingOutputFiles(@NotNull final PsiFile srcFile,
                                                      @Nullable final Module module,
                                                      @NotNull final CoverageSuitesBundle suite) {
    if (module == null) {
      return Collections.emptySet();
    }
    final VirtualFile outputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    final VirtualFile testOutputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests();

    final String packageFQName = getPackageName(srcFile);
    final String packageVmName = packageFQName.replace('.', '/');

    final List<VirtualFile> children = new ArrayList<VirtualFile>();
    final VirtualFile vDir = packageVmName.length() > 0 && outputpath != null ? outputpath.findFileByRelativePath(packageVmName) : outputpath;
    if (vDir != null) {
      Collections.addAll(children, vDir.getChildren());
    }

    if (suite.isTrackTestFolders()) {
      final VirtualFile testDir = packageVmName.length() > 0 && testOutputpath != null ? testOutputpath.findFileByRelativePath(packageVmName) : testOutputpath;
      if (testDir != null) {
        Collections.addAll(children, testDir.getChildren());
      }
    }

    final Set<VirtualFile> classFiles = new HashSet<VirtualFile>();
    final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
      public PsiClass[] compute() {
        return ((PsiClassOwner)srcFile).getClasses();
      }
    });
    for (PsiClass psiClass : classes) {
      final String className = psiClass.getName();
      for (VirtualFile child : children) {
        if (child.getFileType().equals(StdFileTypes.CLASS)) {
          final String childName = child.getNameWithoutExtension();
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

    final List<PsiExpression> expressions = new ArrayList<PsiExpression>();

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
            boolean reverse = parentExpression instanceof PsiBinaryExpression && ((PsiBinaryExpression)parentExpression).getOperationSign().getTokenType() == JavaTokenType.OROR || parentExpression instanceof PsiDoWhileStatement || parentExpression instanceof PsiAssertStatement;
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
          if (hasDefaultLabel(conditionExpression)) {
            buf.append(indent).append(indent).append("Default hits: ").append(switchData.getDefaultHits()).append("\n");
            hits += switchData.getDefaultHits();
          }
          int i = 0;
          for (int key : switchData.getKeys()) {
            final int switchHits = switchData.getHits()[i++];
            buf.append(indent).append(indent).append("case ").append(key).append(": ").append(switchHits).append("\n");
            hits += switchHits;
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
    return testProxy.toString();
  }


  @NotNull
  public List<PsiElement> findTestsByNames(@NotNull String[] testNames, @NotNull Project project) {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    for (String testName : testNames) {
      final PsiClass psiClass =
          facade.findClass(StringUtil.getPackageName(testName, '_').replaceAll("\\_", "\\."), GlobalSearchScope.projectScope(project));
      if (psiClass != null) {
        final PsiMethod[] testsByName = psiClass.findMethodsByName(testName.substring(testName.lastIndexOf("_") + 1), true);
        if (testsByName.length == 1) {
          elements.add(testsByName[0]);
        }
      }
    }
    return elements;
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

  private JavaCoverageSuite createSuite(CoverageRunner acceptedCovRunner,
                                        String name, CoverageFileProvider coverageDataFileProvider,
                                        String[] filters,
                                        long lastCoverageTimeStamp,
                                        boolean coverageByTestEnabled,
                                        boolean tracingEnabled,
                                        boolean trackTestFolders) {
    return new JavaCoverageSuite(name, coverageDataFileProvider, filters, lastCoverageTimeStamp, coverageByTestEnabled, tracingEnabled,
                                 trackTestFolders, acceptedCovRunner, this);
  }

  @NotNull
  private static String getPackageName(final PsiFile sourceFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return ((PsiClassOwner)sourceFile).getPackageName();
      }
    });
  }

  protected static void generateJavaReport(@NotNull final Project project,
                                           final boolean trackTestFolders,
                                           @NotNull final ProjectData projectData,
                                           final String outputDir,
                                           final boolean openInBrowser) {

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating coverage report ...") {
      final Exception[] myExceptions = new Exception[1];

      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          final HTMLReportBuilder builder = ReportBuilderFactory.createHTMLReportBuilder();
          builder.setReportDir(new File(outputDir));
          final SourceCodeProvider sourceCodeProvider = new SourceCodeProvider() {
            public String getSourceCode(@NotNull final String classname) throws IOException {
              return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
                public String compute() {
                  final PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), classname);
                  return psiClass != null ? psiClass.getContainingFile().getText() : "";
                }
              });
            }
          };
          builder.generateReport(new IDEACoverageData(projectData, sourceCodeProvider) {
            @NotNull
            @Override
            public Collection<ClassInfo> getClasses() {
              final Collection<ClassInfo> classes = super.getClasses();
              if (!trackTestFolders) {
                final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
                final GlobalSearchScope productionScope = GlobalSearchScope.projectProductionScope(project);
                for (Iterator<ClassInfo> iterator = classes.iterator(); iterator.hasNext();) {
                  final ClassInfo aClass = iterator.next();
                  final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
                    public PsiClass compute() {
                      return psiFacade.findClass(aClass.getFQName(), productionScope);
                    }
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
        if (openInBrowser) BrowserUtil.launchBrowser(VfsUtil.pathToUrl(outputDir + "/index.html"));
      }
    });
  }


  @Override
  public final void generateReport(@NotNull final Project project,
                                   @NotNull final DataContext dataContext,
                                   @NotNull final CoverageSuitesBundle currentSuite) {
    try {
      final File tempFile = FileUtil.createTempFile("temp", "");
      tempFile.deleteOnExit();
      final ProjectData projectData = currentSuite.getCoverageData();
      new SaveHook(tempFile, true, new IdeaClassFinder(project, currentSuite)).save(projectData);
      final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
      generateJavaReport(project,
                         currentSuite.isTrackTestFolders(),
                         projectData,
                         settings.OUTPUT_DIRECTORY,
                         settings.OPEN_IN_BROWSER);
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  @Override
  public String getPresentableText() {
    return "Java Coverage";
  }
}
