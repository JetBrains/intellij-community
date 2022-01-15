// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog;
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel;
import com.intellij.vcs.CommittedChangeListForRevision;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.DataGetter;
import com.intellij.vcs.log.history.FileHistoryUi;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.vcs.log.util.VcsLogUtil.createCommittedChangeList;

public class ShowAllAffectedFromHistoryAction extends FileHistorySingleCommitAction<VcsFullCommitDetails> {

  @NotNull
  @Override
  protected List<VcsFullCommitDetails> getSelection(@NotNull FileHistoryUi ui) {
    return ui.getVcsLog().getSelectedDetails();
  }

  @NotNull
  @Override
  protected DataGetter<VcsFullCommitDetails> getDetailsGetter(@NotNull FileHistoryUi ui) {
    return ui.getLogData().getCommitDetailsGetter();
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryUi ui,
                               @NotNull VcsFullCommitDetails detail,
                               @NotNull AnActionEvent e) {
    FilePath file = ui.getPathInCommit(detail.getId());
    String title = VcsLogBundle.message("dialog.title.paths.affected.by.commit", detail.getId().toShortString());

    LoadingCommittedChangeListPanel panel = new LoadingCommittedChangeListPanel(project);
    panel.loadChangesInBackground(() -> {
      CommittedChangeListForRevision committedChangeList = createCommittedChangeList(detail);
      return new LoadingCommittedChangeListPanel.ChangelistData(committedChangeList, file);
    });

    ChangeListViewerDialog.show(project, title, panel);
  }
}
