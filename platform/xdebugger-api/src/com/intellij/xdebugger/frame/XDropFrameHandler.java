// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

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
   * Checks is Drop Frame available.
   *
   * @param frame frame to be dropped
   * @return true if frame can be dropped
   */
  boolean canDrop(@NotNull XStackFrame frame);

  /**
   * Drops a frame.
   *
   * @param frame frame to be dropped
   */
  void drop(@NotNull XStackFrame frame);

}
