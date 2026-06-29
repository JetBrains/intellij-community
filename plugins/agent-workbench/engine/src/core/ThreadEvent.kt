// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.agent.workbench.engine.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Canonical event catalog for structured Engine runtimes. Runtime adapters normalize their native
 * protocol into these types, so the structured UI never depends on the originating runtime.
 */
@Serializable
enum class ThreadEventType {
  ThreadCreated,
  ThreadUpdated,
  ThreadStarted,
  RuntimeSessionBound,
  MessageDelta,
  MessageCompleted,
  PlanCreated,
  PlanUpdated,
  ToolCallStarted,
  ToolCallOutput,
  ToolCallFinished,
  ApprovalRequested,
  ApprovalResolved,
  FileChangeDetected,
  FileChangeProposed,
  FileChangeApplied,
  DiffReady,
  CheckpointCreated,
  DiagnosticsUpdated,
  ContextCompacted,
  CommandStarted,
  CommandOutput,
  CommandFinished,
  UsageUpdated,
  AvailableCommandsUpdated,
  ModeChanged,
  ConfigOptionUpdated,
  TestsStarted,
  TestsFinished,
  ThreadWaiting,
  ThreadCompleted,
  ThreadFailed,
  ThreadDisconnected,
  ThreadCancelled,
}

/**
 * Immutable, append-only event. The durable source of truth for a thread is the ordered log of
 * these envelopes; UI state is a [ThreadProjection] reduced from the log.
 *
 * [seq] is monotonic per thread and assigned by the event store on append. [timestamp] is the
 * store-assigned ingestion time in epoch milliseconds (an agent-provided time, if any, lives in
 * [payload]). [payload] carries type-specific fields as a JSON object so the catalog can evolve
 * without schema churn in this module.
 */
@Serializable
data class ThreadEventEnvelope(
  val id: EventId,
  val threadId: ThreadId,
  val seq: Long,
  val timestamp: Long,
  val source: EventSource,
  val type: ThreadEventType,
  val payload: JsonObject,
  val correlationId: String? = null,
  val causationId: String? = null,
  val rawRef: String? = null,
  val visibility: EventVisibility = EventVisibility.User,
)
