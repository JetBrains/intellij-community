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
