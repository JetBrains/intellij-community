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

  /**
   * Tries to display a simplified ('light') version of the panel.
   * <p>
   * The method evaluates the configuration of the current breakpoint and returns a boolean indicating whether
   * the panel could be hidden to provide a streamlined user interface experience.
   * </p>
   *
   * @param showAllOptions a boolean flag indicating whether all panels should be forcibly displayed
   *                       regardless of breakpoint configuration.
   * @return {@code true} if the panel is set to be invisible;
   *         {@code false} otherwise, meaning the full set of options is retained.
   */
  public boolean lightVariant(boolean showAllOptions) {
    return false;
  }
}
