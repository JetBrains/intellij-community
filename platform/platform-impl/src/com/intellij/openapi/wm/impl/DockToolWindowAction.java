// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ToolWindowMoveAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class DockToolWindowAction extends DumbAwareAction /*implements FusAwareAction*/ {
  public DockToolWindowAction() {
    super(ActionsBundle.messagePointer("action.DockToolWindow.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (!(toolWindow instanceof ToolWindowImpl)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Icon icon = ExperimentalUI.isNewUI()
                ? AllIcons.General.OpenInToolWindow
                : ToolWindowMoveAction.Anchor.fromWindowInfo(((ToolWindowImpl)toolWindow).getWindowInfo()).getIcon();
    e.getPresentation().setIcon(icon);
    e.getPresentation()
      .setEnabledAndVisible(toolWindow.getType() == ToolWindowType.FLOATING || toolWindow.getType() == ToolWindowType.WINDOWED);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) return;
    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (!(toolWindow instanceof ToolWindowImpl)) return;
    toolWindow.setType(((ToolWindowEx)toolWindow).getInternalType(), null);
    ToolWindowMoveAction.Anchor anchor = ToolWindowMoveAction.Anchor.fromWindowInfo(((ToolWindowImpl)toolWindow).getWindowInfo());
    anchor.applyTo(toolWindow, ((ToolWindowImpl)toolWindow).getWindowInfo().getOrder());
  }

  //@Override
  //public @NotNull List<EventPair<?>> getAdditionalUsageData(@NotNull AnActionEvent event) {
  //  ToolWindow toolWindow = event.getData(PlatformDataKeys.TOOL_WINDOW);
  //  if (toolWindow != null) {
  //    return Collections.singletonList(ToolwindowFusEventFields.TOOLWINDOW.with(toolWindow.getId()));
  //  }
  //  return Collections.emptyList();
  //}
}
