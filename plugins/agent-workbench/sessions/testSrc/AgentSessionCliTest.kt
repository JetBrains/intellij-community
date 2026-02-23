// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionProviderIconIds
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionCliTest {
  private val extensionPoint =
    ExtensionPointName<AgentSessionProviderBridge>("com.intellij.agent.workbench.sessionProviderBridge")

  @Test
  fun parseIdentityParsesProviderAndSessionId() {
    val parsed = parseAgentSessionIdentity("codex:thread-1")

    assertEquals(AgentSessionProvider.CODEX, parsed?.provider)
    assertEquals("thread-1", parsed?.sessionId)
  }

  @Test
  fun parseIdentityRejectsMalformedValue() {
    assertNull(parseAgentSessionIdentity("codex"))
    assertNull(parseAgentSessionIdentity("codex:"))
    assertNull(parseAgentSessionIdentity(":thread-1"))
    assertNull(parseAgentSessionIdentity("Codex:thread-1"))
  }

  @Test
  fun resolveSessionIdExtractsThreadIdFromIdentity() {
    assertEquals("thread-1", resolveAgentSessionId("codex:thread-1"))
  }

  @Test
  fun resolveSessionIdFallsBackForMalformedIdentity() {
    assertEquals("invalid", resolveAgentSessionId("invalid"))
  }

  @Test
  fun buildResumeCommandUsesProviderSpecificCommands() {
    withTestBridges {
      assertEquals(
        listOf("codex", "resume", "thread-1"),
        buildAgentSessionResumeCommand(AgentSessionProvider.CODEX, "thread-1"),
      )
      assertEquals(
        listOf("claude", "--resume", "session-1"),
        buildAgentSessionResumeCommand(AgentSessionProvider.CLAUDE, "session-1"),
      )
    }
  }

  @Test
  fun buildNewEntryCommandUsesProviderSpecificCommands() {
    withTestBridges {
      assertEquals(listOf("codex"), buildAgentSessionEntryCommand(AgentSessionProvider.CODEX))
      assertEquals(listOf("claude"), buildAgentSessionEntryCommand(AgentSessionProvider.CLAUDE))
    }
  }

  @Test
  fun buildNewClaudeCommands() {
    withTestBridges {
      assertEquals(
        listOf("claude"),
        buildAgentSessionNewCommand(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.STANDARD),
      )
      assertEquals(
        listOf("claude", "--dangerously-skip-permissions"),
        buildAgentSessionNewCommand(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.YOLO),
      )
    }
  }

  @Test
  fun buildNewCodexCommands() {
    withTestBridges {
      assertEquals(
        listOf("codex"),
        buildAgentSessionNewCommand(AgentSessionProvider.CODEX, AgentSessionLaunchMode.STANDARD),
      )
      assertEquals(
        listOf("codex", "--full-auto"),
        buildAgentSessionNewCommand(AgentSessionProvider.CODEX, AgentSessionLaunchMode.YOLO),
      )
    }
  }

  @Test
  fun resolveSessionIdReturnsBlankForPendingIdentity() {
    assertEquals("", resolveAgentSessionId("codex:new-123"))
    assertTrue(isAgentSessionNewIdentity("codex:new-123"))
  }

  @Test
  fun buildNewIdentityIsUniqueAndIncludesProvider() {
    val claudeA = buildAgentSessionNewIdentity(AgentSessionProvider.CLAUDE)
    val claudeB = buildAgentSessionNewIdentity(AgentSessionProvider.CLAUDE)

    assertNotEquals(claudeA, claudeB)
    assertTrue(claudeA.startsWith("claude:"))
    assertTrue(buildAgentSessionNewIdentity(AgentSessionProvider.CODEX).startsWith("codex:"))
  }

  @Test
  fun buildExistingIdentityFormat() {
    assertEquals("claude:abc", buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "abc"))
    assertEquals("codex:xyz", buildAgentSessionIdentity(AgentSessionProvider.CODEX, "xyz"))
  }

  private fun withTestBridges(block: () -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      extensionPoint.point.registerExtension(TestBridge.codex(), disposable)
      extensionPoint.point.registerExtension(TestBridge.claude(), disposable)
      block()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private class TestBridge private constructor(
    override val provider: AgentSessionProvider,
    private val resumeCommandBuilder: (String) -> List<String>,
    private val newSessionCommandBuilder: (AgentSessionLaunchMode) -> List<String>,
    private val newEntryCommand: List<String>,
  ) : AgentSessionProviderBridge {
    override val displayNameKey: String
      get() = "toolwindow.provider.codex"

    override val newSessionLabelKey: String
      get() = "toolwindow.action.new.session.codex"

    override val iconId: String
      get() = AgentSessionProviderIconIds.CODEX

    override val supportedLaunchModes: Set<AgentSessionLaunchMode>
      get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

    override val sessionSource: AgentSessionSource = object : AgentSessionSource {
      override val provider: AgentSessionProvider
        get() = this@TestBridge.provider

      override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

      override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
    }

    override val cliMissingMessageKey: String
      get() = "toolwindow.error"

    override fun isCliAvailable(): Boolean = true

    override fun buildResumeCommand(sessionId: String): List<String> = resumeCommandBuilder(sessionId)

    override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> = newSessionCommandBuilder(mode)

    override fun buildNewEntryCommand(): List<String> = newEntryCommand

    override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
      return AgentSessionLaunchSpec(sessionId = null, command = buildNewSessionCommand(mode))
    }

    companion object {
      fun codex(): TestBridge {
        return TestBridge(
          provider = AgentSessionProvider.CODEX,
          resumeCommandBuilder = { sessionId -> listOf("codex", "resume", sessionId) },
          newSessionCommandBuilder = { mode ->
            if (mode == AgentSessionLaunchMode.YOLO) listOf("codex", "--full-auto")
            else listOf("codex")
          },
          newEntryCommand = listOf("codex"),
        )
      }

      fun claude(): TestBridge {
        return TestBridge(
          provider = AgentSessionProvider.CLAUDE,
          resumeCommandBuilder = { sessionId -> listOf("claude", "--resume", sessionId) },
          newSessionCommandBuilder = { mode ->
            if (mode == AgentSessionLaunchMode.YOLO) listOf("claude", "--dangerously-skip-permissions")
            else listOf("claude")
          },
          newEntryCommand = listOf("claude"),
        )
      }
    }
  }
}
