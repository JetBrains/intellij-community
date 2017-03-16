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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.StandardDiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

public class CompareRevisionsFromHistoryAction extends AnAction implements DumbAware {
  private static final String COMPARE_TEXT = "Compare";
  private static final String COMPARE_DESCRIPTION = "Compare selected versions";
  private static final String DIFF_TEXT = "Show Diff";
  private static final String DIFF_DESCRIPTION = "Show diff with previous version";
  @NotNull private final DiffFromHistoryHandler myDiffHandler = new StandardDiffFromHistoryHandler();

  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (project == null || ui == null || filePath == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    List<VcsFullCommitDetails> details = ui.getVcsLog().getSelectedDetails();

    if (e.getInputEvent() instanceof KeyEvent) {
      e.getPresentation().setEnabled(true);
    }
    else {
      if (details.size() == 2) {
        VcsFullCommitDetails detail0 = details.get(0);
        VcsFullCommitDetails detail1 = details.get(1);
        if (detail0 != null && !(detail0 instanceof LoadingDetails) &&
            detail1 != null && !(detail1 instanceof LoadingDetails)) {
          VcsFileRevision newestRevision = ui.createRevision(detail0);
          VcsFileRevision olderRevision = ui.createRevision(detail1);
          e.getPresentation().setEnabled(newestRevision != null && olderRevision != null && !filePath.isDirectory());
        }
        else {
          e.getPresentation().setEnabled(!filePath.isDirectory());
        }
      }
      else {
        e.getPresentation().setEnabled(details.size() == 1);
      }
    }

    if (details.size() == 2) {
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

    List<Integer> commitIds = ContainerUtil.map(commits, c -> ui.getLogData().getCommitIndex(c.getHash(), c.getRoot()));
    ui.getLogData().getCommitDetailsGetter().loadCommitsData(commitIds, details -> {
      if (details.size() == 2) {
        VcsFileRevision newestRevision = ui.createRevision(details.get(0));
        VcsFileRevision olderRevision = ui.createRevision(details.get(1));
        if (olderRevision != null && newestRevision != null) {
          myDiffHandler.showDiffForTwo(project, filePath, olderRevision, newestRevision);
        }
      }
      else if (details.size() == 1) {
        VcsFullCommitDetails detail = ObjectUtils.notNull(ContainerUtil.getFirstItem(details));
        List<Change> changes = ui.collectRelevantChanges(detail);
        if (filePath.isDirectory()) {
          VcsDiffUtil.showChangesDialog(project, "Changes in " + detail.getId().toShortString() + " for " + filePath.getName(),
                                        ContainerUtil.newArrayList(changes));
        }
        else {
          ShowDiffAction.showDiffForChange(project, changes, 0, new ShowDiffContext());
        }
      }
    }, null);
  }
}
