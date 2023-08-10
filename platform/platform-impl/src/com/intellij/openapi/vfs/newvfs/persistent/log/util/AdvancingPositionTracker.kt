// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

interface AdvancingPositionTracker {
  fun beginAdvance(size: Long): AdvanceToken
  fun finishAdvance(token: AdvanceToken)

  /**
   * Value of [getReadyPosition] means that every advance from positions in [0, value) has been already finished.
   */
  fun getReadyPosition(): Long
  fun getCurrentAdvancePosition(): Long

  interface AdvanceToken {
    val position: Long
  }
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