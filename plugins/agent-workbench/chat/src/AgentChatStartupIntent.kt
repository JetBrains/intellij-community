// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.launch.normalizeAgentSessionSurfaceId
import java.util.Locale

internal sealed interface AgentChatStartupIntent {
  data class NewSession(
    val provider: AgentSessionProvider,
    val launchMode: AgentSessionLaunchMode,
    val launchProfileId: String? = null,
    val launchTargetId: String? = null,
    val surfaceId: AgentSessionSurfaceId? = null,
  ) : AgentChatStartupIntent
}

internal fun resolveAgentChatNewSessionStartupIntent(file: AgentChatVirtualFile): AgentChatStartupIntent.NewSession? {
  val provider = pendingProviderForThreadIdentity(file.threadIdentity) ?: return null
  return AgentChatStartupIntent.NewSession(
    provider = provider,
    launchMode = parseAgentChatLaunchMode(file.pendingLaunchMode),
    launchProfileId = file.launchProfileId,
    launchTargetId = file.launchTargetId,
    surfaceId = parseAgentChatSurfaceId(file.surfaceId),
  )
}

internal fun parseAgentChatLaunchMode(value: String?): AgentSessionLaunchMode {
  return parseAgentChatLaunchModeOrNull(value) ?: AgentSessionLaunchMode.STANDARD
}

internal fun parseAgentChatLaunchModeOrNull(value: String?): AgentSessionLaunchMode? {
  val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return runCatching { AgentSessionLaunchMode.valueOf(normalized.uppercase(Locale.ROOT)) }.getOrNull()
}

fun serializeAgentChatLaunchMode(mode: AgentSessionLaunchMode?): String? {
  return mode?.name?.lowercase(Locale.ROOT)
}

internal fun normalizeAgentChatLaunchMode(value: String?): String? {
  return serializeAgentChatLaunchMode(parseAgentChatLaunchModeOrNull(value))
}

internal fun normalizeAgentChatSurfaceId(value: String?): String? {
  return normalizeAgentSessionSurfaceId(value)
}

internal fun parseAgentChatSurfaceId(value: String?): AgentSessionSurfaceId? {
  return AgentSessionSurfaceId.fromOrNull(value)
}
