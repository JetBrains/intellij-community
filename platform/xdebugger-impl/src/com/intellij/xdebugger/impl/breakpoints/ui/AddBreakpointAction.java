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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public class AddBreakpointAction<B extends XBreakpoint<?>> extends XBreakpointPanelAction<B> {
  public AddBreakpointAction(final XBreakpointsPanel<B> breakpointsPanel) {
    super(breakpointsPanel, XDebuggerBundle.message("xbreakpoints.dialog.button.add"));
  }

  public boolean isEnabled(@NotNull final Collection<? extends B> breakpoints) {
    return true;
  }

  public void perform(@NotNull final Collection<? extends B> breakpoints) {
    B b = myBreakpointsPanel.getType().addBreakpoint(myBreakpointsPanel.getProject(), myBreakpointsPanel.getTree());
    if (b != null) {
      myBreakpointsPanel.resetBreakpoints();
      myBreakpointsPanel.selectBreakpoint(b);
    }
  }
}
