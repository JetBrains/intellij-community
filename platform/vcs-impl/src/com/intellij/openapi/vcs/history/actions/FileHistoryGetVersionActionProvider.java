// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class FileHistoryGetVersionActionProvider implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.HISTORY_SESSION) != null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsHistorySession historySession = e.getData(VcsDataKeys.HISTORY_SESSION);
    if (historySession == null) return;
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);

    if (e.getProject() == null || filePath == null || revision == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(historySession.isContentAvailable(revision) &&
                                               !filePath.isDirectory());
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (revision == null) return;
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (filePath == null) return;

    GetVersionAction.doGet(project, revision, filePath);
  }
}
