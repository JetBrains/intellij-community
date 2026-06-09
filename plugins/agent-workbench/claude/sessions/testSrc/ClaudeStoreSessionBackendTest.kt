// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.claude.sessions.backend.store.ClaudeStoreSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeStoreSessionBackendTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun aggregatesUsageAcrossMainAndSubagentTranscriptsWithoutDoubleCountingDuplicateAssistantEvents() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-usage"
      val sessionId = "671b2ad4-f275-47c3-b705-fe4d1867af1b"
      val encodedPath = "-work-project-usage"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      Files.write(projectDir.resolve("$sessionId.jsonl"), loadUsageFixture("usage/main-session.jsonl", projectPath))
      writeJsonl(
        projectDir.resolve(sessionId).resolve("subagents").resolve("agent-afd2e7156495f43ad.jsonl"),
        loadUsageFixture("usage/subagent-session.jsonl", projectPath),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val thread = backend.listThreads(path = projectPath, openProject = null).single()

      assertThat(thread.usageSnapshots).containsExactlyInAnyOrder(
        AgentSessionUsageSnapshot(
          modelId = "claude-opus-4-7",
          inputTokens = 1,
          outputTokens = 1972,
          cacheReadTokens = 39560,
          cacheWriteTokens = 3721,
          requestCount = 1,
        ),
        AgentSessionUsageSnapshot(
          modelId = "claude-haiku-4-5-20251001",
          inputTokens = 1463,
          outputTokens = 266,
          cacheReadTokens = 19948,
          cacheWriteTokens = 19081,
          requestCount = 2,
        ),
      )
    }
  }

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
      writeJsonl(
        projectDir.resolve("session-needs-input.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:03:00.000Z", "session-needs-input", projectPath, "Ask me"),
          claudeAssistantUserInteractionToolLine("2026-02-10T10:03:01.000Z", "session-needs-input", projectPath, "AskUserQuestion"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      assertThat(threads).hasSize(4)
      val activityById = threads.associate { it.id to it.activity }
      assertThat(activityById["session-unread"]).isEqualTo(ClaudeSessionActivity.READY)
      assertThat(activityById["session-processing"]).isEqualTo(ClaudeSessionActivity.PROCESSING)
      assertThat(activityById["session-ready"]).isEqualTo(ClaudeSessionActivity.READY)
      assertThat(activityById["session-needs-input"]).isEqualTo(ClaudeSessionActivity.NEEDS_INPUT)
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
          claudeCustomTitleLine("2026-02-16T19:22:22.000Z", "session-a", projectPath, "Transcript title"),
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
      val sessionA = threads.first { it.id == "session-a" }
      assertThat(sessionA.title).isEqualTo("Index title")
      assertThat(sessionA.gitBranch).isEqualTo("master")
    }
  }

  @Test
  fun usesIndexFirstPromptAndGitBranchOnlyAsFallbackMetadata() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-index-metadata"
      val encodedPath = "-work-project-index-metadata"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-index-fallback.jsonl"),
        listOf(
          claudeAssistantLine("2026-02-10T10:00:00.000Z", "session-index-fallback", projectPath, "Done"),
        ),
      )
      writeJsonl(
        projectDir.resolve("session-jsonl-branch.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:01:00.000Z", "session-jsonl-branch", projectPath, "JSONL branch title", gitBranch = "jsonl-branch"),
          claudeAssistantLine("2026-02-10T10:01:01.000Z", "session-jsonl-branch", projectPath, "Done"),
        ),
      )
      Files.writeString(
        projectDir.resolve("sessions-index.json"),
        """
        {"version":1,"entries":[
          {"sessionId":"session-index-fallback","summary":"No prompt","firstPrompt":"Index first prompt","gitBranch":"index-branch","isSidechain":false},
          {"sessionId":"session-jsonl-branch","summary":"Index summary","gitBranch":"index-branch","isSidechain":false}
        ],"originalPath":"$projectPath"}
        """.trimIndent(),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threadsById = backend.listThreads(path = projectPath, openProject = null).associateBy { it.id }

      assertThat(threadsById).containsOnlyKeys("session-index-fallback", "session-jsonl-branch")
      assertThat(threadsById["session-index-fallback"]!!.title).isEqualTo("Index first prompt")
      assertThat(threadsById["session-index-fallback"]!!.gitBranch).isEqualTo("index-branch")
      assertThat(threadsById["session-jsonl-branch"]!!.title).isEqualTo("Index summary")
      assertThat(threadsById["session-jsonl-branch"]!!.gitBranch).isEqualTo("jsonl-branch")
    }
  }

  @Test
  fun usesTranscriptCustomTitleWhenIndexMissing() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-transcript-custom-title"
      val encodedPath = "-work-project-transcript-custom-title"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-custom-title.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-custom-title", projectPath, "Original prompt title"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-custom-title", projectPath, "Done"),
          claudeCustomTitleLine("2026-02-10T10:00:02.000Z", "session-custom-title", projectPath, "Visible custom title"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val thread = backend.listThreads(path = projectPath, openProject = null).single()

      assertThat(thread.id).isEqualTo("session-custom-title")
      assertThat(thread.title).isEqualTo("Visible custom title")
      assertThat(thread.archived).isFalse()
    }
  }

  @Test
  fun stripsArchivePrefixFromTranscriptCustomTitle() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-transcript-archived-title"
      val encodedPath = "-work-project-transcript-archived-title"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-transcript-archived.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-transcript-archived", projectPath, "Initial title"),
          claudeCustomTitleLine("2026-02-10T10:00:01.000Z", "session-transcript-archived", projectPath, "[archived] Visible title"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val thread = backend.listThreads(path = projectPath, openProject = null).single()

      assertThat(thread.id).isEqualTo("session-transcript-archived")
      assertThat(thread.title).isEqualTo("Visible title")
      assertThat(thread.archived).isTrue()
    }
  }

  @Test
  fun transcriptArchivedCustomTitleOverridesStaleActiveIndexTitle() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-transcript-archived-index-active"
      val encodedPath = "-work-project-transcript-archived-index-active"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-transcript-archived-index-active.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-transcript-archived-index-active", projectPath, "Initial title"),
          claudeCustomTitleLine("2026-02-10T10:00:01.000Z",
                                "session-transcript-archived-index-active",
                                projectPath,
                                "[archived] Visible title"),
        ),
      )
      Files.writeString(
        projectDir.resolve("sessions-index.json"),
        """{"version":1,"entries":[{"sessionId":"session-transcript-archived-index-active","summary":"Visible title","isSidechain":false}],"originalPath":"$projectPath"}""",
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val thread = backend.listThreads(path = projectPath, openProject = null).single()

      assertThat(thread.title).isEqualTo("Visible title")
      assertThat(thread.archived).isTrue()
    }
  }

  @Test
  fun transcriptActiveCustomTitleOverridesStaleArchivedIndexTitle() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-transcript-active-index-archived"
      val encodedPath = "-work-project-transcript-active-index-archived"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-transcript-active-index-archived.jsonl"),
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-transcript-active-index-archived", projectPath, "Initial title"),
          claudeCustomTitleLine("2026-02-10T10:00:01.000Z", "session-transcript-active-index-archived", projectPath, "Visible title"),
        ),
      )
      Files.writeString(
        projectDir.resolve("sessions-index.json"),
        """{"version":1,"entries":[{"sessionId":"session-transcript-active-index-archived","summary":"[archived] Visible title","isSidechain":false}],"originalPath":"$projectPath"}""",
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val thread = backend.listThreads(path = projectPath, openProject = null).single()

      assertThat(thread.title).isEqualTo("Visible title")
      assertThat(thread.archived).isFalse()
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
  fun resolvesActiveThreadFilePathsForProjectAndThreadId() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-active-watch"
      val otherProjectPath = "/work/project-active-watch-other"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-active-watch")
      val otherProjectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-active-watch-other")
      Files.createDirectories(projectDir)
      Files.createDirectories(otherProjectDir)

      val activeJsonl = projectDir.resolve("session-active.jsonl")
      writeJsonl(
        activeJsonl,
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-active", projectPath, "Active")),
      )
      writeJsonl(
        projectDir.resolve("session-other.jsonl"),
        listOf(claudeUserLine("2026-02-10T10:01:00.000Z", "session-other", projectPath, "Other")),
      )
      writeJsonl(
        otherProjectDir.resolve("session-active.jsonl"),
        listOf(claudeUserLine("2026-02-10T10:02:00.000Z", "session-active", otherProjectPath, "Other project")),
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })

      assertThat(backend.resolveActiveThreadFilePaths(projectPath, " session-active ")).containsExactly(activeJsonl)
      assertThat(backend.resolveActiveThreadFilePaths(projectPath, "session-other/malformed")).isEmpty()
      assertThat(backend.resolveActiveThreadFilePaths(projectPath, "missing-session")).isEmpty()
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
  fun emitsScopedSessionUpdateWhenJsonlFileChanges() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-scoped-updates"
      val encodedPath = "-work-project-scoped-updates"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-scoped-updates.jsonl")
      writeJsonl(
        jsonl,
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-scoped-updates", projectPath, "Initial title")),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).containsExactly(projectPath)
        assertThat(update.threadIds).containsExactly("session-scoped-updates")
        assertThat(update.activityUpdatesByThreadId.getValue("session-scoped-updates").activityReport)
          .isEqualTo(AgentThreadActivityReport(AgentThreadActivity.READY))
        assertThat(update.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun scopedSessionUpdateWithCompletedMutatingToolIncludesProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-mutating-tool-update"
      val encodedPath = "-work-project-mutating-tool-update"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-mutating-tool-update.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-mutating-tool-update", projectPath, "Change files"),
          claudeAssistantToolUseLine("2026-02-10T10:00:01.000Z",
                                     "session-mutating-tool-update",
                                     projectPath,
                                     "writing",
                                     toolUseId = "tool-write-1",
                                     toolName = "Write"),
          claudeToolResultLine("2026-02-10T10:00:02.000Z", "session-mutating-tool-update", projectPath, "tool-write-1"),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).containsExactly(projectPath)
        assertThat(update.threadIds).containsExactly("session-mutating-tool-update")
        assertThat(update.mayHaveChangedProjectFiles).isTrue()
        assertThat(update.changedProjectFilePaths).isNull()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun scopedSessionUpdateWithCompletedWriteToolIncludesExactProjectFileChangePath() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-exact-write-tool-update"
      val changedFile = "$projectPath/src/Main.kt"
      val encodedPath = "-work-project-exact-write-tool-update"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-exact-write-tool-update.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-exact-write-tool-update", projectPath, "Change files"),
          claudeAssistantToolUseLine(
            "2026-02-10T10:00:01.000Z",
            "session-exact-write-tool-update",
            projectPath,
            "writing",
            toolUseId = "tool-write-1",
            toolName = "Write",
            inputJson = """{"file_path":"$changedFile","content":"fun main() {}"}""",
          ),
          claudeToolResultLine("2026-02-10T10:00:02.000Z", "session-exact-write-tool-update", projectPath, "tool-write-1"),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).containsExactly(projectPath)
        assertThat(update.threadIds).containsExactly("session-exact-write-tool-update")
        assertThat(update.mayHaveChangedProjectFiles).isTrue()
        assertThat(update.changedProjectFilePaths).containsExactly(changedFile)
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun activeThreadUpdateSuppressesRepeatedUnchangedJsonlNotification() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-active-unchanged"
      val sessionId = "session-active-unchanged"
      val encodedPath = "-work-project-active-unchanged"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("$sessionId.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", sessionId, projectPath, "Change files"),
          claudeAssistantToolUseLine("2026-02-10T10:00:01.000Z", sessionId, projectPath, "editing"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        immediateFileChangeFlow = { flowOf(jsonl, jsonl) },
      )

      val updates = backend.activeThreadUpdateEvents(projectPath, sessionId).toList()

      assertThat(updates).hasSize(1)
      val update = updates.single()
      assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
      assertThat(update.scopedPaths).containsExactly(projectPath)
      assertThat(update.threadIds).isNull()
      assertThat(update.activityUpdatesByThreadId.getValue(sessionId).activityReport)
        .isEqualTo(AgentThreadActivityReport(AgentThreadActivity.PROCESSING))
      assertThat(update.mayHaveChangedProjectFiles).isFalse()
      assertThat(update.changedProjectFilePaths).isNull()
    }
  }

  @Test
  fun activeThreadUpdateKeepsProjectFileEvidence() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-active-write-tool-update"
      val changedFile = "$projectPath/src/Main.kt"
      val sessionId = "session-active-write-tool-update"
      val encodedPath = "-work-project-active-write-tool-update"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("$sessionId.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", sessionId, projectPath, "Change files"),
          claudeAssistantToolUseLine(
            "2026-02-10T10:00:01.000Z",
            sessionId,
            projectPath,
            "writing",
            toolUseId = "tool-write-1",
            toolName = "Write",
            inputJson = """{"file_path":"$changedFile","content":"fun main() {}"}""",
          ),
          claudeToolResultLine("2026-02-10T10:00:02.000Z", sessionId, projectPath, "tool-write-1"),
        ),
      )

      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        immediateFileChangeFlow = { flowOf(jsonl) },
      )

      val updates = backend.activeThreadUpdateEvents(projectPath, sessionId).toList()

      assertThat(updates).hasSize(1)
      val update = updates.single()
      assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
      assertThat(update.scopedPaths).containsExactly(projectPath)
      assertThat(update.threadIds).isNull()
      assertThat(update.mayHaveChangedProjectFiles).isTrue()
      assertThat(update.changedProjectFilePaths).containsExactly(changedFile)
    }
  }

  @Test
  fun loadedCompletedMutatingToolDoesNotMakeLaterStatusOnlyUpdateProjectMutating() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-status-only-after-tool"
      val encodedPath = "-work-project-status-only-after-tool"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-status-only-after-tool.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-status-only-after-tool", projectPath, "Change files"),
          claudeAssistantToolUseLine("2026-02-10T10:00:01.000Z",
                                     "session-status-only-after-tool",
                                     projectPath,
                                     "editing",
                                     toolUseId = "tool-edit-1",
                                     toolName = "Edit"),
          claudeToolResultLine("2026-02-10T10:00:02.000Z", "session-status-only-after-tool", projectPath, "tool-edit-1"),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val loadedThreads = backend.listThreads(path = projectPath, openProject = null)
      assertThat(loadedThreads).hasSize(1)

      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        appendJsonl(
          jsonl,
          listOf(claudeAssistantLine("2026-02-10T10:00:03.000Z", "session-status-only-after-tool", projectPath, "Done")),
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).containsExactly(projectPath)
        assertThat(update.threadIds).containsExactly("session-status-only-after-tool")
        assertThat(update.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun completedMutatingToolBeforeTailWindowIncludesProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-tail-mutating-tool"
      val encodedPath = "-work-project-tail-mutating-tool"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-tail-mutating-tool.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-tail-mutating-tool", projectPath, "Change files"),
          claudeAssistantToolUseLine("2026-02-10T10:00:01.000Z",
                                     "session-tail-mutating-tool",
                                     projectPath,
                                     "writing",
                                     toolUseId = "tool-write-tail",
                                     toolName = "Write"),
          claudeAssistantLine("2026-02-10T10:00:02.000Z", "session-tail-mutating-tool", projectPath, "x".repeat(20_000)),
          claudeToolResultLine("2026-02-10T10:00:03.000Z", "session-tail-mutating-tool", projectPath, "tool-write-tail"),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).containsExactly(projectPath)
        assertThat(update.threadIds).containsExactly("session-tail-mutating-tool")
        assertThat(update.mayHaveChangedProjectFiles).isTrue()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun completedNonMutatingToolBeforeTailWindowDoesNotIncludeProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-tail-non-mutating-tool"
      val encodedPath = "-work-project-tail-non-mutating-tool"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-tail-non-mutating-tool.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-tail-non-mutating-tool", projectPath, "Read files"),
          claudeAssistantToolUseLine("2026-02-10T10:00:01.000Z",
                                     "session-tail-non-mutating-tool",
                                     projectPath,
                                     "reading",
                                     toolUseId = "tool-read-tail",
                                     toolName = "Read"),
          claudeAssistantLine("2026-02-10T10:00:02.000Z", "session-tail-non-mutating-tool", projectPath, "x".repeat(20_000)),
          claudeToolResultLine("2026-02-10T10:00:03.000Z", "session-tail-non-mutating-tool", projectPath, "tool-read-tail"),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).containsExactly(projectPath)
        assertThat(update.threadIds).containsExactly("session-tail-non-mutating-tool")
        assertThat(update.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun scopedSessionUpdateMarksCompletedTranscriptAsUnreadHint() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-completed-update-hints"
      val encodedPath = "-work-project-completed-update-hints"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-completed-update-hints.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-completed-update-hints", projectPath, "Initial title"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-completed-update-hints", projectPath, "Done"),
        ),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).containsExactly(projectPath)
        assertThat(update.threadIds).containsExactly("session-completed-update-hints")
        assertThat(update.activityUpdatesByThreadId.getValue("session-completed-update-hints").activityReport)
          .isEqualTo(AgentThreadActivityReport(AgentThreadActivity.UNREAD))
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUnscopedSessionUpdateWhenJsonlFileCannotBeParsed() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-unscoped-updates")
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-unscoped-updates.jsonl")
      writeJsonl(
        jsonl,
        listOf("""{"type":"file-history-snapshot","timestamp":"2026-02-10T10:00:00.000Z"}"""),
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).isNull()
        assertThat(update.threadIds).isNull()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun fullRescanUpdateIncludesProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(requiresFullRescan = true))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).isNull()
        assertThat(update.threadIds).isNull()
        assertThat(update.mayHaveChangedProjectFiles).isTrue()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun refreshPingUpdateDoesNotIncludeProjectFileChangeEvidence() {
    runBlocking(Dispatchers.Default) {
      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet())

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).isNull()
        assertThat(update.threadIds).isNull()
        assertThat(update.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUpdatesWhenTranscriptCustomTitleChanges() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-custom-title-updates"
      val encodedPath = "-work-project-custom-title-updates"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val jsonl = projectDir.resolve("session-custom-title-updates.jsonl")
      writeJsonl(
        jsonl,
        listOf(
          claudeUserLine("2026-02-10T10:00:00.000Z", "session-custom-title-updates", projectPath, "Initial title"),
          claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-custom-title-updates", projectPath, "Done"),
        ),
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
          listOf(
            claudeUserLine("2026-02-10T10:00:00.000Z", "session-custom-title-updates", projectPath, "Initial title"),
            claudeAssistantLine("2026-02-10T10:00:01.000Z", "session-custom-title-updates", projectPath, "Done"),
            claudeCustomTitleLine("2026-02-10T10:05:00.000Z", "session-custom-title-updates", projectPath, "Renamed title"),
          ),
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(jsonl)))

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectPath, openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().title).isEqualTo("Renamed title")
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
          """{"version":1,"entries":[{"sessionId":"session-index-updates","summary":"[archived] Updated summary","isSidechain":false}],"originalPath":"$projectPath"}""",
        )
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(indexFile)))

        val updated = awaitWatcherUpdate(updates)
        assertThat(updated).isTrue()
        val threads = backend.listThreads(path = projectPath, openProject = null)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().title).isEqualTo("Updated summary")
        assertThat(threads.single().archived).isTrue()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun emitsUnscopedSessionUpdateWhenIndexFileChanges() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-index-scope-updates"
      val encodedPath = "-work-project-index-scope-updates"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      val indexFile = projectDir.resolve("sessions-index.json")
      Files.writeString(
        indexFile,
        """{"version":1,"entries":[{"sessionId":"session-index-scope-updates","summary":"Initial summary","isSidechain":false}],"originalPath":"$projectPath"}""",
      )

      val sourceUpdates = MutableSharedFlow<FileBackedSessionChangeSet>(replay = 1, extraBufferCapacity = 1)
      val backend = ClaudeStoreSessionBackend(
        claudeHomeProvider = { tempDir.resolve(".claude") },
        changeSource = { sourceUpdates },
      )
      val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.CONFLATED)
      val updatesJob = launch {
        backend.sessionUpdates.collect { update ->
          updates.trySend(update)
        }
      }

      try {
        sourceUpdates.emit(FileBackedSessionChangeSet(changedPaths = setOf(indexFile)))

        val update = withTimeout(5.seconds) { updates.receive() }
        assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(update.scopedPaths).isNull()
        assertThat(update.threadIds).isNull()
      }
      finally {
        updatesJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun stripsArchivePrefixFromIndexedTitle() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-archived-title"
      val encodedPath = "-work-project-archived-title"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)

      writeJsonl(
        projectDir.resolve("session-archived.jsonl"),
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-archived", projectPath, "Initial title")),
      )
      Files.writeString(
        projectDir.resolve("sessions-index.json"),
        """{"version":1,"entries":[{"sessionId":"session-archived","summary":"[archived] Visible title","isSidechain":false}],"originalPath":"$projectPath"}""",
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      val thread = threads.single()
      assertThat(thread.id).isEqualTo("session-archived")
      assertThat(thread.title).isEqualTo("Visible title")
      assertThat(thread.archived).isTrue()
    }
  }

  @Test
  fun keepsLongIndexedTitleWithoutTruncation() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-long-indexed-title"
      val encodedPath = "-work-project-long-indexed-title"
      val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
      Files.createDirectories(projectDir)
      val longTitle = "Visible archived Claude title " + "x".repeat(160)

      writeJsonl(
        projectDir.resolve("session-long-archived.jsonl"),
        listOf(claudeUserLine("2026-02-10T10:00:00.000Z", "session-long-archived", projectPath, "Initial title")),
      )
      Files.writeString(
        projectDir.resolve("sessions-index.json"),
        """{"version":1,"entries":[{"sessionId":"session-long-archived","summary":"[archived] $longTitle","isSidechain":false}],"originalPath":"$projectPath"}""",
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { tempDir.resolve(".claude") })
      val threads = backend.listThreads(path = projectPath, openProject = null)

      val thread = threads.single()
      assertThat(thread.id).isEqualTo("session-long-archived")
      assertThat(thread.title).isEqualTo(longTitle)
      assertThat(thread.archived).isTrue()
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

private fun loadUsageFixture(relativePath: String, projectPath: String): List<String> {
  val fixtureText = checkNotNull(ClaudeStoreSessionBackendTest::class.java.classLoader.getResource(relativePath)) {
    "Missing fixture resource: $relativePath"
  }.readText()
  return fixtureText
    .replace("__PROJECT_DIR__", projectPath)
    .lineSequence()
    .filter(String::isNotBlank)
    .toList()
}
