// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.model

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

enum class AgentSessionThreadViewMode {
  ACTIVE,
  ARCHIVED,
}

enum class AgentSessionArchivedRangePreset {
  ALL,
  TODAY,
  LAST_7_DAYS,
  LAST_30_DAYS,
}

sealed interface ArchiveThreadTarget {
  val path: String
  val provider: AgentSessionProvider
  val threadId: String

  data class Thread(
    override val path: String,
    override val provider: AgentSessionProvider,
    override val threadId: String,
  ) : ArchiveThreadTarget

  data class SubAgent(
    override val path: String,
    override val provider: AgentSessionProvider,
    @JvmField val parentThreadId: String,
    @JvmField val subAgentId: String,
  ) : ArchiveThreadTarget {
    override val threadId: String
      get() = subAgentId
  }
}

fun normalizeArchiveThreadTarget(target: ArchiveThreadTarget): ArchiveThreadTarget {
  val normalizedPath = normalizeAgentWorkbenchPath(target.path)
  return when (target) {
    is ArchiveThreadTarget.Thread -> {
      if (normalizedPath == target.path) target else target.copy(path = normalizedPath)
    }

    is ArchiveThreadTarget.SubAgent -> {
      if (normalizedPath == target.path) target else target.copy(path = normalizedPath)
    }
  }
}

fun archiveThreadTargetKey(target: ArchiveThreadTarget): String {
  val normalizedTarget = normalizeArchiveThreadTarget(target)
  return when (normalizedTarget) {
    is ArchiveThreadTarget.Thread -> {
      "thread:${normalizedTarget.path}:${normalizedTarget.provider.value}:${normalizedTarget.threadId}"
    }

    is ArchiveThreadTarget.SubAgent -> {
      "sub-agent:${normalizedTarget.path}:${normalizedTarget.provider.value}:${normalizedTarget.parentThreadId}:${normalizedTarget.subAgentId}"
    }
  }
}

data class AgentSessionProviderWarning(
  val provider: AgentSessionProvider,
  @JvmField val message: @NlsSafe String,
)

enum class AgentSessionProviderLoadState {
  LOADING,
  LOADED,
  FAILED,
}

val AgentSessionProviderLoadState.isTerminal: Boolean
  get() = this == AgentSessionProviderLoadState.LOADED || this == AgentSessionProviderLoadState.FAILED

class ProjectBuildSystemBadge(
  @JvmField val id: String,
  @JvmField val icon: Icon,
) {
  override fun equals(other: Any?): Boolean = other is ProjectBuildSystemBadge && id == other.id

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = "ProjectBuildSystemBadge(id=$id)"
}

data class AgentWorktree(
  @JvmField val path: String,
  @JvmField val name: @NlsSafe String,
  @JvmField val branch: @NlsSafe String?,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread> = emptyList(),
  @JvmField val errorMessage: @NlsSafe String? = null,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState> = emptyMap(),
  @JvmField val providersWithUnknownThreadCount: Set<AgentSessionProvider> = emptySet(),
) {
  val isLoading: Boolean
    get() = providerLoadStates.values.any { state -> state == AgentSessionProviderLoadState.LOADING }

  val hasUnknownThreadCount: Boolean
    get() = providersWithUnknownThreadCount.isNotEmpty()
}

data class AgentProjectSessions(
  @JvmField val path: String,
  @JvmField val name: @NlsSafe String,
  @JvmField val branch: @NlsSafe String? = null,
  @JvmField val buildSystemBadge: ProjectBuildSystemBadge? = null,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread> = emptyList(),
  @JvmField val errorMessage: @NlsSafe String? = null,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState> = emptyMap(),
  @JvmField val providersWithUnknownThreadCount: Set<AgentSessionProvider> = emptySet(),
  @JvmField val worktrees: List<AgentWorktree> = emptyList(),
) {
  val isLoading: Boolean
    get() = providerLoadStates.values.any { state -> state == AgentSessionProviderLoadState.LOADING }

  val hasUnknownThreadCount: Boolean
    get() = providersWithUnknownThreadCount.isNotEmpty()
}

fun AgentProjectSessions.hasProviderSnapshot(provider: AgentSessionProvider): Boolean {
  return providerLoadStates[provider]?.isTerminal == true
}

fun AgentProjectSessions.hasAnyProviderSnapshot(): Boolean {
  return providerLoadStates.values.any { state -> state.isTerminal }
}

fun AgentWorktree.hasProviderSnapshot(provider: AgentSessionProvider): Boolean {
  return providerLoadStates[provider]?.isTerminal == true
}

fun AgentWorktree.hasAnyProviderSnapshot(): Boolean {
  return providerLoadStates.values.any { state -> state.isTerminal }
}

data class AgentSessionsState(
  @JvmField val projects: List<AgentProjectSessions> = emptyList(),
  @JvmField val lastUpdatedAt: Long? = null,
  @JvmField val visibleClosedProjectCount: Int = DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT,
  @JvmField val visibleThreadCounts: Map<String, Int> = emptyMap(),
)

data class AgentArchivedSessionsState(
  @JvmField val projects: List<AgentProjectSessions> = emptyList(),
  @JvmField val lastUpdatedAt: Long? = null,
  @JvmField val visibleClosedProjectCount: Int = DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT,
  @JvmField val visibleThreadCounts: Map<String, Int> = emptyMap(),
)
