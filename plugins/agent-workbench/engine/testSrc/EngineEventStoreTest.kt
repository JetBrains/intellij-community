// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.EventSource
import com.intellij.agent.workbench.engine.core.EventVisibility
import com.intellij.agent.workbench.engine.core.StartThreadRequest
import com.intellij.agent.workbench.engine.core.ThreadApprovalStatus
import com.intellij.agent.workbench.engine.core.ThreadCommand
import com.intellij.agent.workbench.engine.core.ThreadEventType
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.agent.workbench.engine.core.ThreadMessage
import com.intellij.agent.workbench.engine.core.ThreadStatus
import com.intellij.agent.workbench.engine.core.ThreadToolCall
import com.intellij.agent.workbench.engine.core.RuntimeKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EngineEventStoreTest {
  private val threadId = ThreadId("mock:thread-1")

  @Test
  fun `append, read and reduce a full lifecycle`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    appendLifecycle(store)

    val events = store.read(threadId)
    assertThat(events.map { it.type }).containsExactly(
      ThreadEventType.ThreadCreated,
      ThreadEventType.ThreadStarted,
      ThreadEventType.MessageDelta,
      ThreadEventType.MessageDelta,
      ThreadEventType.MessageCompleted,
      ThreadEventType.ThreadCompleted,
    )
    assertThat(events.map { it.seq }).containsExactly(0L, 1L, 2L, 3L, 4L, 5L)

    val projection = store.projection(threadId)
    assertThat(projection.thread.status).isEqualTo(ThreadStatus.Completed)
    assertThat(projection.thread.title).isEqualTo("Refactor auth")
    assertThat(projection.thread.summary).isEqualTo("done")
    assertThat(projection.messages).hasSize(1)
    assertThat(projection.messages.single().text).isEqualTo("Working on it…")
    assertThat(projection.messages.single().complete).isTrue()
    assertThat(projection.lastSeq).isEqualTo(5L)
  }

  @Test
  fun `projection rebuilds from log after restart`(@TempDir dir: Path) {
    appendLifecycle(JsonlEventStore(dir))

    // A fresh store over the same directory simulates an IDE restart.
    val reopened = JsonlEventStore(dir)
    assertThat(reopened.threadIds()).containsExactly(threadId)
    assertThat(reopened.projection(threadId).thread.status).isEqualTo(ThreadStatus.Completed)
    assertThat(reopened.projection(threadId).messages.single().text).isEqualTo("Working on it…")
  }

  @Test
  fun `seq keeps increasing across store instances`(@TempDir dir: Path) {
    JsonlEventStore(dir).append(threadId, EventSource.Runtime, ThreadEventType.ThreadCreated)
    val next = JsonlEventStore(dir).append(threadId, EventSource.Runtime, ThreadEventType.ThreadStarted)
    assertThat(next.seq).isEqualTo(1L)
  }

  @Test
  fun `approval requests are reflected in the projection`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(threadId, EventSource.Runtime, ThreadEventType.ThreadStarted)
    store.append(threadId, EventSource.Agent, ThreadEventType.ApprovalRequested)
    assertThat(store.projection(threadId).pendingApprovals).isEqualTo(1)
    assertThat(store.projection(threadId).thread.status).isEqualTo(ThreadStatus.WaitingForApproval)

    store.append(threadId, EventSource.User, ThreadEventType.ApprovalResolved)
    assertThat(store.projection(threadId).pendingApprovals).isEqualTo(0)
  }

  @Test
  fun `tool approval stays attached to tool call through updates`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(
      threadId, EventSource.Agent, ThreadEventType.ToolCallStarted,
      buildJsonObject {
        put("toolCallId", "tool-1")
        put("title", "Run tests")
        put("kind", "EXECUTE")
        put("command", "./tests.cmd")
      },
    )
    store.append(
      threadId, EventSource.Agent, ThreadEventType.ApprovalRequested,
      buildJsonObject {
        put("toolCallId", "tool-1")
        put("approvalId", "approval-1")
        put("title", "Run tests")
      },
    )
    store.append(
      threadId, EventSource.Agent, ThreadEventType.ToolCallOutput,
      buildJsonObject {
        put("toolCallId", "tool-1")
        put("contentDelta", "waiting for permission")
      },
    )

    val requested = store.projection(threadId)
    assertThat(requested.pendingApprovals).isEqualTo(1)
    assertThat(requested.transcript).hasSize(1)
    assertThat(requested.transcript.single()).isInstanceOf(ThreadToolCall::class.java)

    store.append(
      threadId, EventSource.User, ThreadEventType.ApprovalResolved,
      buildJsonObject {
        put("toolCallId", "tool-1")
        put("approvalId", "approval-1")
        put("decision", "approved")
      },
    )
    store.append(
      threadId, EventSource.Agent, ThreadEventType.ToolCallFinished,
      buildJsonObject {
        put("toolCallId", "tool-1")
        put("status", "completed")
        put("exitCode", 0)
      },
    )

    val toolCall = store.projection(threadId).transcript.single() as ThreadToolCall
    assertThat(toolCall.title).isEqualTo("Run tests")
    assertThat(toolCall.command).isEqualTo("./tests.cmd")
    assertThat(toolCall.outputText).isEqualTo("waiting for permission")
    assertThat(toolCall.approval?.status).isEqualTo(ThreadApprovalStatus.Approved)
    assertThat(toolCall.complete).isTrue()
    assertThat(store.projection(threadId).pendingApprovals).isZero()
  }

  @Test
  fun `tool output before start is replayed into the started tool entry`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(
      threadId, EventSource.Agent, ThreadEventType.ToolCallOutput,
      buildJsonObject {
        put("toolCallId", "tool-late")
        put("stream", "stdout")
        put("contentDelta", "first line\n")
      },
    )
    assertThat(store.projection(threadId).transcript).isEmpty()

    store.append(
      threadId, EventSource.Agent, ThreadEventType.ToolCallStarted,
      buildJsonObject {
        put("toolCallId", "tool-late")
        put("title", "Run command")
      },
    )

    val toolCall = store.projection(threadId).transcript.single() as ThreadToolCall
    assertThat(toolCall.title).isEqualTo("Run command")
    assertThat(toolCall.outputText).isEqualTo("first line\n")
  }

  @Test
  fun `command output before start is replayed into command entry`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(
      threadId, EventSource.Agent, ThreadEventType.CommandOutput,
      buildJsonObject {
        put("terminalId", "terminal-1")
        put("stream", "stderr")
        put("contentDelta", "booting\n")
      },
    )
    store.append(
      threadId, EventSource.Agent, ThreadEventType.CommandStarted,
      buildJsonObject {
        put("terminalId", "terminal-1")
        put("command", "npm test")
      },
    )

    val command = store.projection(threadId).transcript.single() as ThreadCommand
    assertThat(command.command).isEqualTo("npm test")
    assertThat(command.outputText).isEqualTo("booting\n")
  }

  @Test
  fun `stable ids are scoped by transcript entry type`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(
      threadId, EventSource.Agent, ThreadEventType.MessageDelta,
      buildJsonObject {
        put("messageId", "same-id")
        put("contentDelta", "message")
      },
    )
    store.append(
      threadId, EventSource.Agent, ThreadEventType.ToolCallStarted,
      buildJsonObject {
        put("toolCallId", "same-id")
        put("title", "tool")
      },
    )

    val transcript = store.projection(threadId).transcript
    assertThat(transcript).hasSize(2)
    assertThat(transcript[0]).isInstanceOf(ThreadMessage::class.java)
    assertThat(transcript[1]).isInstanceOf(ThreadToolCall::class.java)
  }

  @Test
  fun `mock runtime events feed the store and reduce to a completed thread`(@TempDir dir: Path) {
    runBlocking {
      val store = JsonlEventStore(dir)
      val runtime = MockAgentRuntime()
      val request = StartThreadRequest(threadId, prompt = "Add tests", runtimeKind = RuntimeKind.Mock)

      val handle = runtime.startThread(request)
      val emitted = handle.events.toList()
      emitted.forEach(store::persist)

      assertThat(emitted.first().type).isEqualTo(ThreadEventType.ThreadCreated)
      assertThat(emitted.last().type).isEqualTo(ThreadEventType.ThreadCompleted)

      val projection = store.projection(threadId)
      assertThat(projection.thread.status).isEqualTo(ThreadStatus.Completed)
      assertThat(projection.thread.runtimeKind).isEqualTo(RuntimeKind.Mock)
      assertThat(projection.messages.single().text).isEqualTo("Working on: Add tests — done.")
    }
  }

  @Test
  fun `ThreadCreated seeds the runtime binding and RuntimeSessionBound completes it across replay`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(
      threadId, EventSource.Runtime, ThreadEventType.ThreadCreated,
      buildJsonObject {
        put("title", "ACP chat")
        put("runtimeKind", RuntimeKind.Acp.name)
        put("agentId", "acp.goose")
        put("cwd", "/work/project")
      },
    )
    val seeded = store.projection(threadId).runtimeBinding
    assertThat(seeded?.runtimeKind).isEqualTo(RuntimeKind.Acp)
    assertThat(seeded?.agentId).isEqualTo("acp.goose")
    assertThat(seeded?.cwd).isEqualTo("/work/project")
    assertThat(seeded?.agentSessionId).isNull()

    store.append(
      threadId, EventSource.Runtime, ThreadEventType.RuntimeSessionBound,
      buildJsonObject {
        put("runtimeKind", RuntimeKind.Acp.name)
        put("agentSessionId", "srv-123")
      },
      visibility = EventVisibility.AuditOnly,
    )

    // A fresh store over the same directory proves the binding rebuilds from the log, including the AuditOnly event.
    val reopened = JsonlEventStore(dir)
    val binding = reopened.projection(threadId).runtimeBinding
    assertThat(binding?.agentSessionId).isEqualTo("srv-123")
    assertThat(binding?.agentId).isEqualTo("acp.goose")
    assertThat(binding?.cwd).isEqualTo("/work/project")
  }

  @Test
  fun `repeated RuntimeSessionBound never erases a known agentSessionId`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(
      threadId, EventSource.Runtime, ThreadEventType.RuntimeSessionBound,
      buildJsonObject {
        put("runtimeKind", RuntimeKind.Acp.name)
        put("agentSessionId", "srv-1")
        put("cwd", "/work")
      },
    )
    // A later partial event omits agentSessionId and cwd; the merge must preserve them.
    store.append(
      threadId, EventSource.Runtime, ThreadEventType.RuntimeSessionBound,
      buildJsonObject {
        put("runtimeKind", RuntimeKind.Acp.name)
        put("agentId", "acp.goose")
      },
    )
    val binding = store.projection(threadId).runtimeBinding
    assertThat(binding?.agentSessionId).isEqualTo("srv-1")
    assertThat(binding?.cwd).isEqualTo("/work")
    assertThat(binding?.agentId).isEqualTo("acp.goose")
  }

  @Test
  fun `ThreadDisconnected moves the thread to Disconnected while keeping the transcript`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(
      threadId, EventSource.Agent, ThreadEventType.MessageDelta,
      buildJsonObject {
        put("messageId", "m1")
        put("role", "Agent")
        put("contentDelta", "hello")
      },
    )
    store.append(
      threadId, EventSource.Runtime, ThreadEventType.ThreadDisconnected,
      buildJsonObject { put("message", "agent unavailable") },
    )
    val projection = store.projection(threadId)
    assertThat(projection.thread.status).isEqualTo(ThreadStatus.Disconnected)
    assertThat(projection.messages.single().text).isEqualTo("hello")
  }

  @Test
  fun `ThreadWaiting after disconnected recovers the thread status`(@TempDir dir: Path) {
    val store = JsonlEventStore(dir)
    store.append(threadId, EventSource.Runtime, ThreadEventType.ThreadDisconnected)
    store.append(threadId, EventSource.Runtime, ThreadEventType.ThreadWaiting)

    assertThat(store.projection(threadId).thread.status).isEqualTo(ThreadStatus.WaitingForUser)
  }

  private fun appendLifecycle(store: JsonlEventStore) {
    store.append(
      threadId, EventSource.Runtime, ThreadEventType.ThreadCreated,
      buildJsonObject {
        put("title", "Refactor auth")
        put("runtimeKind", RuntimeKind.Mock.name)
      },
    )
    store.append(threadId, EventSource.Runtime, ThreadEventType.ThreadStarted)
    store.append(
      threadId, EventSource.Agent, ThreadEventType.MessageDelta,
      buildJsonObject {
        put("messageId", "m1")
        put("role", "Agent")
        put("contentDelta", "Working on ")
      },
    )
    store.append(
      threadId, EventSource.Agent, ThreadEventType.MessageDelta,
      buildJsonObject {
        put("messageId", "m1")
        put("contentDelta", "it…")
      },
    )
    store.append(
      threadId, EventSource.Agent, ThreadEventType.MessageCompleted,
      buildJsonObject { put("messageId", "m1") },
    )
    store.append(
      threadId, EventSource.Runtime, ThreadEventType.ThreadCompleted,
      buildJsonObject { put("summary", "done") },
    )
  }
}
