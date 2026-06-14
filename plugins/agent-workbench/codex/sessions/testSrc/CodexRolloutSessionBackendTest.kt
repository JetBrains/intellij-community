// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexRolloutSessionBackendTest {
  // Primary owner for rollout parser/activity/title mapping coverage.
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun mapsSessionMetaIdAndProcessingBeatsPassiveUnread() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-a")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-2026-02-13T10-00-00-random.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T10:00:00.000Z",
            id = "session-abc",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-13T10:00:10.000Z","type":"event_msg","payload":{"type":"user_message","message":"Fix flaky test"}}""",
          """{"timestamp":"2026-02-13T10:00:20.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
          """{"timestamp":"2026-02-13T10:00:30.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Working"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      val thread = threads.single()
      assertThat(thread.thread.id).isEqualTo("session-abc")
      assertThat(thread.thread.title).isEqualTo("Fix flaky test")
      assertThat(thread.thread.updatedAt).isEqualTo(Instant.parse("2026-02-13T10:00:30.000Z").toEpochMilli())
      assertThat(thread.activity).isEqualTo(CodexSessionActivity.PROCESSING)
    }
  }

  @Test
  fun ignoresRolloutWithoutSessionMetaId() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-no-id")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-filename-id-only.jsonl"),
        lines = listOf(
          sessionMetaLineWithoutId(cwd = projectDir),
          """{"timestamp":"2026-02-13T10:00:10.000Z","type":"event_msg","payload":{"type":"user_message","message":"No id in session meta"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).isEmpty()
    }
  }

  @Test
  fun keepsLatestCumulativeTokenUsageWithoutDoubleCountingRepeatedSnapshots() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-cost-usage")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("05").resolve("07")
          .resolve("rollout-cost-usage.jsonl"),
        lines = loadRolloutFixture(projectDir),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().usageSnapshots).containsExactly(
        AgentSessionUsageSnapshot(
          modelId = "gpt-5.4",
          inputTokens = 10_230_044,
          outputTokens = 408_288,
          cacheReadTokens = 131_931_008,
        )
      )
    }
  }

  @Test
  fun foldsSubAgentUsageIntoParentRolloutThread() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-subagent-usage")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("05").resolve("08").resolve("rollout-parent-usage.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-05-08T10:00:00.000Z",
            id = "parent-usage",
            cwd = projectDir,
          ),
          tokenUsageLine(
            timestamp = "2026-05-08T10:00:01.000Z",
            model = "gpt-5",
            totalInputTokens = 100,
            cachedInputTokens = 40,
            outputTokens = 5,
          ),
        ),
      )
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("05").resolve("08").resolve("rollout-child-usage.jsonl"),
        lines = listOf(
          subAgentSessionMetaLine(
            timestamp = "2026-05-08T10:00:02.000Z",
            id = "child-usage",
            cwd = projectDir,
            parentThreadId = "parent-usage",
          ),
          tokenUsageLine(
            timestamp = "2026-05-08T10:00:03.000Z",
            model = "gpt-5-mini",
            totalInputTokens = 60,
            cachedInputTokens = 10,
            outputTokens = 7,
          ),
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      val parent = threads.single()
      assertThat(parent.thread.id).isEqualTo("parent-usage")
      assertThat(parent.thread.subAgents.map { it.id }).containsExactly("child-usage")
      assertThat(parent.usageSnapshots).containsExactlyInAnyOrder(
        AgentSessionUsageSnapshot(
          modelId = "gpt-5",
          inputTokens = 60,
          outputTokens = 5,
          cacheReadTokens = 40,
        ),
        AgentSessionUsageSnapshot(
          modelId = "gpt-5-mini",
          inputTokens = 50,
          outputTokens = 7,
          cacheReadTokens = 10,
        ),
      )
    }
  }

  @Test
  fun aggregatesLargeNestedRolloutSetsQuickly() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rollout-stress")
      Files.createDirectories(projectDir)
      val parentCount = 120
      val subAgentsPerParent = 5

      for (parentIndex in 0 until parentCount) {
        val parentId = "parent-$parentIndex"
        writeRollout(
          file = tempDir.resolve("sessions").resolve("2026").resolve("05").resolve("09").resolve("rollout-$parentId.jsonl"),
          lines = listOf(
            sessionMetaLine(
              timestamp = "2026-05-09T10:00:00.000Z",
              id = parentId,
              cwd = projectDir,
            ),
            tokenUsageLine(
              timestamp = "2026-05-09T10:00:01.000Z",
              model = "gpt-5",
              totalInputTokens = 100 + parentIndex.toLong(),
              cachedInputTokens = 20,
              outputTokens = 10,
            ),
          ),
        )
        for (childIndex in 0 until subAgentsPerParent) {
          val childId = "child-$parentIndex-$childIndex"
          writeRollout(
            file = tempDir.resolve("sessions").resolve("2026").resolve("05").resolve("09").resolve("rollout-$childId.jsonl"),
            lines = listOf(
              subAgentSessionMetaLine(
                timestamp = "2026-05-09T10:00:02.000Z",
                id = childId,
                cwd = projectDir,
                parentThreadId = parentId,
              ),
              tokenUsageLine(
                timestamp = "2026-05-09T10:00:03.000Z",
                model = "gpt-5-mini",
                totalInputTokens = 50,
                cachedInputTokens = 5,
                outputTokens = 3,
              ),
            ),
          )
        }
      }

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      var threads = emptyList<com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread>()
      val elapsedMs = measureTimeMillis {
        threads = backend.listThreads(path = projectDir.toString(), openProject = null)
      }

      println("Codex rollout stress aggregation: ${threads.size} parents in ${elapsedMs}ms")
      assertThat(elapsedMs).isLessThan(5_000)
      assertThat(threads).hasSize(parentCount)
      assertThat(threads.all { it.thread.subAgents.size == subAgentsPerParent }).isTrue()
      assertThat(threads.all { it.usageSnapshots.size == subAgentsPerParent + 1 }).isTrue()
    }
  }

  @Test
  fun mapsCurrentCodexRolloutActivitySignals() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-activity")
      Files.createDirectories(projectDir)

      val activityCases = listOf(
        ActivityCase(
          id = "session-review",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:00:05.000Z","type":"event_msg","payload":{"type":"entered_review_mode"}}"""
          ),
          expected = CodexSessionActivity.REVIEWING,
        ),
        ActivityCase(
          id = "session-processing",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:01:05.000Z","type":"event_msg","payload":{"type":"task_started"}}"""
          ),
          expected = CodexSessionActivity.PROCESSING,
        ),
        ActivityCase(
          id = "session-function-call-processing",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:01:10.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run a tool"}}""",
            """{"timestamp":"2026-02-13T11:01:11.000Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[]}}""",
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:12.000Z",
              callId = "call-processing",
              name = "exec_command",
            ),
          ),
          expected = CodexSessionActivity.PROCESSING,
        ),
        ActivityCase(
          id = "session-function-call-output-unread",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:01:20.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run a completed tool"}}""",
            """{"timestamp":"2026-02-13T11:01:21.000Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[]}}""",
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:22.000Z",
              callId = "call-output-unread",
              name = "exec_command",
            ),
            """{"timestamp":"2026-02-13T11:01:23.000Z","type":"response_item","payload":{"type":"function_call_output","call_id":"call-output-unread","output":"{}"}}""",
          ),
          expected = CodexSessionActivity.UNREAD,
        ),
        ActivityCase(
          id = "session-function-call-completion-unread",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:01:30.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run a tool to completion"}}""",
            """{"timestamp":"2026-02-13T11:01:31.000Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[]}}""",
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:32.000Z",
              callId = "call-completion-unread",
              name = "exec_command",
            ),
            turnCompleteLine(timestamp = "2026-02-13T11:01:33.000Z"),
          ),
          expected = CodexSessionActivity.UNREAD,
        ),
        ActivityCase(
          id = "session-escalated-exec-needs-input",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:01:34.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run an escalated tool"}}""",
            """{"timestamp":"2026-02-13T11:01:35.000Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[]}}""",
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:36.000Z",
              callId = "call-escalated-exec",
              name = "exec_command",
              arguments = """{"sandbox_permissions":"require_escalated"}""",
            ),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-normal-exec-with-sandbox-arg-processing",
          eventLines = listOf(
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:37.000Z",
              callId = "call-normal-exec",
              name = "exec_command",
              arguments = """{"sandbox_permissions":"use_default"}""",
            ),
          ),
          expected = CodexSessionActivity.PROCESSING,
        ),
        ActivityCase(
          id = "session-escalated-exec-output-unread",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:01:38.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run an approved tool"}}""",
            """{"timestamp":"2026-02-13T11:01:39.000Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[]}}""",
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:40.000Z",
              callId = "call-escalated-output",
              name = "exec_command",
              arguments = """{"sandbox_permissions":"require_escalated"}""",
            ),
            """{"timestamp":"2026-02-13T11:01:41.000Z","type":"response_item","payload":{"type":"function_call_output","call_id":"call-escalated-output","output":"{}"}}""",
          ),
          expected = CodexSessionActivity.UNREAD,
        ),
        ActivityCase(
          id = "session-escalated-exec-started-processing",
          eventLines = listOf(
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:42.000Z",
              callId = "call-escalated-started",
              name = "exec_command",
              arguments = """{"sandbox_permissions":"require_escalated"}""",
            ),
            approvalEventLine(
              timestamp = "2026-02-13T11:01:43.000Z",
              type = "exec_command_begin",
              callId = "call-escalated-started",
            ),
          ),
          expected = CodexSessionActivity.PROCESSING,
        ),
        ActivityCase(
          id = "session-request-permissions-function-call",
          eventLines = listOf(
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:44.000Z",
              callId = "call-request-permissions",
              name = "request_permissions",
            ),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-exec-approval-event",
          eventLines = listOf(
            approvalEventLine(timestamp = "2026-02-13T11:01:45.000Z", type = "exec_approval_request", callId = "call-exec-approval"),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-exec-approval-event-finished-ready",
          eventLines = listOf(
            approvalEventLine(timestamp = "2026-02-13T11:01:45.100Z", type = "exec_approval_request", callId = "call-exec-finished"),
            approvalEventLine(timestamp = "2026-02-13T11:01:45.200Z", type = "exec_command_begin", callId = "call-exec-finished"),
            approvalEventLine(timestamp = "2026-02-13T11:01:45.300Z", type = "exec_command_end", callId = "call-exec-finished"),
          ),
          expected = CodexSessionActivity.READY,
        ),
        ActivityCase(
          id = "session-apply-patch-approval-event",
          eventLines = listOf(
            approvalEventLine(timestamp = "2026-02-13T11:01:46.000Z",
                              type = "apply_patch_approval_request",
                              callId = "call-patch-approval"),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-request-permissions-event",
          eventLines = listOf(
            approvalEventLine(timestamp = "2026-02-13T11:01:47.000Z", type = "request_permissions", callId = "call-permission-event"),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-elicitation-request-event",
          eventLines = listOf(
            approvalEventLine(timestamp = "2026-02-13T11:01:48.000Z", type = "elicitation_request", callId = "call-elicitation-request"),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-approval-event-completion-ready",
          eventLines = listOf(
            approvalEventLine(
              timestamp = "2026-02-13T11:01:49.000Z",
              type = "exec_approval_request",
              callId = "call-approval-completed",
              turnId = "turn-approval-completed",
            ),
            turnCompleteLine(timestamp = "2026-02-13T11:01:50.000Z", turnId = "turn-approval-completed"),
          ),
          expected = CodexSessionActivity.READY,
        ),
        ActivityCase(
          id = "session-newer-function-call-survives-stale-turn-complete",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:01:40.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run overlapping tools"}}""",
            """{"timestamp":"2026-02-13T11:01:41.000Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[]}}""",
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:42.000Z",
              callId = "call-old-turn",
              name = "exec_command",
              turnId = "turn-old",
            ),
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:01:43.000Z",
              callId = "call-new-turn",
              name = "exec_command",
              turnId = "turn-new",
            ),
            turnCompleteLine(timestamp = "2026-02-13T11:01:44.000Z", turnId = "turn-old"),
          ),
          expected = CodexSessionActivity.PROCESSING,
        ),
        ActivityCase(
          id = "session-pending-input",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:02:05.000Z","type":"event_msg","payload":{"type":"request_user_input"}}"""
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-pending-input-function-call",
          eventLines = listOf(
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:02:10.000Z",
              callId = "call-request-user-input",
            )
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-pending-plan",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:02:30.000Z","type":"event_msg","payload":{"type":"user_message","message":"Plan the change"}}""",
            itemCompletedPlan(timestamp = "2026-02-13T11:02:31.000Z"),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-completed-plan-turn",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:02:32.000Z","type":"event_msg","payload":{"type":"user_message","message":"Plan the change"}}""",
            """{"timestamp":"2026-02-13T11:02:33.000Z","type":"event_msg","payload":{"type":"task_started","turn_id":"turn-plan"}}""",
            itemCompletedPlan(timestamp = "2026-02-13T11:02:34.000Z", turnId = "turn-plan"),
            """{"timestamp":"2026-02-13T11:02:35.000Z","type":"event_msg","payload":{"type":"task_complete","turn_id":"turn-plan"}}""",
          ),
          expected = CodexSessionActivity.READY,
        ),
        ActivityCase(
          id = "session-stale-completion-keeps-newer-plan",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:02:36.000Z","type":"event_msg","payload":{"type":"user_message","message":"Plan the change"}}""",
            itemCompletedPlan(timestamp = "2026-02-13T11:02:37.000Z", turnId = "turn-new"),
            turnCompleteLine(timestamp = "2026-02-13T11:02:38.000Z", turnId = "turn-old"),
          ),
          expected = CodexSessionActivity.NEEDS_INPUT,
          expectedRequiresResponse = true,
        ),
        ActivityCase(
          id = "session-legacy-completion-clears-plan",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:02:39.000Z","type":"event_msg","payload":{"type":"user_message","message":"Plan the change"}}""",
            itemCompletedPlan(timestamp = "2026-02-13T11:02:40.000Z"),
            turnCompleteLine(timestamp = "2026-02-13T11:02:41.000Z"),
          ),
          expected = CodexSessionActivity.READY,
        ),
        ActivityCase(
          id = "session-cleared-plan",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:02:40.000Z","type":"event_msg","payload":{"type":"user_message","message":"Plan the change"}}""",
            itemCompletedPlan(timestamp = "2026-02-13T11:02:41.000Z"),
            """{"timestamp":"2026-02-13T11:02:42.000Z","type":"event_msg","payload":{"type":"user_message","message":"Proceed"}}""",
          ),
          expected = CodexSessionActivity.READY,
        ),
        ActivityCase(
          id = "session-cleared-input-function-call-output",
          eventLines = listOf(
            responseItemFunctionCall(
              timestamp = "2026-02-13T11:02:20.000Z",
              callId = "call-cleared-user-input",
            ),
            """{"timestamp":"2026-02-13T11:02:21.000Z","type":"response_item","payload":{"type":"function_call_output","call_id":"call-cleared-user-input","output":"ok"}}""",
          ),
          expected = CodexSessionActivity.READY,
        ),
        ActivityCase(
          id = "session-processing-over-unread",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:03:05.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
            """{"timestamp":"2026-02-13T11:03:06.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Still working"}}"""
          ),
          expected = CodexSessionActivity.PROCESSING,
        ),
        ActivityCase(
          id = "session-review-over-unread",
          eventLines = listOf(
            """{"timestamp":"2026-02-13T11:04:05.000Z","type":"event_msg","payload":{"type":"entered_review_mode"}}""",
            """{"timestamp":"2026-02-13T11:04:06.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Review result draft"}}"""
          ),
          expected = CodexSessionActivity.REVIEWING,
        ),
      )

      for ((index, testCase) in activityCases.withIndex()) {
        writeRollout(
          file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
            .resolve("rollout-activity-$index.jsonl"),
          lines = listOf(
            sessionMetaLine(
              timestamp = "2026-02-13T11:0$index:00.000Z",
              id = testCase.id,
              cwd = projectDir,
            ),
          ) + testCase.eventLines,
        )
      }

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threadsById = backend.listThreads(path = projectDir.toString(), openProject = null).associateBy { it.thread.id }

      assertThat(threadsById).hasSize(activityCases.size)
      for ((id, _, expected, expectedRequiresResponse) in activityCases) {
        val thread = threadsById.getValue(id)
        assertThat(thread.activity).isEqualTo(expected)
        assertThat(thread.requiresResponse).isEqualTo(expectedRequiresResponse)
      }
    }
  }

  @Test
  fun laterCompletedTaskClearsEarlierIncompleteProcessingEvent() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-superseded-processing")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-superseded-processing.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T12:30:00.000Z",
            id = "session-superseded-processing",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-13T12:30:05.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run a long task"}}""",
          """{"timestamp":"2026-02-13T12:30:10.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
          """{"timestamp":"2026-02-13T12:30:20.000Z","type":"event_msg","payload":{"type":"task_complete"}}""",
          """{"timestamp":"2026-02-13T12:31:10.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
          """{"timestamp":"2026-02-13T12:32:10.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
          """{"timestamp":"2026-02-13T12:32:20.000Z","type":"event_msg","payload":{"type":"task_complete"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().activity).isEqualTo(CodexSessionActivity.READY)
    }
  }

  @Test
  fun staleCompletedTaskForEarlierTurnDoesNotClearNewerProcessingTurn() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-stale-completed-turn")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-stale-completed-turn.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T13:30:00.000Z",
            id = "session-stale-completed-turn",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-13T13:30:10.000Z","type":"event_msg","payload":{"type":"task_started","turn_id":"turn-1"}}""",
          """{"timestamp":"2026-02-13T13:30:20.000Z","type":"event_msg","payload":{"type":"task_started","turn_id":"turn-2"}}""",
          """{"timestamp":"2026-02-13T13:30:30.000Z","type":"event_msg","payload":{"type":"task_complete","turn_id":"turn-1"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().activity).isEqualTo(CodexSessionActivity.PROCESSING)
    }
  }

  @Test
  fun prefersSnakeCaseThreadNameUpdatedEventForTitle() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-thread-rename")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
          .resolve("rollout-thread-name-updated.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-14T12:00:00.000Z",
            id = "session-title-updated",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-14T12:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Initial fallback title"}}""",
          """{"timestamp":"2026-02-14T12:00:02.000Z","type":"event_msg","payload":{"type":"thread_name_updated","thread_name":"  Renamed   from   Codex  "}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().thread.title).isEqualTo("Renamed from Codex")
    }
  }

  @Test
  fun preservesFullLengthThreadNameUpdatedTitle() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-thread-long-rename")
      Files.createDirectories(projectDir)
      val longTitle = "Long renamed thread " + "x".repeat(180) + " tail"
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
          .resolve("rollout-thread-name-long.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-14T12:10:00.000Z",
            id = "session-title-long",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-14T12:10:01.000Z","type":"event_msg","payload":{"type":"thread_name_updated","thread_name":"$longTitle"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().thread.title).isEqualTo(longTitle)
    }
  }

  @Test
  fun filtersByCwdAndMarksReadyAfterCompletedTask() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-c")
      val otherDir = tempDir.resolve("project-d")
      Files.createDirectories(projectDir)
      Files.createDirectories(otherDir)

      val sessionsRoot = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
      writeRollout(
        file = sessionsRoot.resolve("rollout-ready.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T12:00:00.000Z",
            id = "session-ready",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-13T12:00:05.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
          """{"timestamp":"2026-02-13T12:00:10.000Z","type":"event_msg","payload":{"type":"task_complete"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-other.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T12:00:00.000Z",
            id = "session-other",
            cwd = otherDir,
          ),
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads.map { it.thread.id }).containsExactly("session-ready")
      assertThat(threads.single().activity).isEqualTo(CodexSessionActivity.READY)
    }
  }

  @Test
  fun resolvesActiveThreadFilePathsForProjectAndThreadId() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-active-watch")
      val otherDir = tempDir.resolve("project-active-watch-other")
      Files.createDirectories(projectDir)
      Files.createDirectories(otherDir)

      val sessionsRoot = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
      val activeRollout = sessionsRoot.resolve("rollout-active.jsonl")
      writeRollout(
        file = activeRollout,
        lines = listOf(sessionMetaLine(timestamp = "2026-02-13T12:00:00.000Z", id = "session-active", cwd = projectDir)),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-other-thread.jsonl"),
        lines = listOf(sessionMetaLine(timestamp = "2026-02-13T12:01:00.000Z", id = "session-other", cwd = projectDir)),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-other-project.jsonl"),
        lines = listOf(sessionMetaLine(timestamp = "2026-02-13T12:02:00.000Z", id = "session-active", cwd = otherDir)),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })

      assertThat(backend.resolveActiveThreadFilePaths(projectDir.toString(), " session-active ")).containsExactly(activeRollout)
      assertThat(backend.resolveActiveThreadFilePaths(projectDir.toString(), "session-other/malformed")).isEmpty()
      assertThat(backend.resolveActiveThreadFilePaths(projectDir.toString(), "missing-session")).isEmpty()
    }
  }

  @Test
  fun mapsBranchFromSessionMetaPayload() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-branch")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-branch.jsonl"),
        lines = listOf(
          """{"timestamp":"2026-02-13T15:00:00.000Z","type":"session_meta","payload":{"id":"session-branch","timestamp":"2026-02-13T15:00:00.000Z","cwd":"${
            projectDir.toString().replace("\\", "\\\\")
          }","git":{"branch":"feature/codex-rollout"}}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().thread.gitBranch).isEqualTo("feature/codex-rollout")
    }
  }

  @Test
  fun foldsSubAgentThreadSpawnUnderParentThread() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-subagent")
      Files.createDirectories(projectDir)
      val sessionsRoot = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
      writeRollout(
        file = sessionsRoot.resolve("rollout-parent.jsonl"),
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-14T16:00:00.000Z", id = "session-parent", cwd = projectDir),
          """{"timestamp":"2026-02-14T16:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Parent thread title"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-subagent.jsonl"),
        lines = listOf(
          subAgentSessionMetaLine(
            timestamp = "2026-02-14T16:01:00.000Z",
            id = "session-subagent",
            cwd = projectDir,
            parentThreadId = "session-parent",
          ),
          """{"timestamp":"2026-02-14T16:01:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"review the agent threads for rollout nesting"}}""",
          """{"timestamp":"2026-02-14T16:01:02.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Done"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      val parentThread = threads.single()
      assertThat(parentThread.thread.id).isEqualTo("session-parent")
      assertThat(parentThread.activity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parentThread.summaryActivity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parentThread.thread.subAgents).hasSize(1)
      val subAgent = parentThread.thread.subAgents.single()
      assertThat(subAgent.id).isEqualTo("session-subagent")
      assertThat(subAgent.name).isEqualTo("review the agent threads for rollout nesting")
      assertThat(parentThread.subAgentActivitiesById).containsEntry("session-subagent", CodexSessionActivity.UNREAD)
    }
  }

  @Test
  fun keepsSubAgentThreadTopLevelWhenParentIsMissing() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-subagent-missing-parent")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
          .resolve("rollout-subagent-only.jsonl"),
        lines = listOf(
          subAgentSessionMetaLine(
            timestamp = "2026-02-14T17:00:00.000Z",
            id = "session-subagent-only",
            cwd = projectDir,
            parentThreadId = "missing-parent",
          ),
          """{"timestamp":"2026-02-14T17:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"sub-agent fallback thread"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().thread.id).isEqualTo("session-subagent-only")
      assertThat(threads.single().thread.subAgents).isEmpty()
      assertThat(threads.single().summaryActivity).isNull()
    }
  }

  @Test
  fun prefetchThreadsMapsPerResolvedPath() {
    runBlocking(Dispatchers.Default) {
      val projectA = tempDir.resolve("project-prefetch-a")
      val projectB = tempDir.resolve("project-prefetch-b")
      val projectC = tempDir.resolve("project-prefetch-c")
      Files.createDirectories(projectA)
      Files.createDirectories(projectB)
      Files.createDirectories(projectC)

      val sessionsRoot = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
      writeRollout(
        file = sessionsRoot.resolve("rollout-prefetch-a-old.jsonl"),
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-14T09:00:00.000Z", id = "session-a-old", cwd = projectA),
          """{"timestamp":"2026-02-14T09:00:05.000Z","type":"event_msg","payload":{"type":"user_message","message":"A old"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-prefetch-a-new.jsonl"),
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-14T10:00:00.000Z", id = "session-a-new", cwd = projectA),
          """{"timestamp":"2026-02-14T10:00:05.000Z","type":"event_msg","payload":{"type":"user_message","message":"A new"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-prefetch-b.jsonl"),
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-14T11:00:00.000Z", id = "session-b", cwd = projectB),
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val unresolvedPath = tempDir.resolve("project-prefetch-missing").toString()
      val prefetched = backend.prefetchThreads(
        listOf(projectA.toString(), projectB.toString(), projectC.toString(), unresolvedPath)
      )

      assertThat(prefetched.keys).containsExactlyInAnyOrder(projectA.toString(), projectB.toString(), projectC.toString())
      assertThat(prefetched.getValue(projectA.toString()).map { it.thread.id }).containsExactly("session-a-new", "session-a-old")
      assertThat(prefetched.getValue(projectB.toString()).map { it.thread.id }).containsExactly("session-b")
      assertThat(prefetched.getValue(projectC.toString())).isEmpty()
    }
  }

  @Test
  fun refreshesCachedThreadsWhenRolloutFilesChangeAndDelete() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-cache")
      Files.createDirectories(projectDir)

      val sessionsRoot = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("15")
      val rolloutA = sessionsRoot.resolve("rollout-cache-a.jsonl")
      val rolloutB = sessionsRoot.resolve("rollout-cache-b.jsonl")

      writeRollout(
        file = rolloutA,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-15T10:00:00.000Z", id = "session-a", cwd = projectDir),
          """{"timestamp":"2026-02-15T10:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Initial title"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })

      val initialThreads = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(initialThreads.map { it.thread.id }).containsExactly("session-a")
      assertThat(initialThreads.single().thread.title).isEqualTo("Initial title")

      writeRollout(
        file = rolloutA,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-15T10:00:00.000Z", id = "session-a", cwd = projectDir),
          """{"timestamp":"2026-02-15T10:05:00.000Z","type":"event_msg","payload":{"type":"user_message","message":"Updated title with extra text"}}""",
          """{"timestamp":"2026-02-15T10:05:01.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Done"}}""",
        ),
      )
      writeRollout(
        file = rolloutB,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-15T10:06:00.000Z", id = "session-b", cwd = projectDir),
          """{"timestamp":"2026-02-15T10:06:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Newest"}}""",
        ),
      )

      val afterRewriteAndAdd = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(afterRewriteAndAdd.map { it.thread.id }).containsExactly("session-b", "session-a")
      assertThat(afterRewriteAndAdd.first { it.thread.id == "session-a" }.thread.title).isEqualTo("Updated title with extra text")
      assertThat(afterRewriteAndAdd.first { it.thread.id == "session-a" }.thread.updatedAt)
        .isEqualTo(Instant.parse("2026-02-15T10:05:01.000Z").toEpochMilli())

      Files.delete(rolloutB)

      val afterDelete = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(afterDelete.map { it.thread.id }).containsExactly("session-a")
    }
  }

  @Test
  fun retriesPreviouslyUnparseableRolloutAfterRewrite() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-retry")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("15")
        .resolve("rollout-retry.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLineWithoutId(cwd = projectDir),
          """{"timestamp":"2026-02-15T11:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Ignored without id"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })

      val beforeRewrite = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(beforeRewrite).isEmpty()

      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-15T11:00:00.000Z", id = "session-retry", cwd = projectDir),
          """{"timestamp":"2026-02-15T11:00:02.000Z","type":"event_msg","payload":{"type":"user_message","message":"Recovered title"}}""",
        ),
      )

      val afterRewrite = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(afterRewrite).hasSize(1)
      assertThat(afterRewrite.single().thread.id).isEqualTo("session-retry")
      assertThat(afterRewrite.single().thread.title).isEqualTo("Recovered title")
    }
  }

  @Test
  fun usesFirstNonEnvironmentUserMessageAsTitle() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-title")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
          .resolve("rollout-title.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-14T12:00:00.000Z",
            id = "session-title",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-14T12:00:02.000Z","type":"event_msg","payload":{"type":"user_message","message":"<environment_context>\n<cwd>${
            projectDir.toString().replace("\\", "\\\\")
          }</cwd>"}}""",
          """{"timestamp":"2026-02-14T12:00:03.000Z","type":"event_msg","payload":{"type":"user_message","message":"<TURN_ABORTED>\nreason"}}""",
          """{"timestamp":"2026-02-14T12:00:04.000Z","type":"event_msg","payload":{"type":"user_message","message":"<prior context> ## My request for Codex:   Real   title    line   "}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().thread.title).isEqualTo("Real title line")
    }
  }

  @Test
  fun skipsMalformedJsonLineAndKeepsParsingLaterEvents() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-malformed")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
          .resolve("rollout-malformed.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-14T13:00:00.000Z",
            id = "session-malformed",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-14T13:00:02.000Z","type":"event_msg","payload":{"type":"user_message","message":"Initial title"}}""",
          """{"timestamp":"2026-02-14T13:00:03.000Z","type":"event_msg","payload":{"type":"user_message"""",
          """{"timestamp":"2026-02-14T13:00:04.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Still works"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      val thread = threads.single()
      assertThat(thread.thread.id).isEqualTo("session-malformed")
      assertThat(thread.thread.title).isEqualTo("Initial title")
      assertThat(thread.thread.updatedAt).isEqualTo(Instant.parse("2026-02-14T13:00:04.000Z").toEpochMilli())
      assertThat(thread.activity).isEqualTo(CodexSessionActivity.UNREAD)
    }
  }

  @Test
  fun scopedRolloutUpdateIncludesAuthoritativeActivityHint() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-update-activity-hint")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-update-activity-hint.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T12:00:00.000Z", id = "session-update-activity-hint", cwd = projectDir),
          """{"timestamp":"2026-02-16T12:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run a slow tool"}}""",
          responseItemFunctionCall(
            timestamp = "2026-02-16T12:00:02.000Z",
            callId = "call-update-activity-hint",
            name = "exec_command",
          ),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        drainUpdateChannel(updates)
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) { updates.receive() }
        assertThat(update).isNotNull
        assertThat(update!!.scopedPaths).containsExactly(projectDir.toString())
        assertThat(update.threadIds).containsExactly("session-update-activity-hint")
        assertThat(update.activityUpdatesByThreadId.getValue("session-update-activity-hint").activityReport)
          .isEqualTo(AgentThreadActivityReport(AgentThreadActivity.PROCESSING))
        val presentationUpdate = update.presentationUpdatesByThreadId.getValue("session-update-activity-hint")
        assertThat(presentationUpdate.title).isEqualTo("Run a slow tool")
        assertThat(presentationUpdate.activityReport).isEqualTo(AgentThreadActivityReport(AgentThreadActivity.PROCESSING))
        assertThat(presentationUpdate.updatedAt).isEqualTo(Instant.parse("2026-02-16T12:00:02.000Z").toEpochMilli())
        assertThat(update.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun scopedRolloutUpdateWithCompletedMutatingToolIncludesProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-update-mutating-tool")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-update-mutating-tool.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T12:30:00.000Z", id = "session-update-mutating-tool", cwd = projectDir),
          responseItemFunctionCall(
            timestamp = "2026-02-16T12:30:01.000Z",
            callId = "call-update-mutating-tool",
            name = "exec_command",
          ),
          responseItemFunctionCallOutput(
            timestamp = "2026-02-16T12:30:02.000Z",
            callId = "call-update-mutating-tool",
          ),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
        trailingRefreshDelayMs = 60_000L,
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        drainUpdateChannel(updates)
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) { updates.receive() }
        assertThat(update).isNotNull
        assertThat(update!!.scopedPaths).containsExactly(projectDir.toString())
        assertThat(update.threadIds).containsExactly("session-update-mutating-tool")
        assertThat(update.mayHaveChangedProjectFiles).isTrue()
        assertThat(update.changedProjectFilePaths).isNull()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun scopedRolloutUpdateWithCompletedApplyPatchIncludesExactProjectFileChangePath() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-update-apply-patch")
      val changedFile = projectDir.resolve("src").resolve("Main.kt")
      Files.createDirectories(projectDir)
      val patch = """*** Begin Patch
