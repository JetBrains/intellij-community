// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.isTerminal

internal data class AgentSessionPathContent(
  @JvmField val path: String,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
) {
  val hasAnyProviderSnapshot: Boolean
    get() = providerLoadStates.values.any { state -> state.isTerminal }

  fun hasProviderSnapshot(provider: AgentSessionProvider): Boolean {
    return providerLoadStates[provider]?.isTerminal == true
  }
}

internal inline fun AgentSessionsState.forEachPathContent(action: (AgentSessionPathContent) -> Unit) {
  projects.forEach { project ->
    action(project.toPathContent())
    project.worktrees.forEach { worktree ->
      action(worktree.toPathContent())
    }
  }
}

private fun AgentProjectSessions.toPathContent(): AgentSessionPathContent {
  return AgentSessionPathContent(
    path = path,
    isOpen = isOpen,
    threads = threads,
    providerLoadStates = providerLoadStates,
  )
}

private fun AgentWorktree.toPathContent(): AgentSessionPathContent {
  return AgentSessionPathContent(
    path = path,
    isOpen = isOpen,
    threads = threads,
    providerLoadStates = providerLoadStates,
  )
}
