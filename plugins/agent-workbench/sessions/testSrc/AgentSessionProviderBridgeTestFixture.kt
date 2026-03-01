// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderIcon
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.project.Project

internal class TestAgentSessionProviderBridge(
  override val provider: AgentSessionProvider,
  private val supportedModes: Set<AgentSessionLaunchMode>,
  private val cliAvailable: Boolean,
  override val yoloSessionLabelKey: String? = null,
) : AgentSessionProviderBridge {
  override val displayNameKey: String
    get() = if (provider == AgentSessionProvider.CLAUDE) "toolwindow.provider.claude" else "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = if (provider == AgentSessionProvider.CLAUDE) "toolwindow.action.new.session.claude" else "toolwindow.action.new.session.codex"

  override val icon: AgentSessionProviderIcon
    get() = AgentSessionProviderIcon(path = "icons/codex@14x14.svg", iconClass = this::class.java)

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

  override fun buildResumeCommand(sessionId: String): List<String> = listOf("test", "resume", sessionId)

  override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> = listOf("test", "new", mode.name)

  override fun buildNewEntryCommand(): List<String> = listOf("test")

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      command = listOf("test", "create", path, mode.name),
    )
  }
}
