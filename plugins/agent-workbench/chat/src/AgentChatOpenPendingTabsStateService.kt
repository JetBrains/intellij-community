// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
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

data class AgentChatOpenPendingTabsState(
  private val pendingTabsByProvider: Map<AgentSessionProvider, Map<String, List<AgentChatPendingTabSnapshot>>> = emptyMap(),
) {
  fun pendingTabsByPath(provider: AgentSessionProvider): Map<String, List<AgentChatPendingTabSnapshot>> {
    return pendingTabsByProvider[provider].orEmpty()
  }

  fun providers(): Set<AgentSessionProvider> = pendingTabsByProvider.keys

  companion object {
    @JvmField
    val EMPTY: AgentChatOpenPendingTabsState = AgentChatOpenPendingTabsState()
  }
}

@Service(Service.Level.APP)
class AgentChatOpenPendingTabsStateService(private val serviceScope: CoroutineScope) {
  private val mutableState = MutableStateFlow(AgentChatOpenPendingTabsState.EMPTY)
  private val refreshGeneration = AtomicLong()
  val state: StateFlow<AgentChatOpenPendingTabsState> = mutableState.asStateFlow()

  fun refreshOpenTabs() {
    val generation = refreshGeneration.incrementAndGet()
    serviceScope.launch {
      updateOpenTabsIfLatest(generation)
    }
  }

  private suspend fun updateOpenTabsIfLatest(generation: Long) {
    val state = collectOpenPendingAgentChatTabsState()
    if (refreshGeneration.get() == generation) {
      mutableState.value = state
    }
  }
}

internal suspend fun collectOpenPendingAgentChatTabsState(): AgentChatOpenPendingTabsState = withContext(Dispatchers.UI) {
  val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()
  val pendingTabsByProvider = LinkedHashMap<AgentSessionProvider, Map<String, List<AgentChatPendingTabSnapshot>>>()
  for (descriptor in AgentSessionProviders.allProvidersById()) {
    if (!descriptor.supportsPendingEditorTabRebind) {
      continue
    }
    val providerPendingTabs = openTabsSnapshot.pendingTabsByPath(descriptor.provider)
    if (providerPendingTabs.isNotEmpty()) {
      pendingTabsByProvider[descriptor.provider] = providerPendingTabs
    }
  }
  AgentChatOpenPendingTabsState(pendingTabsByProvider)
}

internal fun participatesInPendingAgentChatProjection(file: AgentChatVirtualFile): Boolean {
  val provider = file.provider ?: return false
  return file.participatesInPendingThreadLifecycle() &&
         AgentSessionProviders.find(provider)?.supportsPendingEditorTabRebind == true
}
