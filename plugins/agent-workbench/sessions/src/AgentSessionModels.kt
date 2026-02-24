// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.openapi.util.NlsSafe

data class AgentSessionThreadPreview(
  @JvmField val id: String,
  @JvmField val title: @NlsSafe String,
  @JvmField val updatedAt: Long,
  val provider: AgentSessionProvider = AgentSessionProvider.CODEX,
)

internal data class ArchiveThreadTarget(
  @JvmField val path: String,
  @JvmField val thread: AgentSessionThread,
)

internal data class AgentSessionProviderWarning(
  val provider: AgentSessionProvider,
  @JvmField val message: @NlsSafe String,
)

internal data class AgentWorktree(
  @JvmField val path: String,
  @JvmField val name: @NlsSafe String,
  @JvmField val branch: @NlsSafe String?,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread> = emptyList(),
  @JvmField val isLoading: Boolean = false,
  @JvmField val hasLoaded: Boolean = false,
  @JvmField val hasUnknownThreadCount: Boolean = false,
  @JvmField val errorMessage: @NlsSafe String? = null,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
)

internal data class AgentProjectSessions(
  @JvmField val path: String,
  @JvmField val name: @NlsSafe String,
  @JvmField val branch: @NlsSafe String? = null,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread> = emptyList(),
  @JvmField val isLoading: Boolean = false,
  @JvmField val hasLoaded: Boolean = false,
  @JvmField val hasUnknownThreadCount: Boolean = false,
  @JvmField val errorMessage: @NlsSafe String? = null,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
  @JvmField val worktrees: List<AgentWorktree> = emptyList(),
)

internal data class AgentSessionsState(
  @JvmField val projects: List<AgentProjectSessions> = emptyList(),
  @JvmField val lastUpdatedAt: Long? = null,
  @JvmField val visibleClosedProjectCount: Int = DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT,
  @JvmField val visibleThreadCounts: Map<String, Int> = emptyMap(),
)
