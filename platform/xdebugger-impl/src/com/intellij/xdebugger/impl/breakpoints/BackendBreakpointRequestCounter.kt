// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong

/**
 * Backend request counter.
 * It stored the last completed request id that came from the frontend.
 */
@ApiStatus.Internal
class BackendBreakpointRequestCounter {
  private val lastCompletedRequestId = AtomicLong()

  fun getRequestId(): Long {
    return lastCompletedRequestId.get()
  }

  /**
   * @return true if the update caused the id change
   */
  fun setRequestCompleted(requestId: Long): Boolean {
    while (true) {
      val current = lastCompletedRequestId.get()
      if (requestId <= current) return false
      if (lastCompletedRequestId.compareAndSet(current, requestId)) return true
    }
  }
}
