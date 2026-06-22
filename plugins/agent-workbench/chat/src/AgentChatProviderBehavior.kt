// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.extensions.SnapshotExtensionPointCache
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.common.session.isClaudeMenuCommandPrompt
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

private class AgentChatProviderBehaviorRegistryLog

private val BEHAVIOR_LOG = logger<AgentChatProviderBehaviorRegistryLog>()

private val AGENT_CHAT_PROVIDER_BEHAVIOR_EP: ExtensionPointName<AgentChatProviderBehaviorContributor> =
  ExtensionPointName("com.intellij.agent.workbench.chatProviderBehavior")

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
  return when (provider) {
    AgentSessionProvider.CLAUDE -> ClaudeAgentChatProviderBehavior
    AgentSessionProvider.JUNIE -> JunieAgentChatProviderBehavior
    else -> DefaultAgentChatProviderBehavior
  }
}

@ApiStatus.Internal
interface AgentChatProviderBehaviorContributor {
  val provider: AgentSessionProvider

  val behavior: AgentChatProviderBehavior
}

private fun buildAgentChatProviderBehaviorSnapshot(
  contributors: Iterable<AgentChatProviderBehaviorContributor>,
): AgentChatProviderBehaviorSnapshot {
  val behaviorsByProvider = LinkedHashMap<AgentSessionProvider, AgentChatProviderBehavior>()
  for (contributor in contributors) {
    val previous = behaviorsByProvider.putIfAbsent(contributor.provider, contributor.behavior)
    if (previous != null && previous !== contributor.behavior) {
      BEHAVIOR_LOG.warn(
        "Duplicate Agent Chat provider behavior for ${contributor.provider.value}: " +
        "keeping ${previous::class.java.name}, ignoring ${contributor.behavior::class.java.name}",
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
}

@ApiStatus.Internal
interface AgentChatBehaviorTerminalTab {
  suspend fun readRecentOutputTail(): String
}

@ApiStatus.Internal
interface AgentChatInitialMessageDispatchContext {
  val action: AgentInitialMessageDispatchAction

  val completionPolicy: AgentInitialMessageDispatchCompletionPolicy
}

@ApiStatus.Internal
data class AgentChatInitialMessageSendObservation(
  @JvmField val outputText: String,
  @JvmField val recentOutputTail: String,
) {
  val textWithRecentOutputTail: String
    get() = listOf(outputText, recentOutputTail).filter(String::isNotEmpty).joinToString(separator = "\n")
}

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

private object ClaudeAgentChatProviderBehavior : AgentChatProviderBehavior {
  override fun shouldUseBracketedPasteMode(text: String): Boolean {
    return !text.isClaudeMenuCommandPrompt()
  }
}

private object JunieAgentChatProviderBehavior : AgentChatProviderBehavior {
  override suspend fun beforeInitialMessageSend(
    file: AgentChatBehaviorFile,
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    return if (isJuniePromptInputReady(tab.readRecentOutputTail())) {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
    else {
      AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(calculateJuniePromptReadinessRetryBackoffMs(retryAttempt))
    }
  }

  override suspend fun isInitialMessageDispatchAlreadySatisfied(
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
  ): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE && isJuniePlanModeVisible(tab.readRecentOutputTail())
  }

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (dispatch.action != AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    return if (isJuniePlanModeVisible(observation.textWithRecentOutputTail)) {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
    else {
      AgentChatInitialMessageRetryDecision.Stop
    }
  }
}

private fun calculateJuniePromptReadinessRetryBackoffMs(retryAttempt: Int): Long {
  val cappedAttempt = retryAttempt.coerceIn(0, 2)
  return (JUNIE_PROMPT_READINESS_RETRY_BACKOFF_MS * (1L shl cappedAttempt)).coerceAtMost(JUNIE_PROMPT_READINESS_MAX_RETRY_BACKOFF_MS)
}

private fun isJuniePromptInputReady(text: String): Boolean {
  val normalized = sanitizeJunieTerminalText(text)
  return JUNIE_PROMPT_INPUT_MARKERS.any { marker -> normalized.contains(marker, ignoreCase = true) }
}

private fun isJuniePlanModeVisible(text: String): Boolean {
  return sanitizeJunieTerminalText(text).contains(JUNIE_PLAN_MODE_VISIBLE_MARKER, ignoreCase = true)
}

private fun sanitizeJunieTerminalText(text: String): String {
  val sanitized = buildString(text.length) {
    text.forEach { char ->
      append(
        when {
          char.isWhitespace() || char.isISOControl() -> ' '
          else -> char
        }
      )
    }
  }
  return sanitized.replace(JUNIE_TERMINAL_WHITESPACE_REGEX, " ").trim()
}

private const val JUNIE_PROMPT_READINESS_RETRY_BACKOFF_MS: Long = 250
private const val JUNIE_PROMPT_READINESS_MAX_RETRY_BACKOFF_MS: Long = 1_000
private const val JUNIE_PLAN_MODE_VISIBLE_MARKER: String = "Plan Mode"
private val JUNIE_PROMPT_INPUT_MARKERS: List<String> = listOf(
  "Type your prompt",
)

private val JUNIE_TERMINAL_WHITESPACE_REGEX: Regex = Regex(" +")
