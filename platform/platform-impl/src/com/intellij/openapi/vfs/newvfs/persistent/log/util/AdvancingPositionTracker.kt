// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong

class AdvancingPositionTracker(
  initialValue: Long
) {
  private val position = AtomicLong(initialValue)
  private val inflight = ConcurrentSkipListSet<Long>()

  fun begin(amount: Long): Long {
    val p = position.getAndAdd(amount)
    inflight.add(p)
    return p
  }

  fun finish(position: Long) {
    if (!inflight.remove(position)) {
      throw IllegalStateException("position isn't present in the data structure")
    }
  }

  /**
   * @param body in case exception is thrown, it is expected that state is correct & consistent anyway
   */
  inline fun <R> track(amount: Long, body: (position: Long) -> R): R {
    val pos = begin(amount)
    return try {
      body(pos)
    } finally {
      finish(pos)
    }
  }

  fun getMinInflightPosition(): Long {
    val pos = position.get()
    return inflight.minOrNull() ?: pos
  }
}