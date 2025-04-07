// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;

import java.util.Arrays;
import java.util.Collection;

public class XBreakpointPanelProvider extends BreakpointPanelProvider {

  @Override
  public void provideBreakpointItems(Project project, Collection<? super BreakpointItem> items) {
    Arrays.stream(XDebuggerManager.getInstance(project).getBreakpointManager().getAllBreakpoints())
      .map(XBreakpointItem::new)
      .forEach(items::add);
  }
}
