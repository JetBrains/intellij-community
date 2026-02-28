// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest

interface AgentSessionProviderBridge {
  val provider: AgentSessionProvider
  val displayNameKey: String
  val newSessionLabelKey: String
  val yoloSessionLabelKey: String?
    get() = null
  val icon: AgentSessionProviderIcon

  val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD)

  val sessionSource: AgentSessionSource
  val cliMissingMessageKey: String

  val supportsArchiveThread: Boolean
    get() = false

  val supportsUnarchiveThread: Boolean
    get() = false

  fun isCliAvailable(): Boolean

  fun buildResumeCommand(sessionId: String): List<String>

  fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String>

  fun buildNewEntryCommand(): List<String>

  suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec

  suspend fun archiveThread(path: String, threadId: String): Boolean = false

  suspend fun unarchiveThread(path: String, threadId: String): Boolean = false

  fun composeInitialMessage(request: AgentPromptInitialMessageRequest): String {
    val prompt = request.prompt.trim()
    if (request.contextItems.isEmpty()) {
      return prompt
    }

    val builder = StringBuilder(prompt)
    if (builder.isNotEmpty()) {
      builder.append("\n\n")
    }
    builder.append("## Context\n")
    request.contextItems.forEachIndexed { index, item ->
      builder.append(index + 1)
        .append(". ")
        .append(item.title.ifBlank { item.kindId })
        .append(" [")
        .append(item.kindId)
        .append("]\n")
      if (item.metadata.isNotEmpty()) {
        val metadata = item.metadata.entries.joinToString(separator = ", ") { (key, value) -> "$key=$value" }
        builder.append("   metadata: ").append(metadata).append('\n')
      }
      builder.append(item.content.trim()).append("\n\n")
    }
    return builder.toString().trimEnd()
  }

  fun isCliMissingError(throwable: Throwable): Boolean = false
}

data class AgentSessionLaunchSpec(
  @JvmField val sessionId: String?,
  @JvmField val command: List<String>,
)
