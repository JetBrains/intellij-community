// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class AgentThreadViewOpenTabsPresentationState(
  private val pendingTabsByProvider: Map<AgentSessionProvider, Map<String, List<AgentThreadViewPendingTabSnapshot>>> = emptyMap(),
  private val pinnedTopLevelThreadIdsByProvider: Map<AgentSessionProvider, Map<String, Set<String>>> = emptyMap(),
) {
  fun pendingTabsByPath(provider: AgentSessionProvider): Map<String, List<AgentThreadViewPendingTabSnapshot>> {
    return pendingTabsByProvider[provider].orEmpty()
  }

  fun isPinnedTopLevelThread(provider: AgentSessionProvider, projectPath: String, threadId: String): Boolean {
    return threadId in pinnedTopLevelThreadIdsByProvider[provider]?.get(projectPath).orEmpty()
  }

  fun providers(): Set<AgentSessionProvider> = pendingTabsByProvider.keys + pinnedTopLevelThreadIdsByProvider.keys

  companion object {
    @JvmField
    val EMPTY: AgentThreadViewOpenTabsPresentationState = AgentThreadViewOpenTabsPresentationState()
  }
}

@Service(Service.Level.APP)
class AgentThreadViewOpenTabsPresentationStateService(private val serviceScope: CoroutineScope) {
  private val mutableState = MutableStateFlow(AgentThreadViewOpenTabsPresentationState.EMPTY)
  private val refreshGeneration = AtomicLong()
  val state: StateFlow<AgentThreadViewOpenTabsPresentationState> = mutableState.asStateFlow()

  fun refreshOpenTabs() {
    val generation = refreshGeneration.incrementAndGet()
    serviceScope.launch {
      updateOpenTabsIfLatest(generation)
    }
  }

  private suspend fun updateOpenTabsIfLatest(generation: Long) {
    val state = collectOpenAgentThreadViewTabsPresentationState()
    if (refreshGeneration.get() == generation) {
      mutableState.value = state
    }
  }
}

internal suspend fun collectOpenAgentThreadViewTabsPresentationState(): AgentThreadViewOpenTabsPresentationState = withContext(Dispatchers.UI) {
  val openTabsSnapshot = collectOpenAgentThreadViewTabsSnapshot()
  val pendingTabsByProvider = LinkedHashMap<AgentSessionProvider, Map<String, List<AgentThreadViewPendingTabSnapshot>>>()
  val pinnedThreadsByProvider = LinkedHashMap<AgentSessionProvider, Map<String, Set<String>>>()
  for (descriptor in AgentSessionProviders.allProvidersById()) {
    if (!descriptor.supportsPendingEditorTabRebind) {
      val providerPinnedThreads = openTabsSnapshot.pinnedTopLevelConcreteThreadIdentitiesByPath(descriptor.provider)
      if (providerPinnedThreads.isNotEmpty()) {
        pinnedThreadsByProvider[descriptor.provider] = providerPinnedThreads
      }
      continue
    }
    val providerPendingTabs = openTabsSnapshot.pendingTabsByPath(descriptor.provider)
    if (providerPendingTabs.isNotEmpty()) {
      pendingTabsByProvider[descriptor.provider] = providerPendingTabs
      collectPinnedPendingThreads(
        provider = descriptor.provider,
        pendingTabsByPath = providerPendingTabs,
        pinnedThreadsByProvider = pinnedThreadsByProvider,
      )
    }
    val providerPinnedThreads = openTabsSnapshot.pinnedTopLevelConcreteThreadIdentitiesByPath(descriptor.provider)
    if (providerPinnedThreads.isNotEmpty()) {
      mergePinnedThreads(
        provider = descriptor.provider,
        pinnedThreadsByPath = providerPinnedThreads,
        pinnedThreadsByProvider = pinnedThreadsByProvider,
      )
    }
  }
  AgentThreadViewOpenTabsPresentationState(
    pendingTabsByProvider = pendingTabsByProvider,
    pinnedTopLevelThreadIdsByProvider = pinnedThreadsByProvider,
  )
}

private fun collectPinnedPendingThreads(
  provider: AgentSessionProvider,
  pendingTabsByPath: Map<String, List<AgentThreadViewPendingTabSnapshot>>,
  pinnedThreadsByProvider: LinkedHashMap<AgentSessionProvider, Map<String, Set<String>>>,
) {
  val pinnedByPath = LinkedHashMap<String, LinkedHashSet<String>>()
  for ((path, pendingTabs) in pendingTabsByPath) {
    val pinnedThreadIdentities = pendingTabs
      .filter { pendingTab -> pendingTab.pinnedEditorTab }
      .map { pendingTab -> pendingTab.pendingThreadIdentity }
    for (pendingThreadIdentity in pinnedThreadIdentities) {
      val identity = splitAgentThreadIdentity(pendingThreadIdentity) ?: continue
      if (!provider.value.equals(identity.first, ignoreCase = true)) {
        continue
      }
      pinnedByPath.computeIfAbsent(path) { LinkedHashSet() }.add(identity.second)
    }
  }
  if (pinnedByPath.isNotEmpty()) {
    mergePinnedThreads(
      provider = provider,
      pinnedThreadsByPath = pinnedByPath,
      pinnedThreadsByProvider = pinnedThreadsByProvider,
    )
  }
}

private fun mergePinnedThreads(
  provider: AgentSessionProvider,
  pinnedThreadsByPath: Map<String, Set<String>>,
  pinnedThreadsByProvider: LinkedHashMap<AgentSessionProvider, Map<String, Set<String>>>,
) {
  val mergedByPath = LinkedHashMap<String, LinkedHashSet<String>>()
  for ((path, threadIds) in pinnedThreadsByProvider[provider].orEmpty()) {
    mergedByPath[path] = LinkedHashSet(threadIds)
  }
  for ((path, threadIds) in pinnedThreadsByPath) {
    mergedByPath.computeIfAbsent(path) { LinkedHashSet() }.addAll(threadIds)
  }
  if (mergedByPath.isNotEmpty()) {
    val result = LinkedHashMap<String, Set<String>>(mergedByPath.size)
    for ((path, threadIds) in mergedByPath) {
      result[path] = LinkedHashSet(threadIds)
    }
    pinnedThreadsByProvider[provider] = result
  }
}

internal fun participatesInPendingAgentThreadViewProjection(file: AgentThreadViewVirtualFile): Boolean {
  val provider = file.provider ?: return false
  return file.participatesInPendingThreadLifecycle() &&
         AgentSessionProviders.find(provider)?.supportsPendingEditorTabRebind == true
}

internal fun participatesInOpenAgentThreadViewPresentation(file: AgentThreadViewVirtualFile): Boolean {
  return participatesInPendingAgentThreadViewProjection(file) ||
         (file.provider != null && !file.isPendingThread && file.subAgentId == null)
}
