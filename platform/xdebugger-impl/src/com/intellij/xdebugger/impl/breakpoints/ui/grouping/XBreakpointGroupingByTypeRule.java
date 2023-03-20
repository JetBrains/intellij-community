/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class XBreakpointGroupingByTypeRule<B> extends XBreakpointGroupingRule<B, XBreakpointTypeGroup> {

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
  public XBreakpointTypeGroup getGroup(@NotNull B b, @NotNull Collection<? extends XBreakpointTypeGroup> groups) {
    if (b instanceof XBreakpoint breakpoint) {
      for (XBreakpointTypeGroup group : groups) {
        if (group.getBreakpointType() == breakpoint.getType()) {
          return group;
        }
      }
      return new XBreakpointTypeGroup(breakpoint.getType());
    }
    return null;
  }
}
