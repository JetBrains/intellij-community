// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AutoShowProcessPopupAction extends DumbAwareToggleAction {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return Registry.is("ide.windowSystem.autoShowProcessPopup");
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Registry.get("ide.windowSystem.autoShowProcessPopup").setValue(state);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
