// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import org.jetbrains.annotations.NotNull;

public class FocusTextFilterAction extends DumbAwareAction {
  public FocusTextFilterAction() {
    super(VcsLogBundle.messagePointer("action.FocusTextFilterAction.text"),
          VcsLogBundle.messagePointer("action.FocusTextFilterAction.description"), null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(project != null && ui instanceof MainVcsLogUi);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    MainVcsLogUi logUi = (MainVcsLogUi)e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    Project project = e.getProject();

    if (IdeFocusManager.getInstance(project).getFocusedDescendantFor(logUi.getToolbar()) != null) {
      IdeFocusManager.getInstance(project).requestFocus(logUi.getTable(), true);
    }
    else {
      IdeFocusManager.getInstance(project).requestFocus(logUi.getFilterUi().getTextFilterComponent().getTextField(), true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
