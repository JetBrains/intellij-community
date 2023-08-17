// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker.AdvanceToken
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong

class SkipListAdvancingPositionTracker(
  initialValue: Long
) : AdvancingPositionTracker {
  private val position = AtomicLong(initialValue)
  private val inflight = ConcurrentSkipListSet<Long>()

  override fun startAdvance(size: Long): AdvanceToken {
    check(size > 0)
    val p = position.getAndAdd(size)
    inflight.add(p)
    return Token(p)
  }

  override fun finishAdvance(token: AdvanceToken) {
    if (!inflight.remove(token.position)) {
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

  private class Token(override val position: Long) : AdvanceToken
}