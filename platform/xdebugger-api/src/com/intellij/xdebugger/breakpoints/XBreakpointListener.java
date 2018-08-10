// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.breakpoints;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface XBreakpointListener<B extends XBreakpoint<?>> extends EventListener {
  Topic<XBreakpointListener> TOPIC = new Topic<>("Project open and close events", XBreakpointListener.class);

  default void breakpointAdded(@NotNull B breakpoint) {
  }

  default void breakpointRemoved(@NotNull B breakpoint) {
  }

  default void breakpointChanged(@NotNull B breakpoint) {
  }
}
