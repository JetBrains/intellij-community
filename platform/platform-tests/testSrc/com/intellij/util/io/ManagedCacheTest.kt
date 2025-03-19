// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestLoggerFactory.TestLoggerAssertionError
import com.intellij.testFramework.rethrowLoggedErrorsIn
import com.intellij.util.io.cache.ManagedPersistentCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ManagedCacheTest {

  @TempDir
  lateinit var tempDir: Path

  init {
    // awaitCancellationAndInvoke requires ApplicationManager
    TestApplicationManager.getInstance()
  }

  @Test
  fun testReadWrite() = runBlocking {
    withCache { cache ->
      cache.put(1, 10)
      assertEquals(10, cache.get(1), "written value expected to be read")
    }
  }

  @Test
  fun testRemove() = runBlocking {
    withCache { cache ->
      cache.put(1, 10)
      cache.remove(1)
      assertNull(cache.get(1), "value expected to be removed")
    }
  }

  @Test
  fun testClose() = runBlocking {
    val cache = createCache()
    cache.put(1, 10)
    cache.close0()
    assertTrue(cache.isClosed(), "cache expected to be closed")
    assertNull(cache.get(1), "value expected to be null when cache is closed")
  }

  @Test
  fun testRestore() = runBlocking {
    withCache { cache ->
      cache.put(1, 10)
    }
    withCache { cache ->
      assertEquals(10, cache.get(1), "value expected to be read after restoration")
    }
  }

  @Test
  fun testOpenTwiceFails() = runBlocking {
    withCache {
      rethrowLoggedErrorsIn {
        val exception = assertThrows<TestLoggerAssertionError>("second cache expected to not be opened") {
          createCache()
        }
        // Storage is already registered
        assertTrue(exception.cause is IllegalStateException)
      }
    }
  }

  @Test
  fun testReadWriteMany() = runBlocking {
    val count = 1000
    withCache { cache ->
      repeat(count) { i ->
        cache.put(i, i)
      }
    }
    withCache { cache ->
      assertEquals(cache.size(), count)
      repeat(count) { i ->
        assertEquals(cache.get(i), i)
      }
    }
  }

  @Test
  fun testRestoreMany() = runBlocking {
    val count = 500
    repeat(count) { i ->
      withCache { cache ->
        cache.put(i, i)
      }
      withCache { cache ->
        assertEquals(i, cache.get(i), "value expected to be read after restoration")
      }
    }
  }

  @Test
  fun testCloseOnCancellation(): Unit = runBlocking {
    val coroutineScope = createCoroutineScope()
    val cache = createCache(coroutineScope)
    cache.put(1, 10)
    coroutineScope.coroutineContext[Job]?.cancelAndJoin()
    assertTrue(cache.isClosed(), "cache expected to be closed on scope cancellation")
    withCache { cache0 ->
      assertEquals(10, cache0.get(1), "value expected to be read after restoration")
    }
  }

  @Test
  fun testReadNullWhenCorrupted() = runBlocking {
    withCache { cache ->
      cache.put(1, 10)
    }
    withCache { cache ->
      corruptCache()
      cache.put(2, 20)
      assertFalse(cache.isClosed())
      assertNull(cache.get(1), "value expected to be null when cache is corrupted")
      assertNull(cache.get(2), "value expected to be null when cache is corrupted")
    }
  }

  @Test
  fun testCloseOnIOExceptions() = runBlocking {
    withCache { cache ->
      cache.put(1, 10)
    }
    withCache { cache ->
      corruptCache()
      assertFalse(cache.isClosed())
      repeat(100) { // >IO_ERRORS_THRESHOLD
        cache.put(2, 20)
      }
      assertTrue(cache.isClosed(), "cache expected to be closed when error count exceeds threshold")
    }
  }

  private fun createCache(coroutineScope: CoroutineScope = createCoroutineScope()): ManagedPersistentCache<Int, Int> {
    return ManagedPersistentCache(
      "test-cache",
      mapBuilder(),
      coroutineScope,
      closeOnAppShutdown=true,
      cleanDirOnFailure=true,
    )
  }

  private suspend fun withCache(action: suspend (ManagedPersistentCache<Int, Int>) -> Unit) {
    val coroutineScope = createCoroutineScope()
    val cache = createCache(coroutineScope)
    try {
      action.invoke(cache)
    } finally {
      coroutineScope.coroutineContext[Job]!!.cancelAndJoin()
    }
  }

  private fun createCoroutineScope(): CoroutineScope {
    @Suppress("SSBasedInspection")
    return CoroutineScope(EmptyCoroutineContext)
  }

  private fun corruptCache() {
    IOUtil.deleteAllFilesStartingWith(mapBuilder().file)
  }

  private fun mapBuilder(): PersistentMapBuilder<Int, Int> {
    return PersistentMapBuilder.newBuilder(
      tempDir.resolve("test-cache"),
      EnumeratorIntegerDescriptor.INSTANCE,
      EnumeratorIntegerDescriptor.INSTANCE
    )
  }
}
