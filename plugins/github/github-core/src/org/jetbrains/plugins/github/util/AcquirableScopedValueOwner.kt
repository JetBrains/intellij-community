// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.async.launchNow
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*

class AcquirableScopedValueOwner<out T : Any>(
  parentCs: CoroutineScope,
  private val valueFactory: CoroutineScope.() -> T,
) {
  private val cs = parentCs.childScope(javaClass.name)

  private var subscriptionCount = 0
  private var valueAndCs: Pair<T, CoroutineScope>? = null
  val value: T? get() = valueAndCs?.first

  init {
    if (!cs.isActive) error("Already cancelled")

    cs.launchNow {
      try {
        awaitCancellation()
      }
      finally {
        synchronized(this) {
          valueAndCs?.second?.cancel("Parent scope is cancelled")
          valueAndCs = null
        }
      }
    }
  }

  fun acquireValue(borrowCs: CoroutineScope): T =
    synchronized(this) {
      if (!cs.isActive) error("Already cancelled")

      subscriptionCount += 1
      borrowCs.launchNow {
        try {
          awaitCancellation()
        }
        finally {
          releaseValue()
        }
      }

      value ?: run {
        val newCs = cs.childScope("value")
        valueFactory(newCs) to newCs
      }.also { this.valueAndCs = it }.first
    }

  private fun releaseValue() {
    synchronized(this) {
      subscriptionCount -= 1
      if (subscriptionCount <= 0) {
        valueAndCs?.second?.cancel("All host borrowing are cancelled")
        valueAndCs = null
      }
    }
  }
}