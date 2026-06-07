// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.junie.common.BRAVE_FLAG
import com.intellij.agent.workbench.junie.common.JunieCliSupport
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameAction
import com.intellij.agent.workbench.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.buildTerminalPlanModePostStartDispatchSteps
import javax.swing.Icon

internal class JunieAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = JunieSessionSource(),
  private val threadMutationBackend: JunieSessionThreadMutationBackend =
    (sessionSource as? JunieSessionSource)?.sessionIndexStore ?: JunieSessionIndexStore(),
  private val executableResolver: suspend () -> String = JunieCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
  private val cliAvailableProbe: suspend () -> Boolean = { JunieCliSupport.findExecutableViaTerminalResolver() != null },
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.JUNIE

  override val displayPriority: Int
    get() = 2

  override val displayNameKey: String
    get() = "toolwindow.provider.junie"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.junie"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.junie.yolo"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Junie_14x14

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val promptOptions: List<AgentPromptProviderOption>
    get() = listOf(JUNIE_PROMPT_PROVIDER_PLAN_MODE_OPTION)

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.junie.cli"

  override val terminalAgentKey: String
    get() = JunieCliSupport.JUNIE_TERMINAL_AGENT_KEY

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val pendingSessionLaunchYoloMarker: String
    get() = BRAVE_FLAG

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    threadMutationBackend.renameThread(path, threadId, normalizedName)
  }

  override suspend fun isCliAvailable(): Boolean = cliAvailableProbe()

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return buildResumeLaunchSpec(sessionId, AgentSessionLaunchMode.STANDARD)
  }

  override suspend fun buildResumeLaunchSpec(
    sessionId: String,
    launchMode: AgentSessionLaunchMode,
  ): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = JunieCliSupport.buildResumeCommand(
        sessionId = sessionId,
        yolo = launchMode == AgentSessionLaunchMode.YOLO,
        executable = executableResolver(),
      ),
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = JunieCliSupport.buildNewSessionCommand(yolo = mode == AgentSessionLaunchMode.YOLO, executable = executableResolver()),
    )
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return buildPlanModeInitialMessagePlan(
      request = request,
      startupPolicyWhenPlanModeEnabled = AgentInitialMessageStartupPolicy.POST_START_ONLY,
    )
  }

  override fun buildPostStartDispatchSteps(initialMessagePlan: AgentInitialMessagePlan): List<AgentInitialMessageDispatchStep> {
    if (initialMessagePlan.mode != AgentInitialMessageMode.PLAN) {
      return super.buildPostStartDispatchSteps(initialMessagePlan)
    }

    return buildTerminalPlanModePostStartDispatchSteps(initialMessagePlan)
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.archiveThread(path, threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.unarchiveThread(path, threadId)
  }
}

private val JUNIE_PROMPT_PROVIDER_PLAN_MODE_OPTION: AgentPromptProviderOption = AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION.copy(defaultSelected = false)