*** Update File: src/Main.kt
@@
+fun main() {}
*** End Patch"""

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-update-apply-patch.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T12:30:00.000Z", id = "session-update-apply-patch", cwd = projectDir),
          responseItemFunctionCall(
            timestamp = "2026-02-16T12:30:01.000Z",
            callId = "call-update-apply-patch",
            name = "apply_patch",
            arguments = """{"patch":"${jsonString(patch)}"}""",
          ),
          responseItemFunctionCallOutput(
            timestamp = "2026-02-16T12:30:02.000Z",
            callId = "call-update-apply-patch",
          ),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
        trailingRefreshDelayMs = 60_000L,
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        drainUpdateChannel(updates)
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) { updates.receive() }
        assertThat(update).isNotNull
        assertThat(update!!.scopedPaths).containsExactly(projectDir.toString())
        assertThat(update.threadIds).containsExactly("session-update-apply-patch")
        assertThat(update.mayHaveChangedProjectFiles).isTrue()
        assertThat(update.changedProjectFilePaths).containsExactly(changedFile.toString())
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun loadedCompletedMutatingToolDoesNotMakeLaterStatusOnlyUpdateProjectMutating() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-update-status-only")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-update-status-only.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T12:45:00.000Z", id = "session-update-status-only", cwd = projectDir),
          responseItemFunctionCall(
            timestamp = "2026-02-16T12:45:01.000Z",
            callId = "call-update-status-only",
            name = "exec_command",
          ),
          responseItemFunctionCallOutput(
            timestamp = "2026-02-16T12:45:02.000Z",
            callId = "call-update-status-only",
          ),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
        trailingRefreshDelayMs = 60_000L,
      )
      val loadedThreads = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(loadedThreads).hasSize(1)

      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        drainUpdateChannel(updates)
        Files.write(
          rollout,
          listOf("""{"timestamp":"2026-02-16T12:45:03.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Done"}}"""),
          StandardOpenOption.APPEND,
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) { updates.receive() }
        assertThat(update).isNotNull
        assertThat(update!!.scopedPaths).containsExactly(projectDir.toString())
        assertThat(update.threadIds).containsExactly("session-update-status-only")
        assertThat(update.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun fullRescanUpdateIncludesProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
        trailingRefreshDelayMs = 60_000L,
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        drainUpdateChannel(updates)
        sourceUpdates.emit(FileBackedSessionChangeSet(requiresFullRescan = true))

        val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) { updates.receive() }
        assertThat(update).isNotNull
        assertThat(update!!.scopedPaths).isNull()
        assertThat(update.threadIds).isNull()
        assertThat(update.mayHaveChangedProjectFiles).isTrue()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun refreshPingUpdateDoesNotIncludeProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
        trailingRefreshDelayMs = 60_000L,
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        drainUpdateChannel(updates)
        sourceUpdates.emit(FileBackedSessionChangeSet())

        val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) { updates.receive() }
        assertThat(update).isNotNull
        assertThat(update!!.scopedPaths).isNull()
        assertThat(update.threadIds).isNull()
        assertThat(update.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun scopedRolloutUpdateIncludesNonContributingSummaryHintForStandaloneSubAgent() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-update-sub-agent-summary-hint")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-update-sub-agent-summary-hint.jsonl")
      val escapedProjectDir = projectDir.toString().replace("\\", "\\\\")
      val subAgentMetaLine = buildString {
        append("""{"timestamp":"2026-02-16T13:00:00.000Z","type":"session_meta","payload":{""")
        append("\"id\":\"session-sub-agent-summary-hint\",")
        append("\"timestamp\":\"2026-02-16T13:00:00.000Z\",")
        append("\"cwd\":\"$escapedProjectDir\",")
        append(""""source":{"subagent":"compact"}}}""")
      }
      writeRollout(
        file = rollout,
        lines = listOf(
          subAgentMetaLine,
          """{"timestamp":"2026-02-16T13:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Summarize context"}}""",
          """{"timestamp":"2026-02-16T13:00:02.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Done"}}""",
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        drainUpdateChannel(updates)
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) { updates.receive() }
        assertThat(update).isNotNull
        assertThat(update!!.scopedPaths).containsExactly(projectDir.toString())
        assertThat(update.threadIds).containsExactly("session-sub-agent-summary-hint")
        assertThat(update.activityUpdatesByThreadId.getValue("session-sub-agent-summary-hint").activityReport)
          .isEqualTo(AgentThreadActivityReport(rowActivity = AgentThreadActivity.UNREAD, chromeActivity = null))
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUpdatesWhenExistingRolloutFileChanges() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-updates-modify")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-updates-modify.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T10:00:00.000Z", id = "session-updates-modify", cwd = projectDir),
          """{"timestamp":"2026-02-16T10:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Initial title"}}""",
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
      )
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initialThreads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(initialThreads).hasSize(1)
        assertThat(initialThreads.single().thread.title).isEqualTo("Initial title")

        drainUpdateChannel(updates)
        writeRollout(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-16T10:00:00.000Z", id = "session-updates-modify", cwd = projectDir),
            """{"timestamp":"2026-02-16T10:05:00.000Z","type":"event_msg","payload":{"type":"user_message","message":"Updated title"}}""",
          ),
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().thread.title).isEqualTo("Updated title")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsTrailingUpdateToCatchAppendAfterFirstWatcherEvent() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-updates-trailing")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-updates-trailing.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T10:00:00.000Z", id = "session-updates-trailing", cwd = projectDir),
          """{"timestamp":"2026-02-16T10:00:01.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
        trailingRefreshDelayMs = 1_000L,
      )
      val updates = Channel<Unit>(capacity = Channel.UNLIMITED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initialThreads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(initialThreads).hasSize(1)
        assertThat(initialThreads.single().activity).isEqualTo(CodexSessionActivity.PROCESSING)

        drainUpdateChannel(updates)
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val immediateUpdate = awaitWatcherUpdate(updates)
        assertThat(immediateUpdate).isTrue()
        val threadsBeforeTrailingUpdate = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(threadsBeforeTrailingUpdate).hasSize(1)
        assertThat(threadsBeforeTrailingUpdate.single().activity).isEqualTo(CodexSessionActivity.PROCESSING)

        Files.write(
          rollout,
          listOf("""{"timestamp":"2026-02-16T10:00:02.000Z","type":"event_msg","payload":{"type":"task_complete"}}"""),
          StandardOpenOption.APPEND,
        )

        val trailingUpdate = awaitWatcherUpdate(updates)
        assertThat(trailingUpdate).isTrue()
        val threadsAfterTrailingUpdate = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(threadsAfterTrailingUpdate).hasSize(1)
        assertThat(threadsAfterTrailingUpdate.single().activity).isEqualTo(CodexSessionActivity.READY)
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUpdatesForNonRolloutSessionEventAndRefreshesByStatDiff() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-updates-refresh-ping")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-updates-refresh-ping.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T10:00:00.000Z", id = "session-updates-refresh-ping", cwd = projectDir),
          """{"timestamp":"2026-02-16T10:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Initial title"}}""",
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
      )
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initialThreads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(initialThreads).hasSize(1)
        assertThat(initialThreads.single().thread.title).isEqualTo("Initial title")

        drainUpdateChannel(updates)
        writeRollout(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-16T10:00:00.000Z", id = "session-updates-refresh-ping", cwd = projectDir),
            """{"timestamp":"2026-02-16T10:05:00.000Z","type":"event_msg","payload":{"type":"user_message","message":"Updated title"}}""",
          ),
        )

        // Represents non-rollout file events (temp/rename artifacts) where path-level invalidation is unavailable.
        sourceUpdates.emit(FileBackedSessionChangeSet())

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().thread.title).isEqualTo("Updated title")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUpdatesWhenRolloutFileCreatedInNewNestedSessionsDirectory() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-updates-create")
      Files.createDirectories(projectDir)

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
      )
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        drainUpdateChannel(updates)
        val rollout = tempDir.resolve("sessions")
          .resolve("2026")
          .resolve("03")
          .resolve("01")
          .resolve("rollout-updates-create.jsonl")
        writeRollout(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-03-01T09:00:00.000Z", id = "session-updates-create", cwd = projectDir),
            """{"timestamp":"2026-03-01T09:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Created title"}}""",
          ),
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(threads.map { it.thread.id }).contains("session-updates-create")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun planItemAppendedWithoutWatcherEventBecomesNeedsInputAfterRefreshPing() {
    // Regression marker for the IJPL-244497 plan-mode "blue" case. When codex enters plan mode
    // it appends an item_completed/plan event to the rollout via its long-lived O_APPEND fd;
    // macOS FSEvents withholds the MODIFY notification (see MacOSXListeningWatchServiceTest).
    // The IDE relies on an external refresh ping (the per-tab rollout poll) to re-stat the file
    // and reparse it; this test simulates that ping and asserts the activity resolves to
    // NEEDS_INPUT, not PROCESSING.
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-plan-mode")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-plan-mode.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T12:00:00.000Z", id = "session-plan-mode", cwd = projectDir),
          """{"timestamp":"2026-02-16T12:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Draft a plan"}}""",
          """{"timestamp":"2026-02-16T12:00:02.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
      )
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initial = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(initial).hasSize(1)
        assertThat(initial.single().activity).isEqualTo(CodexSessionActivity.PROCESSING)
        assertThat(backend.resolveActiveThreadFilePaths(projectDir.toString(), "session-plan-mode")).containsExactly(rollout)

        drainUpdateChannel(updates)
        // Codex appends a plan item via its long-lived fd; FSEvents stays silent so no path-scoped
        // change set is available.
        writeRollout(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-16T12:00:00.000Z", id = "session-plan-mode", cwd = projectDir),
            """{"timestamp":"2026-02-16T12:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Draft a plan"}}""",
            """{"timestamp":"2026-02-16T12:00:02.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
            """{"timestamp":"2026-02-16T12:00:03.000Z","type":"event_msg","payload":{"type":"item_completed","item":{"type":"plan"}}}""",
          ),
        )
        // The per-tab rollout poll emits a path-less refresh ping. The stat-diff inside the index
        // detects the size change and reparses; activity must resolve to NEEDS_INPUT.
        sourceUpdates.emit(FileBackedSessionChangeSet())

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().activity).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun refreshesThreadAfterSameSizeRewriteWhenLastModifiedTimeIsReset() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-updates-same-size")
      Files.createDirectories(projectDir)

      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("16")
        .resolve("rollout-updates-same-size.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-16T11:00:00.000Z", id = "session-updates-same-size", cwd = projectDir),
          """{"timestamp":"2026-02-16T11:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"AAAA"}}""",
        ),
      )
      val originalLastModifiedTime = Files.getLastModifiedTime(rollout)

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        rolloutChangeSource = { sourceUpdates },
      )
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initialThreads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(initialThreads).hasSize(1)
        assertThat(initialThreads.single().thread.title).isEqualTo("AAAA")

        drainUpdateChannel(updates)
        writeRollout(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-16T11:00:00.000Z", id = "session-updates-same-size", cwd = projectDir),
            """{"timestamp":"2026-02-16T11:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"BBBB"}}""",
          ),
        )
        Files.setLastModifiedTime(rollout, originalLastModifiedTime)
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()

        val updatedThreads = backend.listThreads(path = projectDir.toString(), openProject = null)
        assertThat(updatedThreads).hasSize(1)
        assertThat(updatedThreads.single().thread.title).isEqualTo("BBBB")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun activeThreadUpdateSuppressesRepeatedUnchangedRolloutNotification() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-active-unchanged")
      Files.createDirectories(projectDir)
      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("17")
        .resolve("rollout-active-unchanged.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-17T10:00:00.000Z", id = "session-active-unchanged", cwd = projectDir),
          """{"timestamp":"2026-02-17T10:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Run checks"}}""",
          """{"timestamp":"2026-02-17T10:00:02.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        immediateFileChangeFlow = { flowOf(rollout, rollout) },
      )

      val updates = backend.activeThreadUpdateEvents(
        path = projectDir.toString(),
        threadId = "session-active-unchanged",
      ).toList()

      assertThat(updates).hasSize(1)
      val update = updates.single()
      assertThat(update.threadIds).isNull()
      assertThat(update.activityUpdatesByThreadId.getValue("session-active-unchanged").activityReport)
        .isEqualTo(AgentThreadActivityReport(AgentThreadActivity.PROCESSING))
      assertThat(update.presentationUpdatesByThreadId.getValue("session-active-unchanged").title).isEqualTo("Run checks")
      assertThat(update.mayHaveChangedProjectFiles).isFalse()
    }
  }

  @Test
  fun activeThreadUpdateKeepsProjectFileEvidence() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-active-apply-patch")
      val changedFile = projectDir.resolve("src").resolve("Main.kt")
      Files.createDirectories(projectDir)
      val patch = """*** Begin Patch
