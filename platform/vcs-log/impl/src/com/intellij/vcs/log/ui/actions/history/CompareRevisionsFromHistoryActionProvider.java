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
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

public class CompareRevisionsFromHistoryActionProvider implements AnActionExtensionProvider {
  private static final String COMPARE_TEXT = "Compare";
  private static final String COMPARE_DESCRIPTION = "Compare selected versions";
  private static final String DIFF_TEXT = "Show Diff";
  private static final String DIFF_DESCRIPTION = "Show diff with previous version";

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null;
  }

  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (project == null || ui == null || filePath == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    List<CommitId> commits = ui.getVcsLog().getSelectedCommits();
    if (e.getInputEvent() instanceof KeyEvent) {
      e.getPresentation().setEnabled(true);
    }
    else {
      if (commits.size() == 2) {
        e.getPresentation().setEnabled(e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER) != null);
      }
      else {
        e.getPresentation().setEnabled(commits.size() == 1);
      }
    }

    if (commits.size() == 2) {
      e.getPresentation().setText(COMPARE_TEXT);
      e.getPresentation().setDescription(COMPARE_DESCRIPTION);
    }
    else {
      e.getPresentation().setText(DIFF_TEXT);
      e.getPresentation().setDescription(DIFF_DESCRIPTION);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    FileHistoryUi ui = e.getRequiredData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    FilePath filePath = e.getRequiredData(VcsDataKeys.FILE_PATH);

    if (e.getInputEvent() instanceof MouseEvent && ui.getTable().isResizingColumns()) {
      // disable action during columns resize
      return;
    }

    VcsLogUtil.triggerUsage(e);

    List<CommitId> commits = ui.getVcsLog().getSelectedCommits();
    if (commits.size() != 1 && commits.size() != 2) return;

    VcsLogDiffHandler handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER);
    // this check is needed here since we may come on key event without performing proper checks
    if (commits.size() == 2 && handler == null) return;

    List<Integer> commitIds = ContainerUtil.map(commits, c -> ui.getLogData().getCommitIndex(c.getHash(), c.getRoot()));
    ui.getLogData().getCommitDetailsGetter().loadCommitsData(commitIds, details -> {
      if (details.size() == 2) {
        // we only need details here to get file names for each revision
        // in order to fix this FileNamesData should be refactored
        // so that it could return a single file path for each revision
        VcsFullCommitDetails newestDetail = details.get(0);
        VcsFullCommitDetails olderDetail = details.get(1);
        notNull(handler).showDiff(olderDetail.getRoot(), ui.getPath(olderDetail), olderDetail.getId(),
                                  ui.getPath(newestDetail), newestDetail.getId());
      }
      else if (details.size() == 1) {
        VcsFullCommitDetails detail = notNull(ContainerUtil.getFirstItem(details));
        List<Change> changes = ui.collectRelevantChanges(detail);
        if (filePath.isDirectory()) {
          VcsDiffUtil.showChangesDialog(project, "Changes in " + detail.getId().toShortString() + " for " + filePath.getName(),
                                        ContainerUtil.newArrayList(changes));
        }
        else {
          ShowDiffAction.showDiffForChange(project, changes, 0, new ShowDiffContext());
        }
      }
    }, t -> VcsBalloonProblemNotifier.showOverChangesView(project, "Could not load selected commits: " + t.getMessage(),
                                                          MessageType.ERROR), null);
  }
}
