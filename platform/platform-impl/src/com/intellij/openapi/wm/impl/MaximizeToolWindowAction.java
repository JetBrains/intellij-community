// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.actions.ToolwindowFusEventFields;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class MaximizeToolWindowAction extends AnAction implements DumbAware, FusAwareAction {
  public MaximizeToolWindowAction() {
    super(ActionsBundle.messagePointer("action.ResizeToolWindowMaximize.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) return;
    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow == null) return;
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    manager.setMaximized(toolWindow, !manager.isMaximized(toolWindow));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(true);
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    e.getPresentation().setText(manager.isMaximized(toolWindow) ?
                                ActionsBundle.message("action.ResizeToolWindowMaximize.text.alternative") :
                                ActionsBundle.message("action.ResizeToolWindowMaximize.text"));
  }

  @Override
  public @NotNull List<EventPair<?>> getAdditionalUsageData(@NotNull AnActionEvent event) {
    ToolWindow toolWindow = event.getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow != null) {
      return Collections.singletonList(ToolwindowFusEventFields.TOOLWINDOW.with(toolWindow.getId()));
    }
    return Collections.emptyList();
  }
}
