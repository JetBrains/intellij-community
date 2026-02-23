// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers

import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName

private class AgentSessionProviderBridgeRegistry

private val LOG = logger<AgentSessionProviderBridgeRegistry>()

private val AGENT_SESSION_PROVIDER_BRIDGE_EP: ExtensionPointName<AgentSessionProviderBridge> =
  ExtensionPointName("com.intellij.agent.workbench.sessionProviderBridge")

private class AgentSessionProviderBridgeSnapshotCacheId

private data class AgentSessionProviderBridgeSnapshot(
  @JvmField val bridgesByProvider: Map<AgentSessionProvider, AgentSessionProviderBridge>,
  @JvmField val orderedBridges: List<AgentSessionProviderBridge>,
  @JvmField val sessionSources: List<AgentSessionSource>,
) {
  companion object {
    val EMPTY = AgentSessionProviderBridgeSnapshot(
      bridgesByProvider = emptyMap(),
      orderedBridges = emptyList(),
      sessionSources = emptyList(),
    )
  }
}

interface AgentSessionProviderRegistry {
  fun find(provider: AgentSessionProvider): AgentSessionProviderBridge?

  fun allBridges(): List<AgentSessionProviderBridge>

  fun sessionSources(): List<AgentSessionSource>
}

private fun buildAgentSessionProviderBridgeSnapshot(bridges: Iterable<AgentSessionProviderBridge>): AgentSessionProviderBridgeSnapshot {
  val bridgesByProvider = LinkedHashMap<AgentSessionProvider, AgentSessionProviderBridge>()
  val uniqueBridges = ArrayList<AgentSessionProviderBridge>()
  for (bridge in bridges) {
    val previous = bridgesByProvider.putIfAbsent(bridge.provider, bridge)
    if (previous != null && previous !== bridge) {
      LOG.warn(
        "Duplicate session provider bridge for ${bridge.provider.value}: keeping ${previous::class.java.name}, ignoring ${bridge::class.java.name}",
      )
    }
    else {
      uniqueBridges += bridge
    }
  }
  return AgentSessionProviderBridgeSnapshot(
    bridgesByProvider = bridgesByProvider,
    orderedBridges = uniqueBridges,
    sessionSources = uniqueBridges.map { it.sessionSource },
  )
}

private class EpBackedAgentSessionProviderRegistry : AgentSessionProviderRegistry {
  override fun find(provider: AgentSessionProvider): AgentSessionProviderBridge? {
    return snapshotOrEmpty().bridgesByProvider[provider]
  }

  override fun allBridges(): List<AgentSessionProviderBridge> {
    return snapshotOrEmpty().orderedBridges
  }

  override fun sessionSources(): List<AgentSessionSource> {
    return snapshotOrEmpty().sessionSources
  }
}

private fun snapshotOrEmpty(): AgentSessionProviderBridgeSnapshot {
  return try {
    AGENT_SESSION_PROVIDER_BRIDGE_EP.computeIfAbsent(AgentSessionProviderBridgeSnapshotCacheId::class.java) {
      buildAgentSessionProviderBridgeSnapshot(AGENT_SESSION_PROVIDER_BRIDGE_EP.extensionList)
    }
  }
  catch (t: IllegalStateException) {
    LOG.debug("Session provider bridge EP is unavailable in this context", t)
    AgentSessionProviderBridgeSnapshot.EMPTY
  }
  catch (t: IllegalArgumentException) {
    LOG.debug("Session provider bridge EP is unavailable in this context", t)
    AgentSessionProviderBridgeSnapshot.EMPTY
  }
}

class InMemoryAgentSessionProviderRegistry(
  bridges: Iterable<AgentSessionProviderBridge>,
) : AgentSessionProviderRegistry {
  private val snapshot = buildAgentSessionProviderBridgeSnapshot(bridges)

  override fun find(provider: AgentSessionProvider): AgentSessionProviderBridge? {
    return snapshot.bridgesByProvider[provider]
  }

  override fun allBridges(): List<AgentSessionProviderBridge> {
    return snapshot.orderedBridges
  }

  override fun sessionSources(): List<AgentSessionSource> {
    return snapshot.sessionSources
  }
}

object AgentSessionProviderBridges {
  private val epRegistry: AgentSessionProviderRegistry = EpBackedAgentSessionProviderRegistry()
  private val testOverrideLock = Any()

  @Volatile
  private var testRegistryOverride: AgentSessionProviderRegistry? = null

  private fun activeRegistry(): AgentSessionProviderRegistry {
    return testRegistryOverride ?: epRegistry
  }

  fun find(provider: AgentSessionProvider): AgentSessionProviderBridge? {
    return activeRegistry().find(provider)
  }

  fun sessionSources(): List<AgentSessionSource> {
    return activeRegistry().sessionSources()
  }

  fun allBridges(): List<AgentSessionProviderBridge> {
    return activeRegistry().allBridges()
  }

  fun <T> withRegistryForTest(registry: AgentSessionProviderRegistry, action: () -> T): T {
    return synchronized(testOverrideLock) {
      val previous = testRegistryOverride
      testRegistryOverride = registry
      try {
        action()
      }
      finally {
        testRegistryOverride = previous
      }
    }
  }
}
