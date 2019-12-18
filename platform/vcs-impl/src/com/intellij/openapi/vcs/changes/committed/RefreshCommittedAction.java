// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RefreshCommittedAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    CommittedChangesPanel panel = ChangesViewContentManager.getInstance(project).getActiveComponent(CommittedChangesPanel.class);
    assert panel != null;
    if (isLoading(panel)) return;
    if (panel instanceof RepositoryLocationCommittedChangesPanel) {
      panel.refreshChanges();
    }
    else {
      RefreshIncomingChangesAction.doRefresh(project);
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      CommittedChangesPanel panel = ChangesViewContentManager.getInstance(project).getActiveComponent(CommittedChangesPanel.class);
      e.getPresentation().setEnabled(panel != null && !isLoading(panel));
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  private static boolean isLoading(@NotNull CommittedChangesPanel panel) {
    return panel instanceof RepositoryLocationCommittedChangesPanel && ((RepositoryLocationCommittedChangesPanel)panel).isLoading();
  }
}
