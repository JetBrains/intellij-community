// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static com.intellij.xdebugger.impl.XDebuggerUtilImpl.performDebuggerAction;

class RemoveBreakpointGutterIconAction extends DumbAwareAction {
  private final XBreakpointProxy myBreakpoint;

  RemoveBreakpointGutterIconAction(XBreakpointProxy breakpoint) {
    super(XDebuggerBundle.message("xdebugger.remove.line.breakpoint.action.text"));
    myBreakpoint = breakpoint;
    AnAction action = ActionManager.getInstance().getAction("ToggleLineBreakpoint");
    copyShortcutFrom(action);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    performDebuggerAction(e, () -> {
      InputEvent event = e.getInputEvent();
      // for mouse events check that no modifiers applied
      if (!(event instanceof MouseEvent) || event.getModifiersEx() == 0 || SwingUtilities.isMiddleMouseButton((MouseEvent)event)) {
        XDebuggerUtilImpl.removeBreakpointWithConfirmation(myBreakpoint);
      }
    });
  }
}