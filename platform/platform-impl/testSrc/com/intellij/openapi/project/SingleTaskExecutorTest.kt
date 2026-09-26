// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

internal class SingleTaskExecutorTest {
  @Test
  fun testChainedSubmissions(): Unit = runBlocking(Dispatchers.IO) {
    val retryCount = 10_000
    val counter = AtomicInteger()
    coroutineScope {
      var launcher: Runnable? = null
      val exec = SingleTaskExecutor {
        if (counter.incrementAndGet() < retryCount) {
          launch {
            waitRandomNs(100_000)
            launcher!!.run()
          }
        }
        waitRandomNs(50_000)
      }

      launcher = Runnable {
        val rawProgressReporter = object : RawProgressReporter {}
        exec.tryStartProcess { progressive ->
          progressive.use {
            it(rawProgressReporter)
          }
        }
      }
      launcher.run()
    }
    Assertions.assertEquals(retryCount, counter.get())
  }

  private fun waitRandomNs(upperBound: Long) {
    val tlr = ThreadLocalRandom.current()
    val random = tlr.nextLong(0, upperBound)
    if (random > 0) {
      LockSupport.parkNanos(random)
    }
  }
}
