// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesViewRefresher;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 * @since 02.11.2006
 */
public class RefreshAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    doRefresh(project);
  }

  public static void doRefresh(final Project project) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    FileDocumentManager.getInstance().saveAllDocuments();
    invokeCustomRefreshes(project);

    VirtualFileManager.getInstance().asyncRefresh(() -> {
      // already called in EDT or under write action
      if (!project.isDisposed()) {
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
      }
    });
  }

  private static void invokeCustomRefreshes(@NotNull Project project) {
    ChangesViewRefresher[] extensions = ChangesViewRefresher.EP_NAME.getExtensions(project);
    for (ChangesViewRefresher refresher : extensions) {
      refresher.refresh(project);
    }
  }
}
