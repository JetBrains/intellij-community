// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextMetadataKeys
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class CodexAgentSessionProviderBridgeTest {
  private val bridge = CodexAgentSessionProviderBridge()

  @Test
  fun buildResumeCommand() {
    assertThat(bridge.buildResumeCommand("thread-1"))
      .containsExactly("codex", "resume", "thread-1")
  }

  @Test
  fun buildNewEntryCommand() {
    assertThat(bridge.buildNewEntryCommand())
      .containsExactly("codex")
  }

  @Test
  fun buildNewSessionCommand() {
    assertThat(bridge.buildNewSessionCommand(AgentSessionLaunchMode.STANDARD))
      .containsExactly("codex")
    assertThat(bridge.buildNewSessionCommand(AgentSessionLaunchMode.YOLO))
      .containsExactly("codex", "--full-auto")
  }

  @Test
  fun buildCommandWithInitialPromptForYoloCommand() {
    assertThat(bridge.buildCommandWithInitialPrompt(listOf("codex", "--full-auto"), "-draft plan\nstep 2"))
      .containsExactly("codex", "--full-auto", "--", "-draft plan\nstep 2")
  }

  @Test
  fun buildCommandWithInitialPromptForResumeCommand() {
    val resumeCommand = bridge.buildResumeCommand("thread-1")

    assertThat(bridge.buildCommandWithInitialPrompt(resumeCommand, "Summarize changes"))
      .containsExactly("codex", "resume", "thread-1", "--", "Summarize changes")
  }

  @Test
  fun supportsUnarchiveThread() {
    assertThat(bridge.supportsUnarchiveThread).isTrue()
  }

  @Test
  fun createNewSessionReturnsPendingLaunchSpec() {
    runBlocking(Dispatchers.Default) {
      val standard = bridge.createNewSession(path = "/work/project", mode = AgentSessionLaunchMode.STANDARD)
      assertThat(standard.sessionId).isNull()
      assertThat(standard.command).containsExactly("codex")

      val yolo = bridge.createNewSession(path = "/work/project", mode = AgentSessionLaunchMode.YOLO)
      assertThat(yolo.sessionId).isNull()
      assertThat(yolo.command).containsExactly("codex", "--full-auto")
    }
  }

  @Test
  fun composeInitialMessageWithoutContext() {
    val message = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(prompt = "  Refactor this  ")
    )

    assertThat(message).isEqualTo("Refactor this")
  }

  @Test
  fun composeInitialMessageUsesCompactContextBlock() {
    val message = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        contextItems = listOf(
          AgentPromptContextItem(
            kindId = "snippet",
            title = "Selection",
            content = "val answer = 42",
            metadata = mapOf(
              AgentPromptContextMetadataKeys.SOURCE to "editor",
            ),
          )
        ),
        contextEnvelopeSummary = AgentPromptContextEnvelopeSummary(
          softCapChars = 12_000,
          softCapExceeded = true,
          autoTrimApplied = false,
        ),
      )
    )

    assertThat(message).startsWith("Refactor this\n\n### IDE Context")
    assertThat(message).contains("soft-cap: limit=12000 auto-trim=no")
    assertThat(message).contains("snippet")
    assertThat(message).doesNotContain("lang=")
    assertThat(message).contains("```\nval answer = 42\n```")
    assertThat(message).doesNotContain("```text")
    assertThat(message).contains("val answer = 42")
    assertThat(message).doesNotContain("Metadata:")
    assertThat(message).doesNotContain("####")
    assertThat(message).doesNotContain("Items:")
    assertThat(message).doesNotContain("<context_envelope>")
    assertThat(message).doesNotContain("<context_item>")
    assertThat(message).doesNotContain("\"schema\"")
  }

  @Test
  fun composeInitialMessageUsesSnippetLanguageWhenProvided() {
    val message = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        contextItems = listOf(
          AgentPromptContextItem(
            kindId = "snippet",
            title = "Selection",
            content = "val answer = 42",
            metadata = mapOf(
              AgentPromptContextMetadataKeys.SOURCE to "editor",
              AgentPromptContextMetadataKeys.LANGUAGE to "JAVA",
            ),
          )
        ),
      )
    )

    assertThat(message).contains("snippet: lang=java")
    assertThat(message).contains("```java\nval answer = 42\n```")
  }

  @Test
  fun composeInitialMessageOmitsSnippetLanguageForInvalidOrLegacyKey() {
    val invalidLanguage = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        contextItems = listOf(
          AgentPromptContextItem(
            kindId = "snippet",
            title = "Selection",
            content = "val answer = 42",
            metadata = mapOf(
              AgentPromptContextMetadataKeys.SOURCE to "editor",
              AgentPromptContextMetadataKeys.LANGUAGE to "java script!",
            ),
          )
        ),
      )
    )

    assertThat(invalidLanguage).doesNotContain("lang=")
    assertThat(invalidLanguage).contains("```\nval answer = 42\n```")
    assertThat(invalidLanguage).doesNotContain("```java")

    val legacyLanguage = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        contextItems = listOf(
          AgentPromptContextItem(
            kindId = "snippet",
            title = "Selection",
            content = "val answer = 42",
            metadata = mapOf(
              AgentPromptContextMetadataKeys.SOURCE to "editor",
              "language" to "java",
            ),
          )
        ),
      )
    )

    assertThat(legacyLanguage).doesNotContain("lang=")
    assertThat(legacyLanguage).contains("```\nval answer = 42\n```")
    assertThat(legacyLanguage).doesNotContain("```java")
  }

  @Test
  fun composeInitialMessageResolvesRelativePathsAgainstProjectRoot() {
    val projectRoot = Path.of("/work/project")
    val expectedFile = projectRoot.resolve("src/Main.java").normalize().toString()
    val expectedPathFile = projectRoot.resolve("src/App.kt").normalize().toString()
    val expectedPathDir = projectRoot.resolve("src").normalize().toString()

    val message = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Review context",
        projectPath = projectRoot.toString(),
        contextItems = listOf(
          AgentPromptContextItem(
            kindId = "file",
            title = "Current File",
            content = "src/Main.java",
            metadata = mapOf(AgentPromptContextMetadataKeys.SOURCE to "editor"),
          ),
          AgentPromptContextItem(
            kindId = "paths",
            title = "Selection",
            content = "file: src/App.kt\ndir: src",
            metadata = mapOf(AgentPromptContextMetadataKeys.SOURCE to "projectView"),
          ),
        ),
      )
    )

    assertThat(message).contains("file: $expectedFile")
    assertThat(message).contains("paths:")
    assertThat(message).contains("file: $expectedPathFile")
    assertThat(message).contains("dir: $expectedPathDir")
  }

  @Test
  fun composeInitialMessageMarksUnresolvedRelativePathWithoutProjectRoot() {
    val message = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Review context",
        contextItems = listOf(
          AgentPromptContextItem(
            kindId = "file",
            title = "Current File",
            content = "src/Main.java",
            metadata = mapOf(AgentPromptContextMetadataKeys.SOURCE to "editor"),
          )
        ),
      )
    )

    assertThat(message).contains("file: src/Main.java [path-unresolved]")
  }

}
