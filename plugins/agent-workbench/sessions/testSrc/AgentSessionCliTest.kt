// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionCliTest {
  @Test
  fun parseIdentityParsesProviderAndSessionId() {
    val parsed = parseAgentSessionIdentity("CODEX:thread-1")

    assertEquals(AgentSessionProvider.CODEX, parsed?.provider)
    assertEquals("thread-1", parsed?.sessionId)
  }

  @Test
  fun parseIdentityRejectsMalformedValue() {
    assertNull(parseAgentSessionIdentity("CODEX"))
    assertNull(parseAgentSessionIdentity("CODEX:"))
    assertNull(parseAgentSessionIdentity(":thread-1"))
    assertNull(parseAgentSessionIdentity("UNKNOWN:thread-1"))
  }

  @Test
  fun buildResumeCommandUsesProviderSpecificCommands() {
    assertEquals(
      listOf("codex", "resume", "thread-1"),
      buildAgentSessionResumeCommand(AgentSessionProvider.CODEX, "thread-1"),
    )
    assertEquals(
      listOf("claude", "--resume", "session-1"),
      buildAgentSessionResumeCommand(AgentSessionProvider.CLAUDE, "session-1"),
    )
  }

  @Test
  fun buildNewEntryCommandUsesProviderSpecificCommands() {
    assertEquals(listOf("codex"), buildAgentSessionNewCommand(AgentSessionProvider.CODEX))
    assertEquals(listOf("claude"), buildAgentSessionNewCommand(AgentSessionProvider.CLAUDE))
  }

  @Test
  fun buildNewClaudeCommands() {
    assertEquals(
      listOf("claude"),
      buildAgentSessionNewCommand(AgentSessionProvider.CLAUDE, yolo = false),
    )
    assertEquals(
      listOf("claude", "--dangerously-skip-permissions"),
      buildAgentSessionNewCommand(AgentSessionProvider.CLAUDE, yolo = true),
    )
  }

  @Test
  fun buildNewCodexCommandsThrow() {
    assertThrows(IllegalStateException::class.java) {
      buildAgentSessionNewCommand(AgentSessionProvider.CODEX, yolo = false)
    }
    assertThrows(IllegalStateException::class.java) {
      buildAgentSessionNewCommand(AgentSessionProvider.CODEX, yolo = true)
    }
  }

  @Test
  fun buildNewIdentityIsUniqueAndIncludesProvider() {
    val claudeA = buildAgentSessionNewIdentity(AgentSessionProvider.CLAUDE)
    val claudeB = buildAgentSessionNewIdentity(AgentSessionProvider.CLAUDE)

    assertNotEquals(claudeA, claudeB)
    assertTrue(claudeA.startsWith("CLAUDE:"))
    assertTrue(buildAgentSessionNewIdentity(AgentSessionProvider.CODEX).startsWith("CODEX:"))
  }

  @Test
  fun buildExistingIdentityFormat() {
    assertEquals("CLAUDE:abc", buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "abc"))
    assertEquals("CODEX:xyz", buildAgentSessionIdentity(AgentSessionProvider.CODEX, "xyz"))
  }
}
