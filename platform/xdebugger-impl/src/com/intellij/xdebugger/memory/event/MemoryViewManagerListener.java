// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.event;

import com.intellij.xdebugger.memory.component.MemoryViewManagerState;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

@FunctionalInterface
public interface MemoryViewManagerListener extends EventListener {
  void stateChanged(@NotNull MemoryViewManagerState state);
}
