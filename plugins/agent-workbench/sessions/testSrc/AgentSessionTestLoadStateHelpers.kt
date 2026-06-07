// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions as ModelAgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree as ModelAgentWorktree

internal typealias AgentProjectSessions = ModelAgentProjectSessions
internal typealias AgentWorktree = ModelAgentWorktree

internal fun loadedProviderStates(provider: AgentSessionProvider): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  return providerStates(listOf(provider))
}

internal fun loadedProviderStates(
  firstProvider: AgentSessionProvider,
  secondProvider: AgentSessionProvider,
): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  return providerStates(listOf(firstProvider, secondProvider))
}

private fun providerStates(providers: Iterable<AgentSessionProvider>): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  return providers.associateWith { AgentSessionProviderLoadState.LOADED }
}
