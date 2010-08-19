/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.breakpoints.ui.actions;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelAction;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointsPanel;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.Result;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Collection;

/**
 * @author nik
*/
public class RemoveBreakpointAction<B extends XBreakpoint<?>> extends XBreakpointPanelAction<B> {
  public RemoveBreakpointAction(final XBreakpointsPanel<B> panel) {
    super(panel, XDebuggerBundle.message("xbreakpoints.dialog.button.remove"));
    panel.getTree().registerKeyboardAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  public boolean isEnabled(@NotNull final Collection<? extends B> breakpoints) {
    return !breakpoints.isEmpty();
  }

  public void perform(@NotNull final Collection<? extends B> breakpoints) {
    final XBreakpointManager breakpointManager = myBreakpointsPanel.getBreakpointManager();
    new WriteAction() {
      protected void run(final Result result) {
        for (B breakpoint : breakpoints) {
          breakpointManager.removeBreakpoint(breakpoint);
        }
      }
    }.execute();
    myBreakpointsPanel.hideBreakpointProperties();
    myBreakpointsPanel.resetBreakpoints();
  }
}
