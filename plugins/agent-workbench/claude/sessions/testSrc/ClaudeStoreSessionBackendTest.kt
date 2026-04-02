// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.claude.sessions.backend.store.ClaudeStoreSessionBackend
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

class ClaudeStoreSessionBackendTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun mapsActivityFromJsonlFallback() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-activity"
      val encodedPath = "-work-project-activity"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-unread.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-unread", projectPath, "Fix bug"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-unread", projectPath, "Done"),
        ),
      )
      writeJsonl(
        projectDir.resolve("session-processing.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:01:00.000Z", "session-processing", projectPath, "Build it"),
          claudeAssistantToolUseLine("2026-02-10T10:01:00.500Z", "session-processing", projectPath, "Working"),
          claudeProgressLine("2026-02-10T10:01:01.000Z", "session-processing", projectPath),
        ),
      )
      writeJsonl(
        projectDir.resolve("session-ready.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:02:00.000Z", "session-ready", projectPath, "Just sent"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      assertThat(threads).hasSize(3)
      val activityById = threads.associate { it.id to it.activity }
      assertThat(activityById["session-unread"]).isEqualTo(ClaudeSessionActivity.READY)
      assertThat(activityById["session-processing"]).isEqualTo(ClaudeSessionActivity.PROCESSING)
      assertThat(activityById["session-ready"]).isEqualTo(ClaudeSessionActivity.READY)
    }
  }

  @Test
  fun parsesFilesWithSnapshotHeaderBeforeConversation() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-snapshot"
      val encodedPath = "-work-project-snapshot"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      // Real-world pattern: snapshot header, then conversation data on lines 2+.
      writeJsonl(
        projectDir.resolve("session-with-header.jsonl"),
        listOf(
          """{"type":"file-history-snapshot","messageId":"aaa","snapshot":{"messageId":"aaa","trackedFileBackups":{},"timestamp":"2026-02-10T09:59:00.000Z"},"isSnapshotUpdate":false}""",
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-with-header", projectPath, "Fix the build"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-with-header", projectPath, "Done"),
        ),
      )

      // Snapshot-only file (no conversation): should be ignored.
      writeJsonl(
        projectDir.resolve("snapshot-only.jsonl"),
        listOf(
          """{"type":"file-history-snapshot","messageId":"bbb","snapshot":{"messageId":"bbb","trackedFileBackups":{},"timestamp":"2026-02-10T10:00:00.000Z"},"isSnapshotUpdate":false}""",
          """{"type":"file-history-snapshot","messageId":"ccc","snapshot":{"messageId":"ccc","trackedFileBackups":{},"timestamp":"2026-02-10T10:01:00.000Z"},"isSnapshotUpdate":false}""",
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().id).isEqualTo("session-with-header")
      assertThat(threads.single().title).contains("Fix the build")
    }
  }

  @Test
  fun parsesRealWorldMixOfSnapshotAndConversationFiles() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/Users/test/JetBrains/ollama"
      val encodedPath = "-Users-test-JetBrains-ollama"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      // File 1: snapshot header, then rich user event with many extra fields (thinkingMetadata, todos, etc.)
      writeJsonl(
        projectDir.resolve("session-a.jsonl"),
        listOf(
          """{"type":"file-history-snapshot","messageId":"m1","snapshot":{"messageId":"m1","trackedFileBackups":{},"timestamp":"2026-02-16T19:22:20.668Z"},"isSnapshotUpdate":false}""",
          """{"parentUuid":null,"isSidechain":false,"userType":"external","cwd":"$projectPath","sessionId":"session-a","version":"2.1.42","gitBranch":"master","type":"user","thinkingMetadata":{"budget":10000},"timestamp":"2026-02-16T19:22:20.666Z","todos":[],"message":{"role":"user","content":"say hi"},"uuid":"m1","permissionMode":"bypassPermissions"}""",
          """{"parentUuid":"m1","isSidechain":false,"userType":"external","cwd":"$projectPath","sessionId":"session-a","type":"assistant","timestamp":"2026-02-16T19:22:21.000Z","message":{"role":"assistant","content":[{"type":"text","text":"Hello!"}]}}""",
        ),
      )

      // File 2: many snapshot-only lines (should be ignored).
      writeJsonl(
        projectDir.resolve("session-snaponly.jsonl"),
        listOf(
          """{"type":"file-history-snapshot","messageId":"s1","snapshot":{"messageId":"s1","trackedFileBackups":{},"timestamp":"2026-02-16T19:22:20.000Z"},"isSnapshotUpdate":false}""",
          """{"type":"file-history-snapshot","messageId":"s2","snapshot":{"messageId":"s2","trackedFileBackups":{},"timestamp":"2026-02-16T19:22:21.000Z"},"isSnapshotUpdate":false}""",
          """{"type":"file-history-snapshot","messageId":"s3","snapshot":{"messageId":"s3","trackedFileBackups":{},"timestamp":"2026-02-16T19:22:22.000Z"},"isSnapshotUpdate":false}""",
          """{"type":"file-history-snapshot","messageId":"s4","snapshot":{"messageId":"s4","trackedFileBackups":{},"timestamp":"2026-02-16T19:22:23.000Z"},"isSnapshotUpdate":false}""",
        ),
      )

      // File 3: another conversation file with snapshot header.
      writeJsonl(
        projectDir.resolve("session-b.jsonl"),
        listOf(
          """{"type":"file-history-snapshot","messageId":"m2","snapshot":{"messageId":"m2","trackedFileBackups":{},"timestamp":"2026-02-10T10:00:00.000Z"},"isSnapshotUpdate":false}""",
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-b", projectPath, "Fix the build"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-b", projectPath, "Done"),
        ),
      )

      // Index summaries should override JSONL-derived titles for matching sessions only.
      Files.writeString(
        projectDir.resolve("sessions-index.json"),
        """{"version":1,"entries":[{"sessionId":"session-a","summary":"Index title","modified":"2026-02-16T19:22:20.000Z","projectPath":"$projectPath","isSidechain":false},{"sessionId":"session-phantom","summary":"Phantom title","modified":"2026-02-16T19:22:20.000Z","projectPath":"$projectPath","isSidechain":false}],"originalPath":"$projectPath"}""",
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      assertThat(threads).hasSize(2)
      val ids = threads.map { it.id }.toSet()
      assertThat(ids).containsExactlyInAnyOrder("session-a", "session-b")
      assertThat(threads.first { it.id == "session-a" }.title).isEqualTo("Index title")
    }
  }

  @Test
  fun matchesProjectDirectoryWhenPathContainsDots() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/Users/d.kopfmann/JetBrains/ollama"
      val encodedPath = "-Users-d-kopfmann-JetBrains-ollama"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-dot.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-dot", projectPath, "Hello from dotted path"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-dot", projectPath, "Done"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      assertThat(threads).hasSize(1)
      assertThat(threads.single().id).isEqualTo("session-dot")
      assertThat(threads.single().title).contains("Hello from dotted path")
    }
  }

  @Test
  fun sortsByUpdatedAtDescending() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-sort"
      val encodedPath = "-work-project-sort"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-old.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T09:00:00.000Z", "session-old", projectPath, "Old"),
        ),
      )
      writeJsonl(
        projectDir.resolve("session-new.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T11:00:00.000Z", "session-new", projectPath, "New"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      assertThat(threads.map { it.id }).containsExactly("session-new", "session-old")
    }
  }

  @Test
  fun listThreadsDoesNotLeakThreadsAcrossConcurrentProjectLoads() {
    runBlocking(Dispatchers.Default) {
      val projectAPath = "/work/project-concurrent-a"
      val projectBPath = "/work/project-concurrent-b"
      val projectADir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-concurrent-a")
      val projectBDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-concurrent-b")
      Files.createDirectories(projectADir)
      Files.createDirectories(projectBDir)

      // Project A has enough files to keep its parse loop active while B loads concurrently.
      for (i in 1..80) {
        val sessionId = "a-session-$i"
        writeJsonl(
          projectADir.resolve("$sessionId.jsonl"),
          listOf(
            claudeUserLine("2026-02-10T10:00:00.000Z", sessionId, projectAPath, "A task $i"),
            claudeAssistantLine("2026-02-10T10:00:01.000Z", sessionId, projectAPath, "Done"),
          ),
        )
      }

      writeJsonl(
        projectBDir.resolve("b-session.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "b-session", projectBPath, "B task"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "b-session", projectBPath, "Done"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })

      val projectAThreadsDeferred = async { backend.listThreads(path = projectAPath, openProject = null) }
      delay(10.milliseconds)
      val projectBThreads = backend.listThreads(path = projectBPath, openProject = null)
      val projectAThreads = projectAThreadsDeferred.await()

      assertThat(projectBThreads.map { it.id }).containsExactly("b-session")
      assertThat(projectAThreads.map { it.id }).allMatch { it.startsWith("a-session-") }
    }
  }

  @Test
  fun refreshesCachedThreadsWhenJsonlFilesChangeAndDelete() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-cache"
      val encodedPath = "-work-project-cache"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonlA = projectDir.resolve("session-a.jsonl")
      val jsonlB = projectDir.resolve("session-b.jsonl")
      writeJsonl(
        jsonlA,
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-a", projectPath, "Initial title")),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })

      val initialThreads = backend.listThreads(path = projectPath, openProject = null)
      assertThat(initialThreads.map { it.id }).containsExactly("session-a")
      assertThat(initialThreads.single().title).isEqualTo("Initial title")

      writeJsonl(
        jsonlA,
        listOf(
          claudeUserLine("2026-02-10T10:05:00.000Z", "session-a", projectPath, "Updated title"),
          claudeAssistantLine("2026-02-10T10:05:01.000Z", "session-a", projectPath, "Done"),
        ),
      )
      writeJsonl(
        jsonlB,
        listOf(claudeUserLine("2026-02-10T10:06:00.000Z", "session-b", projectPath, "Newest")),
      )

      val afterUpdate = backend.listThreads(path = projectPath, openProject = null)
      assertThat(afterUpdate.map { it.id }).containsExactly("session-b", "session-a")
      assertThat(afterUpdate.first { it.id == "session-a" }.title).isEqualTo("Updated title")
      assertThat(afterUpdate.first { it.id == "session-a" }.updatedAt)
        .isEqualTo(Instant.parse("2026-02-10T10:05:01.000Z").toEpochMilli())

      Files.delete(jsonlB)

      val afterDelete = backend.listThreads(path = projectPath, openProject = null)
      assertThat(afterDelete.map { it.id }).containsExactly("session-a")
    }
  }

  @Test
  fun emitsUpdatesWhenJsonlFileChanges() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-updates"
      val encodedPath = "-work-project-updates"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-updates.jsonl")
      writeJsonl(
        jsonl,
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-updates", projectPath, "Initial title")),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
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

        drainUpdateChannel(updates)
        writeJsonl(
          jsonl,
          listOf(claudeUserLine("2026-02-10T10:05:00.000Z", "session-updates", projectPath, "Updated title")),
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectPath, openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().title).isEqualTo("Updated title")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUpdatesWhenIndexFileChanges() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-index-updates"
      val encodedPath = "-work-project-index-updates"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-index-updates.jsonl"),
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-index-updates", projectPath, "Initial title")),
      )
      val indexFile = projectDir.resolve("sessions-index.json")
      Files.writeString(
        indexFile,
        """{"version":1,"entries":[{"sessionId":"session-index-updates","summary":"Initial summary","isSidechain":false}],"originalPath":"$projectPath"}""",
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<Unit>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.updates.collect {
          updates.trySend(Unit)
        }
      }

      try {
        val initialThreads = backend.listThreads(path = projectPath, openProject = null)
        assertThat(initialThreads).hasSize(1)
        assertThat(initialThreads.single().title).isEqualTo("Initial summary")

        drainUpdateChannel(updates)
        Files.writeString(
          indexFile,
          """{"version":1,"entries":[{"sessionId":"session-index-updates","summary":"Updated summary","isSidechain":false}],"originalPath":"$projectPath"}""",
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(indexFile)))

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectPath, openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().title).isEqualTo("Updated summary")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUpdatesForRefreshPingAndRefreshesByStatDiff() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-refresh-ping"
      val encodedPath = "-work-project-refresh-ping"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-refresh.jsonl")
      writeJsonl(
        jsonl,
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-refresh", projectPath, "Initial title")),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
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

        drainUpdateChannel(updates)
        writeJsonl(
          jsonl,
          listOf(claudeUserLine("2026-02-10T10:05:00.000Z", "session-refresh", projectPath, "Updated title")),
        )

        // Refresh ping: no specific paths, just a stat-based rescan trigger.
        sourceUpdates.emit(FileBackedSessionChangeSet())

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectPath, openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().title).isEqualTo("Updated title")
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

}
