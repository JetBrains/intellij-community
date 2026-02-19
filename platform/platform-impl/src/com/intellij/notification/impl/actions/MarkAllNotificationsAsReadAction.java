// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.ActionCenter;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MarkAllNotificationsAsReadAction extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.Frontend {
  public MarkAllNotificationsAsReadAction() {
    super(IdeBundle.messagePointer("action.MarkAllNotificationsAsReadAction.text"),
          IdeBundle.messagePointer("action.MarkAllNotificationsAsReadAction.description"),
          ExperimentalUI.isNewUI() ? null : AllIcons.Actions.Selectall);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!ActionCenter.getNotifications(e.getProject()).isEmpty());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      ActionCenter.expireNotifications(project);
    }
  }
}
