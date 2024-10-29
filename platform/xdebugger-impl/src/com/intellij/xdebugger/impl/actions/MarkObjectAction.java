// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class MarkObjectAction extends XDebuggerActionBase {
  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    boolean enabled = false;
    Presentation presentation = event.getPresentation();
    boolean hidden = true;
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        MarkObjectActionHandler handler = support.getMarkObjectHandler();
        hidden &= handler.isHidden(project, event);
        if (handler.isEnabled(project, event)) {
          enabled = true;
          String text;
          if (handler.isMarked(project, event)) {
            text = ActionsBundle.message("action.Debugger.MarkObject.unmark.text");
          }
          else {
            text = ActionsBundle.message("action.Debugger.MarkObject.text");
          }
          presentation.setText(text);
          break;
        }
      }
    }
    presentation.setVisible(!hidden && (!event.isFromContextMenu() || enabled));
    presentation.setEnabled(enabled);
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return debuggerSupport.getMarkObjectHandler();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
