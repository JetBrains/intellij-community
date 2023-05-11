// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong

class SkipListAdvancingPositionTracker(
  initialValue: Long
) : AdvancingPositionTracker {
  private val position = AtomicLong(initialValue)
  private val inflight = ConcurrentSkipListSet<Long>()

  override fun beginAdvance(size: Long): Long {
    val p = position.getAndAdd(size)
    inflight.add(p)
    return p
  }

  override fun finishAdvance(fromPosition: Long) {
    if (!inflight.remove(fromPosition)) {
      throw IllegalStateException("position isn't present in the data structure")
    }
  }

  override fun getReadyPosition(): Long {
    val pos = position.get()
    return inflight.minOrNull() ?: pos
  }

  override fun getCurrentAdvancePosition(): Long {
    return position.get()
  }
}