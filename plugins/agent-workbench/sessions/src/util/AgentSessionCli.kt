// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.util

import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.parseAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import java.util.UUID

internal class AgentSessionProviderUnavailableException(provider: AgentSessionProvider) :
  IllegalStateException("No session provider registered for ${provider.value}")

private fun requireAgentSessionProviderDescriptor(provider: AgentSessionProvider) =
  AgentSessionProviders.find(provider)
  ?: throw AgentSessionProviderUnavailableException(provider)

internal fun buildAgentSessionIdentity(provider: AgentSessionProvider, sessionId: String): String {
  return buildAgentThreadIdentity(providerId = provider.value, threadId = sessionId)
}

internal data class AgentSessionIdentity(
  val provider: AgentSessionProvider,
  @JvmField val sessionId: String,
)

internal fun parseAgentSessionIdentity(identity: String): AgentSessionIdentity? {
  val threadIdentity = parseAgentThreadIdentity(identity) ?: return null
  val provider = AgentSessionProvider.fromOrNull(threadIdentity.providerId) ?: return null
  return AgentSessionIdentity(provider = provider, sessionId = threadIdentity.threadId)
}

internal fun isAgentSessionNewIdentity(identity: String): Boolean {
  val sessionId = parseAgentSessionIdentity(identity)?.sessionId ?: return false
  return isAgentSessionNewSessionId(sessionId)
}

fun isAgentSessionNewSessionId(sessionId: String): Boolean {
  return sessionId.startsWith("new-")
}

internal fun resolveAgentSessionId(identity: String): String {
  val sessionId = parseAgentSessionIdentity(identity)?.sessionId ?: return identity
  return sessionId.takeUnless(::isAgentSessionNewSessionId).orEmpty()
}

internal fun buildAgentSessionNewIdentity(provider: AgentSessionProvider): String {
  return buildAgentThreadIdentity(providerId = provider.value, threadId = "new-${UUID.randomUUID()}")
}

internal fun agentSessionCliMissingMessageKey(provider: AgentSessionProvider): String {
  return requireAgentSessionProviderDescriptor(provider).cliMissingMessageKey
}
