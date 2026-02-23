// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers

import com.intellij.agent.workbench.sessions.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.AgentSessionProvider

interface AgentSessionProviderBridge {
  val provider: AgentSessionProvider
  val displayNameKey: String
  val newSessionLabelKey: String
  val yoloSessionLabelKey: String?
    get() = null
  val iconId: String

  val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD)

  val sessionSource: AgentSessionSource
  val cliMissingMessageKey: String

  val supportsArchiveThread: Boolean
    get() = false

  fun isCliAvailable(): Boolean

  fun buildResumeCommand(sessionId: String): List<String>

  fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String>

  fun buildNewEntryCommand(): List<String>

  suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec

  suspend fun archiveThread(path: String, threadId: String): Boolean = false

  fun isCliMissingError(throwable: Throwable): Boolean = false
}

data class AgentSessionLaunchSpec(
  @JvmField val sessionId: String?,
  @JvmField val command: List<String>,
)
