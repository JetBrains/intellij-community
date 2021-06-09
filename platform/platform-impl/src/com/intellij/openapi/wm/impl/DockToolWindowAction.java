// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.actions.ToolWindowMoveAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.jetbrains.annotations.NotNull;

public class DockToolWindowAction extends DumbAwareAction /*implements FusAwareAction*/ {
  public DockToolWindowAction() {
    super(ActionsBundle.messagePointer("action.DockToolWindow.text"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) return;
    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (!(toolWindow instanceof ToolWindowImpl)) return;
    e.getPresentation().setIcon(ToolWindowMoveAction.Anchor.fromWindowInfo(((ToolWindowImpl)toolWindow).getWindowInfo()).getIcon());
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
    ToolWindowMoveAction.Anchor.fromWindowInfo(((ToolWindowImpl)toolWindow).getWindowInfo()).applyTo(toolWindow);

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
