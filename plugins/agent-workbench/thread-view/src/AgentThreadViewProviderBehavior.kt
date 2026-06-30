// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.extensions.SnapshotExtensionPointCache
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

private class AgentThreadViewProviderBehaviorRegistryLog

private val BEHAVIOR_LOG = logger<AgentThreadViewProviderBehaviorRegistryLog>()

private val AGENT_THREAD_VIEW_PROVIDER_BEHAVIOR_EP: ExtensionPointName<AgentThreadViewProviderBehaviorBean> =
  ExtensionPointName("com.intellij.agent.workbench.agentThreadViewProviderBehavior")

class AgentThreadViewProviderBehaviorBean : BaseKeyedLazyInstance<AgentThreadViewProviderBehavior>() {
  @Attribute("providerId")
  @JvmField
  @RequiredElement
  var providerId: String = ""

  @Attribute("implementation")
  @JvmField
  @RequiredElement
  var implementation: String = ""

  override fun getImplementationClassName(): String = implementation

  fun providerOrNull(): AgentSessionProvider? {
    val provider = AgentSessionProvider.fromOrNull(providerId)
    if (provider == null) {
      BEHAVIOR_LOG.warn("Ignoring Agent Thread View provider behavior with invalid providerId '$providerId': $implementation")
    }
    return provider
  }
}

private data class AgentThreadViewProviderBehaviorSnapshot(
  @JvmField val behaviorsByProvider: Map<AgentSessionProvider, AgentThreadViewProviderBehavior>,
) {
  companion object {
    val EMPTY = AgentThreadViewProviderBehaviorSnapshot(
      behaviorsByProvider = emptyMap(),
    )
  }
}

private val BEHAVIOR_SNAPSHOT_CACHE = SnapshotExtensionPointCache(
  log = BEHAVIOR_LOG,
  extensionPoint = AGENT_THREAD_VIEW_PROVIDER_BEHAVIOR_EP,
  cacheId = AgentThreadViewProviderBehaviorSnapshot::class.java,
  emptySnapshot = AgentThreadViewProviderBehaviorSnapshot.EMPTY,
  unavailableMessage = "Agent Thread View provider behavior EP is unavailable in this context",
  buildSnapshot = ::buildAgentThreadViewProviderBehaviorSnapshot,
)

internal fun resolveAgentThreadViewProviderBehavior(provider: AgentSessionProvider?): AgentThreadViewProviderBehavior {
  if (provider != null) {
    AgentThreadViewProviderBehaviors.find(provider)?.let { return it }
  }
  return DefaultAgentThreadViewProviderBehavior
}

private fun buildAgentThreadViewProviderBehaviorSnapshot(
  behaviorBeans: Iterable<AgentThreadViewProviderBehaviorBean>,
): AgentThreadViewProviderBehaviorSnapshot {
  val behaviorsByProvider = LinkedHashMap<AgentSessionProvider, AgentThreadViewProviderBehavior>()
  for (behaviorBean in behaviorBeans) {
    val provider = behaviorBean.providerOrNull() ?: continue
    val behavior = behaviorBean.instance
    val previous = behaviorsByProvider.putIfAbsent(provider, behavior)
    if (previous != null && previous !== behavior) {
      BEHAVIOR_LOG.warn(
        "Duplicate Agent Thread View provider behavior for ${provider.value}: " +
        "keeping ${previous::class.java.name}, ignoring ${behavior::class.java.name}",
      )
    }
  }
  return AgentThreadViewProviderBehaviorSnapshot(behaviorsByProvider)
}

@ApiStatus.Internal
object AgentThreadViewProviderBehaviors {
  fun find(provider: AgentSessionProvider): AgentThreadViewProviderBehavior? {
    return BEHAVIOR_SNAPSHOT_CACHE.getSnapshotOrEmpty().behaviorsByProvider[provider]
  }
}

@ApiStatus.Internal
interface AgentThreadViewBehaviorFile {
  val provider: AgentSessionProvider?

  val isPendingThread: Boolean

  val subAgentId: String?

  val pendingFirstInputAtMs: Long?

  val threadActivity: AgentThreadActivity

  val initialMessageMode: AgentInitialMessageMode?
}

@ApiStatus.Internal
interface AgentThreadViewBehaviorTerminalTab {
  suspend fun readRecentOutputTail(): String
}

@ApiStatus.Internal
interface AgentThreadViewInitialMessageDispatchContext {
  val action: AgentInitialMessageDispatchAction

  val message: String

  val stepIndex: Int
}

@ApiStatus.Internal
data class AgentThreadViewInitialMessageSendObservation(
  @JvmField val outputText: String,
  @JvmField val recentOutputTail: String,
)

@ApiStatus.Internal
interface AgentThreadViewProviderBehavior {
  fun supportsPendingThreadRefreshRetry(file: AgentThreadViewBehaviorFile): Boolean = false

  fun pendingThreadRefreshRetryDelayMs(file: AgentThreadViewBehaviorFile, currentTimeMs: Long, retryIntervalMs: Long): Long? = null

  fun supportsConcreteNewThreadRebind(
    file: AgentThreadViewBehaviorFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean = false

  fun isConcreteNewThreadRebindCommand(command: String): Boolean = false

  fun shouldUseBracketedPasteMode(text: String): Boolean = true

  suspend fun beforeInitialMessageSend(
    file: AgentThreadViewBehaviorFile,
    tab: AgentThreadViewBehaviorTerminalTab,
    dispatch: AgentThreadViewInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentThreadViewInitialMessageRetryDecision = AgentThreadViewInitialMessageRetryDecision.PROCEED

  suspend fun isInitialMessageDispatchAlreadySatisfied(
    tab: AgentThreadViewBehaviorTerminalTab,
    dispatch: AgentThreadViewInitialMessageDispatchContext,
  ): Boolean = false

  fun requiresPostSendObservation(dispatch: AgentThreadViewInitialMessageDispatchContext): Boolean = false

  fun afterInitialMessageSendObservation(
    file: AgentThreadViewBehaviorFile,
    dispatch: AgentThreadViewInitialMessageDispatchContext,
    observation: AgentThreadViewInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentThreadViewInitialMessageRetryDecision = AgentThreadViewInitialMessageRetryDecision.PROCEED
}

private object DefaultAgentThreadViewProviderBehavior : AgentThreadViewProviderBehavior
