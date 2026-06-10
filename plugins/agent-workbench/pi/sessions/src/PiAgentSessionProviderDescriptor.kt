// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions-pi.spec.md

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameAction
import java.util.UUID
import javax.swing.Icon

internal class PiAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = PiSessionSource(),
  private val threadMutationBackend: PiSessionThreadMutationBackend =
    (sessionSource as? PiSessionSource)?.sessionStore ?: PiSessionStore(),
  private val executableResolver: suspend () -> String = PiCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
  private val cliAvailableProbe: suspend () -> Boolean = { PiCliSupport.findExecutableViaTerminalResolver() != null },
  private val sessionIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.PI

  override val displayPriority: Int
    get() = 3

  override val displayNameKey: String
    get() = "toolwindow.provider.pi"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.pi"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.PI

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.pi.cli"

  override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy
    get() = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE

  override val supportsNewThreadRebind: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val terminalAgentKey: String
    get() = PiCliSupport.PI_TERMINAL_AGENT_KEY

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    threadMutationBackend.renameThread(path, threadId, normalizedName)
  }

  override suspend fun isCliAvailable(): Boolean = cliAvailableProbe()

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf(executableResolver(), PI_SESSION_FLAG, sessionId))
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    val sessionId = sessionIdGenerator()
    return AgentSessionTerminalLaunchSpec(
      command = listOf(executableResolver(), PI_SESSION_ID_FLAG, sessionId),
      preallocatedSessionId = sessionId,
    )
  }

  override fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    val message = initialMessagePlan.message ?: return baseLaunchSpec
    return baseLaunchSpec.copy(command = baseLaunchSpec.command + message)
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.archiveThread(path, threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.unarchiveThread(path, threadId)
  }
}

private const val PI_SESSION_FLAG: String = "--session"
private const val PI_SESSION_ID_FLAG: String = "--session-id"
