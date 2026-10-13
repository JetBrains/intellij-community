// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.SeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the open backend sessions of Search Everywhere.
 *
 * The frontend makes the session id. The first call that needs the providers opens the session with [openIfNotClosed].
 * The session stays open until one of these events:
 * - the frontend calls [close],
 * - the scope of [openIfNotClosed] ends, for example on the end of the client session, the project close, or the plugin unload.
 *
 * A closed session never opens again, also when [close] comes before the first call.
 * The RPC connection does not control the session, so the session survives a reconnect.
 * [onClose] runs once for each session that was open.
 */
@ApiStatus.Internal
class SeBackendSessions(
  private val closedSessionLimit: Int = MAX_CLOSED_SESSION_COUNT,
  private val onClose: (SeSession) -> Unit,
) {
  // Guards every change of the collections. isOpen() reads `jobsBySessionId` without it.
  private val mutex = Mutex()

  private val jobsBySessionId = ConcurrentHashMap<String, Job>()

  // Guarded by `mutex`. The oldest ids go first when the set is full.
  private val closedSessionIds = LinkedHashSet<String>()

  /**
   * Opens the [session] for the life of [scope].
   * Returns `true` when the session is open after the call, also when it was open before.
   * Returns `false` when the session is closed.
   */
  suspend fun openIfNotClosed(session: SeSession, scope: CoroutineScope): Boolean = mutex.withLock {
    val sessionId = session.id
    if (sessionId in closedSessionIds) return@withLock false
    if (jobsBySessionId.containsKey(sessionId)) return@withLock true

    // ATOMIC: the job runs onClose also when the scope is already cancelled.
    // The cleanup waits for `mutex`, so it runs after this function adds the job.
    val job = scope.launch(start = CoroutineStart.ATOMIC) {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          mutex.withLock { markClosed(sessionId) }
        }
        onClose(session)
      }
    }
    jobsBySessionId[sessionId] = job
    true
  }

  /**
   * Closes the [session]. After this call, [isOpen] returns `false` and [isClosed] returns `true`.
   * A close of a session that is not open yet forbids its open. A second call does nothing.
   */
  suspend fun close(session: SeSession) {
    val job = mutex.withLock { markClosed(session.id) } ?: return
    job.cancel()
  }

  fun isOpen(session: SeSession): Boolean {
    return jobsBySessionId.containsKey(session.id)
  }

  suspend fun isClosed(session: SeSession): Boolean = mutex.withLock {
    session.id in closedSessionIds
  }

  // Call it only while you hold `mutex`. Returns the job of an open session.
  private fun markClosed(sessionId: String): Job? {
    if (closedSessionIds.add(sessionId) && closedSessionIds.size > closedSessionLimit) {
      closedSessionIds.remove(closedSessionIds.first())
    }
    return jobsBySessionId.remove(sessionId)
  }

  private companion object {
    const val MAX_CLOSED_SESSION_COUNT = 1000
  }
}
