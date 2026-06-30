// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec plugins/ij-air/spec/thread-view/agent-thread-view.spec.md
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.thread.view.AgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.thread.view.AgentThreadViewTabRebindTarget
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.isTerminal
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
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

  fun stateFlow(): StateFlow<AgentSessionsState> = requiredStateStoreProvider().state

  fun resolvePendingThreadRebindTarget(
    context: AgentThreadViewEditorTabActionContext,
    provider: AgentSessionProvider,
  ): AgentThreadViewTabRebindTarget? {
    if (!isPendingEditorContext(context, provider)) {
      return null
    }

    val pendingSessionId = context.threadCoordinates?.sessionId ?: return null
    val state = optionalSessionsStateProvider() ?: return null
    val normalizedPath = normalizeAgentWorkbenchPath(context.path)
    val pathState = resolveAgentSessionPathState(state, normalizedPath) ?: return null
    val thread = pathState.threads
                   .asSequence()
                   .filter { thread -> thread.isConcreteRebindTarget(provider = provider, pendingSessionId = pendingSessionId) }
                   .maxByOrNull { thread -> thread.updatedAt }
                 ?: return null

    return AgentThreadViewTabRebindTarget(
      projectPath = normalizedPath,
      projectDirectory = pathState.projectDirectory,
      provider = thread.provider,
      threadIdentity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id),
      threadId = thread.id,
      threadTitle = thread.title,
      threadActivity = thread.activityReport.rowActivity,
      threadUpdatedAt = thread.updatedAt,
    )
  }

}

internal data class AgentSessionPathState(
  @JvmField val projectDirectory: String?,
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  @JvmField val errorMessage: String?,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning>,
) {
  fun isProviderLoading(provider: AgentSessionProvider): Boolean {
    return providerLoadStates[provider] == AgentSessionProviderLoadState.LOADING
  }

  fun hasProviderSnapshot(provider: AgentSessionProvider): Boolean {
    return providerLoadStates[provider]?.isTerminal == true
  }
}

internal fun resolveAgentSessionPathState(state: AgentSessionsState, normalizedPath: String): AgentSessionPathState? {
  state.projects.firstOrNull { project -> project.path == normalizedPath }?.let { project ->
    return AgentSessionPathState(
      projectDirectory = project.projectDirectory,
      threads = project.threads,
      providerLoadStates = project.providerLoadStates,
      errorMessage = project.errorMessage,
      providerWarnings = project.providerWarnings,
    )
  }

  state.projects.forEach { project ->
    val worktree = project.worktrees.firstOrNull { candidate -> candidate.path == normalizedPath } ?: return@forEach
    return AgentSessionPathState(
      projectDirectory = worktree.projectDirectory,
      threads = worktree.threads,
      providerLoadStates = worktree.providerLoadStates,
      errorMessage = worktree.errorMessage,
      providerWarnings = worktree.providerWarnings,
    )
  }

  return null
}

fun isPendingEditorContext(context: AgentThreadViewEditorTabActionContext, provider: AgentSessionProvider): Boolean {
  val threadCoordinates = context.threadCoordinates ?: return false
  return threadCoordinates.isPending &&
         threadCoordinates.participatesInPendingThreadLifecycle &&
         threadCoordinates.provider == provider
}

private fun AgentSessionThread.isConcreteRebindTarget(provider: AgentSessionProvider, pendingSessionId: String): Boolean {
  // Projected `new-*` rows keep pending tabs visible in the tree but never represent a concrete
  // provider-backed thread, so binding a pending editor tab to them would self-rebind it.
  return this.provider == provider &&
         id != pendingSessionId &&
         !isAgentSessionNewSessionId(id)
}
