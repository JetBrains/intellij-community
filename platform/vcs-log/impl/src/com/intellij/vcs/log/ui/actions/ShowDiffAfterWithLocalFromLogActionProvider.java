// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogCommitSelectionUtils;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

@ApiStatus.Internal
public class ShowDiffAfterWithLocalFromLogActionProvider implements AnActionExtensionProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) != null && e.getData(ChangesBrowserBase.DATA_KEY) == null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (project == null || selection == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (VcsLogCommitSelectionUtils.getSize(selection) != 1) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (selection == null) return;

    List<CommitId> commits = selection.getCommits();
    if (commits.size() != 1) return;
    CommitId commit = Objects.requireNonNull(getFirstItem(commits));

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    VcsLogDiffHandler handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER);
    if (handler == null) return;
    handler.showDiffForPaths(commit.getRoot(), VcsLogUtil.getAffectedPaths(commit.getRoot(), e), commit.getHash(), null);
  }
}
