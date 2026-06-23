// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBehaviorFile
import com.intellij.agent.workbench.chat.AgentChatBehaviorTerminalTab
import com.intellij.agent.workbench.chat.AgentChatInitialMessageDispatchContext
import com.intellij.agent.workbench.chat.AgentChatInitialMessageRetryDecision
import com.intellij.agent.workbench.chat.AgentChatInitialMessageSendObservation
import com.intellij.agent.workbench.chat.AgentChatProviderBehavior
import com.intellij.agent.workbench.chat.AgentChatProviderBehaviorContributor
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.isBusyForExistingThreadPlanMode
import kotlin.math.min

internal class CodexAgentChatProviderBehaviorContributor : AgentChatProviderBehaviorContributor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CODEX

  override val behavior: AgentChatProviderBehavior
    get() = CodexAgentChatProviderBehavior
}

internal object CodexAgentChatProviderBehavior : AgentChatProviderBehavior {
  override fun supportsPendingThreadRefreshRetry(file: AgentChatBehaviorFile): Boolean {
    return file.isPendingThread && file.subAgentId == null && file.provider == AgentSessionProvider.CODEX
  }

  override fun pendingThreadRefreshRetryDelayMs(file: AgentChatBehaviorFile, currentTimeMs: Long, retryIntervalMs: Long): Long? {
    if (!supportsPendingThreadRefreshRetry(file)) {
      return null
    }
    val pendingFirstInputAtMs = file.pendingFirstInputAtMs ?: return null
    val retryDeadlineMs = pendingFirstInputAtMs + AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_POST_WINDOW_MS
    val remainingMs = retryDeadlineMs - currentTimeMs
    if (remainingMs <= 0L) {
      return null
    }
    return min(retryIntervalMs, remainingMs)
  }

  override fun supportsConcreteNewThreadRebind(
    file: AgentChatBehaviorFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean {
    return descriptor?.supportsNewThreadRebind == true && !file.isPendingThread && file.subAgentId == null
  }

  override fun isConcreteNewThreadRebindCommand(command: String): Boolean {
    return command == CODEX_NEW_THREAD_COMMAND || command == CODEX_FORK_THREAD_COMMAND
  }

  override fun shouldUseBracketedPasteMode(text: String): Boolean {
    return text.trim() != CODEX_PLAN_COMMAND
  }

  override suspend fun beforeInitialMessageSend(
    file: AgentChatBehaviorFile,
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (file.initialMessageMode != AgentInitialMessageMode.PLAN || !file.threadActivity.isBusyForExistingThreadPlanMode()) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    val backoffMs = calculateCodexPlanModeRetryBackoffMs(retryAttempt)
    if (dispatch.stepIndex > 0) {
      return AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterRewindAndReadiness(backoffMs)
    }
    return AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterReadiness(backoffMs)
  }

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return dispatch.isRetryableCodexPlanCommand()
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (!dispatch.isRetryableCodexPlanCommand()) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    if (!CODEX_PLAN_BUSY_PATTERN.containsMatchIn(normalizeCodexTerminalOutput(observation.outputText))) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    return AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterReadiness(
      backoffMs = calculateCodexPlanModeRetryBackoffMs(retryAttempt),
    )
  }

}

private fun AgentChatInitialMessageDispatchContext.isRetryableCodexPlanCommand(): Boolean {
  return action == AgentInitialMessageDispatchAction.SEND_TEXT &&
         completionPolicy == AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY &&
         message.trim() == CODEX_PLAN_COMMAND
}

private fun normalizeCodexTerminalOutput(text: String): String {
  val withoutAnsi = ANSI_ESCAPE_PATTERN.replace(text, " ")
  val sanitized = buildString(withoutAnsi.length) {
    for (char in withoutAnsi) {
      append(if (char.isWhitespace() || char.isISOControl()) ' ' else char)
    }
  }
  return WHITESPACE_PATTERN.replace(sanitized, " ").trim()
}

private fun calculateCodexPlanModeRetryBackoffMs(retryAttempt: Int): Long {
  val cappedAttempt = retryAttempt.coerceIn(0, 2)
  return (CODEX_PLAN_MODE_RETRY_BACKOFF_MS * (1L shl cappedAttempt)).coerceAtMost(CODEX_PLAN_MODE_MAX_RETRY_BACKOFF_MS)
}

private val ANSI_ESCAPE_PATTERN = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
private val WHITESPACE_PATTERN = Regex("\\s+")
private val CODEX_PLAN_BUSY_PATTERN = Regex("'\\s*/plan\\s*'\\s+is disabled while a task is in progress\\.", RegexOption.IGNORE_CASE)

private const val CODEX_PLAN_MODE_RETRY_BACKOFF_MS: Long = 250
private const val CODEX_PLAN_MODE_MAX_RETRY_BACKOFF_MS: Long = 1_000
private const val CODEX_NEW_THREAD_COMMAND: String = "/new"
private const val CODEX_FORK_THREAD_COMMAND: String = "/fork"
private const val CODEX_PLAN_COMMAND: String = "/plan"
