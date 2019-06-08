// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class ShowDiffAfterWithLocalFromFileHistoryActionProvider implements AnActionExtensionProvider {

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null && e.getData(ChangesBrowserBase.DATA_KEY) == null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if (project == null || log == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    List<CommitId> commits = log.getSelectedCommits();
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
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);

    List<CommitId> commits = log.getSelectedCommits();
    if (commits.size() != 1) return;
    CommitId commit = notNull(getFirstItem(commits));

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    FilePath localPath = e.getRequiredData(VcsDataKeys.FILE_PATH);
    FilePath pathInCommit = e.getRequiredData(VcsLogInternalDataKeys.FILE_HISTORY_UI).getPathInCommit(commit.getHash());
    VcsLogDiffHandler handler = e.getRequiredData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER);

    handler.showDiffWithLocal(commit.getRoot(), pathInCommit, commit.getHash(), localPath);
  }
}
