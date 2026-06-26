// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.EventSource
import com.intellij.agent.workbench.engine.core.EventVisibility
import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadEventType
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.agent.workbench.engine.core.ThreadProjection
import com.intellij.agent.workbench.engine.core.replay
import kotlinx.serialization.json.JsonObject

/**
 * Durable, append-only log of [ThreadEventEnvelope]s, the source of truth for thread state.
 * UI/state are derived by folding the log into a [ThreadProjection].
 */
interface EventStore {
  /**
   * Builds an envelope (assigning a monotonic per-thread `seq`, a `timestamp`, and an id),
   * persists it, and returns the stored envelope.
   */
  fun append(
    threadId: ThreadId,
    source: EventSource,
    type: ThreadEventType,
    payload: JsonObject = EMPTY_PAYLOAD,
    visibility: EventVisibility = EventVisibility.User,
    correlationId: String? = null,
    causationId: String? = null,
    rawRef: String? = null,
  ): ThreadEventEnvelope

  /** Persists a fully-formed [envelope] as-is (e.g. one emitted by a runtime's event flow). */
  fun persist(envelope: ThreadEventEnvelope)

  /** Reads all persisted events for [threadId] in ascending `seq` order. */
  fun read(threadId: ThreadId): List<ThreadEventEnvelope>

  /** Thread ids that have at least one persisted event. */
  fun threadIds(): List<ThreadId>

  /** Rebuilds the projection for [threadId] from its event log. */
  fun projection(threadId: ThreadId): ThreadProjection =
    replay(ThreadProjection.empty(threadId), read(threadId))

  companion object {
    val EMPTY_PAYLOAD: JsonObject = JsonObject(emptyMap())
  }
}
