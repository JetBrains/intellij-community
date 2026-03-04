// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ClaudeAgentSessionProviderBridgeTest {
  private val bridge = ClaudeAgentSessionProviderBridge()

  @Test
  fun buildNewEntryLaunchSpec() {
    assertThat(bridge.buildNewEntryLaunchSpec().command)
      .containsExactly("claude")
    assertThat(bridge.buildNewEntryLaunchSpec().envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildResumeLaunchSpec() {
    assertThat(bridge.buildResumeLaunchSpec("session-1").command)
      .containsExactly("claude", "--resume", "session-1")
    assertThat(bridge.buildResumeLaunchSpec("session-1").envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildYoloLaunchSpec() {
    assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO).command)
      .containsExactly("claude", "--dangerously-skip-permissions")
  }

  @Test
  fun buildLaunchSpecWithInitialPromptForResumeCommand() {
    val resumeLaunchSpec = bridge.buildResumeLaunchSpec("session-1")

    val launchSpec = bridge.buildLaunchSpecWithInitialPrompt(resumeLaunchSpec, "-summarize\nchanges")

    assertThat(launchSpec.command)
      .containsExactly("claude", "--resume", "session-1", "--", "-summarize\nchanges")
    assertThat(launchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun composeInitialMessageUsesCompactContextBlock() {
    val plan = bridge.buildInitialMessagePlan(
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
    val message = checkNotNull(plan.message)

    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
    assertThat(message).startsWith("Summarize changes\n\n### IDE Context")
    assertThat(message).contains("paths:")
    assertThat(message).contains("file: /tmp/demo.kt")
    assertThat(message).doesNotContain("soft-cap:")
    assertThat(message).doesNotContain("Metadata:")
    assertThat(message).doesNotContain("Items:")
    assertThat(message).doesNotContain("\"schema\"")
  }
}
