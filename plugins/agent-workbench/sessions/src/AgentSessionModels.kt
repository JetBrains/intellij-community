// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import androidx.compose.runtime.Immutable

internal enum class AgentSessionProvider {
  CODEX,
  CLAUDE,
}

internal enum class AgentSessionActivity {
  READY,
  PROCESSING,
  REVIEWING,
  UNREAD,
}

@Immutable
internal data class AgentSubAgent(
  @JvmField val id: String,
  @JvmField val name: String,
)

@Immutable
internal data class AgentSessionThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val archived: Boolean,
  @JvmField val activity: AgentSessionActivity = AgentSessionActivity.READY,
  @JvmField val provider: AgentSessionProvider = AgentSessionProvider.CODEX,
  @JvmField val subAgents: List<AgentSubAgent> = emptyList(),
  @JvmField val originBranch: String? = null,
)

@Immutable
internal data class AgentSessionProviderWarning(
  @JvmField val provider: AgentSessionProvider,
  @JvmField val message: String,
)

@Immutable
internal data class AgentWorktree(
  @JvmField val path: String,
  @JvmField val name: String,
  @JvmField val branch: String?,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread> = emptyList(),
  @JvmField val isLoading: Boolean = false,
  @JvmField val hasLoaded: Boolean = false,
  @JvmField val hasUnknownThreadCount: Boolean = false,
  @JvmField val errorMessage: String? = null,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
)

@Immutable
internal data class AgentProjectSessions(
  @JvmField val path: String,
  @JvmField val name: String,
  @JvmField val branch: String? = null,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread> = emptyList(),
  @JvmField val isLoading: Boolean = false,
  @JvmField val hasLoaded: Boolean = false,
  @JvmField val hasUnknownThreadCount: Boolean = false,
  @JvmField val errorMessage: String? = null,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
  @JvmField val worktrees: List<AgentWorktree> = emptyList(),
)

internal data class AgentSessionsState(
  @JvmField val projects: List<AgentProjectSessions> = emptyList(),
  @JvmField val lastUpdatedAt: Long? = null,
  @JvmField val visibleProjectCount: Int = DEFAULT_VISIBLE_PROJECT_COUNT,
  @JvmField val visibleThreadCounts: Map<String, Int> = emptyMap(),
)
