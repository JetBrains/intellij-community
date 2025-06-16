// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;

class ToggleBreakpointGutterIconAction extends DumbAwareAction {
  private final XBreakpointProxy myBreakpoint;

  ToggleBreakpointGutterIconAction(XBreakpointProxy breakpoint) {
    super(breakpoint.isEnabled() ? XDebuggerBundle.message("xdebugger.disable.breakpoint.action.text") : XDebuggerBundle.message("xdebugger.enable.breakpoint.action.text"));
    this.myBreakpoint = breakpoint;
    AnAction action = ActionManager.getInstance().getAction("ToggleBreakpointEnabled");
    copyShortcutFrom(action);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    myBreakpoint.setEnabled(!myBreakpoint.isEnabled());
  }
}
