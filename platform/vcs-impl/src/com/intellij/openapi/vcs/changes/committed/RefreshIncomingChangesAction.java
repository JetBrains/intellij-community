// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RefreshIncomingChangesAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      doRefresh(project);
    }
  }

  public static void doRefresh(final Project project) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(project);
    cache.hasCachesForAnyRoot(notEmpty -> {
      if ((! notEmpty) && (!CacheSettingsDialog.showSettingsDialog(project))) {
        return;
      }
      cache.refreshAllCachesAsync(true, false);
      cache.refreshIncomingChangesAsync();
    });
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && !CommittedChangesCache.getInstance(project).isRefreshingIncomingChanges());
  }
}