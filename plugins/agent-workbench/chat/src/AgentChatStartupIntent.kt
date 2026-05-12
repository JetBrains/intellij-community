// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider

internal sealed interface AgentChatStartupIntent {
  data class NewSession(
    val provider: AgentSessionProvider,
    val launchMode: AgentSessionLaunchMode,
  ) : AgentChatStartupIntent
}

internal fun resolveAgentChatNewSessionStartupIntent(file: AgentChatVirtualFile): AgentChatStartupIntent.NewSession? {
  val provider = pendingProviderForThreadIdentity(file.threadIdentity) ?: return null
  return AgentChatStartupIntent.NewSession(
    provider = provider,
    launchMode = parseAgentChatLaunchMode(file.pendingLaunchMode),
  )
}

internal fun parseAgentChatLaunchMode(value: String?): AgentSessionLaunchMode {
  val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return AgentSessionLaunchMode.STANDARD
  return runCatching { AgentSessionLaunchMode.valueOf(normalized.uppercase()) }.getOrDefault(AgentSessionLaunchMode.STANDARD)
}
