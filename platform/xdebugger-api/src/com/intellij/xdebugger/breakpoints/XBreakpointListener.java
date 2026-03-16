// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints;

import com.intellij.util.messages.Topic;
import com.intellij.xdebugger.BreakpointErrorData;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface XBreakpointListener<B extends XBreakpoint<?>> extends EventListener {

  @Topic.ProjectLevel
  Topic<XBreakpointListener> TOPIC = new Topic<>("XBreakpointManager events", XBreakpointListener.class);

  default void breakpointAdded(@NotNull B breakpoint) {
  }

  default void breakpointRemoved(@NotNull B breakpoint) {
  }

  default void breakpointChanged(@NotNull B breakpoint) {
  }

  default void breakpointPresentationUpdated(@NotNull B breakpoint, @Nullable XDebugSession session) {
  }

  /**
   * Called when a breakpoint error is occurred, e.g., when a condition expression is invalid
   * <p>
   * Currently supported only for JVM debugger
   */
  @ApiStatus.Experimental
  default void breakpointError(@NotNull B breakpoint, @NotNull XDebugSession session, @NotNull BreakpointErrorData error) {
  }

  /**
   * Called when a breakpoint with a log expression is hit
   * <p>
   * Currently supported only for JVM debugger
   */
  @ApiStatus.Experimental
  default void breakpointLogMessage(@NotNull B breakpoint, @NotNull XDebugSession session, @NotNull String message) {
  }
}
