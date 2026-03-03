// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import javax.swing.Icon

interface AgentSessionProviderBridge {
  val provider: AgentSessionProvider
  val displayNameKey: String
  val newSessionLabelKey: String
  val yoloSessionLabelKey: String?
    get() = null
  val icon: Icon

  val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD)

  val sessionSource: AgentSessionSource
  val cliMissingMessageKey: String

  val supportsArchiveThread: Boolean
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

  fun composeInitialMessage(request: AgentPromptInitialMessageRequest): String {
    return AgentPromptContextEnvelopeFormatter.composeInitialMessage(request)
  }

  fun shouldUseStartupPromptCommand(request: AgentPromptInitialMessageRequest): Boolean = true

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
