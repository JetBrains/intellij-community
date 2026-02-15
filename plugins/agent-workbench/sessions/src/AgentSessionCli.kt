// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.sessions.providers.claude.ClaudeCliSupport
import com.intellij.agent.workbench.sessions.providers.codex.CodexCliCommands
import java.util.UUID

internal fun buildAgentSessionResumeCommand(provider: AgentSessionProvider, sessionId: String): List<String> =
  when (provider) {
    AgentSessionProvider.CODEX -> CodexCliCommands.buildResumeCommand(sessionId)
    AgentSessionProvider.CLAUDE -> ClaudeCliSupport.buildResumeCommand(sessionId)
  }

internal fun buildAgentSessionNewCommand(provider: AgentSessionProvider, yolo: Boolean = false): List<String> =
  when (provider) {
    AgentSessionProvider.CODEX -> error("Codex new sessions use thread/start + resume, not direct CLI")
    AgentSessionProvider.CLAUDE -> ClaudeCliSupport.buildNewSessionCommand(yolo)
  }

internal fun buildAgentSessionNewCommand(provider: AgentSessionProvider): List<String> {
  return when (provider) {
    AgentSessionProvider.CODEX -> listOf("codex")
    AgentSessionProvider.CLAUDE -> listOf("claude")
  }
}

internal fun buildAgentSessionIdentity(provider: AgentSessionProvider, sessionId: String): String {
  return "${provider.name}:$sessionId"
}

internal data class AgentSessionIdentity(
  val provider: AgentSessionProvider,
  val sessionId: String,
)

internal fun parseAgentSessionIdentity(identity: String): AgentSessionIdentity? {
  val separator = identity.indexOf(':')
  if (separator <= 0 || separator == identity.lastIndex) return null
  val provider = AgentSessionProvider.entries.firstOrNull { it.name == identity.substring(0, separator) } ?: return null
  val sessionId = identity.substring(separator + 1)
  return AgentSessionIdentity(provider = provider, sessionId = sessionId)
}

internal fun buildAgentSessionNewIdentity(provider: AgentSessionProvider): String {
  return "${provider.name}:new-${UUID.randomUUID()}"
}

internal fun isAgentCliAvailable(provider: AgentSessionProvider): Boolean =
  when (provider) {
    AgentSessionProvider.CODEX -> CodexCliUtils.isAvailable()
    AgentSessionProvider.CLAUDE -> ClaudeCliSupport.isAvailable()
  }
internal fun agentSessionCliMissingMessageKey(provider: AgentSessionProvider): String {
  return when (provider) {
    AgentSessionProvider.CODEX -> "toolwindow.error.cli"
    AgentSessionProvider.CLAUDE -> "toolwindow.error.claude.cli"
  }
}
