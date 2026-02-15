// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers

import com.intellij.agent.workbench.sessions.providers.claude.ClaudeSessionSource
import com.intellij.agent.workbench.sessions.providers.codex.CodexSessionSource

internal fun createDefaultAgentSessionSources(): List<AgentSessionSource> {
  return listOf(
    CodexSessionSource(),
    ClaudeSessionSource(),
  )
}
