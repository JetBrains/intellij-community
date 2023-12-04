// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class ManagedCacheTestBase {

  @TempDir lateinit var tempDir: Path

  protected abstract fun createCache(): ManagedCache<Int, Int>

  @Test
  fun testReadWrite() {
    withCache { cache ->
      cache[1] = 10
      assertEquals(10, cache[1], "written value expected to be read")
    }
  }

  @Test
  fun testRemove() {
    withCache { cache ->
      cache[1] = 10
      cache.remove(1)
      assertNull(cache[1], "value expected to be removed")
    }
  }

  @Test
  fun testForce() {
    withCache { cache ->
      cache[1] = 10
      cache.force()
      assertEquals(10, cache[1])
    }
  }

  @Test
  fun testClose() {
    withCache { cache ->
      cache[1] = 10
      cache.close()
      assertTrue(cache.isClosed(), "cache expected to be closed")
      assertNull(cache[1], "value expected to be null when cache is closed")
    }
  }

  @Test
  fun testRestore() {
    withCache { cache ->
      cache[1] = 10
    }
    withCache { cache ->
      assertEquals(10, cache[1], "value expected to be read after restoration")
    }
  }

  @Test
  fun testOpenTwiceFails() {
    withCache {
      assertThrows<TestLoggerFactory.TestLoggerAssertionError>("second cache expected to not be opened") {
        createCache()
      }
    }
  }

  @Test
  fun testCloseOnCancellation() {
    TestApplicationManager.getInstance() // awaitCancellationAndInvoke requires ApplicationManager
    withCache { cache ->
      cache[1] = 10
      runBlocking {
        val job = launch {
          cache.forceOnTimer(500)
        }
        delay(1_000) // wait for awaitCancellationAndInvoke invocation before cancel
        job.cancel()
      }
      assertTrue(cache.isClosed(), "cache expected to be closed on scope cancellation")
    }
    withCache { cache ->
      assertEquals(10, cache[1], "value expected to be read after restoration")
    }
  }

  protected fun withCache(action: (ManagedCache<Int, Int>) -> Unit) {
    val cache = createCache()
    try {
      action.invoke(cache)
    } finally {
      cache.close()
    }
  }

  protected fun corruptCache() {
    IOUtil.deleteAllFilesStartingWith(mapBuilder().file)
  }

  protected fun mapBuilder(): PersistentMapBuilder<Int, Int> {
    return PersistentMapBuilder.newBuilder(
      tempDir.resolve("test-cache"),
      EnumeratorIntegerDescriptor.INSTANCE,
      EnumeratorIntegerDescriptor.INSTANCE
    )
  }
}
