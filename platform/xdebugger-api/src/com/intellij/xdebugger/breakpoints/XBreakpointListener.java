// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.breakpoints;

import com.intellij.util.messages.Topic;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface XBreakpointListener<B extends XBreakpoint<?>> extends EventListener {
  Topic<XBreakpointListener> TOPIC = new Topic<>("XBreakpointManager events", XBreakpointListener.class);

  default void breakpointAdded(@NotNull B breakpoint) {
  }

  default void breakpointRemoved(@NotNull B breakpoint) {
  }

  default void breakpointChanged(@NotNull B breakpoint) {
  }

  default void breakpointPresentationUpdated(@NotNull B breakpoint, @Nullable XDebugSession session) {
  }
}
