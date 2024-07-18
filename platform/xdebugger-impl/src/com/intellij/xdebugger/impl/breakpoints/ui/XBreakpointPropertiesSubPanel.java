// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class XBreakpointPropertiesSubPanel {
  protected Project myProject;
  protected XBreakpointManager myBreakpointManager;
  protected XBreakpointBase myBreakpoint;
  protected XBreakpointType myBreakpointType;

  public void init(Project project, final XBreakpointManager breakpointManager, @NotNull XBreakpointBase breakpoint) {
    myProject = project;
    myBreakpointManager = breakpointManager;
    myBreakpoint = breakpoint;
    myBreakpointType = breakpoint.getType();
  }

  abstract void loadProperties();

  abstract void saveProperties();

  public boolean lightVariant(boolean showAllOptions) {
    return false;
  }
}
