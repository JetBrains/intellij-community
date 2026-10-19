// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

class FreezeService(val coroutineScope: CoroutineScope) {
  private val gate = AtomicBoolean(false)

  /** @param maxDuration the freeze stops after this time even without [stopFreeze]. */
  suspend fun startEdtFreeze(maxDuration: Duration = 3.minutes) {
    val awaiter = CompletableDeferred<Unit>()
    coroutineScope.launch(Dispatchers.EDT) {
      thisLogger().info("EDT freeze started")
      awaiter.complete(Unit)
      val deadline = TimeSource.Monotonic.markNow() + maxDuration
      while (!gate.get() && isActive) {
        if (deadline.hasPassedNow()) {
          thisLogger().error("EDT freeze stopped after $maxDuration without stopFreeze()")
          break
        }
        Thread.sleep(100)
      }
      thisLogger().info("EDT freeze completed")
    }
    awaiter.await() // Await until the EDT is really frozen
  }

  fun stopFreeze() {
    gate.set(true)
  }
}