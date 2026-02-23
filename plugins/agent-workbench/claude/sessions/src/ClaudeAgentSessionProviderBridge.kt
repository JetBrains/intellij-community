// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionProviderIconIds
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource

internal class ClaudeAgentSessionProviderBridge(
  override val sessionSource: AgentSessionSource = ClaudeSessionSource(),
) : AgentSessionProviderBridge {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CLAUDE

  override val displayNameKey: String
    get() = "toolwindow.provider.claude"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.claude"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.claude.yolo"

  override val iconId: String
    get() = AgentSessionProviderIconIds.CLAUDE

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.claude.cli"

  override fun isCliAvailable(): Boolean = ClaudeCliSupport.isAvailable()

  override fun buildResumeCommand(sessionId: String): List<String> = ClaudeCliSupport.buildResumeCommand(sessionId)

  override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> {
    return ClaudeCliSupport.buildNewSessionCommand(yolo = mode == AgentSessionLaunchMode.YOLO)
  }

  override fun buildNewEntryCommand(): List<String> = listOf(ClaudeCliSupport.CLAUDE_COMMAND)

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      command = buildNewSessionCommand(mode),
    )
  }
}
