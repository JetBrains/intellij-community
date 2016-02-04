/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract class CollapseOrExpandGraphAction extends DumbAwareAction {
  private static final String LINEAR_BRANCHES = "Linear Branches";
  private static final String LINEAR_BRANCHES_DESCRIPTION = "linear branches";
  private static final String MERGES = "Merges";
  private static final String MERGES_DESCRIPTION = "merges";

  public CollapseOrExpandGraphAction() {
    super("Collapse or Expand " + LINEAR_BRANCHES, "Collapse or Expand " + LINEAR_BRANCHES_DESCRIPTION, null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUi ui = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    executeAction((VcsLogUiImpl)ui);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);

    if (ui != null && ui.areGraphActionsEnabled()) {
      e.getPresentation().setEnabled(true);
      if (!ui.getFilterUi().getFilters().getDetailsFilters().isEmpty()) {
        e.getPresentation().setEnabled(false);
      }

      if (ui.getBekType() == PermanentGraph.SortType.LinearBek) {
        e.getPresentation().setText(getPrefix() + MERGES);
        e.getPresentation().setDescription(getPrefix() + MERGES_DESCRIPTION);
      }
      else {
        e.getPresentation().setText(getPrefix() + LINEAR_BRANCHES);
        e.getPresentation().setDescription(getPrefix() + LINEAR_BRANCHES_DESCRIPTION);
      }
    }
    else {
      e.getPresentation().setEnabled(false);
    }

    e.getPresentation().setText(getPrefix() + LINEAR_BRANCHES);
    e.getPresentation().setDescription(getPrefix() + LINEAR_BRANCHES_DESCRIPTION);
    if (isIconHidden(e)) {
      e.getPresentation().setIcon(null);
    }
    else {
      e.getPresentation().setIcon(ui != null && ui.getBekType() == PermanentGraph.SortType.LinearBek ? getMergesIcon() : getBranchesIcon());
    }
  }

  protected abstract void executeAction(@NotNull VcsLogUiImpl vcsLogUi);

  @NotNull
  protected abstract Icon getMergesIcon();

  @NotNull
  protected abstract Icon getBranchesIcon();

  @NotNull
  protected abstract String getPrefix();

  private static boolean isIconHidden(@NotNull AnActionEvent e) {
    return e.getPlace().equals(ToolWindowContentUi.POPUP_PLACE);
  }

}
