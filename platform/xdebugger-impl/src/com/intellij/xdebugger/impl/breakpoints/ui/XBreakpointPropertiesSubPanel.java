/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;

public abstract class XBreakpointPropertiesSubPanel<B extends XBreakpoint<?>> {
  protected Project myProject;
  protected XBreakpointManager myBreakpointManager;
  protected B myBreakpoint;
  protected XBreakpointType<?, ? extends XBreakpointProperties> myBreakpointType;

  public void init(Project project, final XBreakpointManager breakpointManager, @NotNull B breakpoint) {
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
