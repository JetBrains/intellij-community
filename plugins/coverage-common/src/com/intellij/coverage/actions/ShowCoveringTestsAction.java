/*
 * User: anna
 * Date: 29-May-2008
 */
package com.intellij.coverage.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ShowCoveringTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + ShowCoveringTestsAction.class.getName());

  private final String myClassFQName;
  private final LineData myLineData;

  public ShowCoveringTestsAction(final String classFQName, LineData lineData) {
    super("Show tests covering line", "Show tests covering line", PlatformIcons.TEST_SOURCE_FOLDER);
    myClassFQName = classFQName;
    myLineData = lineData;
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(context);
    LOG.assertTrue(project != null);
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    LOG.assertTrue(editor != null);

    final CoverageSuitesBundle currentSuite = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
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

    if (ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, "Extract information about tests", false, project)) { //todo cache them? show nothing found message
      final String[] testNames = ArrayUtil.toStringArray(tests);
      Arrays.sort(testNames);
      if (testNames.length == 0) {
        HintManager.getInstance().showErrorHint(editor, "Failed to load covered tests");
        return;
      }
      final List<PsiElement> elements = currentSuite.getCoverageEngine().findTestsByNames(testNames, project);
      final ImplementationViewComponent component;
      final String title = "Tests covering line " + myClassFQName + ":" + myLineData.getLineNumber();
      final ComponentPopupBuilder popupBuilder;
      if (!elements.isEmpty()) {
        component = new ImplementationViewComponent(PsiUtilCore.toPsiElementArray(elements), 0);
        popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPreferredFocusableComponent())
          .setDimensionServiceKey(project, "ShowTestsPopup", false)
          .setCouldPin(new Processor<JBPopup>() {
            @Override
            public boolean process(JBPopup popup) {
              component.showInUsageView();
              popup.cancel();
              return false;
            }
          });
      } else {
        component = null;
        final JPanel panel = new PanelWithText("Following test" + (testNames.length > 1 ? "s" : "") + " could not be found: " + StringUtil.join(testNames, ",").replace("_", "."));
        popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null);
      }
      final JBPopup popup = popupBuilder.setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
        .setProject(project)
        .setResizable(true)
        .setMovable(true)
        .setTitle(title)
        .createPopup();
      popup.showInBestPositionFor(editor);

      if (component != null) {
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
      final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
      if (project != null) {
        final File[] files = getTraceFiles(project);
        if (files != null && files.length > 0) {
          presentation.setEnabled(CoverageDataManager.getInstance(project).getCurrentSuitesBundle().isCoverageByTestEnabled());
        }
      }
    }
  }

  @Nullable
  private static File[] getTraceFiles(Project project) {
    final CoverageSuitesBundle currentSuite = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
    if (currentSuite == null) return null;
    final List<File> files = new ArrayList<File>();
    for (CoverageSuite coverageSuite : currentSuite.getSuites()) {

      final String filePath = coverageSuite.getCoverageDataFileName();
      final String dirName = FileUtil.getNameWithoutExtension(new File(filePath).getName());

      final File parentDir = new File(filePath).getParentFile();
      final File tracesDir = new File(parentDir, dirName);
      final File[] suiteFiles = tracesDir.listFiles();
      if (suiteFiles != null) {
        Collections.addAll(files, suiteFiles);
      }
    }

    return files.isEmpty() ? null : files.toArray(new File[files.size()]);
  }
}
