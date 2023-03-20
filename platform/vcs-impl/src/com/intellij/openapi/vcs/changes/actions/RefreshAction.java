// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class RefreshAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean isEnabled = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss();
    e.getPresentation().setEnabledAndVisible(isEnabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    doRefresh(project);
  }

  public static void doRefresh(@NotNull Project project) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    ChangeListManagerEx changeListManager = ChangeListManagerEx.getInstanceEx(project);
    Collection<Change> changesBeforeUpdate = changeListManager.getAllChanges();
    Collection<FilePath> unversionedBefore = changeListManager.getUnversionedFilesPaths();
    boolean wasUpdatingBefore = changeListManager.isInUpdate();

    FileDocumentManager.getInstance().saveAllDocuments();
    invokeCustomRefreshes(project);

    VirtualFileManager.getInstance().asyncRefresh(() -> {
      performRefreshAndTrackChanges(project, changesBeforeUpdate, unversionedBefore, wasUpdatingBefore);
    });
  }

  private static void invokeCustomRefreshes(@NotNull Project project) {
    ChangesViewRefresher[] extensions = ChangesViewRefresher.EP_NAME.getExtensions(project);
    for (ChangesViewRefresher refresher : extensions) {
      refresher.refresh(project);
    }
  }

  private static void performRefreshAndTrackChanges(Project project,
                                                    Collection<? extends Change> changesBeforeUpdate,
                                                    Collection<? extends FilePath> unversionedBefore,
                                                    boolean wasUpdatingBefore) {
    if (project.isDisposed()) return;
    ChangeListManagerEx changeListManager = ChangeListManagerEx.getInstanceEx(project);

    VcsDirtyScopeManager.getInstance(project).markEverythingDirty();

    changeListManager.invokeAfterUpdate(false, () -> {
      Collection<Change> changesAfterUpdate = changeListManager.getAllChanges();
      Collection<FilePath> unversionedAfter = changeListManager.getUnversionedFilesPaths();

      VcsStatisticsCollector
        .logRefreshActionPerformed(project, changesBeforeUpdate, changesAfterUpdate, unversionedBefore, unversionedAfter,
                                   wasUpdatingBefore);
    });
  }
}