*** Update File: src/Main.kt
@@
+fun main() {}
*** End Patch"""
      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("17")
        .resolve("rollout-active-apply-patch.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-17T10:30:00.000Z", id = "session-active-apply-patch", cwd = projectDir),
          responseItemFunctionCall(
            timestamp = "2026-02-17T10:30:01.000Z",
            callId = "call-active-apply-patch",
            name = "apply_patch",
            arguments = """{"patch":"${jsonString(patch)}"}""",
          ),
          responseItemFunctionCallOutput(
            timestamp = "2026-02-17T10:30:02.000Z",
            callId = "call-active-apply-patch",
          ),
        ),
      )

      val backend = CodexRolloutSessionBackend(
        codexHomeProvider = { tempDir },
        immediateFileChangeFlow = { flowOf(rollout) },
      )

      val update = backend.activeThreadUpdateEvents(
        path = projectDir.toString(),
        threadId = "session-active-apply-patch",
      ).toList().single()

      assertThat(update.threadIds).isNull()
      assertThat(update.mayHaveChangedProjectFiles).isTrue()
      assertThat(update.changedProjectFilePaths).containsExactly(changedFile.toString())
    }
  }
}

private val WATCHER_UPDATE_WAIT_TIMEOUT = 5.seconds
private const val COST_ROLLOUT_FIXTURE_PATH = "rollout/cost/repeated-total-token-usage.jsonl"

private suspend fun awaitWatcherUpdate(
  updates: Channel<Unit>,
): Boolean {
  val update = withTimeoutOrNull(WATCHER_UPDATE_WAIT_TIMEOUT) {
    updates.receive()
  }
  return update != null
}

private fun <T> drainUpdateChannel(updates: Channel<T>) {
  while (true) {
    if (!updates.tryReceive().isSuccess) {
      break
    }
  }
}

private fun sessionMetaLine(timestamp: String, id: String, cwd: Path): String {
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$id","timestamp":"$timestamp","cwd":"${
    cwd.toString().replace("\\", "\\\\")
  }"}}"""
}

