// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentUtilEx;
import org.jetbrains.annotations.NotNull;

public final class ViewUpdateInfoNotification extends NotificationAction {
  private final @NotNull Project myProject;
  private final @NotNull UpdateInfoTree myTree;

  public ViewUpdateInfoNotification(@NotNull Project project, @NotNull UpdateInfoTree updateInfoTree,
                                    @NotNull @NlsContexts.NotificationContent String actionName) {
    super(actionName);
    myProject = project;
    myTree = updateInfoTree;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
    focusUpdateInfoTree(myProject, myTree);
    notification.expire();
  }

  public static void focusUpdateInfoTree(@NotNull Project project, @NotNull UpdateInfoTree updateInfoTree) {
    ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID).activate(() -> {
      ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).getContentManager();
      if (contentManager != null) ContentUtilEx.selectContent(contentManager, updateInfoTree, true);
    }, true, true);
  }
}
