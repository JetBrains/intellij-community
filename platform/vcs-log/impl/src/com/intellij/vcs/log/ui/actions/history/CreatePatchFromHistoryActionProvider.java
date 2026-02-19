// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogCommitSelectionUtils;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class CreatePatchFromHistoryActionProvider implements AnActionExtensionProvider {
  private final boolean mySilentClipboard;

  private CreatePatchFromHistoryActionProvider(boolean silentClipboard) {
    mySilentClipboard = silentClipboard;
  }

  public static class Dialog extends CreatePatchFromHistoryActionProvider {
    public Dialog() {
      super(false);
    }
  }

  public static class Clipboard extends CreatePatchFromHistoryActionProvider {
    public Clipboard() {
      super(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (project == null || ui == null || selection == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    String commitMessage = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    e.getPresentation().setEnabled(VcsLogCommitSelectionUtils.isNotEmpty(selection) && commitMessage != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (selection == null) return;
    String commitMessage = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    if (commitMessage == null) return;

    selection.requestFullDetails(detailsList -> {
      List<Change> changes = VcsLogUtil.collectChanges(detailsList);
      CreatePatchFromChangesAction.createPatch(project, commitMessage, changes, mySilentClipboard);
    });
  }
}
