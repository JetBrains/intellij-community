// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.messages.Topic;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface XDependentBreakpointListener extends EventListener {
  Topic<XDependentBreakpointListener> TOPIC = new Topic<>("XBreakpointManager events", XDependentBreakpointListener.class);

  void dependencySet(@NotNull XBreakpoint<?> slave, @NotNull XBreakpoint<?> master);

  void dependencyCleared(XBreakpoint<?> breakpoint);
}
