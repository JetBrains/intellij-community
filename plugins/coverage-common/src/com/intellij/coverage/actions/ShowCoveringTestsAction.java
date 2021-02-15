// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.coverage.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.hint.PsiImplementationViewElement;
import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ShowCoveringTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(ShowCoveringTestsAction.class);

  private final String myClassFQName;
  private final LineData myLineData;

  public ShowCoveringTestsAction(final String classFQName, LineData lineData) {
    super(CoverageBundle.message("action.text.show.tests.covering.line"),
          CoverageBundle.message("action.description.show.tests.covering.line"), PlatformIcons.TEST_SOURCE_FOLDER);
    myClassFQName = classFQName;
    myLineData = lineData;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();
    LOG.assertTrue(project != null);
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    LOG.assertTrue(editor != null);

    final CoverageSuitesBundle currentSuite = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
    LOG.assertTrue(currentSuite != null);
    final CoverageEngine coverageEngine = currentSuite.getCoverageEngine();

    final Set<String> tests = new HashSet<>();
    if (ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> tests.addAll(coverageEngine.getTestsForLine(project, myClassFQName, myLineData.getLineNumber())),
                                                                          CoverageBundle.message("extract.information.about.tests"), false, project)) { //todo cache them? show nothing found message
      final String[] testNames = ArrayUtilRt.toStringArray(tests);
      Arrays.sort(testNames);
      if (testNames.length == 0) {
        HintManager.getInstance().showErrorHint(editor, CoverageBundle.message("hint.text.failed.to.load.covered.tests"));
        return;
      }
      final List<PsiElement> elements = coverageEngine.findTestsByNames(testNames, project);
      final ImplementationViewComponent component;
      final String title = CoverageBundle.message("popup.title.tests.covering.line", myClassFQName, myLineData.getLineNumber());
      final ComponentPopupBuilder popupBuilder;
      if (!elements.isEmpty()) {
        Consumer<ImplementationViewComponent> processor = viewComponent -> viewComponent.showInUsageView();
        component = new ImplementationViewComponent(ContainerUtil.map(elements, PsiImplementationViewElement::new), 0, processor);
        popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPreferredFocusableComponent())
          .setDimensionServiceKey(project, "ShowTestsPopup", false);
      } else {
        component = null;
        @NonNls String testsPresentation = StringUtil.join(testNames, "<br/>").replace("_", ".");
        final JPanel panel = new PanelWithText(CoverageBundle.message("following.test.could.not.be.found.1", testNames.length, testsPresentation));
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

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (myLineData != null && myLineData.getStatus() != LineCoverage.NONE) {
      final Project project = e.getProject();
      if (project != null) {
        CoverageSuitesBundle currentSuitesBundle = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
        presentation.setEnabled(currentSuitesBundle != null &&
                                currentSuitesBundle.isCoverageByTestEnabled() &&
                                currentSuitesBundle.getCoverageEngine().wasTestDataCollected(project));
      }
    }
  }
}
