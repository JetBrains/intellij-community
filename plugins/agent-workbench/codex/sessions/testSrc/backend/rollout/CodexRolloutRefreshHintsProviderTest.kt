// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CodexRolloutRefreshHintsProviderTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun emitsRebindCandidatesOnlyForTopLevelCliThreads() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rollout-hints")
      Files.createDirectories(projectDir)
      val sessionsRoot = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("14")

      writeRollout(
        file = sessionsRoot.resolve("rollout-parent.jsonl"),
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-14T16:00:00.000Z", id = "cli-parent", cwd = projectDir),
          """{"timestamp":"2026-02-14T16:00:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Parent thread"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-cli-new.jsonl"),
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-14T16:01:00.000Z", id = "cli-new", cwd = projectDir),
          """{"timestamp":"2026-02-14T16:01:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"New top-level thread"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-subagent.jsonl"),
        lines = listOf(
          subAgentSessionMetaLine(cwd = projectDir),
          """{"timestamp":"2026-02-14T16:02:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Nested sub-agent thread"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-subagent-camel.jsonl"),
        lines = listOf(
          subAgentSessionMetaLine(cwd = projectDir, sourceFieldName = "subAgent"),
          """{"timestamp":"2026-02-14T16:03:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"Nested camel-case sub-agent thread"}}""",
        ),
      )
      writeRollout(
        file = sessionsRoot.resolve("rollout-vscode.jsonl"),
        lines = listOf(
          sessionMetaLine(timestamp = "2026-02-14T16:04:00.000Z", id = "vscode-top-level", cwd = projectDir, source = "vscode"),
          """{"timestamp":"2026-02-14T16:04:01.000Z","type":"event_msg","payload":{"type":"user_message","message":"VS Code thread"}}""",
        ),
      )

      val provider = CodexRolloutRefreshHintsProvider(
        rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir }),
      )

      val hints = provider.prefetchRefreshHints(
        paths = listOf(projectDir.toString()),
        refreshThreadSeedsByPath = mapOf(
          projectDir.toString() to setOf(AgentSessionRefreshThreadSeed(threadId = "cli-parent"))
        ),
      ).getValue(projectDir.toString())

      assertThat(hints.rebindCandidates.map { it.threadId }).containsExactly("cli-new")
    }
  }
}

private fun sessionMetaLine(timestamp: String, id: String, cwd: Path, source: String? = null): String {
  val sourceField = if (source == null) "" else ",\"source\":\"$source\""
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$id","timestamp":"$timestamp","cwd":"${cwd.toString().replace("\\", "\\\\")}"$sourceField}}"""
}

private fun subAgentSessionMetaLine(cwd: Path, sourceFieldName: String = "subagent"): String {
  val timestamp = "2026-02-14T16:02:00.000Z"
  val id = "subagent-thread"
  val parentThreadId = "cli-parent"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$id","timestamp":"$timestamp","cwd":"${cwd.toString().replace("\\", "\\\\")}","source":{"$sourceFieldName":{"thread_spawn":{"parent_thread_id":"$parentThreadId","depth":1}}}}}"""
}

private fun writeRollout(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines)
}