private fun responseItemFunctionCall(
  timestamp: String,
  callId: String,
  name: String = "request_user_input",
  arguments: String = "{}",
  turnId: String? = null,
): String {
  val turnIdField = turnId?.let { ""","turn_id":"$it"""" }.orEmpty()
  val escapedArguments = jsonString(arguments)
  return """{"timestamp":"$timestamp","type":"response_item","payload":{"type":"function_call","name":"$name","arguments":"$escapedArguments","call_id":"$callId"$turnIdField}}"""
}

private fun responseItemFunctionCallOutput(timestamp: String, callId: String, output: String = "{}"): String {
  return """{"timestamp":"$timestamp","type":"response_item","payload":{"type":"function_call_output","call_id":"$callId","output":"${
    jsonString(output)
  }"}}"""
}

private fun approvalEventLine(timestamp: String, type: String, callId: String? = null, turnId: String? = null): String {
  val callIdField = callId?.let { ""","call_id":"$it"""" }.orEmpty()
  val turnIdField = turnId?.let { ""","turn_id":"$it"""" }.orEmpty()
  return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"$type"$callIdField$turnIdField}}"""
}

private fun jsonString(value: String): String {
  return value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
}

private fun turnCompleteLine(timestamp: String, turnId: String? = null): String {
  val turnIdField = turnId?.let { ""","turn_id":"$it"""" }.orEmpty()
  return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"turn_complete"$turnIdField}}"""
}

private fun itemCompletedPlan(timestamp: String, turnId: String? = null): String {
  val turnIdField = turnId?.let { ""","turn_id":"$it"""" }.orEmpty()
  return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"item_completed"$turnIdField,"item":{"type":"Plan","id":"turn-plan","text":"Plan text"}}}"""
}

private fun sessionMetaLineWithoutId(cwd: Path): String {
  val timestamp = "2026-02-13T10:00:00.000Z"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"timestamp":"$timestamp","cwd":"${
    cwd.toString().replace("\\", "\\\\")
  }"}}"""
}

