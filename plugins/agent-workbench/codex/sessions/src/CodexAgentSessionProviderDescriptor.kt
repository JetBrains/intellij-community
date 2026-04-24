// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentPendingSessionMetadata
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.openapi.components.serviceAsync
import javax.swing.Icon

internal class CodexAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = CodexSessionSource(),
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CODEX

  override val displayPriority: Int
    get() = 0

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

  override val supportsPromptTabQueueShortcut: Boolean
    get() = true

  override val suppressPromptExistingTaskSelectionHint: Boolean
    get() = true

  override val promptOptions: List<AgentPromptProviderOption>
    get() = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)

  override val editorTabActionIds: List<String>
    get() = listOf(AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB)

  override val supportsPendingEditorTabRebind: Boolean
    get() = true

  override val supportsNewThreadRebind: Boolean
    get() = true

  override val emitsScopedRefreshSignals: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val supportsPlanMode: Boolean
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
    return buildPlanModeInitialMessagePlan(
      request = request,
      startupPolicyWhenPlanModeEnabled = AgentInitialMessageStartupPolicy.POST_START_ONLY,
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
    if (identity.substring(separator + 1).startsWith("new-").not()) {
      return null
    }
    if (AgentSessionProvider.from(identity.substring(0, separator)) != AgentSessionProvider.CODEX) {
      return null
    }
    return AgentPendingSessionMetadata(
      createdAtMs = System.currentTimeMillis(),
      launchMode = if ("--full-auto" in launchSpec.command) "yolo" else "standard",
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
}

private const val CODEX_AUTO_UPDATE_CONFIG: String = "check_for_update_on_startup=false"
