// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.project.Project
import javax.swing.Icon

internal class TestAgentSessionProviderBridge(
  override val provider: AgentSessionProvider,
  private val supportedModes: Set<AgentSessionLaunchMode>,
  private val cliAvailable: Boolean,
  private val resumeEnvVariables: Map<String, String> = emptyMap(),
  override val yoloSessionLabelKey: String? = null,
) : AgentSessionProviderBridge {
  override val displayNameKey: String
    get() = if (provider == AgentSessionProvider.CLAUDE) "toolwindow.provider.claude" else "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = if (provider == AgentSessionProvider.CLAUDE) "toolwindow.action.new.session.claude" else "toolwindow.action.new.session.codex"

  override val icon: Icon
    get() = if (provider == AgentSessionProvider.CLAUDE) AgentWorkbenchCommonIcons.Claude_14x14 else AgentWorkbenchCommonIcons.Codex_14x14

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = supportedModes

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@TestAgentSessionProviderBridge.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override fun isCliAvailable(): Boolean = cliAvailable

  override fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = listOf("test", "resume", sessionId),
      envVariables = resumeEnvVariables,
    )
  }

  override fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
  }

  override fun buildNewEntryLaunchSpec(): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test"))
  }

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("test", "create", path, mode.name)),
    )
  }
}
