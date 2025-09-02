// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class XBreakpointPropertiesSubPanel {
  protected Project myProject;
  protected XBreakpointProxy myBreakpoint;
  protected XBreakpointTypeProxy myBreakpointType;

  public void init(Project project, @NotNull XBreakpointProxy breakpoint) {
    myProject = project;
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
