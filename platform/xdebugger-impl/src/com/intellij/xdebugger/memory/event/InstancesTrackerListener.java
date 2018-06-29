// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.event;

import com.intellij.xdebugger.memory.tracking.TrackingType;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface InstancesTrackerListener extends EventListener {
  default void classChanged(@NotNull String name, @NotNull TrackingType type) {
  }

  default void classRemoved(@NotNull String name) {
  }

  default void backgroundTrackingValueChanged(boolean newState) {
  }
}
