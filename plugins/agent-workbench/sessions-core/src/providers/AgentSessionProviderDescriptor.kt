// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.openapi.project.Project
import javax.swing.Icon
import javax.swing.JComponent

enum class AgentInitialMessageStartupPolicy {
  TRY_STARTUP_COMMAND,
  POST_START_ONLY,
}

enum class AgentInitialMessageMode {
  STANDARD,
  PLAN,
}

enum class AgentInitialMessageTimeoutPolicy {
  ALLOW_TIMEOUT_FALLBACK,
  REQUIRE_EXPLICIT_READINESS,
}

enum class AgentInitialMessageDispatchCompletionPolicy {
  IMMEDIATE,
  RETRY_ON_CODEX_PLAN_BUSY,
}

enum class AgentThreadRenameContext {
  TREE_POPUP,
  EDITOR_TAB,
}

sealed interface AgentThreadRenameHandler {
  val supportedContexts: Set<AgentThreadRenameContext>

  interface Backend : AgentThreadRenameHandler {
    suspend fun execute(path: String, threadId: String, normalizedName: String): Boolean
  }

  interface ChatDispatch : AgentThreadRenameHandler {
    fun buildDispatchPlan(normalizedName: String): AgentInitialMessageDispatchPlan?
  }
}

data class AgentInitialMessagePlan(
  @JvmField val message: String?,
  @JvmField val mode: AgentInitialMessageMode = AgentInitialMessageMode.STANDARD,
  @JvmField val startupPolicy: AgentInitialMessageStartupPolicy = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
  @JvmField val timeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
) {
  companion object {
    @JvmField
    val EMPTY: AgentInitialMessagePlan = AgentInitialMessagePlan(message = null)

    fun composeDefault(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
      val message = AgentPromptContextEnvelopeFormatter.composeInitialMessage(request)
        .trim()
        .takeIf { it.isNotEmpty() }
      return AgentInitialMessagePlan(message = message)
    }
  }
}

data class AgentInitialMessageDispatchPlan(
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec? = null,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep> = emptyList(),
  @JvmField val initialMessageToken: String? = null,
) {
  companion object {
    @JvmField
    val EMPTY: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan()
  }
}

data class AgentInitialMessageDispatchStep(
  @JvmField val text: String,
  @JvmField val timeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
  @JvmField val completionPolicy: AgentInitialMessageDispatchCompletionPolicy = AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE,
)

data class AgentPendingSessionMetadata(
  @JvmField val createdAtMs: Long,
  @JvmField val launchMode: String?,
)

interface AgentSessionProviderDescriptor {
  val provider: AgentSessionProvider
  val displayNameKey: String
  val displayNameFallback: String
    get() = provider.value.replaceFirstChar { char ->
      if (char.isLowerCase()) char.titlecase() else char.toString()
    }
  val displayPriority: Int
    get() = Int.MAX_VALUE
  val newSessionLabelKey: String
  val yoloSessionLabelKey: String?
    get() = null
  val icon: Icon
  val promptOptions: List<AgentPromptProviderOption>
    get() = emptyList()

  val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD)

  val supportsPromptTabQueueShortcut: Boolean
    get() = false

  val suppressPromptExistingTaskSelectionHint: Boolean
    get() = false

  val editorTabActionIds: List<String>
    get() = emptyList()

  val supportsPendingEditorTabRebind: Boolean
    get() = false

  val supportsNewThreadRebind: Boolean
    get() = false

  val emitsScopedRefreshSignals: Boolean
    get() = false

  val refreshPathAfterCreateNewSession: Boolean
    get() = false

  val archiveRefreshDelayMs: Long
    get() = 0L

  val suppressArchivedThreadsDuringRefresh: Boolean
    get() = false

  val sessionSource: AgentSessionSource
  val cliMissingMessageKey: String

  val supportsArchiveThread: Boolean
    get() = false

  val threadRenameHandler: AgentThreadRenameHandler?
    get() = null

  val supportsUnarchiveThread: Boolean
    get() = false

  fun isCliAvailable(): Boolean

  fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec

  fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec

  fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec? = null

  fun buildPostStartDispatchSteps(initialMessagePlan: AgentInitialMessagePlan): List<AgentInitialMessageDispatchStep> {
    val message = initialMessagePlan.message ?: return emptyList()
    if (initialMessagePlan.mode != AgentInitialMessageMode.PLAN) {
      return listOf(
        AgentInitialMessageDispatchStep(
          text = message,
          timeoutPolicy = initialMessagePlan.timeoutPolicy,
        )
      )
    }

    val planCommand = if (message.isEmpty()) AGENT_PROMPT_PLAN_MODE_COMMAND else "$AGENT_PROMPT_PLAN_MODE_COMMAND $message"
    return listOf(
      AgentInitialMessageDispatchStep(
        text = planCommand,
        timeoutPolicy = initialMessagePlan.timeoutPolicy,
      )
    )
  }

  suspend fun archiveThread(path: String, threadId: String): Boolean = false

  suspend fun unarchiveThread(path: String, threadId: String): Boolean = false

  fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan

  fun onConversationOpened() {
  }

  fun resolvePendingSessionMetadata(
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentPendingSessionMetadata? = null

  fun createToolWindowNorthComponent(project: Project): JComponent? = null

  fun isCliMissingError(throwable: Throwable): Boolean = false
}

data class AgentSessionTerminalLaunchSpec(
  @JvmField val command: List<String>,
  @JvmField val envVariables: Map<String, String> = emptyMap(),
)
