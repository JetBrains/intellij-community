// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.sessions.backend.CodexSessionActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionCostKind
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionSourceRolloutIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun rolloutTaskStartedAndAgentMessageDoesNotOverrideAppServerReadyHint() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-processing")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T10:00:01.000Z", type = "task_started"),
          eventMsg(timestamp = "2026-03-08T10:00:02.000Z", type = "agent_message", message = "Working"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        appServerHints = mapOf(
          projectDir.toString() to testRefreshHintsOf(
            THREAD_ID to testRefreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
          )
        ),
      )

      assertThat(testRefreshActivities(source, projectDir, listOf(THREAD_ID)))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.READY))
    }
  }

  @Test
  fun startupListKeepsAppServerActivityWithoutRolloutFallback() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-list-processing")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T10:10:01.000Z", type = "task_started"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
      )

      val listedThreads = source.listThreadsFromClosedProject(projectDir.toString())

      assertThat(listedThreads).hasSize(1)
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun rolloutUsageAddsEstimatedCostToVisibleAppServerThread() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-rollout-cost")
      writeRolloutFixture(projectDir)

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf("019e50fc-cf2c-77a1-8055-5fdc85fdd56e"),
        calculateCost = { usage ->
          AgentSessionCost(
            amountUsd = BigDecimal(usage.inputTokens + usage.outputTokens + usage.cacheReadTokens),
            kind = AgentSessionCostKind.ESTIMATED,
            matchedModelId = usage.modelId,
          )
        },
      )

      val listedThreads = source.listThreadsFromClosedProject(projectDir.toString())
      val loadedCosts = source.loadThreadCosts(projectDir.toString(), listedThreads)

      assertThat(listedThreads).hasSize(1)
      assertThat(listedThreads.single().cost).isNull()
      assertThat(loadedCosts.getValue(listedThreads.single().id)).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal("613250"),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5.4",
        )
      )
    }
  }

  @Test
  fun rolloutTopLevelTurnContextProvidesFallbackModelForTokenUsage() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-rollout-top-level-turn-context")
      writeRolloutFile(
        fileName = "rollout-top-level-turn-context.jsonl",
        lines = listOf(
          sessionMetaLine(cwd = projectDir),
          turnContextLine(cwd = projectDir),
          tokenUsageLineWithoutModel(
            timestamp = "2026-03-08T10:05:01.000Z",
            totalInputTokens = 100,
            cachedInputTokens = 40,
            outputTokens = 5,
          ),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        calculateCost = { usage ->
          AgentSessionCost(
            amountUsd = BigDecimal.valueOf(usage.inputTokens + usage.outputTokens + usage.cacheReadTokens),
            kind = AgentSessionCostKind.ESTIMATED,
            matchedModelId = usage.modelId,
          )
        },
      )

      val listedThreads = source.listThreadsFromClosedProject(projectDir.toString())
      val loadedCosts = source.loadThreadCosts(projectDir.toString(), listedThreads)

      assertThat(listedThreads).hasSize(1)
      assertThat(loadedCosts.getValue(listedThreads.single().id)).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(105),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5.4",
        )
      )
    }
  }

  @Test
  fun rolloutSubAgentUsageContributesToParentVisibleSessionCost() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-rollout-subagent-cost")
      writeRolloutFile(
        fileName = "rollout-parent-thread.jsonl",
        lines = listOf(
          sessionMetaLine(cwd = projectDir),
          tokenUsageLine(
            timestamp = "2026-03-08T10:05:01.000Z",
            model = "gpt-5",
            totalInputTokens = 100,
            cachedInputTokens = 40,
            outputTokens = 5,
          ),
        ),
      )
      writeRolloutFile(
        fileName = "rollout-child-thread.jsonl",
        lines = listOf(
          subAgentSessionMetaLine(cwd = projectDir),
          tokenUsageLine(
            timestamp = "2026-03-08T10:05:02.000Z",
            model = "gpt-5-mini",
            totalInputTokens = 60,
            cachedInputTokens = 10,
            outputTokens = 7,
          ),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        calculateCost = { usage ->
          AgentSessionCost(
            amountUsd = BigDecimal.valueOf(usage.inputTokens + usage.outputTokens + usage.cacheReadTokens),
            kind = AgentSessionCostKind.EXACT,
            matchedModelId = usage.modelId,
          )
        },
      )

      val listedThreads = source.listThreadsFromClosedProject(projectDir.toString())
      val loadedCosts = source.loadThreadCosts(projectDir.toString(), listedThreads)

      assertThat(listedThreads).hasSize(1)
      assertThat(listedThreads.single().cost).isNull()
      assertThat(loadedCosts.getValue(listedThreads.single().id)).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(172),
          kind = AgentSessionCostKind.EXACT,
          matchedModelId = null,
        )
      )
    }
  }

  @Test
  fun startupListKeepsStaleAppServerProcessingUntilHintRefreshRuns() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-list-complete-ready")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T10:15:01.000Z", type = "task_started"),
          eventMsg(timestamp = "2026-03-08T10:15:02.000Z", type = "task_complete"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        backendThreadCustomizer = { backendThread -> backendThread.copy(activity = CodexSessionActivity.PROCESSING) },
      )

      val listedThreads = source.listThreadsFromClosedProject(projectDir.toString())

      assertThat(listedThreads).hasSize(1)
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun rolloutTaskCompleteDoesNotOverrideAppServerProcessingRefreshHint() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-refresh-complete-unread")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T10:16:00.000Z", type = "user_message", message = "Run it"),
          eventMsg(timestamp = "2026-03-08T10:16:01.000Z", type = "task_started"),
          eventMsg(timestamp = "2026-03-08T10:16:02.000Z", type = "agent_message", message = "Done"),
          eventMsg(timestamp = "2026-03-08T10:16:03.000Z", type = "task_complete"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        appServerHints = mapOf(
          projectDir.toString() to testRefreshHintsOf(
            THREAD_ID to testRefreshHint(activity = AgentThreadActivity.PROCESSING, updatedAt = 100L)
          )
        ),
      )

      assertThat(testRefreshActivities(source, projectDir, listOf(THREAD_ID)))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.PROCESSING))
    }
  }

  @Test
  fun unscopedRefreshKeepsAppServerActivityUntilHintsApply() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-refresh-processing")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T10:20:01.000Z", type = "task_started"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
      )
      val projectPath = projectDir.toString()

      val refreshResult = source.refreshThreads(
        AgentSessionSourceRefreshRequest(
          paths = listOf(projectPath),
          updateEvent = AgentSessionSourceUpdateEvent.hintsChanged(),
        )
      )

      assertThat(refreshResult.completeThreadsByPath[projectPath]).hasSize(1)
      assertThat(refreshResult.completeThreadsByPath.getValue(projectPath).single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun startupListKeepsAppServerResponseRequiredActivityWithoutRolloutFallback() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-list-response-required")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T10:30:01.000Z", type = "task_started"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        backendThreadCustomizer = { backendThread ->
          backendThread.copy(activity = CodexSessionActivity.NEEDS_INPUT, requiresResponse = true)
        },
      )

      val listedThreads = source.listThreadsFromClosedProject(projectDir.toString())

      assertThat(listedThreads).hasSize(1)
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.NEEDS_INPUT)
    }
  }

  @Test
  fun currentAppServerResponseRequiredListActivityWinsOverRolloutProcessing() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-list-current-response-required")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T10:35:01.000Z", type = "task_started"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        backendThreadCustomizer = { backendThread ->
          backendThread.copy(
            thread = backendThread.thread.copy(updatedAt = Instant.parse("2026-03-08T10:35:01.000Z").toEpochMilli()),
            activity = CodexSessionActivity.NEEDS_INPUT,
            requiresResponse = true,
          )
        },
      )

      val listedThreads = source.listThreadsFromClosedProject(projectDir.toString())

      assertThat(listedThreads).hasSize(1)
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.NEEDS_INPUT)
    }
  }

  @Test
  fun rolloutReviewModeDoesNotOverrideAppServerReadyHint() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-review")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T11:00:01.000Z", type = "entered_review_mode"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        appServerHints = mapOf(
          projectDir.toString() to testRefreshHintsOf(
            THREAD_ID to testRefreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
          )
        ),
      )

      assertThat(testRefreshActivities(source, projectDir, listOf(THREAD_ID)))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.READY))
    }
  }

  @Test
  fun rolloutRequestUserInputDoesNotOverrideAppServerReadyHint() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-user-input")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T12:00:01.000Z", type = "request_user_input"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        appServerHints = mapOf(
          projectDir.toString() to testRefreshHintsOf(
            THREAD_ID to testRefreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
          )
        ),
      )

      assertThat(testRefreshActivities(source, projectDir, listOf(THREAD_ID)))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.READY))
    }
  }

  @Test
  fun rolloutProcessingDoesNotOverrideAppServerResponseRequiredHint() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-response-required")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T13:00:01.000Z", type = "task_started"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        appServerHints = mapOf(
          projectDir.toString() to testRefreshHintsOf(
            THREAD_ID to testRefreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 100L,
              responseRequired = true,
            )
          )
        ),
      )

      assertThat(testRefreshActivities(source, projectDir, listOf(THREAD_ID)))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.NEEDS_INPUT))
    }
  }

  @Test
  fun currentAppServerResponseRequiredHintStillWinsOverRolloutProcessing() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-current-response-required")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T13:10:01.000Z", type = "task_started"),
        ),
      )

      val source = testCreateSource(
        projectDir = projectDir,
        codexHome = tempDir,
        threadIds = listOf(THREAD_ID),
        appServerHints = mapOf(
          projectDir.toString() to testRefreshHintsOf(
            THREAD_ID to testRefreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = Instant.parse("2026-03-08T13:10:01.000Z").toEpochMilli(),
              responseRequired = true,
            )
          )
        ),
      )

      assertThat(testRefreshActivities(source, projectDir, listOf(THREAD_ID)))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.NEEDS_INPUT))
    }
  }
}

