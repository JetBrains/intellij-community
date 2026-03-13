// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

enum class AgentPromptContextContributorPhase {
  INVOCATION,
  FALLBACK,
}

data class AgentPromptInvocationData(
  @JvmField val project: Project,
  @JvmField val actionId: String?,
  @JvmField val actionText: String?,
  @JvmField val actionPlace: String?,
  @JvmField val invokedAtMs: Long,
  @JvmField val attributes: Map<String, Any?> = emptyMap(),
)

interface AgentPromptContextContributorBridge {
  val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  val order: Int
    get() = 0

  fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem>
}

private class AgentPromptContextContributorBridgeRegistryLog

private val LOG = logger<AgentPromptContextContributorBridgeRegistryLog>()

private val AGENT_PROMPT_CONTEXT_CONTRIBUTOR_BRIDGE_EP: ExtensionPointName<AgentPromptContextContributorBridge> =
  ExtensionPointName("com.intellij.agent.workbench.promptContextContributor")

private class AgentPromptContextContributorBridgeSnapshotCacheId

private val CONTRIBUTOR_ORDERING: Comparator<AgentPromptContextContributorBridge> =
  compareBy(
    { it.phase.ordinal },
    { it.order },
    { it::class.java.name },
  )

private data class AgentPromptContextContributorBridgeSnapshot(
  @JvmField val orderedContributors: List<AgentPromptContextContributorBridge>,
) {
  companion object {
    val EMPTY = AgentPromptContextContributorBridgeSnapshot(orderedContributors = emptyList())
  }
}

interface AgentPromptContextContributorRegistry {
  fun allBridges(): List<AgentPromptContextContributorBridge>
}

private fun buildAgentPromptContextContributorBridgeSnapshot(
  contributors: Iterable<AgentPromptContextContributorBridge>,
): AgentPromptContextContributorBridgeSnapshot {
  return AgentPromptContextContributorBridgeSnapshot(
    orderedContributors = contributors
      .toList()
      .sortedWith(CONTRIBUTOR_ORDERING),
  )
}

private class EpBackedAgentPromptContextContributorRegistry : AgentPromptContextContributorRegistry {
  override fun allBridges(): List<AgentPromptContextContributorBridge> {
    return snapshotOrEmpty().orderedContributors
  }
}

private fun snapshotOrEmpty(): AgentPromptContextContributorBridgeSnapshot {
  return try {
    AGENT_PROMPT_CONTEXT_CONTRIBUTOR_BRIDGE_EP.computeIfAbsent(AgentPromptContextContributorBridgeSnapshotCacheId::class.java) {
      buildAgentPromptContextContributorBridgeSnapshot(AGENT_PROMPT_CONTEXT_CONTRIBUTOR_BRIDGE_EP.extensionList)
    }
  }
  catch (t: IllegalStateException) {
    LOG.debug("Prompt context contributor EP is unavailable in this context", t)
    AgentPromptContextContributorBridgeSnapshot.EMPTY
  }
  catch (t: IllegalArgumentException) {
    LOG.debug("Prompt context contributor EP is unavailable in this context", t)
    AgentPromptContextContributorBridgeSnapshot.EMPTY
  }
}

@Suppress("unused")
class InMemoryAgentPromptContextContributorRegistry(
  contributors: Iterable<AgentPromptContextContributorBridge>,
) : AgentPromptContextContributorRegistry {
  private val snapshot = buildAgentPromptContextContributorBridgeSnapshot(contributors)

  override fun allBridges(): List<AgentPromptContextContributorBridge> {
    return snapshot.orderedContributors
  }
}

object AgentPromptContextContributors {
  private val epRegistry: AgentPromptContextContributorRegistry = EpBackedAgentPromptContextContributorRegistry()
  private val testOverrideLock = Any()

  @Volatile
  private var testRegistryOverride: AgentPromptContextContributorRegistry? = null

  private fun activeRegistry(): AgentPromptContextContributorRegistry {
    return testRegistryOverride ?: epRegistry
  }

  fun allBridges(): List<AgentPromptContextContributorBridge> {
    return activeRegistry().allBridges()
  }

  @Suppress("unused")
  fun <T> withRegistryForTest(registry: AgentPromptContextContributorRegistry, action: () -> T): T {
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
