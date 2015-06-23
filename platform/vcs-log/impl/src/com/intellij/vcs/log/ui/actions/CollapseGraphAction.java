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

public class CollapseGraphAction extends GraphAction {
  public CollapseGraphAction() {
    super("Collapse linear branches", "Collapse linear branches", VcsLogIcons.CollapseBranches);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUi ui = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    ((VcsLogUiImpl)ui).collapseAll();
  }

  @Override
  protected void update(@NotNull VcsLogUi ui, @NotNull AnActionEvent e) {
    if (!ui.getFilterUi().getFilters().getDetailsFilters().isEmpty()) {
      e.getPresentation().setEnabled(false);
    }
    if (ui.getBekType() == PermanentGraph.SortType.LinearBek) {
      e.getPresentation().setIcon(VcsLogIcons.CollapseMerges);
      e.getPresentation().setText("Collapse all merges");
      e.getPresentation().setDescription("Collapse all merges");
    }
    else {
      e.getPresentation().setIcon(VcsLogIcons.CollapseBranches);
      e.getPresentation().setText("Collapse all linear branches");
      e.getPresentation().setDescription("Collapse all linear branches");
    }
  }
}