private const val THREAD_ID = "thread-1"
private const val COST_ROLLOUT_FIXTURE_PATH = "rollout/cost/repeated-total-token-usage.jsonl"

private fun CodexSessionSourceRolloutIntegrationTest.createProjectDir(name: String): Path {
  return testCreateProjectDir(tempDir, name)
}

private fun CodexSessionSourceRolloutIntegrationTest.writeRollout(projectDir: Path, lines: List<String>) {
  writeRolloutFile(
    fileName = "rollout-${projectDir.fileName}.jsonl",
    lines = listOf(sessionMetaLine(cwd = projectDir)) + lines,
  )
}

private fun CodexSessionSourceRolloutIntegrationTest.writeRolloutFile(fileName: String, lines: List<String>) {
  val rolloutDir = tempDir.resolve("sessions").resolve("2026").resolve("03").resolve("08")
  val rolloutFile = rolloutDir.resolve(fileName)
  Files.createDirectories(rolloutDir)
  Files.write(rolloutFile, lines)
}

private fun CodexSessionSourceRolloutIntegrationTest.writeRolloutFixture(projectDir: Path) {
  val rolloutDir = tempDir.resolve("sessions").resolve("2026").resolve("03").resolve("08")
  val rolloutFile = rolloutDir.resolve("rollout-${projectDir.fileName}.jsonl")
  Files.createDirectories(rolloutDir)
  Files.write(rolloutFile, loadRolloutFixture(projectDir))
}

