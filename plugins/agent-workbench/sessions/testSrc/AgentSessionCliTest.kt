// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.util.buildAgentSessionEntryLaunchSpec
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionNewLaunchSpec
import com.intellij.agent.workbench.sessions.util.buildAgentSessionResumeLaunchSpec
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.parseAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.resolveAgentSessionId
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionCliTest {
  @Test
  fun parseIdentityParsesProviderAndSessionId() {
    val parsed = parseAgentSessionIdentity("codex:thread-1")

    assertThat(parsed?.provider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(parsed?.sessionId).isEqualTo("thread-1")
  }

  @Test
  fun parseIdentityRejectsMalformedValue() {
    assertThat(parseAgentSessionIdentity("codex")).isNull()
    assertThat(parseAgentSessionIdentity("codex:")).isNull()
    assertThat(parseAgentSessionIdentity(":thread-1")).isNull()
    assertThat(parseAgentSessionIdentity("Codex:thread-1")).isNull()
  }

  @Test
  fun resolveSessionIdExtractsThreadIdFromIdentity() {
    assertThat(resolveAgentSessionId("codex:thread-1")).isEqualTo("thread-1")
  }

  @Test
  fun resolveSessionIdFallsBackForMalformedIdentity() {
    assertThat(resolveAgentSessionId("invalid")).isEqualTo("invalid")
  }

  @Test
  fun buildResumeLaunchSpecUsesProviderSpecificCommands() {
    assertThat(buildAgentSessionResumeLaunchSpec(AgentSessionProvider.CODEX, "thread-1").command)
      .isEqualTo(listOf("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1"))
    assertThat(buildAgentSessionResumeLaunchSpec(AgentSessionProvider.CLAUDE, "session-1").command)
      .isEqualTo(listOf("claude", "--resume", "session-1"))
    assertThat(buildAgentSessionResumeLaunchSpec(AgentSessionProvider.CLAUDE, "session-1").envVariables)
      .isEqualTo(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildNewEntryLaunchSpecUsesProviderSpecificCommands() {
    assertThat(buildAgentSessionEntryLaunchSpec(AgentSessionProvider.CODEX).command)
      .isEqualTo(listOf("codex", "-c", "check_for_update_on_startup=false"))
    assertThat(buildAgentSessionEntryLaunchSpec(AgentSessionProvider.CLAUDE).command)
      .isEqualTo(listOf("claude", "--permission-mode", "default"))
    assertThat(buildAgentSessionEntryLaunchSpec(AgentSessionProvider.CLAUDE).envVariables)
      .isEqualTo(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildNewClaudeCommands() {
    assertThat(buildAgentSessionNewLaunchSpec(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.STANDARD).command)
      .isEqualTo(listOf("claude", "--permission-mode", "default"))
    assertThat(buildAgentSessionNewLaunchSpec(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.STANDARD).envVariables)
      .isEqualTo(mapOf("DISABLE_AUTOUPDATER" to "1"))
    assertThat(buildAgentSessionNewLaunchSpec(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.YOLO).command)
      .isEqualTo(listOf("claude", "--dangerously-skip-permissions"))
    assertThat(buildAgentSessionNewLaunchSpec(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.YOLO).envVariables)
      .isEqualTo(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildNewCodexCommands() {
    assertThat(buildAgentSessionNewLaunchSpec(AgentSessionProvider.CODEX, AgentSessionLaunchMode.STANDARD).command)
      .isEqualTo(listOf("codex", "-c", "check_for_update_on_startup=false"))
    assertThat(buildAgentSessionNewLaunchSpec(AgentSessionProvider.CODEX, AgentSessionLaunchMode.YOLO).command)
      .isEqualTo(listOf("codex", "-c", "check_for_update_on_startup=false", "--full-auto"))
  }

  @Test
  fun resolveSessionIdReturnsBlankForPendingIdentity() {
    assertThat(resolveAgentSessionId("codex:new-123")).isEqualTo("")
    assertThat(isAgentSessionNewIdentity("codex:new-123")).isTrue()
  }

  @Test
  fun buildNewIdentityIsUniqueAndIncludesProvider() {
    val claudeA = buildAgentSessionNewIdentity(AgentSessionProvider.CLAUDE)
    val claudeB = buildAgentSessionNewIdentity(AgentSessionProvider.CLAUDE)

    assertThat(claudeA).isNotEqualTo(claudeB)
    assertThat(claudeA.startsWith("claude:")).isTrue()
    assertThat(buildAgentSessionNewIdentity(AgentSessionProvider.CODEX).startsWith("codex:")).isTrue()
  }

  @Test
  fun buildExistingIdentityFormat() {
    assertThat(buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "abc")).isEqualTo("claude:abc")
    assertThat(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "xyz")).isEqualTo("codex:xyz")
  }

}
