// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker.AdvanceToken

interface AdvancingPositionTracker {
  /**
   * @return an [AdvanceToken], which contains an [AdvanceToken.position] pointing to the start of the allocated range `[position, position+size)`.
   * Following advances are guaranteed to have [AdvanceToken.position] at least `position+size`, meaning that the range `[position, position+size)`
   * is allocated only once. Acquired [AdvanceToken] must be [finished][finishAdvance] after processing is finished.
   */
  fun beginAdvance(size: Long): AdvanceToken
  fun finishAdvance(token: AdvanceToken)

  /**
   * Value of [getReadyPosition] means that every advance from positions in `[0, value)` has been already finished.
   */
  fun getReadyPosition(): Long

  /**
   * @return position that is yet to be allocated via [beginAdvance]
   */
  fun getCurrentAdvancePosition(): Long

  interface AdvanceToken {
    val position: Long
  }
}

interface CloseableAdvancingPositionTracker : AdvancingPositionTracker {
  /**
   * @see [AdvancingPositionTracker.beginAdvance]
   * @throws IllegalStateException in case [CloseableAdvancingPositionTracker] is closed
   */
  override fun beginAdvance(size: Long): AdvanceToken

  /**
   * Forbids new advance attempts
   * @see [beginAdvance]
   */
  fun close()
}

/**
 * @param body in case exception is thrown, it is expected that state is correct & consistent anyway
 */
inline fun <R> AdvancingPositionTracker.trackAdvance(size: Long, body: (position: Long) -> R): R {
  val token = beginAdvance(size)
  return try {
    body(token.position)
  }
  finally {
    finishAdvance(token)
  }
}