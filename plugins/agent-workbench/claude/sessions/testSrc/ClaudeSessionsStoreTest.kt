// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
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
  fun parsesSessionFromJsonlFile() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-b")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("abcd1234-1111-2222-3333-xyzxyzxyzxyz.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"abcd1234-1111-2222-3333-xyzxyzxyzxyz","cwd":"/work/project-b","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Investigate flaky test in module"}}
      {"type":"assistant","sessionId":"abcd1234-1111-2222-3333-xyzxyzxyzxyz","cwd":"/work/project-b","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"working"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.id).isEqualTo("abcd1234-1111-2222-3333-xyzxyzxyzxyz")
    assertThat(thread.title).contains("Investigate flaky test")
    assertThat(thread.updatedAt).isGreaterThan(0)
  }

  @Test
  fun extractsPromptWhenJsonlHasMessageBeforeType() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-order")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("order-1111-2222-3333-444444444444.jsonl")
    Files.writeString(
      transcript,
      """
      {"message":{"role":"user","content":"Investigate message-first ordering"},"type":"user","sessionId":"order-1111-2222-3333-444444444444","cwd":"/work/project-order","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z"}
      {"type":"assistant","sessionId":"order-1111-2222-3333-444444444444","cwd":"/work/project-order","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"working"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.title).contains("Investigate message-first ordering")
  }

  @Test
  fun readsSessionWithSnapshotHeaderBeforeConversation() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-snapshot-header")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("snap-1111-2222-3333-444444444444.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"file-history-snapshot","messageId":"aaa","snapshot":{"messageId":"aaa","trackedFileBackups":{},"timestamp":"2026-02-08T00:59:00.000Z"},"isSnapshotUpdate":false}
      {"type":"user","sessionId":"snap-1111-2222-3333-444444444444","cwd":"/work/project-snapshot-header","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Fix the build"}}
      {"type":"assistant","sessionId":"snap-1111-2222-3333-444444444444","cwd":"/work/project-snapshot-header","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.id).isEqualTo("snap-1111-2222-3333-444444444444")
    assertThat(thread.title).contains("Fix the build")
  }

  @Test
  fun ignoresSnapshotOnlyJsonlArtifacts() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-c")
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

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNull()
  }

  @Test
  fun skipsMalformedJsonlLineWhenReadingFallbackMetadata() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-malformed")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("malformed-1111-2222-3333-444444444444.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"malformed-1111-2222-3333-444444444444","cwd":"/work/project-malformed","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Recover after malformed line"}}
      {"type":"assistant","sessionId":"malformed-1111-2222-3333-444444444444","cwd":"/work/project-malformed","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z","message":{"role":"assistant"
      {"type":"assistant","sessionId":"malformed-1111-2222-3333-444444444444","cwd":"/work/project-malformed","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.id).isEqualTo("malformed-1111-2222-3333-444444444444")
    assertThat(thread.title).contains("Recover after malformed line")
    assertThat(thread.updatedAt).isEqualTo(Instant.parse("2026-02-08T01:00:02.000Z").toEpochMilli())
  }

  @Test
  fun derivesReadyActivityWhenLastConversationEventIsAssistant() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-unread")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("unread-session.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"unread-session","cwd":"/work/project-unread","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Do something"}}
      {"type":"assistant","sessionId":"unread-session","cwd":"/work/project-unread","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.READY)
  }

  @Test
  fun derivesProcessingActivityWhenLastEventIsProgress() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-processing")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("processing-session.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"processing-session","cwd":"/work/project-processing","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Do something"}}
      {"type":"assistant","sessionId":"processing-session","cwd":"/work/project-processing","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"working"}]}}
      {"type":"progress","sessionId":"processing-session","cwd":"/work/project-processing","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z"}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.PROCESSING)
  }

  @Test
  fun derivesReadyActivityWhenLastConversationEventIsUser() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-ready")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("ready-session.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"ready-session","cwd":"/work/project-ready","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Do something"}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.READY)
  }

  @Test
  fun findMatchingDirectoriesReturnsEncodedPath() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-multi")
    Files.createDirectories(projectDir)

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val directories = store.findMatchingDirectories("/work/project-multi")

    assertThat(directories).hasSize(1)
    assertThat(directories).containsExactly(projectDir)
  }

  @Test
  fun findMatchingDirectoriesEncodesDotsAsHyphens() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-Users-d-kopfmann-JetBrains-ollama")
    Files.createDirectories(projectDir)

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val directories = store.findMatchingDirectories("/Users/d.kopfmann/JetBrains/ollama")

    assertThat(directories).hasSize(1)
    assertThat(directories).containsExactly(projectDir)
  }

  @Test
  fun findMatchingDirectoriesEncodesHiddenDirectoryDots() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-Users-d-kopfmann--claude")
    Files.createDirectories(projectDir)

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val directories = store.findMatchingDirectories("/Users/d.kopfmann/.claude")

    assertThat(directories).hasSize(1)
    assertThat(directories).containsExactly(projectDir)
  }

  @Test
  fun systemEventTypeDefaultsToReadyActivity() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-system")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("system-session.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"system-session","cwd":"/work/project-system","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Hello"}}
      {"type":"system","sessionId":"system-session","cwd":"/work/project-system","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z"}
      """.trimIndent(),
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.READY)
  }

  @Test
  fun queueOperationEventTypeDefaultsToReadyActivity() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-queue")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("queue-session.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"queue-session","cwd":"/work/project-queue","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Hello"}}
      {"type":"queue-operation","sessionId":"queue-session","cwd":"/work/project-queue","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z"}
      """.trimIndent(),
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.READY)
  }

  @Test
  fun parseJsonlFileReturnsActivityWithoutProjectPathFiltering() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-any-path")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("direct-parse.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"direct-parse","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Hello"}}
      {"type":"assistant","sessionId":"direct-parse","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"Hi"}]}}
      {"type":"progress","sessionId":"direct-parse","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z"}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.id).isEqualTo("direct-parse")
    assertThat(thread.activity).isEqualTo(ClaudeSessionActivity.PROCESSING)
    assertThat(thread.title).contains("Hello")
  }

  @Test
  fun parseJsonlFileTailScansForActivity() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-any-path")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("large-session.jsonl")

    // Build a file with more than 3 events (head scan cap) so the tail scan picks up the last event.
    val lines = mutableListOf<String>()
    lines.add("""{"type":"user","sessionId":"large-session","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Start task"}}""")
    for (i in 1..5) {
      val ts = "2026-02-08T01:%02d:%02d.000Z".format(i / 60, i % 60)
      lines.add("""{"type":"assistant","sessionId":"large-session","cwd":"/any/path","isSidechain":false,"timestamp":"$ts","message":{"role":"assistant","content":[{"type":"text","text":"step $i"}]}}""")
    }
    // The tail event that determines final activity: progress → PROCESSING.
    lines.add("""{"type":"progress","sessionId":"large-session","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T05:00:00.000Z"}""")

    Files.write(transcript, lines)

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.id).isEqualTo("large-session")
    assertThat(thread.activity).isEqualTo(ClaudeSessionActivity.PROCESSING)
    assertThat(thread.title).contains("Start task")
  }

  @Test
  fun parseJsonlFileReturnsNullForSidechainSession() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-any-path")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("sidechain-parse.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"sidechain-parse","cwd":"/any/path","isSidechain":true,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Hello"}}
      {"type":"assistant","sessionId":"sidechain-parse","cwd":"/any/path","isSidechain":true,"timestamp":"2026-02-08T01:00:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"Hi"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNull()
  }
}
