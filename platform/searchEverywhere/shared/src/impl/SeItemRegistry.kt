// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeSession
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Identifies an [SeItem] in the [SeItemRegistry] of one process.
 */
@ApiStatus.Internal
@Serializable
data class SeItemId(val sessionId: String, val registryId: Long, val ordinal: Long)

/**
 * Keeps the items of the open Search Everywhere sessions of this process.
 *
 * An [SeItem] is not serializable, so [com.intellij.platform.searchEverywhere.SeItemData] carries an [SeItemId].
 * [closeSession] removes every item of the session. After that, [register] returns `null` for the session.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class SeItemRegistry {
  private val registryId: Long = Random.nextLong()
  private val lastOrdinal = AtomicLong()
  private val itemsBySessionId = ConcurrentHashMap<String, ConcurrentHashMap<Long, SeItem>>()

  private val lock = Any()

  // Guarded by `lock`. The oldest ids go first when the set is full.
  private val closedSessionIds = LinkedHashSet<String>()

  /** Returns `null` when the session is closed. */
  fun register(session: SeSession, item: SeItem): SeItemId? {
    val sessionId = session.id
    val ordinal = lastOrdinal.incrementAndGet()
    synchronized(lock) {
      if (sessionId in closedSessionIds) return null
      itemsBySessionId.computeIfAbsent(sessionId) { ConcurrentHashMap() }[ordinal] = item
    }
    return SeItemId(sessionId, registryId, ordinal)
  }

  /** Returns `null` when the session is closed or when another process made the [id]. Does not block. */
  fun find(id: SeItemId): SeItem? {
    if (id.registryId != registryId) return null
    return itemsBySessionId[id.sessionId]?.get(id.ordinal)
  }

  fun isClosed(session: SeSession): Boolean {
    val sessionId = session.id
    synchronized(lock) {
      return sessionId in closedSessionIds
    }
  }

  /** Removes every item of the session. A second call does nothing. */
  fun closeSession(session: SeSession) {
    val sessionId = session.id
    synchronized(lock) {
      if (closedSessionIds.add(sessionId) && closedSessionIds.size > MAX_CLOSED_SESSION_COUNT) {
        closedSessionIds.remove(closedSessionIds.first())
      }
      itemsBySessionId.remove(sessionId)
    }
  }

  companion object {
    private const val MAX_CLOSED_SESSION_COUNT = 1000

    fun getInstance(): SeItemRegistry = service()
  }
}
