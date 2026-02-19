// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface that represents a process that kills itself, for example a remote process, that can't be killed by the local OS.
 *
 * The process that can't be killed by local OS should implement this interface for OSProcessHandler to work correctly.
 * The process implementing this interface is supposed to be destroyed with Process.destroy() method.
 *
 * Also implementations of ProcessHandler should take in account that process can implement this interface
 */
public interface SelfKiller {
  /**
   * Send some signal to the process that indicates some soft termination. For example, SIGINT.
   * <p>
   * The method returns false if an attempt to destroy the process gracefully failed, or if it's not possible to destroy the process
   * gracefully at all and there was no actual attempt. In that case, the caller should try to destroy the process in a less gentle way
   * using {@link Process#destroy()}.
   */
  @ApiStatus.Experimental
  default boolean tryDestroyGracefully() {
    return false;
  }
}
