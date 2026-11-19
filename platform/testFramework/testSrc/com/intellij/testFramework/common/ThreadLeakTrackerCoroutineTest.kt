// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class ThreadLeakTrackerCoroutineTest {
  @Test
  fun `ignores a coroutine that resumed on another thread`() {
    val before = ThreadLeakTracker.getThreads() + ("worker-1 @fixture#1000001" to Thread.currentThread())
    withBusyThread("worker-2 @fixture#1000001") {
      ThreadLeakTracker.checkLeak(before)
    }
  }

  @Test
  fun `reports a new coroutine on a thread`() {
    val before = ThreadLeakTracker.getThreads() + ("worker-1 @fixture#1000002" to Thread.currentThread())
    withBusyThread("worker-2 @fixture#1000003") {
      assertThrows<AssertionError> {
        ThreadLeakTracker.checkLeak(before)
      }
    }
  }

  private fun withBusyThread(name: String, action: () -> Unit) {
    val release = CountDownLatch(1)
    val busyThread = thread(name = name) { release.await() }
    try {
      action()
    }
    finally {
      release.countDown()
      busyThread.join()
    }
  }
}
