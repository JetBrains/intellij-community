// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.history.FileHistoryModel;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogCommitSelectionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class ShowDiffAfterWithLocalFromFileHistoryActionProvider implements AnActionExtensionProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null && e.getData(ChangesBrowserBase.DATA_KEY) == null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    FileHistoryModel model = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_MODEL);
    if (project == null || selection == null || model == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    if (VcsLogCommitSelectionUtils.getSize(selection) != 1) {
      e.getPresentation().setEnabled(false);
      return;
    }

    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsLogDiffHandler handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER);

    e.getPresentation().setEnabled(filePath != null && filePath.getVirtualFile() != null && handler != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLogCommitSelection selection = e.getRequiredData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    FileHistoryModel model = e.getRequiredData(VcsLogInternalDataKeys.FILE_HISTORY_MODEL);

    List<CommitId> commits = selection.getCommits();
    if (commits.size() != 1) return;
    CommitId commit = Objects.requireNonNull(getFirstItem(commits));

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    FilePath localPath = e.getRequiredData(VcsDataKeys.FILE_PATH);
    FilePath pathInCommit = model.getPathInCommit(commit.getHash());
    VcsLogDiffHandler handler = e.getRequiredData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER);

    handler.showDiffWithLocal(commit.getRoot(), pathInCommit, commit.getHash(), localPath);
  }
}
