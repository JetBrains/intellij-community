// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XDebugProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this class and return its instance from {@link XDebugProcess#getDropFrameHandler()} to support
 * Drop Frame action
 */
@ApiStatus.Experimental
public interface XDropFrameHandler {

  /**
   * @deprecated Use {@link #canDropFrame(XStackFrame)} instead.
   */
  @Deprecated
  default boolean canDrop(@NotNull XStackFrame ignoredFrame) {
    throw new AbstractMethodError();
  }

  /**
   * Checks is Drop Frame available.
   *
   * @param frame frame to be dropped
   * @return {@code ThreeState.YES} if frame can be dropped. Can return {@code ThreeState.UNSURE} if it is not known yet
   * (e.g., requires other frames computation).
   */
  default ThreeState canDropFrame(@NotNull XStackFrame frame) {
    return ThreeState.fromBoolean(canDrop(frame));
  }

  /**
   * Drops a frame.
   *
   * @param frame frame to be dropped
   */
  void drop(@NotNull XStackFrame frame);
}
