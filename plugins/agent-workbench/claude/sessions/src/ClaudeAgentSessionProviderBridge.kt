// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
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
        get() = listOf(CLAUDE_PLAN_MODE_OPTION)

    override val supportsPlanMode: Boolean
        get() = true

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
        val planMode = isPlanModeCommand(prompt)
        val effectivePrompt = if (planMode) prompt.removePrefix(PLAN_MODE_COMMAND).trim() else prompt
        val permissionMode = if (planMode) PERMISSION_MODE_PLAN else PERMISSION_MODE_DEFAULT
        val command = replaceOrAddPermissionMode(baseLaunchSpec.command, permissionMode) + listOf("--", effectivePrompt)
        return baseLaunchSpec.copy(command = command)
    }

    override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
        val basePlan = AgentInitialMessagePlan.composeDefault(request)
        val normalizedMessage = basePlan.message ?: return basePlan
        val planModeEnabled = request.planModeEnabled || AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE in request.providerOptionIds
        val message = if (planModeEnabled) {
            ensurePlanModePrefix(normalizedMessage)
        } else {
            normalizedMessage
        }
        return AgentInitialMessagePlan(
            message = message,
            startupPolicy = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
            timeoutPolicy = if (isPlanModeCommand(message)) {
                AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS
            } else {
                AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK
            },
        )
    }

    override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
        return AgentSessionLaunchSpec(
            sessionId = null,
            launchSpec = buildNewSessionLaunchSpec(mode),
        )
    }

    private fun ensurePlanModePrefix(message: String): String {
        val normalized = message.trim()
        if (normalized.isEmpty()) {
            return PLAN_MODE_COMMAND
        }
        if (isPlanModeCommand(normalized)) {
            return normalized
        }
        return "$PLAN_MODE_COMMAND $normalized"
    }

    private fun isPlanModeCommand(message: String): Boolean {
        if (!message.startsWith(PLAN_MODE_COMMAND)) {
            return false
        }
        val suffix = message.removePrefix(PLAN_MODE_COMMAND)
        return suffix.isEmpty() || suffix.first().isWhitespace()
    }
}

private fun replaceOrAddPermissionMode(command: List<String>, mode: String): List<String> {
    val result = command.toMutableList()
    val index = result.indexOf(PERMISSION_MODE_FLAG)
    if (index >= 0 && index + 1 < result.size) {
        result[index + 1] = mode
    } else {
        result.addAll(listOf(PERMISSION_MODE_FLAG, mode))
    }
    return result
}

private const val CLAUDE_DISABLE_AUTO_UPDATER_ENV: String = "DISABLE_AUTOUPDATER"
private const val CLAUDE_DISABLE_AUTO_UPDATER_VALUE: String = "1"
private const val PLAN_MODE_COMMAND: String = "/plan"

private val CLAUDE_PLAN_MODE_OPTION = AgentPromptProviderOption(
    id = AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE,
    labelKey = "toolwindow.prompt.option.plan.mode",
    labelFallback = "Plan mode",
    defaultSelected = true,
    disabledExistingTaskActivities = setOf(AgentThreadActivity.PROCESSING, AgentThreadActivity.REVIEWING),
)
