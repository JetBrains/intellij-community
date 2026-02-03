// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

final class ShowProcessWindowAction extends ToggleAction implements DumbAware {
  ShowProcessWindowAction() {
    super(ActionsBundle.messagePointer("action.ShowProcessWindow.text"), ActionsBundle.messagePointer("action.ShowProcessWindow.description"),
          null);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    ProjectFrameHelper frame = getFrame();
    if (frame != null) {
      StatusBarEx statusBar = frame.getStatusBar();
      if (statusBar != null) {
        return statusBar.isProcessWindowOpen();
      }
    }

    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getFrame() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    ProjectFrameHelper frame = getFrame();
    if (frame != null) {
      StatusBarEx statusBar = frame.getStatusBar();
      if (statusBar != null) {
        statusBar.setProcessWindowOpen(state);
      }
    }
  }

  private static @Nullable ProjectFrameHelper getFrame() {
    return ProjectFrameHelper.getFrameHelper(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow());
  }
}
