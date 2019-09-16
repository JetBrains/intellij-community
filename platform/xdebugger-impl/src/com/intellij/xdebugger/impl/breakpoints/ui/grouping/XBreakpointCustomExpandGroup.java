package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface XBreakpointCustomExpandGroup {
  boolean shouldBeExpandedByDefault();
}
