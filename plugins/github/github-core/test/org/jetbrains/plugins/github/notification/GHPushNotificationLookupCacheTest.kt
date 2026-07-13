// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code
// is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertSame
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly
import org.junit.Test

class GHPushNotificationLookupCacheTest {
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
    val cache = GHPushNotificationLookupCache(ttlMs = 1000L, maxEntries = 10)
    assertNull(cache.get(key(), nowMs = 0L))
  }

  @Test
  fun `returns stored value within ttl`() {
    val cache = GHPushNotificationLookupCache(ttlMs = 1000L, maxEntries = 10)
    val value = prs(1L)
    cache.put(key(), value, nowMs = 0L)

    assertSame(value, cache.get(key(), nowMs = 999L))
  }

  @Test
  fun `expires stored value once ttl elapsed`() {
    val cache = GHPushNotificationLookupCache(ttlMs = 1000L, maxEntries = 10)
    cache.put(key(), prs(1L), nowMs = 0L)

    assertNull(cache.get(key(), nowMs = 1000L))
  }

  @Test
  fun `distinguishes entries by every key field`() {
    val cache = GHPushNotificationLookupCache(ttlMs = 1000L, maxEntries = 10)
    val value = prs(1L)
    cache.put(key(), value, nowMs = 0L)

    assertNull(cache.get(key(server = "ghe.example.com"), nowMs = 0L))
    assertNull(cache.get(key(repositoryPath = "owner/other"), nowMs = 0L))
    assertNull(cache.get(key(remoteBranch = "other"), nowMs = 0L))
    assertNull(cache.get(key(sourceRevision = "0123abcd"), nowMs = 0L))
    assertSame(value, cache.get(key(), nowMs = 0L))
  }

  @Test
  fun `overwrites value and refreshes timestamp for same key`() {
    val cache = GHPushNotificationLookupCache(ttlMs = 1000L, maxEntries = 10)
    cache.put(key(), prs(1L), nowMs = 0L)
    val updated = prs(1L, 2L)
    cache.put(key(), updated, nowMs = 500L)

    // Still present at now=1499 because the second put refreshed the timestamp to 500.
    assertSame(updated, cache.get(key(), nowMs = 1499L))
    assertNull(cache.get(key(), nowMs = 1500L))
  }

  @Test
  fun `evicts oldest entries beyond max size`() {
    val cache = GHPushNotificationLookupCache(ttlMs = 10_000L, maxEntries = 2)
    cache.put(key(remoteBranch = "a"), prs(1L), nowMs = 0L)
    cache.put(key(remoteBranch = "b"), prs(2L), nowMs = 1L)
    cache.put(key(remoteBranch = "c"), prs(3L), nowMs = 2L)

    assertNull(cache.get(key(remoteBranch = "a"), nowMs = 3L))
    assertEquals(prs(2L), cache.get(key(remoteBranch = "b"), nowMs = 3L))
    assertEquals(prs(3L), cache.get(key(remoteBranch = "c"), nowMs = 3L))
  }
}