private fun loadRolloutFixture(projectDir: Path): List<String> {
  val fixtureText = checkNotNull(CodexSessionSourceRolloutIntegrationTest::class.java.classLoader.getResource(COST_ROLLOUT_FIXTURE_PATH)) {
    "Missing fixture resource: $COST_ROLLOUT_FIXTURE_PATH"
  }.readText()
  return fixtureText
    .replace("__PROJECT_DIR__", projectDir.toJsonEscapedString())
    .lineSequence()
    .filter(String::isNotBlank)
    .toList()
}

private fun sessionMetaLine(cwd: Path): String {
  val timestamp = "2026-03-08T10:00:00.000Z"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$THREAD_ID","timestamp":"$timestamp","cwd":"${cwd.toJsonEscapedString()}"}}"""
}

private fun subAgentSessionMetaLine(cwd: Path): String {
  val timestamp = "2026-03-08T10:00:00.000Z"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"subagent-1","timestamp":"$timestamp","cwd":"${cwd.toJsonEscapedString()}","source":{"subagent":{"thread_spawn":{"parent_thread_id":"$THREAD_ID","depth":1}}}}}"""
}

private fun eventMsg(timestamp: String, type: String, message: String? = null): String {
  val messageField = if (message == null) "" else ",\"message\":\"$message\""
  return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"$type"$messageField}}"""
}

private fun turnContextLine(cwd: Path): String {
  val timestamp = "2026-03-08T10:05:00.000Z"
  return """{"timestamp":"$timestamp","type":"turn_context","payload":{"cwd":"${cwd.toJsonEscapedString()}","model":"gpt-5.4"}}"""
}

private fun Path.toJsonEscapedString(): String {
  return toString().replace("\\", "\\\\")
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

private fun tokenUsageLineWithoutModel(
  timestamp: String,
  totalInputTokens: Long,
  cachedInputTokens: Long,
  outputTokens: Long,
  reasoningOutputTokens: Long = 0,
): String {
  return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":$totalInputTokens,"cached_input_tokens":$cachedInputTokens,"output_tokens":$outputTokens,"reasoning_output_tokens":$reasoningOutputTokens}}}}"""
}
