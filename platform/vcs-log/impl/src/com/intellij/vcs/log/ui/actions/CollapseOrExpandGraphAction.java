// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

abstract class CollapseOrExpandGraphAction extends DumbAwareAction {
  private final Supplier<@NlsActions.ActionText String> myLinearBranchesAction;
  private final Supplier<@NlsActions.ActionDescription String> myLinearBranchesDescription;
  private final Supplier<@NlsActions.ActionText String> myMergesAction;
  private final Supplier<@NlsActions.ActionDescription String> myMergesDescription;

  protected CollapseOrExpandGraphAction(@NotNull Supplier<String> linearBranchesAction,
                                        @NotNull Supplier<String> linearBranchesDescription,
                                        @NotNull Supplier<String> mergesAction,
                                        @NotNull Supplier<String> mergesDescription) {
    super(linearBranchesAction, linearBranchesDescription);
    myLinearBranchesAction = linearBranchesAction;
    myLinearBranchesDescription = linearBranchesDescription;
    myMergesAction = mergesAction;
    myMergesDescription = mergesDescription;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    executeAction(e.getRequiredData(VcsLogInternalDataKeys.MAIN_UI));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    MainVcsLogUi ui = e.getData(VcsLogInternalDataKeys.MAIN_UI);
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);

    boolean visible = ui != null && ui.getDataPack().getVisibleGraph().getActionController().isActionSupported(getGraphAction());
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && !ui.getDataPack().isEmpty());
    if (visible) {
      if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE) &&
          properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE) == PermanentGraph.SortType.LinearBek) {
        e.getPresentation().setText(myMergesAction.get());
        e.getPresentation().setDescription(myMergesDescription.get());
      }
      else {
        e.getPresentation().setText(myLinearBranchesAction.get());
        e.getPresentation().setDescription(myLinearBranchesDescription.get());
      }
    }
  }

  protected abstract void executeAction(@NotNull MainVcsLogUi vcsLogUi);

  protected abstract @NotNull GraphAction getGraphAction();

  protected void performLongAction(@NotNull MainVcsLogUi logUi,
                                   @NotNull GraphAction graphAction,
                                   @NotNull @NlsContexts.ProgressTitle String title) {
    VisiblePack dataPack = logUi.getDataPack();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ActionController<Integer> actionController = dataPack.getVisibleGraph().getActionController();
      GraphAnswer<Integer> answer = actionController.performAction(graphAction);
      Runnable updater = answer.getGraphUpdater();
      ApplicationManager.getApplication().invokeLater(() -> {
        assert updater != null : "Action:" + title +
                                 "\nController: " + actionController +
                                 "\nAnswer:" + answer;
        updater.run();
        logUi.getTable().handleAnswer(answer);
      });
    }, title, false, null, logUi.getMainComponent());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
