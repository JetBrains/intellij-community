// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import java.util.concurrent.atomic.AtomicLong


internal class BreakpointRequestCounter {
  private val counter = AtomicLong()

  fun increment(): Long = counter.incrementAndGet()
  fun isSuitableUpdate(count: Long): Boolean {
    while (true) {
      val currentValue = counter.get()
      if (count < currentValue) return false
      if (count == currentValue) return true
      if (counter.compareAndSet(currentValue, count)) return true
    }
  }

  companion object {
    const val REQUEST_IS_NOT_NEEDED: Long = -1L
  }
}