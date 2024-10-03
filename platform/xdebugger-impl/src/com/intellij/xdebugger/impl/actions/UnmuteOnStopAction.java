// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class UnmuteOnStopAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isUnmuteOnStop();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().setUnmuteOnStop(state);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}