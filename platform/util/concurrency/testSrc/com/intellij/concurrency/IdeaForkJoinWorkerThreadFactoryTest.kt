// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit

internal class IdeaForkJoinWorkerThreadFactoryTest {
  @Test
  fun `worker names remain unique in pools larger than 64 threads`() {
    val names = collectWorkerNames(IdeaForkJoinWorkerThreadFactory(), 128)
    assertEquals(128, names.size)
  }

  @Test
  fun `worker numbers are reused after termination`() {
    val factory = IdeaForkJoinWorkerThreadFactory()
    assertEquals(collectWorkerNames(factory, 1), collectWorkerNames(factory, 1))
  }

  private fun collectWorkerNames(factory: IdeaForkJoinWorkerThreadFactory, workerCount: Int): Set<String> {
    val names = ConcurrentHashMap.newKeySet<String>()
    val started = CountDownLatch(workerCount)
    val release = CountDownLatch(1)
    ForkJoinPool(workerCount, factory, null, false).use { pool ->
      try {
        repeat(workerCount) {
          pool.execute {
            names.add(Thread.currentThread().name)
            started.countDown()
            release.await()
          }
        }
        assertTrue(started.await(10, TimeUnit.SECONDS), "Workers did not start")
      }
      finally {
        release.countDown()
      }
    }
    return names
  }
}
