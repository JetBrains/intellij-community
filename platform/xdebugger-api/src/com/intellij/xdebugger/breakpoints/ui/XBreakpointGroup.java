// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints.ui;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class XBreakpointGroup implements Comparable<XBreakpointGroup> {
  public @Nullable Icon getIcon(boolean isOpen) {
    return null;
  }

  public abstract @NotNull @NlsSafe String getName();

  public boolean expandedByDefault() {
    return true;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null) return false;
    return (getClass() == obj.getClass()) && compareTo((XBreakpointGroup)obj) == 0;
  }

  @Override
  public int compareTo(final XBreakpointGroup o) {
    return getName().compareTo(o.getName());
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }
}
