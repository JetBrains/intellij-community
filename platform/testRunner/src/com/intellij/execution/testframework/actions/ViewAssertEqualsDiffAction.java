// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.testframework.actions;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ViewAssertEqualsDiffAction extends AnAction implements TestTreeViewAction, DumbAware, UpdateInBackground {
  @NonNls public static final String ACTION_ID = "openAssertEqualsDiff";

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    if (!e.getPresentation().isVisible()) {
      return;
    }
    if (!openDiff(e.getDataContext(), null)) {
      final Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      Messages.showInfoMessage(component, TestRunnerBundle.message("dialog.message.comparison.error.was.found"),
                               TestRunnerBundle.message("dialog.title.no.comparison.data.found"));
    }
  }

  public static boolean openDiff(DataContext context, @Nullable DiffHyperlink currentHyperlink) {
    final AbstractTestProxy testProxy = AbstractTestProxy.DATA_KEY.getData(context);
    final Project project = CommonDataKeys.PROJECT.getData(context);
    ListSelection<DiffHyperlink> hyperlinks = null;
    if (currentHyperlink != null) {
      hyperlinks = ListSelection.createSingleton(currentHyperlink);
    }
    else if (testProxy != null) {
      hyperlinks = showDiff(testProxy, TestTreeView.MODEL_DATA_KEY.getData(context));
    }
    if (hyperlinks == null) return false;

    DiffRequestChain chain = TestDiffRequestProcessor.createRequestChain(project, hyperlinks);
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
    return true;
  }

  @NotNull
  public static ListSelection<DiffHyperlink> showDiff(@NotNull AbstractTestProxy testProxy,
                                                      @Nullable TestFrameworkRunningModel model) {
    final List<DiffHyperlink> providers = collectAvailableProviders(model);

    DiffHyperlink diffViewerProvider = testProxy.getLeafDiffViewerProvider();
    int index = diffViewerProvider != null ? providers.indexOf(diffViewerProvider) : -1;

    return ListSelection.createAt(providers, index);
  }

  private static List<DiffHyperlink> collectAvailableProviders(TestFrameworkRunningModel model) {
    final List<DiffHyperlink> providers = new ArrayList<>();
    if (model != null) {
      final AbstractTestProxy root = model.getRoot();
      final List<? extends AbstractTestProxy> allTests = root.getAllTests();
      for (AbstractTestProxy test : allTests) {
        providers.addAll(test.getDiffViewerProviders());
      }
    }
    return providers;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    DataContext context = e.getDataContext();
    AbstractTestProxy test = AbstractTestProxy.DATA_KEY.getData(context);
    TestFrameworkRunningModel model = TestTreeView.MODEL_DATA_KEY.getData(context);
    boolean visible = test != null && model != null && test.getLeafDiffViewerProvider() != null;

    presentation.setEnabled(test != null);
    presentation.setVisible(visible);
  }
}