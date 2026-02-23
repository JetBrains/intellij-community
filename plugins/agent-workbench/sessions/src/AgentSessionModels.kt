// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import androidx.compose.runtime.Immutable

@Immutable
data class AgentSessionThreadPreview(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  val provider: AgentSessionProvider = AgentSessionProvider.CODEX,
)

@Immutable
internal data class AgentSessionProviderWarning(
  val provider: AgentSessionProvider,
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
