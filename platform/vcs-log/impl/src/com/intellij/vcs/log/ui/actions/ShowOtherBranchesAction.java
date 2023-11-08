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

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.data.index.VcsLogIndexUtils;
import com.intellij.vcs.log.history.FileHistoryUiProperties;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

import static com.intellij.vcs.log.data.index.VcsLogIndexUtils.isIndexingEnabled;
import static com.intellij.vcs.log.data.index.VcsLogIndexUtils.isIndexingPausedFor;

public class ShowOtherBranchesAction extends BooleanPropertyToggleAction {

  public ShowOtherBranchesAction() {
    super(VcsLogBundle.messagePointer("vcs.log.action.show.all.branches"),
          VcsLogBundle.messagePointer("vcs.log.action.description.show.all.branches"), AllIcons.Vcs.Branch);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return FileHistoryUiProperties.SHOW_ALL_BRANCHES;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    if (!e.getPresentation().isEnabled()) return;

    if (!Registry.is("vcs.history.use.index")) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, null);
      return;
    }

    Project project = e.getProject();
    VcsLogManager logManager = e.getData(VcsLogInternalDataKeys.LOG_MANAGER);
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    Set<VirtualFile> roots = e.getData(VcsLogInternalDataKeys.VCS_LOG_VISIBLE_ROOTS);
    if (project != null && logManager != null && logUi != null && roots != null && roots.size() == 1) {
      VcsLogIndex index = logManager.getDataManager().getIndex();
      VirtualFile root = ContainerUtil.getOnlyItem(roots);
      if (!index.isIndexed(root)) {
        e.getPresentation().setEnabled(false);
      }
      HelpTooltip helpTooltip = getHelpTooltip(e.getPresentation(), logManager.getDataManager(), root);
      e.getPresentation().putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, helpTooltip);
    }
    else {
      e.getPresentation().putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, null);
    }
  }

  @SuppressWarnings("DialogTitleCapitalization")
  private @Nullable HelpTooltip getHelpTooltip(@NotNull Presentation presentation,
                                               @NotNull VcsLogData vcsLogData,
                                               @NotNull VirtualFile root) {
    if (presentation.isEnabled()) return null;
    if (isIndexingEnabled(vcsLogData.getProject()) && !isIndexingPausedFor(root)) return null;

    HelpTooltip previousTooltip = presentation.getClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP);
    if (previousTooltip != null) return previousTooltip;

    HelpTooltip tooltip = new HelpTooltip();
    tooltip.setTitle(presentation.getText());
    tooltip.setShortcut(KeymapUtil.getFirstKeyboardShortcutText(this));
    tooltip.setDescription(VcsLogBundle.message("action.help.tooltip.show.all.branches"));
    String vcsDisplayName = VcsLogUtil.getVcsDisplayName(vcsLogData.getProject(), Collections.singleton(vcsLogData.getLogProvider(root)));
    tooltip.setLink(VcsLogBundle.message("action.help.tooltip.link.show.all.branches", vcsDisplayName, root.getPresentableName()), () -> {
      VcsLogIndexUtils.enableAndResumeIndexing(vcsLogData.getProject(), vcsLogData, Collections.singleton(root));
    });
    return tooltip;
  }
}
