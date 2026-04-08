// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Duration
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

        primeWatcherWithAtomicReplace(
          updates = updates,
          watchedFile = rollout,
        )
        replaceRolloutAtomically(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-18T11:00:00.000Z", id = "session-watch", cwd = projectDir),
            """{"timestamp":"2026-02-18T11:00:02.000Z","type":"event_msg","payload":{"type":"user_message","message":"Updated via atomic replace"}}""",
          ),
        )

        awaitWatcherUpdate(updates)

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

        primeWatcherWithRefreshPing(
          updates = updates,
          watchedFile = rollout,
        )
        writeRollout(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-18T12:00:00.000Z", id = "session-watch-inplace", cwd = projectDir),
            """{"timestamp":"2026-02-18T12:00:03.000Z","type":"event_msg","payload":{"type":"user_message","message":"Updated in place"}}""",
          ),
        )

        awaitWatcherUpdate(updates)

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

  @Test
  fun backendWatcherRefreshesProcessingThreadToUnreadAfterCompletionRewrite() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-watch-complete")
      Files.createDirectories(projectDir)
      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("18")
        .resolve("rollout-watch-complete.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-18T12:30:00.000Z", id = "session-watch-complete", cwd = projectDir),
          """{"timestamp":"2026-02-18T12:30:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Investigate stale working badge"}}""",
          """{"timestamp":"2026-02-18T12:30:02.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
          """{"timestamp":"2026-02-18T12:30:03.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Still working"}}""",
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
        assertThat(initialThreads.single().activity).isEqualTo(CodexSessionActivity.PROCESSING)

        primeWatcherWithRefreshPing(
          updates = updates,
          watchedFile = rollout,
        )
        writeRollout(
          file = rollout,
          lines = listOf(
            sessionMetaLine(timestamp = "2026-02-18T12:30:00.000Z", id = "session-watch-complete", cwd = projectDir),
            """{"timestamp":"2026-02-18T12:30:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Investigate stale working badge"}}""",
            """{"timestamp":"2026-02-18T12:30:02.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
            """{"timestamp":"2026-02-18T12:30:03.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Still working"}}""",
            """{"timestamp":"2026-02-18T12:30:04.000Z","type":"event_msg","payload":{"type":"agent_message","message":"Final assistant output"}}""",
            """{"timestamp":"2026-02-18T12:30:05.000Z","type":"event_msg","payload":{"type":"task_complete"}}""",
          ),
        )

        awaitWatcherUpdate(updates)

        val refreshedActivity = awaitThreadActivity(
          backend = backend,
          projectPath = projectDir.toString(),
          threadId = "session-watch-complete",
          expectedActivity = CodexSessionActivity.UNREAD,
        )
        assertThat(refreshedActivity).isEqualTo(CodexSessionActivity.UNREAD)
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun updatesCollectorCancelsPromptlyDuringWatcherStartup() {
    runBlocking(Dispatchers.Default) {
      val sessionsRoot = tempDir.resolve("sessions")
      writeLargeSessionsTree(sessionsRoot)
      val backend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir })
      val updatesJob = launch {
        backend.updates.collect { }
      }

      try {
        delay(100.milliseconds)
        withTimeout(WATCHER_CANCEL_TIMEOUT) {
          updatesJob.cancelAndJoin()
        }
      }
      finally {
        if (updatesJob.isActive) {
          updatesJob.cancelAndJoin()
        }
      }
    }
  }
}

private val FILE_WATCH_UPDATE_TIMEOUT = 8.seconds
private val WATCHER_CANCEL_TIMEOUT = 5.seconds
private val WATCHER_PRIME_ATTEMPT_TIMEOUT = 500.milliseconds
private val WATCHER_PRIME_RETRY_DELAY = 100.milliseconds
private const val WATCHER_STRESS_FILE_COUNT = 256
private const val WATCHER_STRESS_FILE_SIZE_BYTES = 256 * 1024

