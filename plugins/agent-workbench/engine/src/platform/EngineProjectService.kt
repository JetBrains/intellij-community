// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.EventSource
import com.intellij.agent.workbench.engine.core.EventVisibility
import com.intellij.agent.workbench.engine.core.RuntimeKind
import com.intellij.agent.workbench.engine.core.StartThreadRequest
import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadEventType
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.agent.workbench.engine.core.ThreadProjection
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

/**
 * Project-level entry point to the Engine: owns the [EventStore] and publishes change
 * notifications. Heavy logs live under the IDE system dir, never in `.idea` (design §18).
 */
@Service(Service.Level.PROJECT)
class EngineProjectService(
  private val project: Project,
  private val scope: CoroutineScope,
) {
  val eventStore: EventStore = JsonlEventStore(storageRoot(projectKey(project)))

  /**
   * Demo/preview driver: launches a [MockAgentRuntime] thread and streams its events into the store.
   * Self-contained — it does not touch the live agent session pipeline.
   */
  fun startMockThread(prompt: String): ThreadId {
    val threadId = ThreadId("mock:" + java.util.UUID.randomUUID().toString().take(8))
    scope.launch {
      val handle = MockAgentRuntime().startThread(StartThreadRequest(threadId, prompt, RuntimeKind.Mock))
      handle.events.collect { ingest(it) }
    }
    return threadId
  }

  /** Appends an event and notifies subscribers; returns the stored envelope. */
  fun recordEvent(
    threadId: ThreadId,
    source: EventSource,
    type: ThreadEventType,
    payload: JsonObject = EventStore.EMPTY_PAYLOAD,
    visibility: EventVisibility = EventVisibility.User,
  ): ThreadEventEnvelope {
    val envelope = eventStore.append(threadId, source, type, payload, visibility)
    publish(envelope)
    return envelope
  }

  /** Persists a runtime-produced [envelope] and notifies subscribers. */
  fun ingest(envelope: ThreadEventEnvelope) {
    eventStore.persist(envelope)
    publish(envelope)
  }

  /** Current projection for [threadId], rebuilt from the log. */
  fun projection(threadId: ThreadId): ThreadProjection = eventStore.projection(threadId)

  /** Nudges subscribers to re-read the projection for [threadId] without appending a new event. */
  fun fireProjectionChanged(threadId: ThreadId) {
    project.messageBus.syncPublisher(EngineEvents.TOPIC).projectionUpdated(threadId)
    EngineChangeBus.fireChanged(threadId)
  }

  private fun publish(envelope: ThreadEventEnvelope) {
    val publisher = project.messageBus.syncPublisher(EngineEvents.TOPIC)
    publisher.eventAppended(envelope)
    publisher.projectionUpdated(envelope.threadId)
    EngineChangeBus.fireChanged(envelope.threadId)
  }

  companion object {
    fun getInstance(project: Project): EngineProjectService = project.service()

    /** Opens a read view of the event store for [projectPath], independent of an open [Project] instance. */
    fun storeForPath(projectPath: String): EventStore = JsonlEventStore(storageRoot(projectPath))

    private fun projectKey(project: Project): String = project.basePath ?: project.locationHash

    private fun storageRoot(projectKey: String): Path =
      PathManager.getSystemDir()
        .resolve("acp")
        .resolve("projects")
        .resolve(Integer.toHexString(projectKey.hashCode()))
  }
}
