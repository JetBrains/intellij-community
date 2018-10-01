/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class ShowDiffAfterWithLocalFromHistoryActionProvider implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    if (project == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    List<CommitId> commits = ui.getVcsLog().getSelectedCommits();
    if (commits.size() != 1) {
      e.getPresentation().setEnabled(false);
      return;
    }

    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsLogDiffHandler handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER);

    e.getPresentation().setEnabled(filePath != null && filePath.getVirtualFile() != null && handler != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e);

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    FileHistoryUi ui = e.getRequiredData(VcsLogInternalDataKeys.FILE_HISTORY_UI);

    List<CommitId> commits = ui.getVcsLog().getSelectedCommits();
    if (commits.size() != 1) return;
    CommitId commit = notNull(getFirstItem(commits));

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    FilePath path = e.getRequiredData(VcsDataKeys.FILE_PATH);
    VcsLogDiffHandler handler = e.getRequiredData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER);

    handler.showDiffWithLocal(commit.getRoot(), ui.getPathInCommit(commit.getHash()), commit.getHash(), path);
  }
}
