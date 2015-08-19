/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.debugger;

import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.NotNull;

public final class LineBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<?>> {
  private final LineBreakpointManager lineBreakpointManager;
  private final boolean onlySourceMappedBreakpoints;

  public LineBreakpointHandler(@NotNull Class<? extends XLineBreakpointType<?>> breakpointTypeClass, LineBreakpointManager lineBreakpointManager, boolean onlySourceMappedBreakpoints) {
    super(breakpointTypeClass);

    this.lineBreakpointManager = lineBreakpointManager;
    this.onlySourceMappedBreakpoints = onlySourceMappedBreakpoints;
  }

  @NotNull
  public LineBreakpointManager getManager() {
    return lineBreakpointManager;
  }

  @Override
  public void registerBreakpoint(@NotNull XLineBreakpoint<?> breakpoint) {
    lineBreakpointManager.setBreakpoint(breakpoint, onlySourceMappedBreakpoints);
  }

  @Override
  public void unregisterBreakpoint(@NotNull XLineBreakpoint<?> breakpoint, boolean temporary) {
    lineBreakpointManager.removeBreakpoint(breakpoint, temporary);
  }
}