// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentUtilEx;
import org.jetbrains.annotations.NotNull;

public final class ViewUpdateInfoNotification extends NotificationAction {
  @NotNull private final Project myProject;
  @NotNull private final UpdateInfoTree myTree;

  public ViewUpdateInfoNotification(@NotNull Project project, @NotNull UpdateInfoTree updateInfoTree, 
                                    @NotNull @NlsContexts.NotificationContent String actionName, @NotNull Notification notification) {
    super(actionName);
    myProject = project;
    myTree = updateInfoTree;
    Disposer.register(updateInfoTree, new Disposable() {
      @Override
      public void dispose() {
        notification.expire();
      }
    });
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
