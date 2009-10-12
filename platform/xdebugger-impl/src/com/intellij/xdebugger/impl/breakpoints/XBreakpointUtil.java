/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public class XBreakpointUtil {
  private XBreakpointUtil() {
  }

  public static <B extends XBreakpoint<?>> String getDisplayText(@NotNull B breakpoint) {
    return getType(breakpoint).getDisplayText(breakpoint);
  }

  public static <B extends XBreakpoint<?>> XBreakpointType<B, ?> getType(@NotNull B breakpoint) {
    //noinspection unchecked
    return (XBreakpointType<B,?>)breakpoint.getType();
  }

  @Nullable
  public static XBreakpointType<?,?> findType(@NotNull @NonNls String id) {
    XBreakpointType[] breakpointTypes = getBreakpointTypes();
    for (XBreakpointType breakpointType : breakpointTypes) {
      if (id.equals(breakpointType.getId())) {
        return breakpointType;
      }
    }
    return null;
  }

  public static XBreakpointType<?,?>[] getBreakpointTypes() {
    return Extensions.getExtensions(XBreakpointType.EXTENSION_POINT_NAME);
  }
}
