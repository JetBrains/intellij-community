// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code
// is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly
import java.util.concurrent.TimeUnit

/**
 * Small time-bounded cache that de-duplicates the "existing pull requests" lookup that
 * [GHPushNotificationCustomizer] performs on every successful push.
 *
 * Without it, pushing the same source revision to the same remote branch (duplicate push
 * events, retries, or re-pushes of an unchanged head) issues a fresh
 * `GET /repos/{}/{}/pulls` request every time. Repeated across many clients this is a
 * significant, avoidable REST load.
 *
 * The cache key includes the pushed [Key.sourceRevision]: when a push carries a concrete
 * commit hash (detached/revision push) a new revision is a natural cache miss, so genuinely
 * new state is always re-fetched. For ordinary branch pushes the revision is the branch ref
 * (stable between pushes), so entries additionally expire after [ttlMs] to bound staleness.
 *
 * All access is synchronized; the customizer is a project-level extension whose
 * `getActions` is a suspend function that may be invoked concurrently.
 */
internal class GHPushNotificationLookupCache @VisibleForTesting constructor(
  private val ttlMs: Long,
  private val maxEntries: Int,
) {
  constructor() : this(DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES)

  internal data class Key(
    val server: String,
    val repositoryPath: String,
    val remoteBranch: String,
    val sourceRevision: String,
  )

  private class Entry(val value: List<GHPullRequestRestIdOnly>, val timestampMs: Long)

  private val entries = LinkedHashMap<Key, Entry>()

  /**
   * Returns the cached lookup result for [key] if present and not older than [ttlMs], otherwise `null`.
   * Expired entries are evicted on access.
   */
  @Synchronized
  fun get(key: Key, nowMs: Long): List<GHPullRequestRestIdOnly>? {
    val entry = entries[key] ?: return null
    if (isExpired(entry, nowMs)) {
      entries.remove(key)
      return null
    }
    return entry.value
  }

  /**
   * Stores [value] for [key]. Expired entries are dropped and the cache is bounded to
   * [maxEntries] by evicting the least-recently stored keys.
   */
  @Synchronized
  fun put(key: Key, value: List<GHPullRequestRestIdOnly>, nowMs: Long) {
    entries.entries.removeAll { isExpired(it.value, nowMs) }
    // Re-insert so the freshest key moves to the end of the insertion-ordered map.
    entries.remove(key)
    entries[key] = Entry(value, nowMs)
    val iterator = entries.keys.iterator()
    while (entries.size > maxEntries && iterator.hasNext()) {
      iterator.next()
      iterator.remove()
    }
  }

  private fun isExpired(entry: Entry, nowMs: Long): Boolean = nowMs - entry.timestampMs >= ttlMs

  companion object {
    val DEFAULT_TTL_MS: Long = TimeUnit.MINUTES.toMillis(1)
    const val DEFAULT_MAX_ENTRIES: Int = 100
  }
}
