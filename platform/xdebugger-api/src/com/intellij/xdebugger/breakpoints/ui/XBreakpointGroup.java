package com.intellij.xdebugger.breakpoints.ui;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XBreakpointGroup implements Comparable<XBreakpointGroup> {
  @Nullable
  public Icon getIcon(boolean isOpen) {
    return null;
  }

  @NotNull
  public abstract String getName();

  public int compareTo(final XBreakpointGroup o) {
    return getName().compareTo(o.getName());
  }
}
