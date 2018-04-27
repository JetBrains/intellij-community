// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

class RemoveBreakpointGutterIconAction extends DumbAwareAction {
  private final XBreakpointBase<?,?,?> myBreakpoint;

  RemoveBreakpointGutterIconAction(XBreakpointBase<?, ?, ?> breakpoint) {
    super(XDebuggerBundle.message("xdebugger.remove.line.breakpoint.action.text"));
    myBreakpoint = breakpoint;
    AnAction action = ActionManager.getInstance().getAction("ToggleLineBreakpoint");
    copyShortcutFrom(action);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    InputEvent event = e.getInputEvent();
    // for mouse events check that no modifiers applied
    if (!(event instanceof MouseEvent) || event.getModifiersEx() == 0 || SwingUtilities.isMiddleMouseButton((MouseEvent)event)) {
      XDebuggerUtilImpl.removeBreakpointWithConfirmation(myBreakpoint.getProject(), myBreakpoint);
    }
  }
}