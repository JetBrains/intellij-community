// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public final class XBreakpointFileGroupingRule<B> extends XBreakpointGroupingRule<B, XBreakpointFileGroup> {
  public XBreakpointFileGroupingRule() {
    super("by-file", XDebuggerBundle.message("rule.name.group.by.file"));
  }

  @Override
  public int getPriority() {
    return XBreakpointsGroupingPriorities.BY_FILE;
  }

  @Override
  public XBreakpointFileGroup getGroup(final @NotNull B breakpoint) {
    if (!(breakpoint instanceof XLineBreakpoint)) {
      return null;
    }
    XSourcePosition position = ((XLineBreakpoint<?>)breakpoint).getSourcePosition();

    if (position == null) return null;

    VirtualFile file = position.getFile();

    return new XBreakpointFileGroup(file);
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Actions.GroupByFile;
  }
}