private fun subAgentSessionMetaLine(timestamp: String, id: String, cwd: Path, parentThreadId: String): String {
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$id","timestamp":"$timestamp","cwd":"${
    cwd.toString().replace("\\", "\\\\")
  }","source":{"subagent":{"thread_spawn":{"parent_thread_id":"$parentThreadId","depth":1}}}}}"""
}

private fun tokenUsageLine(
  timestamp: String,
  model: String,
  totalInputTokens: Long,
  cachedInputTokens: Long,
  outputTokens: Long,
  reasoningOutputTokens: Long = 0,
): String {
  return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"token_count","model":"$model","info":{"total_token_usage":{"input_tokens":$totalInputTokens,"cached_input_tokens":$cachedInputTokens,"output_tokens":$outputTokens,"reasoning_output_tokens":$reasoningOutputTokens}}}}"""
}

private fun writeRollout(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines)
}

private fun loadRolloutFixture(projectDir: Path): List<String> {
  val fixtureText = checkNotNull(CodexRolloutSessionBackendTest::class.java.classLoader.getResource(COST_ROLLOUT_FIXTURE_PATH)) {
    "Missing fixture resource: $COST_ROLLOUT_FIXTURE_PATH"
  }.readText()
  return fixtureText
    .replace("__PROJECT_DIR__", projectDir.toString().replace("\\", "\\\\"))
    .lineSequence()
    .filter(String::isNotBlank)
    .toList()
}

private data class ActivityCase(
  @JvmField val id: String,
  @JvmField val eventLines: List<String>,
  @JvmField val expected: CodexSessionActivity,
  @JvmField val expectedRequiresResponse: Boolean = false,
)
