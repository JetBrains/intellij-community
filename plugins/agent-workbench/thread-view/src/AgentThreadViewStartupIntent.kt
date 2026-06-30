// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.launch.normalizeAgentSessionSurfaceId
import java.util.Locale

internal sealed interface AgentThreadViewStartupIntent {
  data class NewSession(
    val provider: AgentSessionProvider,
    val launchMode: AgentSessionLaunchMode,
    val launchProfileId: String? = null,
    val launchTargetId: String? = null,
    val surfaceId: AgentSessionSurfaceId? = null,
  ) : AgentThreadViewStartupIntent
}

internal fun resolveAgentThreadViewNewSessionStartupIntent(file: AgentThreadViewVirtualFile): AgentThreadViewStartupIntent.NewSession? {
  val provider = pendingProviderForThreadIdentity(file.threadIdentity) ?: return null
  return AgentThreadViewStartupIntent.NewSession(
    provider = provider,
    launchMode = parseAgentThreadViewLaunchMode(file.pendingLaunchMode),
    launchProfileId = file.launchProfileId,
    launchTargetId = file.launchTargetId,
    surfaceId = parseAgentThreadViewSurfaceId(file.surfaceId),
  )
}

internal fun parseAgentThreadViewLaunchMode(value: String?): AgentSessionLaunchMode {
  return parseAgentThreadViewLaunchModeOrNull(value) ?: AgentSessionLaunchMode.STANDARD
}

internal fun parseAgentThreadViewLaunchModeOrNull(value: String?): AgentSessionLaunchMode? {
  val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return runCatching { AgentSessionLaunchMode.valueOf(normalized.uppercase(Locale.ROOT)) }.getOrNull()
}

fun serializeAgentThreadViewLaunchMode(mode: AgentSessionLaunchMode?): String? {
  return mode?.name?.lowercase(Locale.ROOT)
}

internal fun normalizeAgentThreadViewLaunchMode(value: String?): String? {
  return serializeAgentThreadViewLaunchMode(parseAgentThreadViewLaunchModeOrNull(value))
}

internal fun normalizeAgentThreadViewSurfaceId(value: String?): String? {
  return normalizeAgentSessionSurfaceId(value)
}

internal fun parseAgentThreadViewSurfaceId(value: String?): AgentSessionSurfaceId? {
  return AgentSessionSurfaceId.fromOrNull(value)
}
