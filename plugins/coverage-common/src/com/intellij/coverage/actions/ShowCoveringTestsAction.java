// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.coverage.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.hint.PsiImplementationViewElement;
import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageLogger;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@ApiStatus.Internal
public class ShowCoveringTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(ShowCoveringTestsAction.class);

  private final CoverageSuitesBundle myBundle;
  private final String myClassFQName;
  private final LineData myLineData;
  private final boolean myTestsAvailable;

  public ShowCoveringTestsAction(@Nullable Project project, CoverageSuitesBundle bundle, final String classFQName, LineData lineData) {
    super(CoverageBundle.message("action.text.show.tests.covering.line"),
          CoverageBundle.message("action.description.show.tests.covering.line"), PlatformIcons.TEST_SOURCE_FOLDER);
    myBundle = bundle;
    myClassFQName = classFQName;
    myLineData = lineData;
    myTestsAvailable = isEnabled(project, bundle, lineData);
  }

  @ApiStatus.Internal
  public static boolean isEnabled(Project project, CoverageSuitesBundle bundle, LineData lineData) {
    if (lineData != null && lineData.getStatus() != LineCoverage.NONE && project != null) {
      return bundle != null && bundle.isCoverageByTestEnabled() && bundle.getCoverageEngine().wasTestDataCollected(project, bundle);
    }
    return false;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final Project project = e.getProject();
    LOG.assertTrue(project != null);
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    LOG.assertTrue(editor != null);

    LOG.assertTrue(myBundle != null);
    final CoverageEngine coverageEngine = myBundle.getCoverageEngine();

    final Set<String> tests = new HashSet<>();
    if (ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> tests.addAll(coverageEngine.getTestsForLine(project, myBundle, myClassFQName, myLineData.getLineNumber())),
                                                                          CoverageBundle.message("extract.information.about.tests"), false, project)) { //todo cache them? show nothing found message
      final String[] testNames = ArrayUtilRt.toStringArray(tests);
      Arrays.sort(testNames);
      CoverageLogger.logShowCoveringTests(project, testNames.length);
      if (testNames.length == 0) {
        HintManager.getInstance().showErrorHint(editor, CoverageBundle.message("hint.text.failed.to.load.covered.tests"));
        return;
      }
      ThrowableComputable<List<PsiImplementationViewElement>, RuntimeException> computeTestElements =
        () -> ContainerUtil.map(coverageEngine.findTestsByNames(testNames, project),
                                el -> ReadAction.compute(() -> new PsiImplementationViewElement(el)));
      final List<PsiImplementationViewElement> elements =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(computeTestElements,
                                                                          CoverageBundle.message("dialog.title.find.tests.by.names"), true,
                                                                          project);
      final ImplementationViewComponent component;
      final String title = CoverageBundle.message("popup.title.tests.covering.line", myClassFQName, myLineData.getLineNumber());
      final ComponentPopupBuilder popupBuilder;
      if (!elements.isEmpty()) {
        Consumer<ImplementationViewComponent> processor = viewComponent -> viewComponent.showInUsageView();
        component = new ImplementationViewComponent(elements, 0);
        component.setShowInFindWindowProcessor(processor);
        popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPreferredFocusableComponent())
          .setDimensionServiceKey(project, "ShowTestsPopup", false)
          .addListener(new JBPopupListener() {
            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
              component.cleanup();
            }
          });
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
  public void update(final @NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(myTestsAvailable);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
