// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.components.serviceAsync
import javax.swing.Icon

internal class CodexAgentSessionProviderBridge(
  override val sessionSource: AgentSessionSource = CodexSessionSource(),
) : AgentSessionProviderBridge {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CODEX

  override val displayNameKey: String
    get() = "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex.yolo"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Codex_14x14

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override fun isCliAvailable(): Boolean = CodexCliUtils.findExecutable() != null

  override fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = listOf(CodexCliUtils.CODEX_COMMAND, "-c", CODEX_AUTO_UPDATE_CONFIG, "resume", sessionId),
    )
  }

  override fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    val command = if (mode == AgentSessionLaunchMode.YOLO) {
      listOf(CodexCliUtils.CODEX_COMMAND, "-c", CODEX_AUTO_UPDATE_CONFIG, "--full-auto")
    }
    else {
      listOf(CodexCliUtils.CODEX_COMMAND, "-c", CODEX_AUTO_UPDATE_CONFIG)
    }
    return AgentSessionTerminalLaunchSpec(command = command)
  }

  override fun buildNewEntryLaunchSpec(): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = listOf(CodexCliUtils.CODEX_COMMAND, "-c", CODEX_AUTO_UPDATE_CONFIG),
    )
  }

  override fun buildLaunchSpecWithInitialPrompt(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    prompt: String,
  ): AgentSessionTerminalLaunchSpec {
    return baseLaunchSpec.copy(command = baseLaunchSpec.command + listOf("--", prompt))
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    val basePlan = AgentInitialMessagePlan.composeDefault(request)
    val normalizedMessage = basePlan.message ?: return basePlan
    val message = if (request.codexPlanModeEnabled) {
      ensurePlanModePrefix(normalizedMessage)
    }
    else {
      normalizedMessage
    }
    return AgentInitialMessagePlan(
      message = message,
      startupPolicy = if (request.codexPlanModeEnabled) {
        AgentInitialMessageStartupPolicy.POST_START_ONLY
      }
      else {
        AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND
      },
      timeoutPolicy = if (isPlanModeCommand(message)) {
        AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS
      }
      else {
        AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK
      },
    )
  }

  @Suppress("UNUSED_PARAMETER")
  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      launchSpec = buildNewSessionLaunchSpec(mode),
    )
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    serviceAsync<SharedCodexAppServerService>().archiveThread(threadId)
    return true
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    serviceAsync<SharedCodexAppServerService>().unarchiveThread(threadId)
    return true
  }

  override fun isCliMissingError(throwable: Throwable): Boolean {
    return throwable is CodexCliNotFoundException
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

private const val CODEX_AUTO_UPDATE_CONFIG: String = "check_for_update_on_startup=false"
private const val PLAN_MODE_COMMAND: String = "/plan"
