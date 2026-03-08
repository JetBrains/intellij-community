// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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

      val source = createSource(
        projectDir = projectDir,
        appServerHints = mapOf(
          projectDir.toString() to refreshHints(
            THREAD_ID to refreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
          )
        ),
      )

      assertThat(refreshActivities(source, projectDir))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.PROCESSING))
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

      val source = createSource(
        projectDir = projectDir,
        appServerHints = mapOf(
          projectDir.toString() to refreshHints(
            THREAD_ID to refreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
          )
        ),
      )

      assertThat(refreshActivities(source, projectDir))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.REVIEWING))
    }
  }

  @Test
  fun rolloutRequestUserInputProducesUnreadHint() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-user-input")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T12:00:01.000Z", type = "request_user_input"),
        ),
      )

      val source = createSource(
        projectDir = projectDir,
        appServerHints = mapOf(
          projectDir.toString() to refreshHints(
            THREAD_ID to refreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
          )
        ),
      )

      assertThat(refreshActivities(source, projectDir))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.UNREAD))
    }
  }

  @Test
  fun appServerResponseRequiredUnreadStillWinsOverNewerRolloutProcessing() {
    runBlocking(Dispatchers.Default) {
      val projectDir = createProjectDir("project-response-required")
      writeRollout(
        projectDir = projectDir,
        lines = listOf(
          eventMsg(timestamp = "2026-03-08T13:00:01.000Z", type = "task_started"),
        ),
      )

      val source = createSource(
        projectDir = projectDir,
        appServerHints = mapOf(
          projectDir.toString() to refreshHints(
            THREAD_ID to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 100L,
              responseRequired = true,
            )
          )
        ),
      )

      assertThat(refreshActivities(source, projectDir))
        .containsExactlyEntriesOf(mapOf(THREAD_ID to AgentThreadActivity.UNREAD))
    }
  }
}

private const val THREAD_ID = "thread-1"

private fun createSource(
  projectDir: Path,
  appServerHints: Map<String, CodexRefreshHints>,
): CodexSessionSource {
  val projectPath = projectDir.toString()
  return CodexSessionSource(
    backend = object : CodexSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
        return if (path == projectPath) {
          listOf(
            CodexBackendThread(
              thread = CodexThread(
                id = THREAD_ID,
                title = "Thread 1",
                updatedAt = 100L,
                archived = false,
              )
            )
          )
        }
        else {
          emptyList()
        }
      }
    },
    appServerRefreshHintsProvider = staticHintsProvider(appServerHints),
    rolloutRefreshHintsProvider = CodexRolloutRefreshHintsProvider(
      rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { projectDir.parent })
    ),
  )
}

private suspend fun refreshActivities(source: CodexSessionSource, projectDir: Path): Map<String, AgentThreadActivity> {
  val projectPath = projectDir.toString()
  val listedThreads = source.listThreadsFromClosedProject(projectPath)
  assertThat(listedThreads.map { it.id }).containsExactly(THREAD_ID)

  return source.prefetchRefreshHints(
    paths = listOf(projectPath),
    knownThreadIdsByPath = mapOf(projectPath to listedThreads.mapTo(HashSet(listedThreads.size)) { it.id }),
  ).getValue(projectPath).activityByThreadId
}

private fun staticHintsProvider(hintsByPath: Map<String, CodexRefreshHints>): CodexRefreshHintsProvider {
  return object : CodexRefreshHintsProvider {
    override val updates = emptyFlow<Unit>()

    override suspend fun prefetchRefreshHints(
      paths: List<String>,
      knownThreadIdsByPath: Map<String, Set<String>>,
    ): Map<String, CodexRefreshHints> {
      return hintsByPath.filterKeys(paths::contains)
    }
  }
}

private fun refreshHints(vararg entries: Pair<String, CodexRefreshActivityHint>): CodexRefreshHints {
  return CodexRefreshHints(activityHintsByThreadId = linkedMapOf(*entries))
}

private fun refreshHint(
  activity: AgentThreadActivity,
  updatedAt: Long,
  responseRequired: Boolean = false,
): CodexRefreshActivityHint {
  return CodexRefreshActivityHint(
    activity = activity,
    updatedAt = updatedAt,
    responseRequired = responseRequired,
  )
}

private fun createProjectDir(root: Path, name: String): Path {
  val projectDir = root.resolve(name)
  Files.createDirectories(projectDir)
  return projectDir
}

private fun CodexSessionSourceRolloutIntegrationTest.createProjectDir(name: String): Path {
  return createProjectDir(tempDir, name)
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
