// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.getToolWindowFor;
import static com.intellij.util.ObjectUtils.assertNotNull;

public abstract class VcsShowToolWindowTabAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ToolWindow toolWindow = assertNotNull(getToolWindow(project));
    final ChangesViewContentManager changesViewContentManager = (ChangesViewContentManager)ChangesViewContentManager.getInstance(project);
    final String tabName = getTabName();

    if (toolWindow.isActive() && changesViewContentManager.isContentSelected(tabName)) {
        toolWindow.hide(null);
    }
    else {
      toolWindow.activate(() -> {
        if (!changesViewContentManager.isContentSelected(tabName)) {
          changesViewContentManager.selectContent(tabName, true);
        }
      }, true, true);
    }
  }

  @Nullable
  private ToolWindow getToolWindow(@Nullable Project project) {
    if (project == null) return null;
    return getToolWindowFor(project, getTabName());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(getToolWindow(e.getProject()) != null);
  }

  @NotNull
  protected abstract String getTabName();
}
