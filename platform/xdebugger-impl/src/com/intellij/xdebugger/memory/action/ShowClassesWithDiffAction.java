// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.memory.component.MemoryViewManager;
import org.jetbrains.annotations.NotNull;

public class ShowClassesWithDiffAction extends ToggleAction {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return MemoryViewManager.getInstance().isNeedShowDiffOnly();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project != null) {
      MemoryViewManager.getInstance().setShowDiffOnly(state);
    }
  }
}
