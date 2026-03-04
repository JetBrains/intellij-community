// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import javax.swing.Icon

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

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Claude_14x14

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.claude.cli"

  override fun isCliAvailable(): Boolean = ClaudeCliSupport.isAvailable()

  override fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = ClaudeCliSupport.buildResumeCommand(sessionId),
      envVariables = mapOf(CLAUDE_DISABLE_AUTO_UPDATER_ENV to CLAUDE_DISABLE_AUTO_UPDATER_VALUE),
    )
  }

  override fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = ClaudeCliSupport.buildNewSessionCommand(yolo = mode == AgentSessionLaunchMode.YOLO),
      envVariables = mapOf(CLAUDE_DISABLE_AUTO_UPDATER_ENV to CLAUDE_DISABLE_AUTO_UPDATER_VALUE),
    )
  }

  override fun buildNewEntryLaunchSpec(): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = listOf(ClaudeCliSupport.CLAUDE_COMMAND),
      envVariables = mapOf(CLAUDE_DISABLE_AUTO_UPDATER_ENV to CLAUDE_DISABLE_AUTO_UPDATER_VALUE),
    )
  }

  override fun buildLaunchSpecWithInitialPrompt(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    prompt: String,
  ): AgentSessionTerminalLaunchSpec {
    return baseLaunchSpec.copy(command = baseLaunchSpec.command + listOf("--", prompt))
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      launchSpec = buildNewSessionLaunchSpec(mode),
    )
  }
}

private const val CLAUDE_DISABLE_AUTO_UPDATER_ENV: String = "DISABLE_AUTOUPDATER"
private const val CLAUDE_DISABLE_AUTO_UPDATER_VALUE: String = "1"
