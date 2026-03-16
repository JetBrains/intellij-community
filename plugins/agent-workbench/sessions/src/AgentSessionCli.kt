// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.providers.AgentSessionProviderBridges
import java.util.UUID

internal class AgentSessionProviderUnavailableException(provider: AgentSessionProvider) :
  IllegalStateException("No session provider bridge registered for ${provider.value}")

private fun requireAgentSessionProviderBridge(provider: AgentSessionProvider) =
  AgentSessionProviderBridges.find(provider)
  ?: throw AgentSessionProviderUnavailableException(provider)

internal fun buildAgentSessionResumeCommand(provider: AgentSessionProvider, sessionId: String): List<String> =
  requireAgentSessionProviderBridge(provider).buildResumeCommand(sessionId)

internal fun buildAgentSessionNewCommand(
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
): List<String> =
  requireAgentSessionProviderBridge(provider).buildNewSessionCommand(mode)

internal fun buildAgentSessionEntryCommand(provider: AgentSessionProvider): List<String> {
  return requireAgentSessionProviderBridge(provider).buildNewEntryCommand()
}

internal fun buildAgentSessionIdentity(provider: AgentSessionProvider, sessionId: String): String {
  return "${provider.value}:$sessionId"
}

internal data class AgentSessionIdentity(
  val provider: AgentSessionProvider,
  val sessionId: String,
)

internal fun parseAgentSessionIdentity(identity: String): AgentSessionIdentity? {
  val separator = identity.indexOf(':')
  if (separator <= 0 || separator == identity.lastIndex) return null
  val provider = AgentSessionProvider.fromOrNull(identity.substring(0, separator)) ?: return null
  val sessionId = identity.substring(separator + 1)
  return AgentSessionIdentity(provider = provider, sessionId = sessionId)
}

internal fun buildAgentSessionNewIdentity(provider: AgentSessionProvider): String {
  return "${provider.value}:new-${UUID.randomUUID()}"
}

internal fun agentSessionCliMissingMessageKey(provider: AgentSessionProvider): String {
  return requireAgentSessionProviderBridge(provider).cliMissingMessageKey
}
