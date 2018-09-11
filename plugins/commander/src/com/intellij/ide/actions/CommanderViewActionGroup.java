// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class CommanderViewActionGroup extends DefaultActionGroup  implements DumbAware {
  public CommanderViewActionGroup() {
    super();
  }

  @Override
  public void update(@NotNull AnActionEvent e){
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setVisible(false);
      return;
    }
    String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    boolean isCommanderActive = ToolWindowId.COMMANDER.equals(id);
    presentation.setVisible(isCommanderActive);
  }
}