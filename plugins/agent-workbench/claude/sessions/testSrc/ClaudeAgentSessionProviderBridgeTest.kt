// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ClaudeAgentSessionProviderBridgeTest {
  private val bridge = ClaudeAgentSessionProviderBridge()

  @Test
  fun buildNewEntryCommand() {
    assertThat(bridge.buildNewEntryCommand())
      .containsExactly("claude")
  }

  @Test
  fun buildResumeCommand() {
    assertThat(bridge.buildResumeCommand("session-1"))
      .containsExactly("claude", "--resume", "session-1")
  }

  @Test
  fun buildYoloCommand() {
    assertThat(bridge.buildNewSessionCommand(AgentSessionLaunchMode.YOLO))
      .containsExactly("claude", "--dangerously-skip-permissions")
  }

  @Test
  fun buildCommandWithInitialPromptForResumeCommand() {
    val resumeCommand = bridge.buildResumeCommand("session-1")

    assertThat(bridge.buildCommandWithInitialPrompt(resumeCommand, "-summarize\nchanges"))
      .containsExactly("claude", "--resume", "session-1", "--", "-summarize\nchanges")
  }

  @Test
  fun composeInitialMessageUsesCompactContextBlock() {
    val message = bridge.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Summarize changes",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "Project Selection",
            body = "file: /tmp/demo.kt",
            source = "projectView",
          )
        ),
      )
    )

    assertThat(message).startsWith("Summarize changes\n\n### IDE Context")
    assertThat(message).contains("paths:")
    assertThat(message).contains("file: /tmp/demo.kt")
    assertThat(message).doesNotContain("soft-cap:")
    assertThat(message).doesNotContain("Metadata:")
    assertThat(message).doesNotContain("Items:")
    assertThat(message).doesNotContain("\"schema\"")
  }
}
