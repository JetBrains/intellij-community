// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.handlers.XMarkObjectActionHandler;
import com.intellij.xdebugger.impl.messages.XDebuggerImplBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class MarkObjectAction extends XDebuggerActionBase implements SplitDebuggerAction {
  private static final XMarkObjectActionHandler ourHandler = new XMarkObjectActionHandler();

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    boolean enabled = false;
    Presentation presentation = event.getPresentation();
    boolean hidden = true;
    if (project != null) {
      enabled = ourHandler.isEnabled(project, event);
      hidden = ourHandler.isHidden(project, event);
      String text;
      if (ourHandler.isMarked(project, event)) {
        text = XDebuggerImplBundle.message("action.Debugger.MarkObject.unmark.text");
      }
      else {
        text = XDebuggerImplBundle.message("action.Debugger.MarkObject.text");
      }
      presentation.setText(text);
    }
    presentation.setVisible(!hidden && (!event.isFromContextMenu() || enabled));
    presentation.setEnabled(enabled);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
