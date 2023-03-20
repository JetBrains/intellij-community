// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.history.FileHistoryModel;
import org.jetbrains.annotations.NotNull;

import static com.intellij.vcs.log.util.VcsLogUtil.createCommittedChangeList;

public class ShowAllAffectedFromHistoryAction extends FileHistoryOneCommitAction<VcsFullCommitDetails> {

  @Override
  protected @NotNull DataGetter<VcsFullCommitDetails> getDetailsGetter(@NotNull VcsLogData logData) {
    return logData.getCommitDetailsGetter();
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryModel model,
                               @NotNull VcsFullCommitDetails detail,
                               @NotNull AnActionEvent e) {
    FilePath file = model.getPathInCommit(detail.getId());
    String title = VcsLogBundle.message("dialog.title.paths.affected.by.commit", detail.getId().toShortString());

    LoadingCommittedChangeListPanel panel = new LoadingCommittedChangeListPanel(project);
    panel.loadChangesInBackground(() -> {
      CommittedChangeListForRevision committedChangeList = createCommittedChangeList(detail);
      return new LoadingCommittedChangeListPanel.ChangelistData(committedChangeList, file);
    });

    ChangeListViewerDialog.show(project, title, panel);
  }
}
