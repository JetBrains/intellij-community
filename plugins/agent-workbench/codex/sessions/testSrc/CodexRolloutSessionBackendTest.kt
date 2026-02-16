// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
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

  @Test
  fun prefetchThreadsMapsPerResolvedPath() {
    runBlocking {
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
    runBlocking {
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
    runBlocking {
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
    runBlocking {
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
          """{"timestamp":"2026-02-14T12:00:02.000Z","type":"event_msg","payload":{"type":"user_message","message":"<environment_context>\n<cwd>${projectDir.toString().replace("\\", "\\\\")}</cwd>"}}""",
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
  fun prefersThreadNameUpdatedEventForTitle() {
    runBlocking {
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
  fun skipsMalformedJsonLineAndKeepsParsingLaterEvents() {
    runBlocking {
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