private suspend fun awaitWatcherUpdate(
  updates: Channel<Unit>,
  timeout: Duration = FILE_WATCH_UPDATE_TIMEOUT,
): Boolean {
  val update = withTimeoutOrNull(timeout) {
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

private suspend fun awaitThreadActivity(
  backend: CodexRolloutSessionBackend,
  projectPath: String,
  threadId: String,
  expectedActivity: CodexSessionActivity,
): CodexSessionActivity {
  var resolvedActivity: CodexSessionActivity? = null
  withTimeoutOrNull(FILE_WATCH_UPDATE_TIMEOUT) {
    while (true) {
      val threads = backend.listThreads(path = projectPath, openProject = null)
      val thread = threads.firstOrNull { it.thread.id == threadId }
      if (thread?.activity == expectedActivity) {
        resolvedActivity = thread.activity
        break
      }
      delay(100.milliseconds)
    }
  }
  return requireNotNull(resolvedActivity) { "Timed out waiting for thread $threadId activity $expectedActivity" }
}

private fun drainUpdateChannel(updates: Channel<Unit>) {
  while (true) {
    if (!updates.tryReceive().isSuccess) {
      break
    }
  }
}

private suspend fun primeWatcherWithAtomicReplace(updates: Channel<Unit>, watchedFile: Path) {
  primeWatcher(
    updates = updates,
    watchedFile = watchedFile,
    changeDescription = "atomic replace",
  ) { attempt ->
    replaceFileAtomically(watchedFile, attempt)
  }
}

private suspend fun primeWatcherWithRefreshPing(updates: Channel<Unit>, watchedFile: Path) {
  primeWatcher(
    updates = updates,
    watchedFile = watchedFile,
    changeDescription = "watcher startup refresh ping",
  ) { attempt ->
    writeWatcherPrimeFile(watchedFile, attempt)
  }
}

private suspend fun primeWatcher(
  updates: Channel<Unit>,
  watchedFile: Path,
  changeDescription: String,
  primeChange: (attempt: Int) -> Unit,
) {
  drainUpdateChannel(updates)

  var attempt = 0
  val primed = withTimeoutOrNull(FILE_WATCH_UPDATE_TIMEOUT) {
    while (true) {
      attempt++
      primeChange(attempt)
      if (awaitWatcherUpdate(updates, timeout = WATCHER_PRIME_ATTEMPT_TIMEOUT)) {
        return@withTimeoutOrNull true
      }
      delay(WATCHER_PRIME_RETRY_DELAY)
    }
  } == true

  assertThat(primed)
    .withFailMessage("Timed out waiting for watcher to observe %s on %s", changeDescription, watchedFile)
    .isTrue()
  drainUpdateChannel(updates)
}

private fun writeWatcherPrimeFile(watchedFile: Path, attempt: Int) {
  val primeFile = watchedFile.resolveSibling("${watchedFile.fileName}.watcher-prime-$attempt.tmp")
  Files.writeString(primeFile, "prime-$attempt")
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

private fun replaceFileAtomically(file: Path, attempt: Int) {
  val temporaryFile = file.resolveSibling("${file.fileName}.watcher-prime-$attempt.tmp")
  Files.write(temporaryFile, Files.readAllBytes(file))
  Files.move(
    temporaryFile,
    file,
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.ATOMIC_MOVE,
  )
}

private fun writeLargeSessionsTree(
  sessionsRoot: Path,
  fileCount: Int = WATCHER_STRESS_FILE_COUNT,
  fileSizeBytes: Int = WATCHER_STRESS_FILE_SIZE_BYTES,
) {
  val payload = ByteArray(fileSizeBytes) { index -> (index % 31).toByte() }
  for (index in 0 until fileCount) {
    val day = (index % 28 + 1).toString().padStart(2, '0')
    val bucket = sessionsRoot.resolve("2026/03/$day")
    Files.createDirectories(bucket)
    Files.write(bucket.resolve("watcher-shutdown-$index.tmp"), payload)
  }
}
