/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollingUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;

public class ShowCommitTooltipAction extends DumbAwareAction {
  public ShowCommitTooltipAction() {
    super("Show Commit Tooltip", "Show tooltip for currently selected commit in the Log", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (project == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(ui instanceof AbstractVcsLogUi &&
                                               ((AbstractVcsLogUi)ui).getTable().getSelectedRowCount() == 1);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUtil.triggerUsage(e);
    
    VcsLogGraphTable table = ((AbstractVcsLogUi)e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI)).getTable();
    int row = table.getSelectedRow();
    if (ScrollingUtil.isVisible(table, row)) {
      table.showTooltip(row);
    }
  }
}
