// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionSourceRolloutIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun rolloutTaskStartedAndAgentMessageKeepsProcessingWhenAppServerIsReady() {
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
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.PROCESSING))
    }
  }

  @Test
  fun rolloutTaskStartedOverridesReadyAppServerListActivity() {
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
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun rolloutTaskCompleteClearsStaleAppServerProcessingListActivityToReady() {
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
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun rolloutTaskCompleteClearsStaleAppServerProcessingRefreshHintToUnread() {
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
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.UNREAD))
    }
  }

  @Test
  fun rolloutHintRefreshUpdatesReadyAppServerThreadWithoutAppServerNotification() {
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
          updateEvent = AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.HINTS_CHANGED),
        )
      )

      assertThat(refreshResult.completeThreadsByPath[projectPath]).hasSize(1)
      assertThat(refreshResult.completeThreadsByPath.getValue(projectPath).single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun newerRolloutProcessingOverridesStaleAppServerResponseRequiredListActivity() {
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
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
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
  fun rolloutEnteredReviewModeOverridesReadyAppServerHint() {
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
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.REVIEWING))
    }
  }

  @Test
  fun rolloutRequestUserInputProducesNeedsInputHint() {
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
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.NEEDS_INPUT))
    }
  }

  @Test
  fun rolloutProcessingRefreshOverridesStaleAppServerResponseRequiredHint() {
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
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.PROCESSING))
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

private fun CodexSessionSourceRolloutIntegrationTest.createProjectDir(name: String): Path {
  return testCreateProjectDir(tempDir, name)
}

private fun CodexSessionSourceRolloutIntegrationTest.writeRollout(projectDir: Path, lines: List<String>) {
  val rolloutDir = tempDir.resolve("sessions").resolve("2026").resolve("03").resolve("08")
  val rolloutFile = rolloutDir.resolve("rollout-${projectDir.fileName}.jsonl")
  Files.createDirectories(rolloutDir)
  Files.write(
    rolloutFile,
    listOf(sessionMetaLine(cwd = projectDir)) + lines,
  )
}

private fun sessionMetaLine(cwd: Path): String {
  val timestamp = "2026-03-08T10:00:00.000Z"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$THREAD_ID","timestamp":"$timestamp","cwd":"${cwd.toString().replace("\\", "\\\\")}"}}"""
}

private fun eventMsg(timestamp: String, type: String, message: String? = null): String {
  val messageField = if (message == null) "" else ",\"message\":\"$message\""
  return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"$type"$messageField}}"""
}
