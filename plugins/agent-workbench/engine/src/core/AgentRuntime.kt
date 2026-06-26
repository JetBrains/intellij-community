// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.agent.workbench.engine.core

import kotlinx.coroutines.flow.Flow

/**
 * A runtime adapter that hosts agent threads and emits a canonical event stream.
 *
 * This is the structured-runtime boundary. Existing terminal-backed Agent Chat providers keep their
 * terminal lifecycle and are not expected to implement this interface.
 */
interface AgentRuntime {
  val id: RuntimeId
  val kind: RuntimeKind
  val capabilities: Set<AgentCapability>

  /** Starts a new thread for [request] and returns a handle to its live event stream. */
  suspend fun startThread(request: StartThreadRequest): AgentThreadHandle
}

/** Live handle to a running thread. The event [Flow] is the single channel of truth for its state. */
interface AgentThreadHandle {
  val threadId: ThreadId
  val runtimeId: RuntimeId
  val events: Flow<ThreadEventEnvelope>

  /** Sends user-originated input to the running agent. */
  suspend fun send(input: RuntimeInput)

  /** Requests the runtime to stop the thread using the given [mode]. */
  suspend fun stop(mode: StopMode = StopMode.Graceful)
}

/** Input routed from the IDE/user into a running runtime. */
sealed interface RuntimeInput {
  data class UserMessage(val text: String) : RuntimeInput
  data class ApprovalResolved(val requestId: ApprovalRequestId, val approved: Boolean, val reason: String? = null) : RuntimeInput
  data class TerminalInput(val text: String) : RuntimeInput
}

/**
 * Parameters for starting a thread. Deliberately platform-agnostic (no `Project`/`Path`) so this
 * core module stays dependency-light; runtime modules layer richer launch context on top.
 */
data class StartThreadRequest(
  val threadId: ThreadId,
  val prompt: String,
  val runtimeKind: RuntimeKind,
  val isolationMode: IsolationMode = IsolationMode.SharedWorkingTree,
  val workingDirectory: String? = null,
)
