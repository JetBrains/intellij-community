// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-breakpoint request counter used by both the frontend and the backend
 * to track request IDs so that concurrent updates for different breakpoints
 * do not suppress each other's state changes.
 */
@ApiStatus.Internal
class BreakpointRequestCounter {
  // Global monotonic counter for generating unique requestIds (used by frontend)
  private val globalCounter = AtomicLong()

  // Per-breakpoint tracking of the latest requestId
  private val perBreakpoint = ConcurrentHashMap<XBreakpointId, AtomicLong>()

  /**
   * Generates a new unique requestId and records it as the latest for [breakpointId].
   * Used by the frontend when sending a state update request to the backend.
   */
  fun nextRequestId(breakpointId: XBreakpointId): Long {
    val requestId = globalCounter.incrementAndGet()
    advance(breakpointId, requestId)
    return requestId
  }

  /**
   * Returns the latest recorded requestId for [breakpointId], or 0 if none.
   * Used by the backend to stamp outgoing DTO state.
   */
  fun getRequestId(breakpointId: XBreakpointId): Long {
    return perBreakpoint[breakpointId]?.get() ?: 0L
  }

  /**
   * Checks whether a state update with [requestId] should be applied for [breakpointId].
   * Returns `true` if [requestId] is the current or a newer request.
   * Used by the frontend when receiving state updates from the backend.
   */
  fun isSuitableUpdate(breakpointId: XBreakpointId, requestId: Long): Boolean {
    val counter = perBreakpoint[breakpointId] ?: return true
    while (true) {
      val current = counter.get()
      if (requestId < current) return false
      if (requestId == current) return true
      if (counter.compareAndSet(current, requestId)) return true
    }
  }

  /**
   * Marks [requestId] as completed for [breakpointId].
   * Returns `true` if this caused a state change (i.e., [requestId] is strictly newer).
   * Returns `false` for local calls ([requestId] < 0) or already-processed requests.
   * Used by the backend when processing requests from the frontend.
   */
  fun setRequestCompleted(breakpointId: XBreakpointId, requestId: Long): Boolean {
    if (requestId < 0) return false
    return advance(breakpointId, requestId)
  }

  fun remove(breakpointId: XBreakpointId) {
    perBreakpoint.remove(breakpointId)
  }

  /**
   * Tries to advance the counter for [breakpointId] to [requestId].
   * Returns `true` if the counter was actually changed ([requestId] > current).
   */
  private fun advance(breakpointId: XBreakpointId, requestId: Long): Boolean {
    val counter = perBreakpoint.computeIfAbsent(breakpointId) { AtomicLong() }
    while (true) {
      val current = counter.get()
      if (requestId <= current) return false
      if (counter.compareAndSet(current, requestId)) return true
    }
  }

  companion object {
    const val REQUEST_IS_NOT_NEEDED: Long = -1L
  }
}
