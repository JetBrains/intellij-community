// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.tree

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.formatAgentSessionThreadTitle
import com.intellij.openapi.util.NlsSafe

fun threadDisplayTitle(threadId: String, title: String): @NlsSafe String {
  return formatAgentSessionThreadTitle(threadId = threadId, title = title) { idPrefix ->
    AgentSessionsBundle.message("toolwindow.thread.fallback.title", idPrefix)
  }
}
