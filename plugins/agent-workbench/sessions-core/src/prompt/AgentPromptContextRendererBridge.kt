// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

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

private class AgentPromptContextRendererBridgeSnapshotCacheId

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
  return try {
    AGENT_PROMPT_CONTEXT_RENDERER_BRIDGE_EP.computeIfAbsent(AgentPromptContextRendererBridgeSnapshotCacheId::class.java) {
      buildAgentPromptContextRendererBridgeSnapshot(AGENT_PROMPT_CONTEXT_RENDERER_BRIDGE_EP.extensionList)
    }
  }
  catch (t: IllegalStateException) {
    LOG.debug("Prompt context renderer EP is unavailable in this context", t)
    AgentPromptContextRendererBridgeSnapshot.EMPTY
  }
  catch (t: IllegalArgumentException) {
    LOG.debug("Prompt context renderer EP is unavailable in this context", t)
    AgentPromptContextRendererBridgeSnapshot.EMPTY
  }
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
  private val testOverrideLock = Any()

  @Volatile
  private var testRegistryOverride: AgentPromptContextRendererRegistry? = null

  private fun activeRegistry(): AgentPromptContextRendererRegistry {
    return testRegistryOverride ?: epRegistry
  }

  fun allBridges(): List<AgentPromptContextRendererBridge> {
    return activeRegistry().allBridges()
  }

  fun find(rendererId: String): AgentPromptContextRendererBridge? {
    return activeRegistry().find(rendererId)
  }

  @Suppress("unused")
  fun <T> withRegistryForTest(registry: AgentPromptContextRendererRegistry, action: () -> T): T {
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
