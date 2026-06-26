// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.AgentCapability
import com.intellij.agent.workbench.engine.core.AgentRuntime
import com.intellij.agent.workbench.engine.core.AgentThreadHandle
import com.intellij.agent.workbench.engine.core.EventId
import com.intellij.agent.workbench.engine.core.EventSource
import com.intellij.agent.workbench.engine.core.RuntimeId
import com.intellij.agent.workbench.engine.core.RuntimeInput
import com.intellij.agent.workbench.engine.core.RuntimeKind
import com.intellij.agent.workbench.engine.core.StartThreadRequest
import com.intellij.agent.workbench.engine.core.StopMode
import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadEventType
import com.intellij.agent.workbench.engine.core.ThreadId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One scripted step a [MockAgentRuntime] emits. */
data class MockStep(
  val type: ThreadEventType,
  val payload: JsonObject = JsonObject(emptyMap()),
  val source: EventSource = EventSource.Agent,
)

/**
 * Deterministic [AgentRuntime] used to validate the Engine end-to-end (events → store → projection)
 * and to back UI/screenshot tests, with no external process. See the Engine design, section 9.2.
 */
class MockAgentRuntime(
  override val id: RuntimeId = RuntimeId("mock"),
  private val script: (StartThreadRequest) -> List<MockStep> = ::defaultScript,
) : AgentRuntime {
  override val kind: RuntimeKind = RuntimeKind.Mock
  override val capabilities: Set<AgentCapability> = setOf(AgentCapability.StructuredEvents)

  override suspend fun startThread(request: StartThreadRequest): AgentThreadHandle =
    MockThreadHandle(request.threadId, id, script(request))

  companion object {
    /** A minimal, realistic lifecycle: created → started → one streamed message → completed. */
    fun defaultScript(request: StartThreadRequest): List<MockStep> = listOf(
      MockStep(
        ThreadEventType.ThreadCreated,
        buildJsonObject {
          put("title", request.prompt.lineSequence().firstOrNull().orEmpty().take(80))
          put("runtimeKind", RuntimeKind.Mock.name)
        },
        EventSource.Runtime,
      ),
      MockStep(ThreadEventType.ThreadStarted, source = EventSource.Runtime),
      MockStep(
        ThreadEventType.MessageDelta,
        buildJsonObject {
          put("messageId", "m1")
          put("role", "Agent")
          put("contentDelta", "Working on: ${request.prompt}")
        },
      ),
      MockStep(
        ThreadEventType.MessageDelta,
        buildJsonObject {
          put("messageId", "m1")
          put("contentDelta", " — done.")
        },
      ),
      MockStep(
        ThreadEventType.MessageCompleted,
        buildJsonObject { put("messageId", "m1") },
      ),
      MockStep(
        ThreadEventType.ThreadCompleted,
        buildJsonObject { put("summary", "Mock run complete.") },
        EventSource.Runtime,
      ),
    )
  }
}

private val STEP_DELAY = 140.milliseconds

private class MockThreadHandle(
  override val threadId: ThreadId,
  override val runtimeId: RuntimeId,
  private val steps: List<MockStep>,
) : AgentThreadHandle {
  override val events: Flow<ThreadEventEnvelope> = flow {
    steps.forEachIndexed { index, step ->
      if (index > 0) delay(STEP_DELAY)
      emit(
        ThreadEventEnvelope(
          id = EventId("${threadId.value}#$index"),
          threadId = threadId,
          seq = index.toLong(),
          timestamp = index.toLong(),
          source = step.source,
          type = step.type,
          payload = step.payload,
        ),
      )
    }
  }

  override suspend fun send(input: RuntimeInput) = Unit

  override suspend fun stop(mode: StopMode) = Unit
}
