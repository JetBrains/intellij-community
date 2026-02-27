// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderIcon
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.util.buildAgentSessionEntryCommand
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionNewCommand
import com.intellij.agent.workbench.sessions.util.buildAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionResumeCommand
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.parseAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.resolveAgentSessionId
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionCliTest {
  private val extensionPoint =
    ExtensionPointName<AgentSessionProviderBridge>("com.intellij.agent.workbench.sessionProviderBridge")

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
  fun buildResumeCommandUsesProviderSpecificCommands() {
    withTestBridges {
      assertThat(buildAgentSessionResumeCommand(AgentSessionProvider.CODEX, "thread-1"))
        .isEqualTo(listOf("codex", "resume", "thread-1"))
      assertThat(buildAgentSessionResumeCommand(AgentSessionProvider.CLAUDE, "session-1"))
        .isEqualTo(listOf("claude", "--resume", "session-1"))
    }
  }

  @Test
  fun buildNewEntryCommandUsesProviderSpecificCommands() {
    withTestBridges {
      assertThat(buildAgentSessionEntryCommand(AgentSessionProvider.CODEX)).isEqualTo(listOf("codex"))
      assertThat(buildAgentSessionEntryCommand(AgentSessionProvider.CLAUDE)).isEqualTo(listOf("claude"))
    }
  }

  @Test
  fun buildNewClaudeCommands() {
    withTestBridges {
      assertThat(buildAgentSessionNewCommand(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.STANDARD))
        .isEqualTo(listOf("claude"))
      assertThat(buildAgentSessionNewCommand(AgentSessionProvider.CLAUDE, AgentSessionLaunchMode.YOLO))
        .isEqualTo(listOf("claude", "--dangerously-skip-permissions"))
    }
  }

  @Test
  fun buildNewCodexCommands() {
    withTestBridges {
      assertThat(buildAgentSessionNewCommand(AgentSessionProvider.CODEX, AgentSessionLaunchMode.STANDARD))
        .isEqualTo(listOf("codex"))
      assertThat(buildAgentSessionNewCommand(AgentSessionProvider.CODEX, AgentSessionLaunchMode.YOLO))
        .isEqualTo(listOf("codex", "--full-auto"))
    }
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

    override val icon: AgentSessionProviderIcon
      get() = AgentSessionProviderIcon(path = "icons/codex@14x14.svg", iconClass = this::class.java)

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
