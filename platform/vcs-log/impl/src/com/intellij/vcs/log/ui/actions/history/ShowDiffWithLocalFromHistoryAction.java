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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.ui.history.FileHistoryUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ShowDiffWithLocalFromHistoryAction extends FileHistorySingleCommitAction {

  @Override
  protected boolean isEnabled(@NotNull FileHistoryUi ui, @Nullable VcsFullCommitDetails detail, @NotNull AnActionEvent e) {
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (filePath == null || filePath.getVirtualFile() == null) {
      return false;
    }

    List<CommitId> commits = ui.getVcsLog().getSelectedCommits();
    if (filePath.isDirectory()) {
      VcsLogDiffHandler handler = ui.getLogData().getLogProvider(commits.get(0).getRoot()).getDiffHandler();
      if (handler == null) return false;
    }

    return true;
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryUi ui,
                               @NotNull VcsFullCommitDetails detail,
                               @NotNull AnActionEvent e) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    FilePath path = e.getRequiredData(VcsDataKeys.FILE_PATH);

    VirtualFile root = detail.getRoot();
    VcsLogDiffHandler handler = ui.getLogData().getLogProvider(root).getDiffHandler();
    if (handler != null) {
      handler.showDiffWithLocal(root, ui.getPath(detail), detail.getId(), path);
    }
  }
}
