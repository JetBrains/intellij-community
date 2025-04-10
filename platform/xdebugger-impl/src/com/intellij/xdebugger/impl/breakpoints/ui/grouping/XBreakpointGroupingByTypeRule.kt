// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class XBreakpointGroupingByTypeRule<B> extends XBreakpointGroupingRule<B, XBreakpointTypeGroup> {

  public XBreakpointGroupingByTypeRule() {
    super("XBreakpointGroupingByTypeRule", XDebuggerBundle.message("breakpoints.group.by.type.label"));
  }

  @Override
  public boolean isAlwaysEnabled() {
    return true;
  }

  @Override
  public int getPriority() {
    return XBreakpointsGroupingPriorities.BY_TYPE;
  }

  @Override
  public XBreakpointTypeGroup getGroup(@NotNull B b) {
    if (b instanceof XBreakpoint breakpoint) {
      return new XBreakpointTypeGroup(breakpoint.getType());
    }
    return null;
  }
}
