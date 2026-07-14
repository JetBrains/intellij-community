// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code
// is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

import com.google.common.base.Ticker
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertSame
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly
import org.junit.Test
import java.util.concurrent.TimeUnit

class GHPushNotificationLookupCacheTest {
  private class FakeTicker : Ticker() {
    private var nanos: Long = 0
    override fun read(): Long = nanos
    fun advanceMs(ms: Long) {
      nanos += TimeUnit.MILLISECONDS.toNanos(ms)
    }
  }

  private val ticker = FakeTicker()

  private fun cache(ttlMs: Long = 1000L, maxEntries: Long = 10L) =
    GHPushNotificationLookupCache(ttlMs = ttlMs, maxEntries = maxEntries, ticker = ticker)

  private fun key(
    server: String = "github.com",
    repositoryPath: String = "owner/repo",
    remoteBranch: String = "feature",
    sourceRevision: String = "refs/heads/feature",
  ) = GHPushNotificationLookupCache.Key(server, repositoryPath, remoteBranch, sourceRevision)

  private fun prs(vararg numbers: Long): List<GHPullRequestRestIdOnly> =
    numbers.map { GHPullRequestRestIdOnly(id = it.toString(), nodeId = "node$it", number = it) }

  @Test
  fun `returns null for missing key`() {
    assertNull(cache().get(key()))
  }

  @Test
  fun `returns stored value within ttl`() {
    val cache = cache()
    val value = prs(1L)
    cache.put(key(), value)

    ticker.advanceMs(999L)
    assertSame(value, cache.get(key()))
  }

  @Test
  fun `expires stored value once ttl elapsed`() {
    val cache = cache()
    cache.put(key(), prs(1L))

    ticker.advanceMs(1000L)
    assertNull(cache.get(key()))
  }

  @Test
  fun `distinguishes entries by every key field`() {
    val cache = cache()
    val value = prs(1L)
    cache.put(key(), value)

    assertNull(cache.get(key(server = "ghe.example.com")))
    assertNull(cache.get(key(repositoryPath = "owner/other")))
    assertNull(cache.get(key(remoteBranch = "other")))
    assertNull(cache.get(key(sourceRevision = "0123abcd")))
    assertSame(value, cache.get(key()))
  }

  @Test
  fun `overwrites value and refreshes ttl for same key`() {
    val cache = cache()
    cache.put(key(), prs(1L))

    ticker.advanceMs(500L)
    val updated = prs(1L, 2L)
    cache.put(key(), updated)

    // The second put reset the write time, so the entry lives until 500 + 1000.
    ticker.advanceMs(999L)
    assertSame(updated, cache.get(key()))
    ticker.advanceMs(1L)
    assertNull(cache.get(key()))
  }

  @Test
  fun `evicts least recently used entry beyond max size`() {
    val cache = cache(ttlMs = 10_000L, maxEntries = 2L)
    cache.put(key(remoteBranch = "a"), prs(1L))
    cache.put(key(remoteBranch = "b"), prs(2L))

    // Touch "a" so "b" becomes the least recently used entry.
    assertEquals(prs(1L), cache.get(key(remoteBranch = "a")))

    cache.put(key(remoteBranch = "c"), prs(3L))

    assertNull(cache.get(key(remoteBranch = "b")))
    assertEquals(prs(1L), cache.get(key(remoteBranch = "a")))
    assertEquals(prs(3L), cache.get(key(remoteBranch = "c")))
  }
}
