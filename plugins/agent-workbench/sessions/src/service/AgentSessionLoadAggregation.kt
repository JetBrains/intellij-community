// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.sortAgentSessionThreadsForDisplay

internal data class AgentSessionLoadResult(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val errorMessage: String? = null,
  @JvmField val hasUnknownThreadCount: Boolean = false,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState> = emptyMap(),
)

internal data class AgentSessionSourceLoadResult(
  val provider: AgentSessionProvider,
  val result: Result<List<AgentSessionThread>>,
  @JvmField val hasUnknownTotal: Boolean = false,
)

internal fun mergeAgentSessionSourceLoadResults(
  sourceResults: List<AgentSessionSourceLoadResult>,
  resolveErrorMessage: (AgentSessionProvider, Throwable) -> String,
  resolveWarningMessage: (AgentSessionProvider, Throwable) -> String = resolveErrorMessage,
): AgentSessionLoadResult {
  val mergedThreads = buildList {
    sourceResults.forEach { sourceResult ->
      addAll(sourceResult.result.getOrElse { emptyList() })
    }
  }.let(::sortAgentSessionThreadsForDisplay)

  val providerWarnings = sourceResults.mapNotNull { sourceResult ->
    sourceResult.result.exceptionOrNull()?.let { throwable ->
      AgentSessionProviderWarning(
        provider = sourceResult.provider,
        message = resolveWarningMessage(sourceResult.provider, throwable),
      )
    }
  }
  val hasUnknownThreadCount = sourceResults.any { it.hasUnknownTotal }
  val providerLoadStates = buildMap {
    sourceResults.forEach { sourceResult ->
      put(
        sourceResult.provider,
        if (sourceResult.result.isSuccess) AgentSessionProviderLoadState.LOADED else AgentSessionProviderLoadState.FAILED,
      )
    }
  }

  val firstError = sourceResults.firstNotNullOfOrNull { sourceResult ->
    sourceResult.result.exceptionOrNull()?.let { throwable ->
      resolveErrorMessage(sourceResult.provider, throwable)
    }
  }
  val allSourcesFailed = sourceResults.isNotEmpty() && sourceResults.all { it.result.isFailure }
  val errorMessage = if (allSourcesFailed) firstError else null
  return AgentSessionLoadResult(
    threads = mergedThreads,
    errorMessage = errorMessage,
    hasUnknownThreadCount = hasUnknownThreadCount,
    providerWarnings = if (allSourcesFailed) emptyList() else providerWarnings,
    providerLoadStates = providerLoadStates,
  )
}

internal fun buildLoadingProviderLoadStates(
  providers: Collection<AgentSessionProvider>,
): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  if (providers.isEmpty()) {
    return emptyMap()
  }
  return buildMap {
    providers.forEach { provider -> put(provider, AgentSessionProviderLoadState.LOADING) }
  }
}

internal fun deriveLoadedProviderStatesFromThreads(
  threads: List<AgentSessionThread>,
): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  if (threads.isEmpty()) {
    return emptyMap()
  }
  return buildMap {
    threads.forEach { thread -> put(thread.provider, AgentSessionProviderLoadState.LOADED) }
  }
}

internal fun mergeProviderLoadStates(
  current: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  updates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  if (updates.isEmpty()) {
    return current
  }
  if (current.isEmpty()) {
    return updates
  }
  return current + updates
}

internal fun AgentProjectSessions.hasProviderSnapshot(provider: AgentSessionProvider): Boolean {
  if (hasLoaded) {
    return true
  }
  val providerLoadState = providerLoadStates[provider]
  return providerLoadState == AgentSessionProviderLoadState.LOADED ||
         providerLoadState == AgentSessionProviderLoadState.FAILED ||
         threads.any { thread -> thread.provider == provider }
}

internal fun AgentProjectSessions.hasAnyProviderSnapshot(): Boolean {
  return hasLoaded || providerLoadStates.values.any { state -> state.isTerminal() } || threads.isNotEmpty()
}

internal fun AgentWorktree.hasProviderSnapshot(provider: AgentSessionProvider): Boolean {
  if (hasLoaded) {
    return true
  }
  val providerLoadState = providerLoadStates[provider]
  return providerLoadState == AgentSessionProviderLoadState.LOADED ||
         providerLoadState == AgentSessionProviderLoadState.FAILED ||
         threads.any { thread -> thread.provider == provider }
}

internal fun AgentWorktree.hasAnyProviderSnapshot(): Boolean {
  return hasLoaded || providerLoadStates.values.any { state -> state.isTerminal() } || threads.isNotEmpty()
}

private fun AgentSessionProviderLoadState.isTerminal(): Boolean {
  return this == AgentSessionProviderLoadState.LOADED || this == AgentSessionProviderLoadState.FAILED
}
