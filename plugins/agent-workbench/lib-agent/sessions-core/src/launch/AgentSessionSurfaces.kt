// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.launch

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import org.jetbrains.annotations.ApiStatus
import java.util.Locale

@ApiStatus.Internal
const val AGENT_SESSION_SURFACE_TERMINAL: String = "terminal"

@ApiStatus.Internal
const val AGENT_SESSION_SURFACE_ACP: String = "acp"

@ApiStatus.Internal
fun normalizeAgentSessionSurfaceId(surfaceId: String?): String? {
  return surfaceId?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
}

@ApiStatus.Internal
fun defaultAgentSessionSurfaceId(provider: AgentSessionProvider): String {
  return if (provider.value == AGENT_SESSION_SURFACE_ACP) AGENT_SESSION_SURFACE_ACP else AGENT_SESSION_SURFACE_TERMINAL
}

@ApiStatus.Internal
fun effectiveAgentSessionSurfaceId(provider: AgentSessionProvider, surfaceId: String?): String {
  return normalizeAgentSessionSurfaceId(surfaceId) ?: defaultAgentSessionSurfaceId(provider)
}
