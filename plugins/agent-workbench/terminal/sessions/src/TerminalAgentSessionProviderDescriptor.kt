// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.terminal.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalRestoreContext
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameAction
import com.intellij.agent.workbench.terminal.sessions.icons.AgentWorkbenchTerminalSessionsIcons
import com.intellij.openapi.components.service
import java.util.UUID
import javax.swing.Icon

private val TERMINAL_PROVIDER_ICON: Icon = AgentWorkbenchTerminalSessionsIcons.Terminal

internal class TerminalAgentSessionProviderDescriptor(
  private val stateService: TerminalSessionStateService = service(),
  override val sessionSource: AgentSessionSource = TerminalSessionSource(stateService),
  private val sessionIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.TERMINAL

  override val displayPriority: Int
    get() = 4

  override val displayNameKey: String
    get() = "toolwindow.provider.terminal"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.terminal"

  override val newSessionTitleKey: String
    get() = "toolwindow.action.new.session.terminal.title"

  override val quickStartActionTextKey: String
    get() = "action.AgentWorkbenchSessions.NewTerminalSessionQuick.text"

  override val quickStartActionDescriptionKey: String
    get() = "action.AgentWorkbenchSessions.NewTerminalSessionQuick.description"

  override val quickStartActionTargetDescriptionKey: String
    get() = "action.AgentWorkbenchSessions.NewTerminalSessionQuick.target.description"

  override val newSessionDescriptionKey: String
    get() = "toolwindow.action.new.session.terminal.description"

  override val icon: Icon
    get() = TERMINAL_PROVIDER_ICON

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.terminal.cli"

  override val supportsPromptLaunch: Boolean
    get() = false

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val supportsTerminalRestoreContext: Boolean
    get() = true

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    stateService.renameSession(path = path, threadId = threadId, title = normalizedName)
  }

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return buildTerminalLaunchSpec()
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return buildTerminalLaunchSpec(preallocatedSessionId = sessionIdGenerator())
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.EMPTY
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return stateService.archiveSession(path = path, threadId = threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return stateService.unarchiveSession(path = path, threadId = threadId)
  }

  override fun recordNewSession(path: String, threadId: String, title: String, createdAtMs: Long) {
    stateService.recordSession(path = path, threadId = threadId, title = title, createdAtMs = createdAtMs)
  }

  override fun readTerminalRestoreContext(path: String, threadId: String): AgentSessionTerminalRestoreContext? {
    return stateService.readRestoreContext(path = path, threadId = threadId)
  }

  override fun recordTerminalWorkingDirectory(path: String, threadId: String, workingDirectory: String) {
    stateService.recordWorkingDirectory(path = path, threadId = threadId, workingDirectory = workingDirectory)
  }
}

private fun buildTerminalLaunchSpec(preallocatedSessionId: String? = null): AgentSessionTerminalLaunchSpec {
  return AgentSessionTerminalLaunchSpec(
    command = emptyList(),
    useTerminalDefaultShell = true,
    preallocatedSessionId = preallocatedSessionId,
  )
}
