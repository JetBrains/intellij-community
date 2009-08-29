package com.intellij.xdebugger.breakpoints.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import com.intellij.xdebugger.breakpoints.XBreakpoint;

/**
 * @author nik
 */
public abstract class XBreakpointCustomPropertiesPanel<B extends XBreakpoint<?>> {

  @NotNull
  public abstract JComponent getComponent();

  public abstract void saveTo(@NotNull B breakpoint);

  public abstract void loadFrom(@NotNull B breakpoint);

  public void dispose() {
  }
}
