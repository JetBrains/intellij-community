// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.claude.sessions.backend.store.ClaudeStoreSessionBackend
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
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ClaudeStoreSessionBackendFileWatchIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun backendWatcherRefreshesThreadsAfterJsonlRewrite() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-watch"
      val encodedPath = "-work-project-watch"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-watch.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-18T11:00:00.000Z", "session-watch", projectPath, "Initial title"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initialThreads = backend.listThreads(path = projectPath, openProject = null)
        assertThat(initialThreads).hasSize(1)
        assertThat(initialThreads.single().title).isEqualTo("Initial title")
        assertThat(initialThreads.single().activity).isEqualTo(ClaudeSessionActivity.READY)

        primeWatcher(updates, jsonl)

        writeJsonl(
          jsonl,
          listOf(
            claudeUserLine("2026-02-18T11:00:00.000Z", "session-watch", projectPath, "Updated title"),
            claudeAssistantLine("2026-02-18T11:00:01.000Z", "session-watch", projectPath, "Response"),
          ),
        )

        awaitWatcherUpdate(updates)

        val refreshedThread = awaitThread(backend, projectPath, "session-watch")
        assertThat(refreshedThread).isNotNull
        assertThat(refreshedThread!!.title).isEqualTo("Updated title")
        assertThat(refreshedThread.activity).isEqualTo(ClaudeSessionActivity.READY)
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun backendWatcherTracksAppendDrivenBackgroundTaskLifecycle() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-watch-append"
      val sessionId = "session-watch-append"
      val gitBranch = "feature/claude-status"
      val backgroundTaskId = "task-123"
      val encodedPath = "-work-project-watch-append"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("$sessionId.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine(
            timestamp = "2026-02-19T11:00:00.000Z",
            sessionId = sessionId,
            cwd = projectPath,
            content = "Initial title",
            gitBranch = gitBranch,
          ),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initialThread = backend.listThreads(path = projectPath, openProject = null).single()
        assertThat(initialThread.activity).isEqualTo(ClaudeSessionActivity.READY)
        assertThat(initialThread.gitBranch).isEqualTo(gitBranch)

        primeWatcher(updates, jsonl)

        appendJsonl(
          jsonl,
          listOf(claudeAssistantPartialLine("2026-02-19T11:00:01.000Z", sessionId, projectPath, "Thinking")),
        )

        assertThat(awaitWatcherUpdate(updates)).isTrue()

        val partialThread = awaitThread(
          backend = backend,
          projectPath = projectPath,
          threadId = sessionId,
        ) { thread ->
          thread.activity == ClaudeSessionActivity.PROCESSING &&
          thread.updatedAt == Instant.parse("2026-02-19T11:00:01.000Z").toEpochMilli()
        }
        assertThat(partialThread).isNotNull
        assertThat(partialThread!!.gitBranch).isEqualTo(gitBranch)

        appendJsonl(
          jsonl,
          listOf(
            claudeAssistantToolUseLine("2026-02-19T11:00:02.000Z", sessionId, projectPath, "Running"),
            claudeBackgroundToolResultLine("2026-02-19T11:00:03.000Z", sessionId, projectPath, backgroundTaskId),
            claudeQueueOperationLine("2026-02-19T11:00:04.000Z", sessionId, projectPath, backgroundTaskId),
            claudeTaskNotificationLine("2026-02-19T11:00:05.000Z", sessionId, projectPath, backgroundTaskId),
          ),
        )

        assertThat(awaitWatcherUpdate(updates)).isTrue()

        val backgroundThread = awaitThread(
          backend = backend,
          projectPath = projectPath,
          threadId = sessionId,
        ) { thread ->
          thread.activity == ClaudeSessionActivity.PROCESSING &&
          thread.updatedAt == Instant.parse("2026-02-19T11:00:05.000Z").toEpochMilli()
        }
        assertThat(backgroundThread).isNotNull
        assertThat(backgroundThread!!.gitBranch).isEqualTo(gitBranch)

        appendJsonl(
          jsonl,
          listOf(claudeAssistantLine("2026-02-19T11:00:06.000Z", sessionId, projectPath, "Done")),
        )

        assertThat(awaitWatcherUpdate(updates)).isTrue()

        val completedThread = awaitThread(
          backend = backend,
          projectPath = projectPath,
          threadId = sessionId,
        ) { thread ->
          thread.activity == ClaudeSessionActivity.READY &&
          thread.updatedAt == Instant.parse("2026-02-19T11:00:06.000Z").toEpochMilli()
        }
        assertThat(completedThread).isNotNull
        assertThat(completedThread!!.gitBranch).isEqualTo(gitBranch)
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }
}

private val FILE_WATCH_UPDATE_TIMEOUT = 8.seconds
private val WATCHER_PRIME_ATTEMPT_TIMEOUT = 500.milliseconds
private val WATCHER_PRIME_RETRY_DELAY = 100.milliseconds

private suspend fun awaitThread(
  backend: ClaudeStoreSessionBackend,
  projectPath: String,
  threadId: String,
  predicate: (ClaudeBackendThread) -> Boolean = { true },
): ClaudeBackendThread? {
  var resolved: ClaudeBackendThread? = null
  withTimeoutOrNull(FILE_WATCH_UPDATE_TIMEOUT) {
    while (true) {
      val threads = backend.listThreads(path = projectPath, openProject = null)
      val thread = threads.firstOrNull { it.id == threadId }
      if (thread != null && predicate(thread)) {
        resolved = thread
        break
      }
      delay(100.milliseconds)
    }
  }
  return resolved
}

private suspend fun primeWatcher(updates: Channel<Unit>, watchedFile: Path) {
  drainUpdateChannel(updates)

  var attempt = 0
  val primed = withTimeoutOrNull(FILE_WATCH_UPDATE_TIMEOUT) {
    while (true) {
      attempt++
      writeWatcherPrimeFile(watchedFile, attempt)
      if (awaitWatcherUpdate(updates, timeout = WATCHER_PRIME_ATTEMPT_TIMEOUT)) {
        return@withTimeoutOrNull true
      }
      delay(WATCHER_PRIME_RETRY_DELAY)
    }
  } == true

  assertThat(primed)
    .withFailMessage("Timed out waiting for watcher startup refresh ping under %s", watchedFile.parent)
    .isTrue()
  drainUpdateChannel(updates)
}

private fun writeWatcherPrimeFile(watchedFile: Path, attempt: Int) {
  val primeFile = watchedFile.resolveSibling("${watchedFile.fileName}.watcher-prime-$attempt.tmp")
  Files.writeString(primeFile, "prime-$attempt")
}
