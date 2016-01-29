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
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract class CollapseOrExpandGraphAction extends GraphAction {
  private static final String LINEAR_BRANCHES = "Linear Branches";
  private static final String LINEAR_BRANCHES_DESCRIPTION = "linear branches";
  private static final String MERGES = "Merges";
  private static final String MERGES_DESCRIPTION = "merges";

  private final boolean myExpand;

  public CollapseOrExpandGraphAction(boolean isExpand) {
    super(getPrefix(isExpand) + LINEAR_BRANCHES, getPrefix(isExpand) + LINEAR_BRANCHES_DESCRIPTION, getBranchesIcon(isExpand));
    myExpand = isExpand;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUi ui = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogUiImpl vcsLogUi = (VcsLogUiImpl)ui;
    if (myExpand) {
      vcsLogUi.expandAll();
    }
    else {
      vcsLogUi.collapseAll();
    }
  }

  @Override
  protected void update(@NotNull VcsLogUi ui, @NotNull AnActionEvent e) {
    if (!ui.getFilterUi().getFilters().getDetailsFilters().isEmpty()) {
      e.getPresentation().setEnabled(false);
    }
    if (ui.getBekType() == PermanentGraph.SortType.LinearBek) {
      e.getPresentation().setIcon(getMergesIcon(myExpand));
      e.getPresentation().setText(getPrefix(myExpand) + MERGES);
      e.getPresentation().setDescription(getPrefix(myExpand) + MERGES_DESCRIPTION);
    }
    else {
      e.getPresentation().setIcon(getBranchesIcon(myExpand));
      e.getPresentation().setText(getPrefix(myExpand) + LINEAR_BRANCHES);
      e.getPresentation().setDescription(getPrefix(myExpand) + LINEAR_BRANCHES_DESCRIPTION);
    }
  }

  private static Icon getMergesIcon(boolean expand) {
    return expand ? VcsLogIcons.ExpandMerges : VcsLogIcons.CollapseMerges;
  }

  private static Icon getBranchesIcon(boolean expand) {
    return expand ? VcsLogIcons.ExpandBranches : VcsLogIcons.CollapseBranches;
  }

  @NotNull
  private static String getPrefix(boolean expand) {
    return expand ? "Expand " : "Collapse ";
  }

  public static class CollapseGraphAction extends CollapseOrExpandGraphAction {
    public CollapseGraphAction() {
      super(false);
    }
  }

  public static class ExpandGraphAction extends CollapseOrExpandGraphAction {
    public ExpandGraphAction() {
      super(true);
    }
  }
}
