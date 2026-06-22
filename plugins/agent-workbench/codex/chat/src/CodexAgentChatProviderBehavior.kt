// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBehaviorFile
import com.intellij.agent.workbench.chat.AgentChatInitialMessageDispatchContext
import com.intellij.agent.workbench.chat.AgentChatInitialMessageRetryDecision
import com.intellij.agent.workbench.chat.AgentChatInitialMessageSendObservation
import com.intellij.agent.workbench.chat.AgentChatProviderBehavior
import com.intellij.agent.workbench.chat.AgentChatProviderBehaviorContributor
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
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

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return dispatch.completionPolicy == AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (dispatch.completionPolicy != AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    if (isCodexPlanCommandBusyOutput(observation.outputText)) {
      return AgentChatInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
    }
    if (isCodexPlanCommandUnavailableOutput(observation.outputText)) {
      return AgentChatInitialMessageRetryDecision.Stop
    }
    if (isCodexPlanCommandUnsupportedOutput(observation.outputText)) {
      return AgentChatInitialMessageRetryDecision.Stop
    }
    if (isCodexPlanCommandBlankOutput(observation.outputText)) {
      if (retryAttempt < CODEX_PLAN_COMMAND_BLANK_OUTPUT_RETRY_LIMIT) {
        return AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
      }
      return AgentChatInitialMessageRetryDecision.Stop
    }
    return AgentChatInitialMessageRetryDecision.PROCEED
  }

}

private fun calculateCodexPlanModeRetryBackoffMs(retryAttempt: Int): Long {
  val cappedAttempt = retryAttempt.coerceIn(0, 2)
  return (CODEX_PLAN_MODE_RETRY_BACKOFF_MS * (1L shl cappedAttempt)).coerceAtMost(CODEX_PLAN_MODE_MAX_RETRY_BACKOFF_MS)
}

private fun isCodexPlanCommandBusyOutput(text: String): Boolean {
  return CODEX_PLAN_MODE_BUSY_MESSAGE_REGEX.containsMatchIn(normalizeCodexTerminalOutput(text))
}

private fun isCodexPlanCommandUnavailableOutput(text: String): Boolean {
  val normalized = normalizeCodexTerminalOutput(text)
  return normalized.contains(CODEX_PLAN_MODE_UNAVAILABLE_MESSAGE, ignoreCase = true) ||
         normalized.contains(CODEX_COLLABORATION_MODES_DISABLED_MESSAGE, ignoreCase = true)
}

private fun isCodexPlanCommandUnsupportedOutput(text: String): Boolean {
  val normalized = normalizeCodexTerminalOutput(text)
  return normalized.contains(CODEX_PLAN_COMMAND, ignoreCase = true) &&
         CODEX_PLAN_COMMAND_UNSUPPORTED_MARKERS.any { marker -> normalized.contains(marker, ignoreCase = true) }
}

private fun isCodexPlanCommandBlankOutput(text: String): Boolean {
  return normalizeCodexTerminalOutput(text).isEmpty()
}

private fun normalizeCodexTerminalOutput(text: String): String {
  return sanitizeCodexTerminalText(stripCodexTerminalAnsi(text))
}

private fun sanitizeCodexTerminalText(text: String): String {
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
  return sanitized.replace(CODEX_TERMINAL_WHITESPACE_REGEX, " ").trim()
}

private fun stripCodexTerminalAnsi(text: String): String = CODEX_TERMINAL_ANSI_ESCAPE_REGEX.replace(text, "")

private const val CODEX_PLAN_MODE_RETRY_BACKOFF_MS: Long = 250
private const val CODEX_PLAN_MODE_MAX_RETRY_BACKOFF_MS: Long = 1_000
private const val CODEX_PLAN_COMMAND_BLANK_OUTPUT_RETRY_LIMIT: Int = 2
private const val CODEX_NEW_THREAD_COMMAND: String = "/new"
private const val CODEX_FORK_THREAD_COMMAND: String = "/fork"
private const val CODEX_PLAN_COMMAND: String = "/plan"
private val CODEX_PLAN_MODE_BUSY_MESSAGE_REGEX: Regex =
  Regex("'\\s*/plan\\s*'\\s+is disabled while a task is in progress\\.", RegexOption.IGNORE_CASE)
private const val CODEX_PLAN_MODE_UNAVAILABLE_MESSAGE: String = "Plan mode unavailable right now."
private const val CODEX_COLLABORATION_MODES_DISABLED_MESSAGE: String = "Collaboration modes are disabled."
private val CODEX_PLAN_COMMAND_UNSUPPORTED_MARKERS: List<String> = listOf(
  "unknown command",
  "unknown slash command",
  "unrecognized command",
  "unrecognized slash command",
  "unsupported command",
  "invalid command",
  "no such command",
  "command not found",
  "not a recognized command",
  "not recognized as a command",
)

private val CODEX_TERMINAL_ANSI_ESCAPE_REGEX: Regex = Regex("\\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val CODEX_TERMINAL_WHITESPACE_REGEX: Regex = Regex(" +")
