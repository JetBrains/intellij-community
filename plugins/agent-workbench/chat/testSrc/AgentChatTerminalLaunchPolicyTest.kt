// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentChatTerminalLaunchPolicyTest {
  @Test
  fun codexResumeCommandDisablesUpdateCheck() {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = AgentSessionProvider.CODEX,
      command = listOf("codex", "resume", "thread-1"),
    )

    assertThat(launchSpec.command)
      .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1")
    assertThat(launchSpec.envVariables).isEmpty()
  }

  @Test
  fun codexExistingUpdateConfigIsForcedToFalse() {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = AgentSessionProvider.CODEX,
      command = listOf("codex", "-c", "check_for_update_on_startup=true", "resume", "thread-1"),
    )

    assertThat(launchSpec.command)
      .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1")
  }

  @Test
  fun codexConfigIsInjectedBeforePromptDelimiter() {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = AgentSessionProvider.CODEX,
      command = listOf("codex", "resume", "thread-1", "--", "-c check_for_update_on_startup=true"),
    )

    assertThat(launchSpec.command)
      .containsExactly(
        "codex",
        "-c",
        "check_for_update_on_startup=false",
        "resume",
        "thread-1",
        "--",
        "-c check_for_update_on_startup=true",
      )
  }

  @Test
  fun codexExecutablePathIsRecognizedWithoutProvider() {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = null,
      command = listOf("/usr/local/bin/codex", "--full-auto"),
    )

    assertThat(launchSpec.command)
      .containsExactly("/usr/local/bin/codex", "-c", "check_for_update_on_startup=false", "--full-auto")
  }

  @Test
  fun claudeLaunchSetsDisableUpdaterEnv() {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = AgentSessionProvider.CLAUDE,
      command = listOf("claude", "--resume", "session-1"),
    )

    assertThat(launchSpec.command).containsExactly("claude", "--resume", "session-1")
    assertThat(launchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun claudeExecutablePathIsRecognizedWithoutProvider() {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = null,
      command = listOf("/usr/local/bin/claude", "--resume", "session-1"),
    )

    assertThat(launchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun nonAgentCommandIsUnchanged() {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = null,
      command = listOf("bash", "-lc", "echo hi"),
    )

    assertThat(launchSpec.command).containsExactly("bash", "-lc", "echo hi")
    assertThat(launchSpec.envVariables).isEmpty()
  }
}
