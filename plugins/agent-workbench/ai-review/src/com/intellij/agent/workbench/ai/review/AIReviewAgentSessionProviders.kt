// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val CLAUDE_AGENT_SESSION_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("claude")

@ApiStatus.Internal
val CODEX_AGENT_SESSION_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("codex")

@ApiStatus.Internal
val JUNIE_AGENT_SESSION_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("junie")

@ApiStatus.Internal
val OPENCODE_AGENT_SESSION_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("opencode")

@ApiStatus.Internal
val AVAILABLE_AI_REVIEW_AGENT_PROVIDERS: List<AgentSessionProvider> = listOf(
  CLAUDE_AGENT_SESSION_PROVIDER,
  CODEX_AGENT_SESSION_PROVIDER,
  JUNIE_AGENT_SESSION_PROVIDER,
  OPENCODE_AGENT_SESSION_PROVIDER,
)
