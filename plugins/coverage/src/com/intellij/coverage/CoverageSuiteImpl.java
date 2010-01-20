package com.intellij.coverage;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.rt.coverage.util.SourceLineCounter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.EmptyVisitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class CoverageSuiteImpl extends BaseCoverageSuite {
  private static final Logger LOG = Logger.getInstance(CoverageSuiteImpl.class.getName());

  private String[] myFilters;
  private String mySuiteToMerge;

  @NonNls
  private static final String FILTER = "FILTER";
  @NonNls
  private static final String MERGE_SUITE = "MERGE_SUITE";
  @NonNls
  private static final String COVERAGE_RUNNER = "RUNNER";

  //read external only
  public CoverageSuiteImpl() {
    super();
  }

  public CoverageSuiteImpl(final String name,
                           final CoverageFileProvider coverageDataFileProvider,
                           final String[] filters,
                           final long lastCoverageTimeStamp,
                           final String suiteToMerge,
                           final boolean coverageByTestEnabled,
                           final boolean tracingEnabled,
                           final boolean trackTestFolders,
                           final AbstractCoverageRunner coverageRunner) {
    super(name, coverageDataFileProvider, lastCoverageTimeStamp, coverageByTestEnabled,
          tracingEnabled, trackTestFolders,
          coverageRunner != null ? coverageRunner : AbstractCoverageRunner.getInstance(IDEACoverageRunner.class));

    myFilters = filters;
    mySuiteToMerge = suiteToMerge;
  }

  @NotNull
  public String[] getFilteredPackageNames() {
    if (myFilters == null || myFilters.length == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<String>();
    for (String filter : myFilters) {
      if (filter.equals("*")) {
        result.add(""); //default package
      }
      else if (filter.endsWith(".*")) result.add(filter.substring(0, filter.length() - 2));
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public String[] getFilteredClassNames() {
    if (myFilters == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<String>();
    for (String filter : myFilters) {
      if (!filter.equals("*") && !filter.endsWith(".*")) result.add(filter);
    }
    return ArrayUtil.toStringArray(result);
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    // filters
    final List children = element.getChildren(FILTER);
    List<String> filters = new ArrayList<String>();
    //noinspection unchecked
    for (Element child : ((Iterable<Element>)children)) {
      filters.add(child.getValue());
    }
    myFilters = filters.isEmpty() ? null : ArrayUtil.toStringArray(filters);

    // suite to merge
    mySuiteToMerge = element.getAttributeValue(MERGE_SUITE);

    if (getRunner() == null) {
      setRunner(AbstractCoverageRunner.getInstance(IDEACoverageRunner.class)); //default
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (mySuiteToMerge != null) {
      element.setAttribute(MERGE_SUITE, mySuiteToMerge);
    }
    if (myFilters != null) {
      for (String filter : myFilters) {
        final Element filterElement = new Element(FILTER);
        filterElement.setText(filter);
        element.addContent(filterElement);
      }
    }
    final AbstractCoverageRunner coverageRunner = getRunner();
    element.setAttribute(COVERAGE_RUNNER, coverageRunner != null ? coverageRunner.getId() : "emma");
  }

  @Nullable
  public ProjectData getCoverageData(final CoverageDataManager coverageDataManager) {
    final ProjectData data = getCoverageData();
    if (data != null) return data;
    ProjectData map = loadProjectInfo();
    if (mySuiteToMerge != null) {
      CoverageSuiteImpl toMerge = null;
      final CoverageSuite[] suites = coverageDataManager.getSuites();
      for (CoverageSuite suite : suites) {
        if (Comparing.strEqual(suite.getPresentableName(), mySuiteToMerge)) {
          if (!Comparing.strEqual(((CoverageSuiteImpl)suite).getSuiteToMerge(), getPresentableName())) {
            toMerge = (CoverageSuiteImpl)suite;
          }
          break;
        }
      }
      if (toMerge != null) {
        final ProjectData projectInfo = toMerge.getCoverageData(coverageDataManager);
        if (map != null) {
          map.merge(projectInfo);
        } else {
          map = projectInfo;
        }
      }
    }
    setCoverageData(map);
    return map;
  }

  public String getTestMethodName(@NotNull final PsiElement element,
                                  @NotNull final AbstractTestProxy testProxy) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiClass psiClass = method.getContainingClass();
      assert psiClass != null;

      return psiClass.getQualifiedName() + "." + method.getName();
    }

    return null;
  }

  @NotNull
  public List<PsiElement> findTestsByNames(@NotNull String[] testNames, @NotNull Project project) {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    for (String testName : testNames) {
      final PsiClass psiClass =
          facade.findClass(testName.substring(0, testName.lastIndexOf(".")), GlobalSearchScope.projectScope(project));
      if (psiClass != null) {
        final PsiMethod[] testsByName = psiClass.findMethodsByName(testName.substring(testName.lastIndexOf(".") + 1), true);
        if (testsByName.length == 1) {
          elements.add(testsByName[0]);
        }
      }
    }
    return elements;
  }

  public boolean isNotCoveredFileIncluded(@NotNull final String qualifiedName,
                                          @NotNull final VirtualFile outputFile,
                                          @NotNull final PsiFile sourceFile) {
    return isClassFiltered(qualifiedName) || isPackageFiltered(getPackageName(sourceFile));
  }

  @Nullable
  public List<Integer> collectNotCoveredFileLines(@NotNull final VirtualFile classFile, @NotNull final PsiFile srcFile) {
    final List<Integer> uncoveredLines = new ArrayList<Integer>();

    final byte[] content;
    try {
      content = classFile.contentsToByteArray();
    }
    catch (IOException e) {
      return null;
    }

    final ClassReader reader = new ClassReader(content, 0, content.length);
    final boolean excludeLines = getRunner() instanceof IDEACoverageRunner && isTracingEnabled();
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
      if (condition != null) {
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
          }
          int i = 0;
          for (int key : switchData.getKeys()) {
            buf.append(indent).append(indent).append("case ").append(key).append(": ").append(switchData.getHits()[i++]).append("\n");
          }
        }
      }
    }
    catch (Exception e) {
      LOG.info(e);
      return "Hits: " + lineData.getHits();
    }
    return buf.toString();
  }

  public String getQualifiedName(@NotNull final VirtualFile outputFile,
                                 @NotNull final PsiFile sourceFile) {
    final String packageFQName = getPackageName(sourceFile);
    return StringUtil.getQualifiedName(packageFQName, outputFile.getNameWithoutExtension());
  }

  @NotNull
  public Set<VirtualFile> getCorrespondingOutputFiles(@NotNull final PsiFile srcFile, Module module) {
    final VirtualFile outputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    final VirtualFile testOutputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests();

    final String packageFQName = getPackageName(srcFile);
    final String packageVmName = packageFQName.replace('.', '/');

    final List<VirtualFile> children = new ArrayList<VirtualFile>();
    final VirtualFile vDir = packageVmName.length() > 0 ? outputpath.findFileByRelativePath(packageVmName) : outputpath;
    if (vDir != null) {
      Collections.addAll(children, vDir.getChildren());
    }

    if (isTrackTestFolders()) {
      final VirtualFile testDir = packageVmName.length() > 0 ? testOutputpath.findFileByRelativePath(packageVmName) : testOutputpath;
      if (testDir != null) {
        Collections.addAll(children, testDir.getChildren());
      }
    }

    final Set<VirtualFile> classFiles = new HashSet<VirtualFile>();
    for (PsiClass psiClass : ((PsiClassOwner)srcFile).getClasses()) {
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

  public boolean needToRecompileAndRerunAction(@NotNull final Project project, @NotNull Runnable chooseSuiteAction) {
    if (Messages.showOkCancelDialog(
      "Project class files are out of date. Would you like to recompile? The refusal to do it will result in incomplete coverage information",
      "Project is out of date", Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE) {
      final CompilerManager compilerManager = CompilerManager.getInstance(project);
      compilerManager.make(compilerManager.createProjectCompileScope(project), new CompileStatusNotification() {
        public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
          if (aborted || errors != 0) return;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              CoverageDataManager.getInstance(project).chooseSuite(CoverageSuiteImpl.this);
            }
          });
        }
      });
    }
    return true;
  }

  @Nullable
  public String getSuiteToMerge() {
    return mySuiteToMerge;
  }

  public boolean isClassFiltered(final String classFQName) {
    for (final String className : getFilteredClassNames()) {
      if (className.equals(classFQName) || classFQName.startsWith(className) && classFQName.charAt(className.length()) == '$') {
        return true;
      }
    }
    return false;
  }

  public boolean isPackageFiltered(final String packageFQName) {
    final String[] filteredPackageNames = getFilteredPackageNames();
    for (final String packName : filteredPackageNames) {
      if (packName.equals(packageFQName) || packageFQName.startsWith(packName) && packageFQName.charAt(packName.length()) == '.') {
        return true;
      }
    }
    return filteredPackageNames.length == 0;
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

  @NotNull
  private String getPackageName(final PsiFile sourceFile) {
    return ((PsiClassOwner)sourceFile).getPackageName();
  }

}
