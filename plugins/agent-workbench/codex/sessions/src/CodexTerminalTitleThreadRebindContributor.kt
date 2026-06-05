// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.chat.AgentChatTerminalTitleThreadRebindContributor
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import java.util.Locale

internal class CodexTerminalTitleThreadRebindContributor : AgentChatTerminalTitleThreadRebindContributor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CODEX

  override fun extractThreadId(applicationTitle: String?): String? {
    val normalizedTitle = applicationTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return CODEX_THREAD_ID_IN_TERMINAL_TITLE_REGEX.find(normalizedTitle)?.value?.lowercase(Locale.ROOT)
  }
}

private val CODEX_THREAD_ID_IN_TERMINAL_TITLE_REGEX = Regex(
  "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
)
