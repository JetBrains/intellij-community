// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code
// is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

import com.google.common.base.Ticker
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly
import java.util.concurrent.TimeUnit

/**
 * Small, time- and size-bounded cache that de-duplicates the "existing pull requests" lookup that
 * [GHPushNotificationCustomizer] performs on every successful push.
 *
 * Without it, pushing the same source revision to the same remote branch (duplicate push events,
 * retries, or re-pushes of an unchanged head) issues a fresh `GET /repos/{}/{}/pulls` request every
 * time. Repeated across many clients this is a significant, avoidable REST load.
 *
 * The cache key includes the pushed [Key.sourceRevision]: when a push carries a concrete commit
 * hash (detached/revision push) a new revision is a natural cache miss, so genuinely new state is
 * always re-fetched. For ordinary branch pushes the revision is the branch ref (stable between
 * pushes), so entries additionally expire a fixed time after they are written to bound staleness.
 *
 * Backed by a Guava [Cache], which is thread-safe and evicts by size using an approximately
 * least-recently-used policy.
 */
internal class GHPushNotificationLookupCache @VisibleForTesting constructor(
  ttlMs: Long,
  maxEntries: Long,
  ticker: Ticker,
) {
  constructor() : this(DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES, Ticker.systemTicker())

  internal data class Key(
    val server: String,
    val repositoryPath: String,
    val remoteBranch: String,
    val sourceRevision: String,
  )

  private val cache: Cache<Key, List<GHPullRequestRestIdOnly>> =
    CacheBuilder.newBuilder()
      .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
      .maximumSize(maxEntries)
      // single segment so size-based eviction is strictly least-recently-used; contention here is
      // negligible (one lookup per push).
      .concurrencyLevel(1)
      .ticker(ticker)
      .build()

  /**
   * Returns the cached lookup result for [key], or `null` if absent or expired.
   *
   * The returned list is the exact instance stored in the cache (not a copy) and MUST NOT be
   * mutated; mutating it would corrupt the entry returned to later callers.
   */
  fun get(key: Key): List<GHPullRequestRestIdOnly>? = cache.getIfPresent(key)

  /**
   * Stores [value] for [key]. The stored list is retained by reference and MUST NOT be mutated
   * after being put.
   */
  fun put(key: Key, value: List<GHPullRequestRestIdOnly>) = cache.put(key, value)

  companion object {
    val DEFAULT_TTL_MS: Long = TimeUnit.MINUTES.toMillis(1)
    const val DEFAULT_MAX_ENTRIES: Long = 100
  }
}
