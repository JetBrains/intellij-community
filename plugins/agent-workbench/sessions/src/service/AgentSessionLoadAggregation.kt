// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.sortAgentSessionThreadsForDisplay

internal data class AgentSessionLoadResult(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val errorMessage: String? = null,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState> = emptyMap(),
  @JvmField val providersWithUnknownThreadCount: Set<AgentSessionProvider> = emptySet(),
) {
  val hasUnknownThreadCount: Boolean
    get() = providersWithUnknownThreadCount.isNotEmpty()
}

internal data class AgentSessionProviderLoadMetadata(
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  @JvmField val providersWithUnknownThreadCount: Set<AgentSessionProvider>,
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
  val providerLoadStates = buildMap {
    sourceResults.forEach { sourceResult ->
      put(
        sourceResult.provider,
        if (sourceResult.result.isSuccess) AgentSessionProviderLoadState.LOADED else AgentSessionProviderLoadState.FAILED,
      )
    }
  }
  val providersWithUnknownThreadCount = sourceResults
    .asSequence()
    .filter { sourceResult -> sourceResult.hasUnknownTotal }
    .map { sourceResult -> sourceResult.provider }
    .toSet()
    .retainLoadedProviders(providerLoadStates)

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
    providerWarnings = if (allSourcesFailed) emptyList() else providerWarnings,
    providerLoadStates = providerLoadStates,
    providersWithUnknownThreadCount = providersWithUnknownThreadCount,
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

internal fun mergeProviderLoadMetadata(
  currentProviderLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  currentProvidersWithUnknownThreadCount: Set<AgentSessionProvider>,
  providerLoadStateUpdates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  updatedProvidersWithUnknownThreadCount: Set<AgentSessionProvider>,
): AgentSessionProviderLoadMetadata {
  val providerLoadStates = mergeProviderLoadStates(currentProviderLoadStates, providerLoadStateUpdates)
  val providersWithUnknownThreadCount = mergeUpdatedProvidersWithUnknownThreadCount(
    current = currentProvidersWithUnknownThreadCount,
    providerLoadStateUpdates = providerLoadStateUpdates,
    updatedProvidersWithUnknownThreadCount = updatedProvidersWithUnknownThreadCount,
  ).retainLoadedProviders(providerLoadStates)
  return AgentSessionProviderLoadMetadata(
    providerLoadStates = providerLoadStates,
    providersWithUnknownThreadCount = providersWithUnknownThreadCount,
  )
}

internal fun updateProviderLoadMetadata(
  currentProviderLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  currentProvidersWithUnknownThreadCount: Set<AgentSessionProvider>,
  provider: AgentSessionProvider,
  providerLoadState: AgentSessionProviderLoadState?,
  providerHasUnknownThreadCount: Boolean? = null,
): AgentSessionProviderLoadMetadata {
  if (providerLoadState == null && providerHasUnknownThreadCount == null) {
    return AgentSessionProviderLoadMetadata(
      providerLoadStates = currentProviderLoadStates,
      providersWithUnknownThreadCount = currentProvidersWithUnknownThreadCount.retainLoadedProviders(currentProviderLoadStates),
    )
  }
  if (providerLoadState == null) {
    val updatedProvidersWithUnknownThreadCount = if (providerHasUnknownThreadCount == true) {
      currentProvidersWithUnknownThreadCount + provider
    }
    else {
      currentProvidersWithUnknownThreadCount - provider
    }
    val providersWithUnknownThreadCount = updatedProvidersWithUnknownThreadCount.retainLoadedProviders(currentProviderLoadStates)
    return AgentSessionProviderLoadMetadata(
      providerLoadStates = currentProviderLoadStates,
      providersWithUnknownThreadCount = providersWithUnknownThreadCount,
    )
  }
  val providerLoadStateUpdates = mapOf(provider to providerLoadState)
  val updatedProvidersWithUnknownThreadCount = when (providerHasUnknownThreadCount) {
    true -> setOf(provider)
    false -> emptySet()
    null -> if (provider in currentProvidersWithUnknownThreadCount) setOf(provider) else emptySet()
  }
  return mergeProviderLoadMetadata(
    currentProviderLoadStates = currentProviderLoadStates,
    currentProvidersWithUnknownThreadCount = currentProvidersWithUnknownThreadCount,
    providerLoadStateUpdates = providerLoadStateUpdates,
    updatedProvidersWithUnknownThreadCount = updatedProvidersWithUnknownThreadCount,
  )
}

internal fun failLoadingProviderLoadStates(
  providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  if (providerLoadStates.isEmpty() || providerLoadStates.values.none { state -> state == AgentSessionProviderLoadState.LOADING }) {
    return providerLoadStates
  }
  return providerLoadStates.mapValues { (_, state) ->
    if (state == AgentSessionProviderLoadState.LOADING) AgentSessionProviderLoadState.FAILED else state
  }
}

internal fun failLoadingProviderLoadMetadata(
  providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  providersWithUnknownThreadCount: Set<AgentSessionProvider>,
): AgentSessionProviderLoadMetadata {
  val failedProviderLoadStates = failLoadingProviderLoadStates(providerLoadStates)
  return AgentSessionProviderLoadMetadata(
    providerLoadStates = failedProviderLoadStates,
    providersWithUnknownThreadCount = providersWithUnknownThreadCount.retainLoadedProviders(failedProviderLoadStates),
  )
}

private fun mergeUpdatedProvidersWithUnknownThreadCount(
  current: Set<AgentSessionProvider>,
  providerLoadStateUpdates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  updatedProvidersWithUnknownThreadCount: Set<AgentSessionProvider>,
): Set<AgentSessionProvider> {
  if (providerLoadStateUpdates.isEmpty()) {
    return current
  }
  if (current.isEmpty()) {
    return updatedProvidersWithUnknownThreadCount
  }
  val updatedProviders = providerLoadStateUpdates.keys
  return buildSet {
    current.filterTo(this) { provider -> provider !in updatedProviders }
    addAll(updatedProvidersWithUnknownThreadCount)
  }
}

private fun Set<AgentSessionProvider>.retainLoadedProviders(
  providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
): Set<AgentSessionProvider> {
  if (isEmpty()) {
    return this
  }
  return filterTo(LinkedHashSet()) { provider -> providerLoadStates[provider] == AgentSessionProviderLoadState.LOADED }
}
