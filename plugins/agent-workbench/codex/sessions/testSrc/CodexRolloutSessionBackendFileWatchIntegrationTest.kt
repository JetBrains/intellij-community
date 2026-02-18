// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CodexRolloutSessionBackendFileWatchIntegrationTest {
  // Primary owner for end-to-end watcher wiring (filesystem event -> backend update -> refreshed title).
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun backendWatcherRefreshesTitleAfterAtomicRolloutReplace() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-watch")
      Files.createDirectories(projectDir)
      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("18")
        .resolve("rollout-watch.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-18T11:00:00.000Z", id = "session-watch", cwd = projectDir),
          """{"timestamp":"2026-02-18T11:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Initial title"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
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
        replaceRolloutAtomically(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-18T11:00:00.000Z", id = "session-watch", cwd = projectDir),
            """{"timestamp":"2026-02-18T11:00:02.000Z","type":"event_msg","payload":{"type":"user_message","message":"Updated via atomic replace"}}""",
          ),
        )

        val updateReceived = awaitWatcherUpdate(updates)
        assertThat(updateReceived).isTrue()

        val refreshedTitle = awaitThreadTitle(
          backend = backend,
          projectPath = projectDir.toString(),
          threadId = "session-watch",
        )
        assertThat(refreshedTitle).isEqualTo("Updated via atomic replace")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun backendWatcherRefreshesTitleAfterInPlaceRewrite() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-watch-inplace")
      Files.createDirectories(projectDir)
      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("18")
        .resolve("rollout-watch-inplace.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-18T12:00:00.000Z", id = "session-watch-inplace", cwd = projectDir),
          """{"timestamp":"2026-02-18T12:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Initial title"}}""",
        ),
      )

      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
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
            sessionMetaLine(timestamp = "2026-02-18T12:00:00.000Z", id = "session-watch-inplace", cwd = projectDir),
            """{"timestamp":"2026-02-18T12:00:03.000Z","type":"event_msg","payload":{"type":"user_message","message":"Updated in place"}}""",
          ),
        )

        val updateReceived = awaitWatcherUpdate(updates)
        assertThat(updateReceived).isTrue()

        val refreshedTitle = awaitThreadTitle(
          backend = backend,
          projectPath = projectDir.toString(),
          threadId = "session-watch-inplace",
        )
        assertThat(refreshedTitle).isEqualTo("Updated in place")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }
}

private val FILE_WATCH_UPDATE_TIMEOUT = 8.seconds

private suspend fun awaitWatcherUpdate(updates: Channel<Unit>): Boolean {
  val update = withTimeoutOrNull(FILE_WATCH_UPDATE_TIMEOUT) {
    updates.receive()
  }
  return update != null
}

private suspend fun awaitThreadTitle(
  backend: CodexRolloutSessionBackend,
  projectPath: String,
  threadId: String,
): String {
  var resolvedTitle: String? = null
  withTimeoutOrNull(FILE_WATCH_UPDATE_TIMEOUT) {
    while (true) {
      val threads = backend.listThreads(path = projectPath, openProject = null)
      val thread = threads.firstOrNull { it.thread.id == threadId }
      if (thread != null) {
        resolvedTitle = thread.thread.title
        break
      }
      delay(100.milliseconds)
    }
  }
  return requireNotNull(resolvedTitle) { "Timed out waiting for thread $threadId title" }
}

private fun drainUpdateChannel(updates: Channel<Unit>) {
  while (true) {
    if (!updates.tryReceive().isSuccess) {
      break
    }
  }
}

private fun sessionMetaLine(timestamp: String, id: String, cwd: Path): String {
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$id","timestamp":"$timestamp","cwd":"${cwd.toString().replace("\\", "\\\\")}"}}"""
}

private fun writeRollout(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines)
}

private fun replaceRolloutAtomically(file: Path, lines: List<String>) {
  val temporaryFile = file.resolveSibling("${file.fileName}.tmp")
  Files.write(temporaryFile, lines)
  Files.move(
    temporaryFile,
    file,
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.ATOMIC_MOVE,
  )
}
