// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollingUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.column.Commit;
import org.jetbrains.annotations.NotNull;

public class ShowCommitTooltipAction extends DumbAwareAction {
  public ShowCommitTooltipAction() {
    super(VcsLogBundle.messagePointer("action.ShowCommitTooltipAction.text"),
          VcsLogBundle.messagePointer("action.ShowCommitTooltipAction.description"), null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogUiEx ui = e.getData(VcsLogInternalDataKeys.LOG_UI_EX);
    if (project == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(ui.getTable().getSelectedRowCount() == 1);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    VcsLogGraphTable table = e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_EX).getTable();
    int row = table.getSelectedRow();
    if (ScrollingUtil.isVisible(table, row)) {
      table.showTooltip(row, Commit.INSTANCE);
    }
  }
}
