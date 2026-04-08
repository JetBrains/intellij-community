// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.agent.workbench.common.extensions.OverridableValue
import com.intellij.agent.workbench.common.extensions.SnapshotExtensionPointCache
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName

data class AgentPromptEnvelopeRenderInput(
  @JvmField val item: AgentPromptContextItem,
  @JvmField val projectPath: String?,
)

data class AgentPromptChipRenderInput(
  @JvmField val item: AgentPromptContextItem,
  @JvmField val projectBasePath: String?,
)

data class AgentPromptChipRender(
  @JvmField val text: String,
  @JvmField val tooltipText: String? = null,
)

interface AgentPromptContextRendererBridge {
  val rendererId: String

  fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String

  fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender? {
    return null
  }
}

private class AgentPromptContextRendererRegistryLog

private val LOG = logger<AgentPromptContextRendererRegistryLog>()

private val AGENT_PROMPT_CONTEXT_RENDERER_BRIDGE_EP: ExtensionPointName<AgentPromptContextRendererBridge> =
  ExtensionPointName("com.intellij.agent.workbench.promptContextRenderer")

private data class AgentPromptContextRendererBridgeSnapshot(
  @JvmField val orderedBridges: List<AgentPromptContextRendererBridge>,
  @JvmField val bridgesById: Map<String, AgentPromptContextRendererBridge>,
) {
  companion object {
    val EMPTY = AgentPromptContextRendererBridgeSnapshot(
      orderedBridges = emptyList(),
      bridgesById = emptyMap(),
    )
  }
}

private val RENDERER_SNAPSHOT_CACHE = SnapshotExtensionPointCache(
  log = LOG,
  extensionPoint = AGENT_PROMPT_CONTEXT_RENDERER_BRIDGE_EP,
  cacheId = AgentPromptContextRendererBridgeSnapshot::class.java,
  emptySnapshot = AgentPromptContextRendererBridgeSnapshot.EMPTY,
  unavailableMessage = "Prompt context renderer EP is unavailable in this context",
  buildSnapshot = ::buildAgentPromptContextRendererBridgeSnapshot,
)

interface AgentPromptContextRendererRegistry {
  fun allBridges(): List<AgentPromptContextRendererBridge>

  fun find(rendererId: String): AgentPromptContextRendererBridge?
}

private fun buildAgentPromptContextRendererBridgeSnapshot(
  bridges: Iterable<AgentPromptContextRendererBridge>,
): AgentPromptContextRendererBridgeSnapshot {
  val ordered = ArrayList<AgentPromptContextRendererBridge>()
  val byId = LinkedHashMap<String, AgentPromptContextRendererBridge>()
  for (bridge in bridges) {
    val existing = byId.putIfAbsent(bridge.rendererId, bridge)
    if (existing == null) {
      ordered += bridge
    }
    else if (existing !== bridge) {
      LOG.warn(
        "Duplicate prompt context renderer '${bridge.rendererId}': keeping ${existing::class.java.name}, ignoring ${bridge::class.java.name}",
      )
    }
  }
  return AgentPromptContextRendererBridgeSnapshot(
    orderedBridges = ordered,
    bridgesById = byId,
  )
}

private class EpBackedAgentPromptContextRendererRegistry : AgentPromptContextRendererRegistry {
  override fun allBridges(): List<AgentPromptContextRendererBridge> {
    return snapshotOrEmpty().orderedBridges
  }

  override fun find(rendererId: String): AgentPromptContextRendererBridge? {
    return snapshotOrEmpty().bridgesById[rendererId]
  }
}

private fun snapshotOrEmpty(): AgentPromptContextRendererBridgeSnapshot {
  return RENDERER_SNAPSHOT_CACHE.getSnapshotOrEmpty()
}

@Suppress("unused")
class InMemoryAgentPromptContextRendererRegistry(
  bridges: Iterable<AgentPromptContextRendererBridge>,
) : AgentPromptContextRendererRegistry {
  private val snapshot = buildAgentPromptContextRendererBridgeSnapshot(bridges)

  override fun allBridges(): List<AgentPromptContextRendererBridge> {
    return snapshot.orderedBridges
  }

  override fun find(rendererId: String): AgentPromptContextRendererBridge? {
    return snapshot.bridgesById[rendererId]
  }
}

object AgentPromptContextRenderers {
  private val epRegistry: AgentPromptContextRendererRegistry = EpBackedAgentPromptContextRendererRegistry()
  private val registryOverride = OverridableValue { epRegistry }

  fun find(rendererId: String): AgentPromptContextRendererBridge? {
    return registryOverride.value().find(rendererId)
  }

  @Suppress("unused")
  fun <T> withRegistryForTest(registry: AgentPromptContextRendererRegistry, action: () -> T): T {
    return registryOverride.withOverride(registry, action)
  }
}
