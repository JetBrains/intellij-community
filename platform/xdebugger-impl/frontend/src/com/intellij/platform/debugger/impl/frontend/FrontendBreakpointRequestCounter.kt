// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import java.util.concurrent.atomic.AtomicLong

/**
 * Request counter on frontend side.
 */
internal class FrontendBreakpointRequestCounter {
  private val counter = AtomicLong()

  /**
   * Every breakpoint state update should call this method to get the requestId for a request to the backend.
   */
  fun increment(): Long = counter.incrementAndGet()

  /**
   * When an updated state comes from the backend, it should call this method to check whether the update is needed.
   * If the update is not needed, it means that another request was sent in parallel, so another state update is expected to come.
   */
  fun isSuitableUpdate(requestId: Long): Boolean {
    while (true) {
      val currentValue = counter.get()
      if (requestId < currentValue) return false
      if (requestId == currentValue) return true
      if (counter.compareAndSet(currentValue, requestId)) return true
    }
  }

  companion object {
    const val REQUEST_IS_NOT_NEEDED: Long = -1L
  }
}