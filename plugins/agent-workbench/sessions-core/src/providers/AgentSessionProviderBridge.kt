// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import javax.swing.Icon

enum class AgentInitialMessageStartupPolicy {
  TRY_STARTUP_COMMAND,
  POST_START_ONLY,
}

enum class AgentInitialMessageTimeoutPolicy {
  ALLOW_TIMEOUT_FALLBACK,
  REQUIRE_EXPLICIT_READINESS,
}

data class AgentInitialMessagePlan(
  @JvmField val message: String?,
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
  @JvmField val initialComposedMessage: String? = null,
  @JvmField val initialMessageToken: String? = null,
  @JvmField val initialMessageTimeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
) {
  companion object {
    @JvmField
    val EMPTY: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan()
  }
}

interface AgentSessionProviderBridge {
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

  val sessionSource: AgentSessionSource
  val cliMissingMessageKey: String

  val supportsArchiveThread: Boolean
    get() = false

  val supportsPlanMode: Boolean
    get() = false

  val supportsUnarchiveThread: Boolean
    get() = false

  fun isCliAvailable(): Boolean

  fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec

  fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec

  fun buildNewEntryLaunchSpec(): AgentSessionTerminalLaunchSpec

  fun buildLaunchSpecWithInitialPrompt(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    prompt: String,
  ): AgentSessionTerminalLaunchSpec? = null

  suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec

  suspend fun archiveThread(path: String, threadId: String): Boolean = false

  suspend fun unarchiveThread(path: String, threadId: String): Boolean = false

  fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan

  fun isCliMissingError(throwable: Throwable): Boolean = false
}

data class AgentSessionTerminalLaunchSpec(
  @JvmField val command: List<String>,
  @JvmField val envVariables: Map<String, String> = emptyMap(),
)

data class AgentSessionLaunchSpec(
  @JvmField val sessionId: String?,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
)
