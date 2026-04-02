// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.isClaudeMenuCommandPrompt
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentPendingSessionMetadata
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameContext
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameHandler
import com.intellij.agent.workbench.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.isPlanModeCommand
import com.intellij.agent.workbench.sessions.core.providers.stripPlanModePrefix
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import javax.swing.Icon
import javax.swing.JComponent

internal class ClaudeAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = ClaudeSessionSource(),
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CLAUDE

  override val displayPriority: Int
    get() = 1

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

  override val promptOptions: List<AgentPromptProviderOption>
    get() = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)

  override val editorTabActionIds: List<String>
    get() = listOf(AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB)

  override val supportsPendingEditorTabRebind: Boolean
    get() = true

  override val emitsScopedRefreshSignals: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val supportsPlanMode: Boolean
    get() = true

  override val threadRenameHandler: AgentThreadRenameHandler = object : AgentThreadRenameHandler.ChatDispatch {
    override val supportedContexts: Set<AgentThreadRenameContext>
      get() = setOf(AgentThreadRenameContext.TREE_POPUP, AgentThreadRenameContext.EDITOR_TAB)

    override fun buildDispatchPlan(normalizedName: String): AgentInitialMessageDispatchPlan {
      return AgentInitialMessageDispatchPlan(
        postStartDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "/rename $normalizedName")),
      )
    }
  }

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
      command = listOf(ClaudeCliSupport.CLAUDE_COMMAND, PERMISSION_MODE_FLAG, PERMISSION_MODE_DEFAULT),
      envVariables = mapOf(CLAUDE_DISABLE_AUTO_UPDATER_ENV to CLAUDE_DISABLE_AUTO_UPDATER_VALUE),
    )
  }

  override fun buildLaunchSpecWithInitialPrompt(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    prompt: String,
  ): AgentSessionTerminalLaunchSpec {
    val planMode = prompt.isPlanModeCommand()
    val effectivePrompt = prompt.stripPlanModePrefix()
    val permissionMode = if (planMode) PERMISSION_MODE_PLAN else PERMISSION_MODE_DEFAULT
    val command = replaceOrAddPermissionMode(baseLaunchSpec.command, permissionMode) + listOf("--", effectivePrompt)
    return baseLaunchSpec.copy(command = command)
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    if (request.prompt.isClaudeMenuCommandPrompt()) {
      return AgentInitialMessagePlan(
        message = request.prompt.trim(),
        startupPolicy = AgentInitialMessageStartupPolicy.POST_START_ONLY,
      )
    }

    return buildPlanModeInitialMessagePlan(
      request = request,
      startupPolicyWhenPlanModeEnabled = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
    )
  }

  override fun resolvePendingSessionMetadata(
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentPendingSessionMetadata? {
    val separator = identity.indexOf(':')
    if (separator <= 0 || separator == identity.lastIndex) {
      return null
    }
    if (!identity.substring(separator + 1).startsWith("new-")) {
      return null
    }
    if (AgentSessionProvider.from(identity.substring(0, separator)) != AgentSessionProvider.CLAUDE) {
      return null
    }
    return AgentPendingSessionMetadata(createdAtMs = System.currentTimeMillis(), launchMode = resolveLaunchMode(launchSpec))
  }

  override fun onConversationOpened() {
    service<ClaudeQuotaHintStateService>().markEligible()
  }

  override fun createToolWindowNorthComponent(project: Project): JComponent {
    return ClaudeQuotaHintBanner()
  }

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      launchSpec = buildNewSessionLaunchSpec(mode),
    )
  }
}

private fun resolveLaunchMode(launchSpec: AgentSessionTerminalLaunchSpec): String {
  return if ("--dangerously-skip-permissions" in launchSpec.command) "yolo" else "standard"
}

private fun replaceOrAddPermissionMode(command: List<String>, mode: String): List<String> {
  val result = command.toMutableList()
  val index = result.indexOf(PERMISSION_MODE_FLAG)
  if (index >= 0 && index + 1 < result.size) {
    result[index + 1] = mode
  }
  else {
    result.addAll(listOf(PERMISSION_MODE_FLAG, mode))
  }
  return result
}

private const val CLAUDE_DISABLE_AUTO_UPDATER_ENV: String = "DISABLE_AUTOUPDATER"
private const val CLAUDE_DISABLE_AUTO_UPDATER_VALUE: String = "1"
