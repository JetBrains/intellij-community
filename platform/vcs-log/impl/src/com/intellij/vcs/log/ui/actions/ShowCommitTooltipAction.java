// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollingUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.column.Commit;
import org.jetbrains.annotations.NotNull;

public final class ShowCommitTooltipAction extends DumbAwareAction {
  public ShowCommitTooltipAction() {
    super(VcsLogBundle.messagePointer("action.ShowCommitTooltipAction.text"),
          VcsLogBundle.messagePointer("action.ShowCommitTooltipAction.description"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogGraphTable table = e.getData(VcsLogInternalDataKeys.VCS_LOG_GRAPH_TABLE);
    if (project == null || table == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(table.getSelectedRowCount() == 1);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    VcsLogGraphTable table = e.getRequiredData(VcsLogInternalDataKeys.VCS_LOG_GRAPH_TABLE);
    int row = table.getSelectedRow();
    if (ScrollingUtil.isVisible(table, row)) {
      table.showTooltip(row, Commit.INSTANCE);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
