// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.url.ConcurrentVirtualFileUrlManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


class VirtualFileUrlManagerConcurrencyStressTest {
  @Test
  fun `concurrent interning and lookup stay consistent`() {
    val manager = ConcurrentVirtualFileUrlManager()
    val threadCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    val iterationsPerThread = 20_000
    // Overlapping, forward-slash paths (so url round-trips to the input) that share ancestors,
    // maximizing contention on common tree nodes.
    val paths = (0 until 64).map { "/root/dir${it % 8}/file$it" }

    val startLatch = CountDownLatch(threadCount)
    val firstError = AtomicReference<Throwable?>()
    val pool = Executors.newFixedThreadPool(threadCount)
    try {
      repeat(threadCount) { threadIndex ->
        pool.execute {
          try {
            startLatch.countDown()
            startLatch.await() // release all threads at once
            val random = Random(threadIndex.toLong())
            repeat(iterationsPerThread) {
              val path = paths[random.nextInt(paths.size)]
              val first = manager.getOrCreateFromUrl(path)
              val second = manager.getOrCreateFromUrl(path)

              assertEquals(first, second)
              assertEquals(path, first.url)
              val found = manager.findByUrl(path)
              assertNotNull(found)
              assertEquals(first, found)
            }
          }
          catch (t: Throwable) {
            firstError.compareAndSet(null, t)
          }
        }
      }
    }
    finally {
      pool.shutdown()
    }
    val finishedInTime = pool.awaitTermination(5, TimeUnit.MINUTES)
    assertTrue(finishedInTime) { "Stress test timed out. Thread dump:\n${dumpAllStacks()}" }
    firstError.get()?.let { throw AssertionError("Concurrent access produced an inconsistency", it) }
  }

  private fun dumpAllStacks(): String =
    Thread.getAllStackTraces().entries.joinToString("\n\n") { (thread, stack) ->
      "\"${thread.name}\" ${thread.state}\n" + stack.joinToString("\n") { "\tat $it" }
    }
}
