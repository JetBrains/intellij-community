// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import kotlinx.coroutines.flow.StateFlow

@Service(Service.Level.APP)
class AgentSessionReadService private constructor(
  private val requiredStateStoreProvider: () -> AgentSessionsStateStore,
  private val optionalSessionsStateProvider: () -> AgentSessionsState?,
) {
  @Suppress("unused")
  constructor() : this(
    requiredStateStoreProvider = { service() },
    optionalSessionsStateProvider = {
      ApplicationManager.getApplication().serviceIfCreated<AgentSessionsStateStore>()?.state?.value
    },
  )

  internal constructor(
    stateProvider: () -> AgentSessionsState?,
  ) : this(
    requiredStateStoreProvider = {
      error("AgentSessionsStateStore is unavailable in this test setup")
    },
    optionalSessionsStateProvider = stateProvider,
  )

  internal fun stateFlow(): StateFlow<AgentSessionsState> = requiredStateStoreProvider().state

  internal fun stateSnapshot(): AgentSessionsState = stateFlow().value

  internal fun isRefreshing(): Boolean = stateSnapshot().projects.any { project -> project.isLoading }

  fun resolvePendingThreadRebindTarget(
    context: AgentChatEditorTabActionContext,
    provider: AgentSessionProvider,
  ): AgentChatTabRebindTarget? {
    if (!isPendingEditorContext(context, provider)) {
      return null
    }

    val state = optionalSessionsStateProvider() ?: return null
    val normalizedPath = normalizeAgentWorkbenchPath(context.path)
    val candidateThreads = resolveThreadsForPath(state, normalizedPath)
      .asSequence()
      .filter { thread -> thread.provider == provider }
      .sortedByDescending { thread -> thread.updatedAt }
      .toList()

    val thread = candidateThreads.firstOrNull() ?: return null

    return AgentChatTabRebindTarget(
      projectPath = normalizedPath,
      provider = thread.provider,
      threadIdentity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id),
      threadId = thread.id,
      threadTitle = thread.title,
      threadActivity = thread.activity,
      threadUpdatedAt = thread.updatedAt,
    )
  }

  fun resolvePendingCodexRebindTarget(context: AgentChatEditorTabActionContext): AgentChatTabRebindTarget? {
    return resolvePendingThreadRebindTarget(context = context, provider = AgentSessionProvider.CODEX)
  }
}

internal data class AgentSessionPathState(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val isLoading: Boolean,
  @JvmField val hasLoaded: Boolean,
  @JvmField val errorMessage: String?,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning>,
)

internal fun resolveAgentSessionPathState(state: AgentSessionsState, normalizedPath: String): AgentSessionPathState? {
  state.projects.firstOrNull { project -> project.path == normalizedPath }?.let { project ->
    return AgentSessionPathState(
      threads = project.threads,
      isLoading = project.isLoading,
      hasLoaded = project.hasLoaded,
      errorMessage = project.errorMessage,
      providerWarnings = project.providerWarnings,
    )
  }

  state.projects.forEach { project ->
    val worktree = project.worktrees.firstOrNull { candidate -> candidate.path == normalizedPath } ?: return@forEach
    return AgentSessionPathState(
      threads = worktree.threads,
      isLoading = worktree.isLoading,
      hasLoaded = worktree.hasLoaded,
      errorMessage = worktree.errorMessage,
      providerWarnings = worktree.providerWarnings,
    )
  }

  return null
}

fun isPendingEditorContext(context: AgentChatEditorTabActionContext, provider: AgentSessionProvider): Boolean {
  val threadCoordinates = context.threadCoordinates ?: return false
  return threadCoordinates.isPending && threadCoordinates.provider == provider
}

private fun resolveThreadsForPath(state: AgentSessionsState, normalizedPath: String): List<AgentSessionThread> {
  return resolveAgentSessionPathState(state, normalizedPath)?.threads.orEmpty()
}
