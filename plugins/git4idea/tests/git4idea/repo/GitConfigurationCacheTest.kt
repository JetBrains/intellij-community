// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GitConfigurationCacheTest {
  private class TestCache : GitConfigurationCacheBase()

  private data class TestKey(val id: String) : GitConfigKey<String?>

  @Test
  fun `value is computed once and cached`() {
    val cache = TestCache()
    val calls = AtomicInteger()
    val key = TestKey("a")

    val first = cache.computeCachedValue(key) { calls.incrementAndGet(); "value" }
    val second = cache.computeCachedValue(key) { calls.incrementAndGet(); "value2" }

    assertEquals("value", first)
    assertEquals("value", second)
    assertEquals("computeValue should run only once for a cached key", 1, calls.get())
  }

  @Test
  fun `null result is cached`() {
    val cache = TestCache()
    val calls = AtomicInteger()
    val key = TestKey("a")

    val first = cache.computeCachedValue(key) { calls.incrementAndGet(); null }
    val second = cache.computeCachedValue(key) { calls.incrementAndGet(); null }

    assertNull(first)
    assertNull(second)
    assertEquals("a cached null must not trigger recomputation", 1, calls.get())
  }

  @Test
  fun `clearCache forces recompute`() {
    val cache = TestCache()
    val calls = AtomicInteger()
    val key = TestKey("a")

    cache.computeCachedValue(key) { calls.incrementAndGet(); "value" }
    cache.clearCache()
    val actual = cache.computeCachedValue(key) { calls.incrementAndGet(); "value2" }

    assertEquals("value2", actual)
    assertEquals("clearCache should invalidate the cached value", 2, calls.get())
  }

  /**
   * Regression guard for freeze 2069462702761443328 (git4idea welcome-screen clone deadlock).
   *
   * `computeValue()` must run OUTSIDE the [java.util.concurrent.ConcurrentHashMap] bin lock. A worker
   * thread blocks inside its compute lambda while a second thread calls [GitConfigurationCacheBase.clearCache] (which delegates
   * to `ConcurrentHashMap.clear()`). With the fix no bin lock is held during compute, so `clearCache()`
   * finishes promptly and the clearer thread dies. If `computeValue()` were run inside `computeIfAbsent`
   * again, `clearCache()` would block on the reserved bin for as long as the compute lambda runs, so the
   * clearer thread would still be alive — a deterministic failure that does not depend on the compute
   * lambda self-releasing.
   */
  @Test(timeout = 60_000)
  fun `computeValue runs without holding the cache lock`() {
    val cache = TestCache()
    val key = TestKey("a")

    val computeStarted = CountDownLatch(1)
    val releaseWorker = CountDownLatch(1)

    val worker = Thread({
                          cache.computeCachedValue(key) {
                            computeStarted.countDown()
                            // Stay inside computeValue() while another thread mutates the cache concurrently.
                            releaseWorker.await(30, TimeUnit.SECONDS)
                            "value"
                          }
                        }, "git-config-cache-test-worker").apply { isDaemon = true }
    worker.start()

    assertTrue("compute lambda should start", computeStarted.await(20, TimeUnit.SECONDS))

    val clearer = Thread({ cache.clearCache() }, "git-config-cache-test-clearer").apply { isDaemon = true }
    clearer.start()
    try {
      clearer.join(TimeUnit.SECONDS.toMillis(10))
      assertFalse("clearCache() must not block behind the running compute lambda", clearer.isAlive)
    }
    finally {
      releaseWorker.countDown()
      worker.join(TimeUnit.SECONDS.toMillis(20))
      clearer.join(TimeUnit.SECONDS.toMillis(20))
    }
  }
}
