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

  public void perform(@NotNull final Collection<? extends B> breakpoints, final JComponent parentComponent) {
    B b = myBreakpointsPanel.getType().addBreakpoint(myBreakpointsPanel.getProject(), parentComponent);
    if (b != null) {
      myBreakpointsPanel.resetBreakpoints();
      myBreakpointsPanel.selectBreakpoint(b);
    }
  }
}
