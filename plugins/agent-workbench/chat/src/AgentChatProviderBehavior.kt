// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.extensions.SnapshotExtensionPointCache
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.common.session.isClaudeMenuCommandPrompt
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

private class AgentChatProviderBehaviorRegistryLog

private val BEHAVIOR_LOG = logger<AgentChatProviderBehaviorRegistryLog>()

private val AGENT_CHAT_PROVIDER_BEHAVIOR_EP: ExtensionPointName<AgentChatProviderBehaviorBean> =
  ExtensionPointName("com.intellij.agent.workbench.chatProviderBehavior")

class AgentChatProviderBehaviorBean : BaseKeyedLazyInstance<AgentChatProviderBehavior>() {
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
      BEHAVIOR_LOG.warn("Ignoring Agent Chat provider behavior with invalid providerId '$providerId': $implementation")
    }
    return provider
  }
}

private data class AgentChatProviderBehaviorSnapshot(
  @JvmField val behaviorsByProvider: Map<AgentSessionProvider, AgentChatProviderBehavior>,
) {
  companion object {
    val EMPTY = AgentChatProviderBehaviorSnapshot(
      behaviorsByProvider = emptyMap(),
    )
  }
}

private val BEHAVIOR_SNAPSHOT_CACHE = SnapshotExtensionPointCache(
  log = BEHAVIOR_LOG,
  extensionPoint = AGENT_CHAT_PROVIDER_BEHAVIOR_EP,
  cacheId = AgentChatProviderBehaviorSnapshot::class.java,
  emptySnapshot = AgentChatProviderBehaviorSnapshot.EMPTY,
  unavailableMessage = "Agent Chat provider behavior EP is unavailable in this context",
  buildSnapshot = ::buildAgentChatProviderBehaviorSnapshot,
)

internal fun resolveAgentChatProviderBehavior(provider: AgentSessionProvider?): AgentChatProviderBehavior {
  if (provider != null) {
    AgentChatProviderBehaviors.find(provider)?.let { return it }
  }
  return DefaultAgentChatProviderBehavior
}

private fun buildAgentChatProviderBehaviorSnapshot(
  behaviorBeans: Iterable<AgentChatProviderBehaviorBean>,
): AgentChatProviderBehaviorSnapshot {
  val behaviorsByProvider = LinkedHashMap<AgentSessionProvider, AgentChatProviderBehavior>()
  for (behaviorBean in behaviorBeans) {
    val provider = behaviorBean.providerOrNull() ?: continue
    val behavior = behaviorBean.instance
    val previous = behaviorsByProvider.putIfAbsent(provider, behavior)
    if (previous != null && previous !== behavior) {
      BEHAVIOR_LOG.warn(
        "Duplicate Agent Chat provider behavior for ${provider.value}: " +
        "keeping ${previous::class.java.name}, ignoring ${behavior::class.java.name}",
      )
    }
  }
  return AgentChatProviderBehaviorSnapshot(behaviorsByProvider)
}

@ApiStatus.Internal
object AgentChatProviderBehaviors {
  fun find(provider: AgentSessionProvider): AgentChatProviderBehavior? {
    return BEHAVIOR_SNAPSHOT_CACHE.getSnapshotOrEmpty().behaviorsByProvider[provider]
  }
}

@ApiStatus.Internal
interface AgentChatBehaviorFile {
  val provider: AgentSessionProvider?

  val isPendingThread: Boolean

  val subAgentId: String?

  val pendingFirstInputAtMs: Long?

  val threadActivity: AgentThreadActivity

  val initialMessageMode: AgentInitialMessageMode?
}

@ApiStatus.Internal
interface AgentChatBehaviorTerminalTab {
  suspend fun readRecentOutputTail(): String
}

@ApiStatus.Internal
interface AgentChatInitialMessageDispatchContext {
  val action: AgentInitialMessageDispatchAction

  val message: String

  val stepIndex: Int
}

@ApiStatus.Internal
data class AgentChatInitialMessageSendObservation(
  @JvmField val outputText: String,
  @JvmField val recentOutputTail: String,
)

@ApiStatus.Internal
interface AgentChatProviderBehavior {
  fun supportsPendingThreadRefreshRetry(file: AgentChatBehaviorFile): Boolean = false

  fun pendingThreadRefreshRetryDelayMs(file: AgentChatBehaviorFile, currentTimeMs: Long, retryIntervalMs: Long): Long? = null

  fun supportsConcreteNewThreadRebind(
    file: AgentChatBehaviorFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean = false

  fun isConcreteNewThreadRebindCommand(command: String): Boolean = false

  fun shouldUseBracketedPasteMode(text: String): Boolean = true

  suspend fun beforeInitialMessageSend(
    file: AgentChatBehaviorFile,
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision = AgentChatInitialMessageRetryDecision.PROCEED

  suspend fun isInitialMessageDispatchAlreadySatisfied(
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
  ): Boolean = false

  fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean = false

  fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision = AgentChatInitialMessageRetryDecision.PROCEED
}

private object DefaultAgentChatProviderBehavior : AgentChatProviderBehavior

internal class ClaudeAgentChatProviderBehavior : AgentChatProviderBehavior {
  override fun shouldUseBracketedPasteMode(text: String): Boolean {
    return !text.isClaudeMenuCommandPrompt()
  }
}
