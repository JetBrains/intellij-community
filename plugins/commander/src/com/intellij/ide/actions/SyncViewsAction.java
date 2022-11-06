
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.commander.Commander;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class SyncViewsAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    Commander.getInstance(project).syncViews();
  }
  @Override
  public void update(@NotNull AnActionEvent e){
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id = windowManager.getActiveToolWindowId();
    boolean value=ToolWindowId.COMMANDER.equals(id);
    presentation.setEnabled(value);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
