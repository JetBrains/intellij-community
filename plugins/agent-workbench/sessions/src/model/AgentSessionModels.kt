// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.model

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

internal sealed interface ArchiveThreadTarget {
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

internal fun normalizeArchiveThreadTarget(target: ArchiveThreadTarget): ArchiveThreadTarget {
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

internal fun archiveThreadTargetKey(target: ArchiveThreadTarget): String {
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

internal data class AgentSessionProviderWarning(
  val provider: AgentSessionProvider,
  @JvmField val message: @NlsSafe String,
)

internal class ProjectBuildSystemBadge(
  @JvmField val id: String,
  @JvmField val icon: Icon,
) {
  override fun equals(other: Any?): Boolean = other is ProjectBuildSystemBadge && id == other.id

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = "ProjectBuildSystemBadge(id=$id)"
}

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
  @JvmField val buildSystemBadge: ProjectBuildSystemBadge? = null,
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
