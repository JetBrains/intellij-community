// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

interface AdvancingPositionTracker {
  fun beginAdvance(amount: Long): Long
  fun finishAdvance(fromPosition: Long)
  fun getReadyPosition(): Long
  fun getCurrentAdvancePosition(): Long
}

/**
 * @param body in case exception is thrown, it is expected that state is correct & consistent anyway
 */
inline fun <R> AdvancingPositionTracker.trackAdvance(size: Long, body: (position: Long) -> R): R {
  val pos = beginAdvance(size)
  return try {
    body(pos)
  } finally {
    finishAdvance(pos)
  }
}