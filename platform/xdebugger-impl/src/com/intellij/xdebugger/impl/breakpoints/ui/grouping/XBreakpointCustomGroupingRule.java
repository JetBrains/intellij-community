// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Egor
 */
public class XBreakpointCustomGroupingRule<B> extends XBreakpointGroupingRule<B, XBreakpointCustomGroup> {
  public XBreakpointCustomGroupingRule() {
    super("by-group", XDebuggerBundle.message("breakpoints.show.user.groups"));
  }

  @Override
  public int getPriority() {
    return 1200;
  }

  @Override
  public boolean isAlwaysEnabled() {
    return true;
  }

  @Override
  public XBreakpointCustomGroup getGroup(@NotNull final B breakpoint, @NotNull final Collection<? extends XBreakpointCustomGroup> groups) {
    if (!(breakpoint instanceof XBreakpointBase)) {
      return null;
    }
    String name = ((XBreakpointBase<?, ?, ?>)breakpoint).getGroup();
    if (StringUtil.isEmpty(name)) {
      return null;
    }
    return new XBreakpointCustomGroup(name, ((XBreakpointBase<?, ?, ?>)breakpoint).getProject());
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Folder;
  }
}
