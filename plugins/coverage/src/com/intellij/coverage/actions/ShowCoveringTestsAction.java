/*
 * User: anna
 * Date: 29-May-2008
 */
package com.intellij.coverage.actions;

import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.CoverageSuiteImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.containers.HashSet;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ShowCoveringTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + ShowCoveringTestsAction.class.getName());

  private final String myClassFQName;
  private final LineData myLineData;

  public ShowCoveringTestsAction(final String classFQName, LineData lineData) {
    super("Show tests covering line", "Show tests covering line", Icons.TEST_SOURCE_FOLDER);
    myClassFQName = classFQName;
    myLineData = lineData;
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    LOG.assertTrue(project != null);

    final CoverageSuite currentSuite = CoverageDataManager.getInstance(project).getCurrentSuite();
    LOG.assertTrue(currentSuite != null);

    final File[] traceFiles = getTraceFiles(project);

    final Set<String> tests = new HashSet<String>();
    Runnable runnable = new Runnable() {
      public void run() {
        for (File traceFile : traceFiles) {
          DataInputStream in = null;
          try {
            in = new DataInputStream(new FileInputStream(traceFile));
            extractTests(traceFile, in, tests);
          }
          catch (Exception ex) {
            LOG.error(traceFile.getName(), ex);
          }
          finally {
            try {
              in.close();
            }
            catch (IOException ex) {
              LOG.error(ex);
            }
          }
        }
      }
    };

    if (ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, "Exctract information about tests", false,
                                                                          project)) { //todo cache them? show nothing found message
      final String[] testNames = ArrayUtil.toStringArray(tests);
      Arrays.sort(testNames);
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
      final ImplementationViewComponent component = new ImplementationViewComponent(elements.toArray(new PsiElement[elements.size()]), 0);
      if (component.hasElementsToShow()) {
        final String title = "Tests covering line " + myClassFQName + ":" + myLineData.getLineNumber();
        final JBPopup popup =
            JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPrefferedFocusableComponent())
                .setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
                .setProject(project)
                .setDimensionServiceKey(project, "ShowTestsPopup", false)
                .setResizable(true)
                .setMovable(true)
                .setTitle(title)
                .createPopup();
        popup.showInBestPositionFor(DataManager.getInstance().getDataContext());
        component.setHint(popup, title);
      }
    }
  }

  private void extractTests(final File traceFile, final DataInputStream in, final Set<String> tests) throws IOException {
    long traceSize = in.readInt();
    for (int i = 0; i < traceSize; i++) {
      final String className = in.readUTF();
      final int linesSize = in.readInt();
      for(int l = 0; l < linesSize; l++) {
        final int line = in.readInt();
        if (Comparing.strEqual(className, myClassFQName)) {
          if (myLineData.getLineNumber() == line) {
            tests.add(FileUtil.getNameWithoutExtension(traceFile));
            return;
          }
        }
      }
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (myLineData != null && myLineData.getStatus() != LineCoverage.NONE) {
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      if (project != null) {
        final File[] files = getTraceFiles(project);
        if (files != null && files.length > 0) {
          presentation.setEnabled(((CoverageSuiteImpl)CoverageDataManager.getInstance(project).getCurrentSuite()).isCoverageByTestEnabled());
        }
      }
    }
  }

  private static File[] getTraceFiles(Project project) {
    final CoverageSuite currentSuite = CoverageDataManager.getInstance(project).getCurrentSuite();
    LOG.assertTrue(currentSuite != null); //highlight won't be available otherwise

    final String filePath = currentSuite.getCoverageDataFileName();
    final String dirName = FileUtil.getNameWithoutExtension(new File(filePath).getName());

    final File parentDir = new File(filePath).getParentFile();
    final File tracesDir = new File(parentDir, dirName);

    return tracesDir.listFiles();
  }
}