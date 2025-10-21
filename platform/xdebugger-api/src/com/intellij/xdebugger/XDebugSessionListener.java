// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import org.jetbrains.annotations.ApiStatus;

import java.util.EventListener;

public interface XDebugSessionListener extends EventListener {
  default void sessionPaused() {
  }

  default void sessionResumed() {
  }

  default void sessionStopped() {
  }

  default void stackFrameChanged() {
  }

  /**
   * @param changedByUser true if the frame was changed manually in the Frames view
   *                     (e.g., by clicking on a frame or selecting the other thread)
   */
  @ApiStatus.Experimental
  default void stackFrameChanged(boolean changedByUser) {
    stackFrameChanged();
  }

  default void beforeSessionResume() {
  }

  default void settingsChanged() {
  }
  
  default void breakpointsMuted(boolean muted) {
  }
}
