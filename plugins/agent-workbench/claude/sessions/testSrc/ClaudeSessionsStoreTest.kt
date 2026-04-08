// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.stream.Stream

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
  fun parsesSessionWhenConversationStartsAfterThreeMetadataEvents() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-metadata-prefix")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("metadata-prefix-session.jsonl")
    Files.writeString(
      transcript,
      """
      {"type":"queue-operation","timestamp":"2026-02-08T00:59:57.000Z"}
      {"type":"progress","timestamp":"2026-02-08T00:59:58.000Z"}
      {"type":"system","timestamp":"2026-02-08T00:59:59.000Z"}
      {"type":"user","sessionId":"metadata-prefix-session","cwd":"/work/project-metadata-prefix","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Continue from metadata prefix"}}
      {"type":"assistant","sessionId":"metadata-prefix-session","cwd":"/work/project-metadata-prefix","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.id).isEqualTo("metadata-prefix-session")
    assertThat(thread.title).contains("Continue from metadata prefix")
    assertThat(thread.activity).isEqualTo(ClaudeSessionActivity.READY)
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("activityStateMachineCases")
  fun activityStateMachine(name: String, events: List<String>, expected: ClaudeSessionActivity) {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("activity-test.jsonl")
    Files.write(transcript, events)

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(expected)
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
  fun parseJsonlFileTailScansForActivity() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-any-path")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("large-session.jsonl")

    val lines = mutableListOf<String>()
    lines.add("""{"type":"user","sessionId":"large-session","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Start task"}}""")
    for (i in 1..5) {
      val ts = "2026-02-08T01:00:%02d.000Z".format(i)
      lines.add("""{"type":"assistant","sessionId":"large-session","cwd":"/any/path","isSidechain":false,"timestamp":"$ts","message":{"role":"assistant","content":[{"type":"text","text":"step $i"},{"type":"tool_use","id":"tu_$i","name":"edit","input":{}}]}}""")
    }
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
  fun parseJsonlFileParsesOversizedFinalEventFromFullLastLine() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-any-path")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("oversized-tail-session.jsonl")
    val hugePayload = "x".repeat(6000)
    Files.writeString(
      transcript,
      """
      {"type":"user","sessionId":"oversized-tail-session","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:00.000Z","message":{"role":"user","content":"Start task"}}
      {"type":"assistant","sessionId":"oversized-tail-session","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"step 1"},{"type":"tool_use","id":"tu_1","name":"edit","input":{}}]}}
      {"type":"assistant","sessionId":"oversized-tail-session","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T01:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"step 2"},{"type":"tool_use","id":"tu_2","name":"edit","input":{}}]}}
      {"type":"progress","sessionId":"oversized-tail-session","cwd":"/any/path","isSidechain":false,"timestamp":"2026-02-08T06:00:00.000Z","payload":"$hugePayload"}
      """.trimIndent()
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.PROCESSING)
    assertThat(thread.updatedAt).isEqualTo(Instant.parse("2026-02-08T06:00:00.000Z").toEpochMilli())
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

  @Test
  fun explicitPartialAssistantChunkStaysProcessingUntilTerminalAssistantArrives() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-partial")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("partial-processing.jsonl")
    Files.write(
      transcript,
      listOf(
        user("2026-02-08T01:00:00.000Z"),
        assistantPartial("2026-02-08T01:00:01.000Z", "I am thinking"),
      ),
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.PROCESSING)

    Files.write(
      transcript,
      listOf(
        user("2026-02-08T01:00:00.000Z"),
        assistantPartial("2026-02-08T01:00:01.000Z", "I am thinking"),
        assistant("2026-02-08T01:00:02.000Z", "Done"),
      ),
    )

    val completedThread = store.parseJsonlFile(transcript)

    assertThat(completedThread).isNotNull
    assertThat(completedThread!!.activity).isEqualTo(ClaudeSessionActivity.READY)
  }

  @Test
  fun backgroundTaskNotificationKeepsThreadProcessingUntilFinalAssistantReply() {
    val projectDir = tempDir.resolve(".claude").resolve("projects").resolve("-work-project-background")
    Files.createDirectories(projectDir)
    val transcript = projectDir.resolve("background-processing.jsonl")
    Files.write(
      transcript,
      listOf(
        user("2026-02-08T01:00:00.000Z"),
        assistantToolUse("2026-02-08T01:00:01.000Z"),
        claudeBackgroundToolResultLine("2026-02-08T01:00:02.000Z", S, C, "task-123"),
        claudeQueueOperationLine("2026-02-08T01:00:03.000Z", S, C, "task-123"),
        claudeTaskNotificationLine("2026-02-08T01:00:04.000Z", S, C, "task-123"),
      ),
    )

    val store = ClaudeSessionsStore(claudeHomeProvider = { tempDir.resolve(".claude") })

    val thread = store.parseJsonlFile(transcript)

    assertThat(thread).isNotNull
    assertThat(thread!!.activity).isEqualTo(ClaudeSessionActivity.PROCESSING)

    Files.write(
      transcript,
      listOf(
        user("2026-02-08T01:00:00.000Z"),
        assistantToolUse("2026-02-08T01:00:01.000Z"),
        claudeBackgroundToolResultLine("2026-02-08T01:00:02.000Z", S, C, "task-123"),
        claudeQueueOperationLine("2026-02-08T01:00:03.000Z", S, C, "task-123"),
        claudeTaskNotificationLine("2026-02-08T01:00:04.000Z", S, C, "task-123"),
        assistant("2026-02-08T01:00:05.000Z", "All done"),
      ),
    )

    val completedThread = store.parseJsonlFile(transcript)

    assertThat(completedThread).isNotNull
    assertThat(completedThread!!.activity).isEqualTo(ClaudeSessionActivity.READY)
  }

  companion object {
    private const val S = "test-session"
    private const val C = "/work/project"

    private fun user(ts: String, content: String = "Hello"): String =
      claudeUserLine(ts, S, C, content)

    private fun assistant(ts: String, content: String = "Hi"): String =
      claudeAssistantLine(ts, S, C, content)

    private fun assistantPartial(ts: String, content: String = "Hi"): String =
      claudeAssistantPartialLine(ts, S, C, content)

    private fun assistantToolUse(ts: String, content: String = "editing"): String =
      claudeAssistantToolUseLine(ts, S, C, content)

    private fun progress(ts: String): String =
      claudeProgressLine(ts, S, C)

    private fun systemEvent(ts: String): String =
      """{"type":"system","sessionId":"$S","cwd":"$C","isSidechain":false,"timestamp":"$ts"}"""

    private fun toolResult(ts: String): String =
      """{"type":"user","sessionId":"$S","cwd":"$C","isSidechain":false,"timestamp":"$ts","message":{"role":"user","content":[{"tool_use_id":"tu_1","type":"tool_result","content":"ok"}]}}"""

    @JvmStatic
    fun activityStateMachineCases(): Stream<Arguments> = Stream.of(
      Arguments.of("user → READY", listOf(user("2026-02-08T01:00:00.000Z")), ClaudeSessionActivity.READY),
      Arguments.of("user → assistant(text) → READY", listOf(user("2026-02-08T01:00:00.000Z"), assistant("2026-02-08T01:00:01.000Z")), ClaudeSessionActivity.READY),
      Arguments.of("user → assistant(tool_use) → PROCESSING", listOf(user("2026-02-08T01:00:00.000Z"), assistantToolUse("2026-02-08T01:00:01.000Z")), ClaudeSessionActivity.PROCESSING),
      Arguments.of("user → progress → PROCESSING", listOf(user("2026-02-08T01:00:00.000Z"), progress("2026-02-08T01:00:01.000Z"), progress("2026-02-08T01:00:02.000Z")), ClaudeSessionActivity.PROCESSING),
      Arguments.of("user → assistant(partial) → progress → PROCESSING", listOf(user("2026-02-08T01:00:00.000Z"), assistantPartial("2026-02-08T01:00:01.000Z"), progress("2026-02-08T01:00:02.000Z")), ClaudeSessionActivity.PROCESSING),
      Arguments.of("user → assistant(tool_use) → progress → PROCESSING", listOf(user("2026-02-08T01:00:00.000Z"), assistantToolUse("2026-02-08T01:00:01.000Z"), progress("2026-02-08T01:00:02.000Z"), progress("2026-02-08T01:00:03.000Z")), ClaudeSessionActivity.PROCESSING),
      Arguments.of("full tool cycle → READY", listOf(user("2026-02-08T01:00:00.000Z"), assistantToolUse("2026-02-08T01:00:01.000Z"), progress("2026-02-08T01:00:02.000Z"), toolResult("2026-02-08T01:00:03.000Z"), assistant("2026-02-08T01:00:04.000Z")), ClaudeSessionActivity.READY),
      Arguments.of("full tool cycle + trailing system → READY", listOf(user("2026-02-08T01:00:00.000Z"), assistantToolUse("2026-02-08T01:00:01.000Z"), progress("2026-02-08T01:00:02.000Z"), toolResult("2026-02-08T01:00:03.000Z"), assistant("2026-02-08T01:00:04.000Z"), progress("2026-02-08T01:00:05.000Z"), systemEvent("2026-02-08T01:00:06.000Z")), ClaudeSessionActivity.READY),
      Arguments.of("multi-turn re-ask → PROCESSING", listOf(user("2026-02-08T01:00:00.000Z"), assistantToolUse("2026-02-08T01:00:01.000Z"), toolResult("2026-02-08T01:00:02.000Z"), assistant("2026-02-08T01:00:03.000Z"), user("2026-02-08T01:00:04.000Z", "follow up"), progress("2026-02-08T01:00:05.000Z")), ClaudeSessionActivity.PROCESSING),
      Arguments.of("trailing system → READY", listOf(user("2026-02-08T01:00:00.000Z"), systemEvent("2026-02-08T01:00:01.000Z")), ClaudeSessionActivity.READY),
      Arguments.of(
        "trailing queue-operation while awaiting assistant → PROCESSING",
        listOf(user("2026-02-08T01:00:00.000Z"), claudeQueueOperationLine("2026-02-08T01:00:01.000Z", S, C, "task-123")),
        ClaudeSessionActivity.PROCESSING,
      ),
    )
  }
}
