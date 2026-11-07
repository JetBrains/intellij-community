// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * In unit-test mode, [BackgroundStateInput] computes at once on the EDT, and in the background on another thread.
 */
@TestApplication
internal class BackgroundStateInputTest {
  /**
   * A computation at once cancels a background computation that runs, so the older result never reaches the update.
   */
  @Test
  fun testAComputationAtOnceCancelsARunningBackgroundComputation(): Unit = timeoutRunBlocking {
    val scope = childScope("BackgroundStateInputTest")
    val backgroundStarted = CompletableDeferred<Unit>()
    val backgroundRelease = CountDownLatch(1)
    val computations = AtomicInteger()
    val updates = Collections.synchronizedList(ArrayList<Int>())
    val input = BackgroundStateInput(
      scope = scope,
      compute = {
        val computation = computations.incrementAndGet()
        if (computation == 1) {
          backgroundStarted.complete(Unit)
          backgroundRelease.await()
        }
        computation
      },
      update = { updates.add(it) },
    )
    try {
      withContext(Dispatchers.Default) {
        input.recalculate()
      }
      backgroundStarted.await()
      withContext(Dispatchers.EDT) {
        input.recalculate()
      }
      backgroundRelease.countDown()
      scope.coroutineContext.job.children.toList().joinAll()

      assertEquals(listOf(2), updates)
    }
    finally {
      backgroundRelease.countDown()
      scope.cancel()
    }
  }
}
