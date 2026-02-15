// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class CodexRolloutSessionBackendTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun mapsSessionMetaIdAndUnreadPrecedence() {
    runBlocking {
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
      assertThat(thread.activity).isEqualTo(CodexSessionActivity.UNREAD)
    }
  }

  @Test
  fun ignoresRolloutWithoutSessionMetaId() {
    runBlocking {
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
  fun mapsReviewingFromReviewModeItems() {
    runBlocking {
      val projectDir = tempDir.resolve("project-b")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-2026-02-13T11-00-00-review.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T11:00:00.000Z",
            id = "session-review",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-13T11:00:05.000Z","type":"event_msg","payload":{"type":"item_completed","item":{"type":"enteredReviewMode"}}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      val thread = threads.single()
      assertThat(thread.thread.id).isEqualTo("session-review")
      assertThat(thread.activity).isEqualTo(CodexSessionActivity.REVIEWING)
    }
  }

  @Test
  fun filtersByCwdAndMarksReadyAfterCompletedTask() {
    runBlocking {
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
  fun marksProcessingWhenTaskStartedWithoutCompletion() {
    runBlocking {
      val projectDir = tempDir.resolve("project-processing")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-processing.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T13:00:00.000Z",
            id = "session-processing",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-13T13:00:05.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().activity).isEqualTo(CodexSessionActivity.PROCESSING)
    }
  }

  @Test
  fun marksUnreadWhenPendingUserInputRequested() {
    runBlocking {
      val projectDir = tempDir.resolve("project-pending-input")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-pending-input.jsonl"),
        lines = listOf(
          sessionMetaLine(
            timestamp = "2026-02-13T14:00:00.000Z",
            id = "session-pending-input",
            cwd = projectDir,
          ),
          """{"timestamp":"2026-02-13T14:00:02.000Z","type":"event_msg","payload":{"type":"requestUserInput"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().activity).isEqualTo(CodexSessionActivity.UNREAD)
    }
  }

  @Test
  fun mapsBranchFromSessionMetaPayload() {
    runBlocking {
      val projectDir = tempDir.resolve("project-branch")
      Files.createDirectories(projectDir)
      writeRollout(
        file = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("13")
          .resolve("rollout-branch.jsonl"),
        lines = listOf(
          """{"timestamp":"2026-02-13T15:00:00.000Z","type":"session_meta","payload":{"id":"session-branch","timestamp":"2026-02-13T15:00:00.000Z","cwd":"${projectDir.toString().replace("\\", "\\\\")}","git":{"branch":"feature/codex-rollout"}}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().thread.gitBranch).isEqualTo("feature/codex-rollout")
    }
  }
}

private fun sessionMetaLine(timestamp: String, id: String, cwd: Path): String {
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$id","timestamp":"$timestamp","cwd":"${cwd.toString().replace("\\", "\\\\")}"}}"""
}

private fun sessionMetaLineWithoutId(cwd: Path): String {
  val timestamp = "2026-02-13T10:00:00.000Z"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"timestamp":"$timestamp","cwd":"${cwd.toString().replace("\\", "\\\\")}"}}"""
}

private fun writeRollout(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines)
}

