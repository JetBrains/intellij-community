// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.JComponent

data class AgentPendingSessionMetadata(
  @JvmField val createdAtMs: Long,
  @JvmField val launchMode: String?,
)

interface AgentSessionProviderBehavior {
  val provider: AgentSessionProvider

  val editorTabActionIds: List<String>
    get() = emptyList()

  val supportsPendingEditorTabRebind: Boolean
    get() = false

  val supportsNewThreadRebind: Boolean
    get() = false

  val emitsScopedRefreshSignals: Boolean
    get() = false

  val refreshPathAfterCreateNewSession: Boolean
    get() = false

  val archiveRefreshDelayMs: Long
    get() = 0L

  val suppressArchivedThreadsDuringRefresh: Boolean
    get() = false

  fun onConversationOpened() {
  }

  fun resolvePendingSessionMetadata(
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentPendingSessionMetadata? = null

  fun createToolWindowNorthComponent(project: Project): JComponent? = null
}

private class AgentSessionProviderBehaviorRegistry

private val LOG = logger<AgentSessionProviderBehaviorRegistry>()

private val AGENT_SESSION_PROVIDER_BEHAVIOR_EP: ExtensionPointName<AgentSessionProviderBehavior> =
  ExtensionPointName("com.intellij.agent.workbench.sessionProviderBehavior")

private class AgentSessionProviderBehaviorSnapshotCacheId

private data class AgentSessionProviderBehaviorSnapshot(
  @JvmField val behaviorsByProvider: Map<AgentSessionProvider, AgentSessionProviderBehavior>,
  @JvmField val orderedBehaviors: List<AgentSessionProviderBehavior>,
) {
  companion object {
    val EMPTY = AgentSessionProviderBehaviorSnapshot(
      behaviorsByProvider = emptyMap(),
      orderedBehaviors = emptyList(),
    )
  }
}

interface AgentSessionProviderBehaviorRegistryView {
  fun find(provider: AgentSessionProvider): AgentSessionProviderBehavior?

  fun allBehaviors(): List<AgentSessionProviderBehavior>
}

private fun buildAgentSessionProviderBehaviorSnapshot(
  behaviors: Iterable<AgentSessionProviderBehavior>,
): AgentSessionProviderBehaviorSnapshot {
  val behaviorsByProvider = LinkedHashMap<AgentSessionProvider, AgentSessionProviderBehavior>()
  val uniqueBehaviors = ArrayList<AgentSessionProviderBehavior>()
  for (behavior in behaviors) {
    val previous = behaviorsByProvider.putIfAbsent(behavior.provider, behavior)
    if (previous != null && previous !== behavior) {
      LOG.warn(
        "Duplicate session provider behavior for ${behavior.provider.value}: keeping ${previous::class.java.name}, ignoring ${behavior::class.java.name}",
      )
    }
    else {
      uniqueBehaviors += behavior
    }
  }
  return AgentSessionProviderBehaviorSnapshot(
    behaviorsByProvider = behaviorsByProvider,
    orderedBehaviors = uniqueBehaviors.sortedBy { behavior -> behavior.provider.value },
  )
}

private class EpBackedAgentSessionProviderBehaviorRegistry : AgentSessionProviderBehaviorRegistryView {
  override fun find(provider: AgentSessionProvider): AgentSessionProviderBehavior? {
    return snapshotOrEmpty().behaviorsByProvider[provider]
  }

  override fun allBehaviors(): List<AgentSessionProviderBehavior> {
    return snapshotOrEmpty().orderedBehaviors
  }
}

private fun snapshotOrEmpty(): AgentSessionProviderBehaviorSnapshot {
  return try {
    AGENT_SESSION_PROVIDER_BEHAVIOR_EP.computeIfAbsent(AgentSessionProviderBehaviorSnapshotCacheId::class.java) {
      buildAgentSessionProviderBehaviorSnapshot(AGENT_SESSION_PROVIDER_BEHAVIOR_EP.extensionList)
    }
  }
  catch (t: IllegalStateException) {
    LOG.debug("Session provider behavior EP is unavailable in this context", t)
    AgentSessionProviderBehaviorSnapshot.EMPTY
  }
  catch (t: IllegalArgumentException) {
    LOG.debug("Session provider behavior EP is unavailable in this context", t)
    AgentSessionProviderBehaviorSnapshot.EMPTY
  }
}

class InMemoryAgentSessionProviderBehaviorRegistry(
  behaviors: Iterable<AgentSessionProviderBehavior>,
) : AgentSessionProviderBehaviorRegistryView {
  private val snapshot = buildAgentSessionProviderBehaviorSnapshot(behaviors)

  override fun find(provider: AgentSessionProvider): AgentSessionProviderBehavior? {
    return snapshot.behaviorsByProvider[provider]
  }

  override fun allBehaviors(): List<AgentSessionProviderBehavior> {
    return snapshot.orderedBehaviors
  }
}

object AgentSessionProviderBehaviors {
  private val epRegistry: AgentSessionProviderBehaviorRegistryView = EpBackedAgentSessionProviderBehaviorRegistry()
  private val testOverrideLock = Any()

  @Volatile
  private var testRegistryOverride: AgentSessionProviderBehaviorRegistryView? = null

  private fun activeRegistry(): AgentSessionProviderBehaviorRegistryView {
    return testRegistryOverride ?: epRegistry
  }

  fun find(provider: AgentSessionProvider): AgentSessionProviderBehavior? {
    return activeRegistry().find(provider)
  }

  fun allBehaviors(): List<AgentSessionProviderBehavior> {
    return activeRegistry().allBehaviors()
  }

  fun <T> withRegistryForTest(registry: AgentSessionProviderBehaviorRegistryView, action: () -> T): T {
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
