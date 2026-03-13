// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.OverridableValue
import com.intellij.agent.workbench.sessions.core.SnapshotExtensionPointCache
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName

private class AgentSessionProviderRegistryLog

private val LOG = logger<AgentSessionProviderRegistryLog>()

private val AGENT_SESSION_PROVIDER_EP: ExtensionPointName<AgentSessionProviderDescriptor> =
  ExtensionPointName("com.intellij.agent.workbench.sessionProvider")

private data class AgentSessionProviderSnapshot(
  @JvmField val providersById: Map<AgentSessionProvider, AgentSessionProviderDescriptor>,
  @JvmField val displayOrderedProviders: List<AgentSessionProviderDescriptor>,
  @JvmField val providersByIdOrder: List<AgentSessionProviderDescriptor>,
  @JvmField val sessionSources: List<AgentSessionSource>,
) {
  companion object {
    val EMPTY = AgentSessionProviderSnapshot(
      providersById = emptyMap(),
      displayOrderedProviders = emptyList(),
      providersByIdOrder = emptyList(),
      sessionSources = emptyList(),
    )
  }
}

private val PROVIDER_SNAPSHOT_CACHE = SnapshotExtensionPointCache(
  log = LOG,
  extensionPoint = AGENT_SESSION_PROVIDER_EP,
  cacheId = AgentSessionProviderSnapshot::class.java,
  emptySnapshot = AgentSessionProviderSnapshot.EMPTY,
  unavailableMessage = "Session provider EP is unavailable in this context",
  buildSnapshot = ::buildAgentSessionProviderSnapshot,
)

interface AgentSessionProviderRegistry {
  fun find(provider: AgentSessionProvider): AgentSessionProviderDescriptor?

  fun allProviders(): List<AgentSessionProviderDescriptor>

  fun allProvidersById(): List<AgentSessionProviderDescriptor>

  fun sessionSources(): List<AgentSessionSource>
}

private fun buildAgentSessionProviderSnapshot(providers: Iterable<AgentSessionProviderDescriptor>): AgentSessionProviderSnapshot {
  val providersById = LinkedHashMap<AgentSessionProvider, AgentSessionProviderDescriptor>()
  val uniqueProviders = ArrayList<AgentSessionProviderDescriptor>()
  for (provider in providers) {
    val previous = providersById.putIfAbsent(provider.provider, provider)
    if (previous != null && previous !== provider) {
      LOG.warn(
        "Duplicate session provider for ${provider.provider.value}: keeping ${previous::class.java.name}, ignoring ${provider::class.java.name}",
      )
    }
    else {
      uniqueProviders += provider
    }
  }
  val displayOrderedProviders = uniqueProviders.sortedWith(
    compareBy({ it.displayPriority }, { it.provider.value })
  )
  return AgentSessionProviderSnapshot(
    providersById = providersById,
    displayOrderedProviders = displayOrderedProviders,
    providersByIdOrder = uniqueProviders.sortedBy { provider -> provider.provider.value },
    sessionSources = displayOrderedProviders.map { it.sessionSource },
  )
}

private class EpBackedAgentSessionProviderRegistry : AgentSessionProviderRegistry {
  override fun find(provider: AgentSessionProvider): AgentSessionProviderDescriptor? {
    return snapshotOrEmpty().providersById[provider]
  }

  override fun allProviders(): List<AgentSessionProviderDescriptor> {
    return snapshotOrEmpty().displayOrderedProviders
  }

  override fun allProvidersById(): List<AgentSessionProviderDescriptor> {
    return snapshotOrEmpty().providersByIdOrder
  }

  override fun sessionSources(): List<AgentSessionSource> {
    return snapshotOrEmpty().sessionSources
  }
}

private fun snapshotOrEmpty(): AgentSessionProviderSnapshot {
  return PROVIDER_SNAPSHOT_CACHE.getSnapshotOrEmpty()
}

class InMemoryAgentSessionProviderRegistry(
  providers: Iterable<AgentSessionProviderDescriptor>,
) : AgentSessionProviderRegistry {
  private val snapshot = buildAgentSessionProviderSnapshot(providers.toList())

  override fun find(provider: AgentSessionProvider): AgentSessionProviderDescriptor? {
    return snapshot.providersById[provider]
  }

  override fun allProviders(): List<AgentSessionProviderDescriptor> {
    return snapshot.displayOrderedProviders
  }

  override fun allProvidersById(): List<AgentSessionProviderDescriptor> {
    return snapshot.providersByIdOrder
  }

  override fun sessionSources(): List<AgentSessionSource> {
    return snapshot.sessionSources
  }
}

object AgentSessionProviders {
  private val epRegistry: AgentSessionProviderRegistry = EpBackedAgentSessionProviderRegistry()
  private val registryOverride = OverridableValue { epRegistry }

  fun find(provider: AgentSessionProvider): AgentSessionProviderDescriptor? {
    return registryOverride.value().find(provider)
  }

  fun sessionSources(): List<AgentSessionSource> {
    return registryOverride.value().sessionSources()
  }

  fun allProviders(): List<AgentSessionProviderDescriptor> {
    return registryOverride.value().allProviders()
  }

  fun allProvidersById(): List<AgentSessionProviderDescriptor> {
    return registryOverride.value().allProvidersById()
  }

  fun <T> withRegistryForTest(registry: AgentSessionProviderRegistry, action: () -> T): T {
    return registryOverride.withOverride(registry, action)
  }
}
