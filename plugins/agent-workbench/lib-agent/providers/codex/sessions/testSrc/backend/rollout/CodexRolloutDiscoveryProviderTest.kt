// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ai.agent.codex.sessions.backend.rollout

import com.intellij.platform.ai.agent.json.filebacked.FileBackedSessionChangeSet
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexRolloutDiscoveryProviderTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun emitsScopedDiscoveryUpdateForChangedRolloutFile() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rollout-scoped-update")
      Files.createDirectories(projectDir)
      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
        .resolve("rollout-scoped-update.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLine(cwd = projectDir),
          """{"timestamp":"2026-02-14T17:00:01.000Z","type":"event_msg","payload":{"type":"task_started"}}""",
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val provider = CodexRolloutDiscoveryProvider(
        rolloutBackend = CodexRolloutSessionBackend(
          codexHomeProvider = { tempDir },
          rolloutChangeSource = { sourceUpdates },
          trailingRefreshDelayMs = 10_000L,
        ),
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        provider.updateEvents.collect { event ->
          updates.trySend(event)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val event = withTimeout(5.seconds) { updates.receive() }
        assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(event.scopedPaths).containsExactly(projectDir.toString())
        assertThat(event.threadIds).containsExactly("cli-working")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun fallsBackToUnscopedDiscoveryUpdateWhenChangedRolloutFileCannotBeParsed() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rollout-unscoped-update")
      Files.createDirectories(projectDir)
      val rollout = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")
        .resolve("rollout-unscoped-update.jsonl")
      writeRollout(
        file = rollout,
        lines = listOf(
          sessionMetaLineWithoutId(cwd = projectDir),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val provider = CodexRolloutDiscoveryProvider(
        rolloutBackend = CodexRolloutSessionBackend(
          codexHomeProvider = { tempDir },
          rolloutChangeSource = { sourceUpdates },
          trailingRefreshDelayMs = 10_000L,
        ),
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        provider.updateEvents.collect { event ->
          updates.trySend(event)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(rollout)))

        val event = withTimeout(5.seconds) { updates.receive() }
        assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(event.scopedPaths).isNull()
        assertThat(event.threadIds).isNull()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }
}

private fun sessionMetaLine(cwd: Path): String {
  val timestamp = "2026-02-14T17:00:00.000Z"
  val id = "cli-working"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$id","timestamp":"$timestamp","cwd":"${
    cwd.toString().replace("\\", "\\\\")
  }"}}"""
}

private fun sessionMetaLineWithoutId(cwd: Path): String {
  val timestamp = "2026-02-14T18:00:00.000Z"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"timestamp":"$timestamp","cwd":"${
    cwd.toString().replace("\\", "\\\\")
  }"}}"""
}

private fun writeRollout(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines)
}
