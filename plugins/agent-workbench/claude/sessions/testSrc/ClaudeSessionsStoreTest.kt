// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class ClaudeSessionsStoreTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun readsSessionsFromIndexFile() {
    val projectPath = "/work/project-a"
    val encodedPath = "-work-project-a"
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
    Files.createDirectories(projectDir)
    val indexFile = projectDir.resolve("sessions-index.json")
    Files.writeString(
      indexFile,
      """
      {
        "version": 1,
        "originalPath": "$projectPath",
        "entries": [
          {
            "sessionId": "session-1",
            "summary": "Primary summary",
            "firstPrompt": "Ignore me",
            "modified": "2026-02-08T00:00:00.000Z",
            "fileMtime": 123,
            "projectPath": "$projectPath",
            "isSidechain": false
          },
          {
            "sessionId": "session-2",
            "summary": "Sidechain summary",
            "modified": "2026-02-08T00:00:01.000Z",
            "projectPath": "$projectPath",
            "isSidechain": true
          }
        ]
      }
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val threads = runBlocking(Dispatchers.Default) { store.listThreads(projectPath) }

    assertThat(threads).hasSize(1)
    val thread = threads.single()
    assertThat(thread.id).isEqualTo("session-1")
    assertThat(thread.title).isEqualTo("Primary summary")
  }

  @Test
  fun fallsBackToJsonlWhenIndexIsMissing() {
    val projectPath = "/work/project-b"
    val encodedPath = "-work-project-b"
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("abcd1234-1111-2222-3333-xyzxyzxyzxyz.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"abcd1234-1111-2222-3333-xyzxyzxyzxyz","cwd":"$projectPath","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Investigate flaky test in module"}}
      {"type":"assistant","sessionId":"abcd1234-1111-2222-3333-xyzxyzxyzxyz","cwd":"$projectPath","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"working"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val threads = runBlocking(Dispatchers.Default) { store.listThreads(projectPath) }

    assertThat(threads).hasSize(1)
    val thread = threads.single()
    assertThat(thread.id).isEqualTo("abcd1234-1111-2222-3333-xyzxyzxyzxyz")
    assertThat(thread.title).contains("Investigate flaky test")
    assertThat(thread.updatedAt).isGreaterThan(0)
  }

  @Test
  fun extractsPromptWhenJsonlHasMessageBeforeType() {
    val projectPath = "/work/project-order"
    val encodedPath = "-work-project-order"
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("order-1111-2222-3333-444444444444.jsonl")
    Files.writeString(
      transcript,
      """
      {"message":{"role":"user","content":"Investigate message-first ordering"},"type":"user","sessionId":"order-1111-2222-3333-444444444444","cwd":"$projectPath","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z"}
      {"type":"assistant","sessionId":"order-1111-2222-3333-444444444444","cwd":"$projectPath","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"working"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val threads = runBlocking(Dispatchers.Default) { store.listThreads(projectPath) }

    assertThat(threads).hasSize(1)
    assertThat(threads.single().title).contains("Investigate message-first ordering")
  }

  @Test
  fun parsesGitBranchFromIndexEntry() {
    val projectPath = "/work/project-branch"
    val encodedPath = "-work-project-branch"
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
    Files.createDirectories(projectDir)
    val indexFile = projectDir.resolve("sessions-index.json")
    Files.writeString(
      indexFile,
      """
      {
        "version": 1,
        "originalPath": "$projectPath",
        "entries": [
          {
            "sessionId": "branch-session-1",
            "summary": "Branch test",
            "modified": "2026-02-08T00:00:00.000Z",
            "projectPath": "$projectPath",
            "isSidechain": false,
            "gitBranch": "feature-x"
          },
          {
            "sessionId": "branch-session-2",
            "summary": "No branch",
            "modified": "2026-02-08T00:00:01.000Z",
            "projectPath": "$projectPath",
            "isSidechain": false
          }
        ]
      }
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val threads = runBlocking(Dispatchers.Default) { store.listThreads(projectPath) }

    assertThat(threads).hasSize(2)
    val withBranch = threads.first { it.id == "branch-session-1" }
    assertThat(withBranch.gitBranch).isEqualTo("feature-x")
    val withoutBranch = threads.first { it.id == "branch-session-2" }
    assertThat(withoutBranch.gitBranch).isNull()
  }

  @Test
  fun ignoresSnapshotOnlyJsonlArtifacts() {
    val projectPath = "/work/project-c"
    val encodedPath = "-work-project-c"
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("d1fb81b9-2707-4b1d-99dc-4556d4085aaa.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"file-history-snapshot","messageId":"b26b161c-b01e-4983-8c7c-899cdd766e0a","snapshot":{"messageId":"b26b161c-b01e-4983-8c7c-899cdd766e0a","trackedFileBackups":{},"timestamp":"2026-02-03T14:20:23.301Z"},"isSnapshotUpdate":false}
      {"type":"file-history-snapshot","messageId":"45736c73-f002-40cb-afc5-bb461dbf4a32","snapshot":{"messageId":"45736c73-f002-40cb-afc5-bb461dbf4a32","trackedFileBackups":{},"timestamp":"2026-02-03T14:21:09.740Z"},"isSnapshotUpdate":false}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val threads = runBlocking(Dispatchers.Default) { store.listThreads(projectPath) }

    assertThat(threads).isEmpty()
  }

  @Test
  fun skipsMalformedJsonlLineWhenReadingFallbackMetadata() {
    val projectPath = "/work/project-malformed"
    val encodedPath = "-work-project-malformed"
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve(encodedPath)
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("malformed-1111-2222-3333-444444444444.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"malformed-1111-2222-3333-444444444444","cwd":"$projectPath","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Recover after malformed line"}}
      {"type":"assistant","sessionId":"malformed-1111-2222-3333-444444444444","cwd":"$projectPath","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z","message":{"role":"assistant"
      {"type":"assistant","sessionId":"malformed-1111-2222-3333-444444444444","cwd":"$projectPath","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val threads = runBlocking(Dispatchers.Default) { store.listThreads(projectPath) }

    assertThat(threads).hasSize(1)
    val thread = threads.single()
    assertThat(thread.id).isEqualTo("malformed-1111-2222-3333-444444444444")
    assertThat(thread.title).contains("Recover after malformed line")
    assertThat(thread.updatedAt).isEqualTo(Instant.parse("2026-02-08T01:00:02.000Z").toEpochMilli())
  }
}
