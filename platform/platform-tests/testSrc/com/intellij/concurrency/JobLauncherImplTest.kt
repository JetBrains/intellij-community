// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
class JobLauncherImplTest {

  @Test
  fun `process queue works with single thread pool`() {
    val tombStone = "tombStone"
    val queue = ArrayBlockingQueue<String>(2)
    queue.add("item")
    queue.add(tombStone)
    val processed = mutableListOf<String>()

    ForkJoinPool(1).use { pool ->
      val result = JobLauncherImpl(pool).processQueue(
        queue,
        ConcurrentLinkedQueue(),
        EmptyProgressIndicator(),
        tombStone,
      ) { item ->
        processed.add(item)
        true
      }

      assertTrue(result)
      assertEquals(listOf("item"), processed)
    }
  }
}
