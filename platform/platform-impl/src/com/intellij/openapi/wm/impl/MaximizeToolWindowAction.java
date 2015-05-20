/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class MaximizeToolWindowAction extends AnAction implements DumbAware {
  public MaximizeToolWindowAction() {
    super(ActionsBundle.message("action.ResizeToolWindowMaximize.text"));
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
}
