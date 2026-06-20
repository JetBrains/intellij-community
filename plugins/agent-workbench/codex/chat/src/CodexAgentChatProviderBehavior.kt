// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBehaviorFile
import com.intellij.agent.workbench.chat.AgentChatBehaviorTerminalTab
import com.intellij.agent.workbench.chat.AgentChatInitialMessageDispatchContext
import com.intellij.agent.workbench.chat.AgentChatInitialMessageRetryDecision
import com.intellij.agent.workbench.chat.AgentChatInitialMessageSendObservation
import com.intellij.agent.workbench.chat.AgentChatProviderBehavior
import com.intellij.agent.workbench.chat.AgentChatProviderBehaviorContributor
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.isBusyForExistingThreadPlanMode
import com.intellij.openapi.diagnostic.logger
import kotlin.math.min

private class CodexAgentChatProviderBehaviorLog

private val LOG = logger<CodexAgentChatProviderBehaviorLog>()

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

  override fun isConcreteNewThreadRebindCommand(command: String): Boolean = command == "/new"

  override suspend fun beforeInitialMessageSend(
    file: AgentChatBehaviorFile,
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (dispatch.completionPolicy != AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    return if (file.threadActivity.isBusyForExistingThreadPlanMode()) {
      AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
    }
    else {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
  }

  override suspend fun isInitialMessageDispatchAlreadySatisfied(
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
  ): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE && isCodexPlanModeVisible(tab.readRecentOutputTail())
  }

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE ||
           dispatch.completionPolicy == AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE) {
      if (isCodexPlanModeVisible(observation.textWithRecentOutputTail)) {
        return AgentChatInitialMessageRetryDecision.PROCEED
      }
      if (isCodexPlanModeTransientBusyOutput(observation.outputText)) {
        return AgentChatInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
      }
      if (retryAttempt < CODEX_PLAN_MODE_CONFIRMATION_RETRY_LIMIT) {
        return AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
      }
      LOG.warn("Codex plan mode was not confirmed after ${retryAttempt + 1} attempts; stopping initial message dispatch")
      return AgentChatInitialMessageRetryDecision.Stop
    }
    if (dispatch.completionPolicy != AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    return if (isCodexPlanModeTransientBusyOutput(observation.outputText)) {
      AgentChatInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
    }
    else {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
  }

}

private fun calculateCodexPlanModeRetryBackoffMs(retryAttempt: Int): Long {
  val cappedAttempt = retryAttempt.coerceIn(0, 2)
  return (CODEX_PLAN_MODE_RETRY_BACKOFF_MS * (1L shl cappedAttempt)).coerceAtMost(CODEX_PLAN_MODE_MAX_RETRY_BACKOFF_MS)
}

internal fun isCodexPlanModeVisible(text: String): Boolean {
  return normalizeCodexTerminalOutput(text).contains(CODEX_PLAN_MODE_VISIBLE_MARKER, ignoreCase = true)
}

private fun isCodexPlanModeTransientBusyOutput(text: String): Boolean {
  if (CODEX_PLAN_MODE_BUSY_MESSAGE_REGEX.containsMatchIn(normalizeCodexTerminalOutput(text))) {
    return true
  }
  val lines = codexTerminalLines(text)
  return isCodexHookRunningInOutput(lines) || lines.any { line ->
    line.contains(CODEX_PLAN_MODE_MCP_STARTUP_SINGLE_MESSAGE, ignoreCase = true) ||
    line.contains(CODEX_PLAN_MODE_MCP_STARTUP_MULTI_MESSAGE, ignoreCase = true) ||
    line.contains(CODEX_PLAN_MODE_QUEUE_HINT_MESSAGE, ignoreCase = true) ||
    line.contains(CODEX_PLAN_MODE_WORKING_STATUS_MARKER, ignoreCase = true)
  }
}

private fun isCodexHookRunningInOutput(lines: List<String>): Boolean {
  val latestHookStatusLine = lines.lastOrNull { line ->
    CODEX_PLAN_MODE_HOOK_RUNNING_STATUS_REGEX.containsMatchIn(line) ||
    CODEX_PLAN_MODE_HOOK_TERMINAL_STATUS_REGEX.containsMatchIn(line)
  }
  return latestHookStatusLine?.let(CODEX_PLAN_MODE_HOOK_RUNNING_STATUS_REGEX::containsMatchIn) == true
}

private fun codexTerminalLines(text: String): List<String> {
  return stripCodexTerminalAnsi(text)
    .replace("\r", "\n")
    .lineSequence()
    .map(::sanitizeCodexTerminalText)
    .filter(String::isNotEmpty)
    .toList()
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
private const val CODEX_PLAN_MODE_CONFIRMATION_RETRY_LIMIT: Int = 5
private val CODEX_PLAN_MODE_BUSY_MESSAGE_REGEX: Regex =
  Regex("'\\s*/plan\\s*'\\s+is disabled while a task is in progress\\.", RegexOption.IGNORE_CASE)
private const val CODEX_PLAN_MODE_MCP_STARTUP_SINGLE_MESSAGE: String = "Booting MCP server:"
private const val CODEX_PLAN_MODE_MCP_STARTUP_MULTI_MESSAGE: String = "Starting MCP servers"
private const val CODEX_PLAN_MODE_QUEUE_HINT_MESSAGE: String = "tab to queue"
private const val CODEX_PLAN_MODE_WORKING_STATUS_MARKER: String = "Working ("
private const val CODEX_PLAN_MODE_VISIBLE_MARKER: String = "Plan mode"
private val CODEX_PLAN_MODE_HOOK_RUNNING_STATUS_REGEX: Regex = Regex("(?:^| )Running .+ hook(?::|$)", RegexOption.IGNORE_CASE)
private val CODEX_PLAN_MODE_HOOK_TERMINAL_STATUS_REGEX: Regex = Regex(
  "(?:^| ).+ hook \\((?:completed|failed|blocked|stopped)\\)",
  RegexOption.IGNORE_CASE,
)

private val CODEX_TERMINAL_ANSI_ESCAPE_REGEX: Regex = Regex("\\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val CODEX_TERMINAL_WHITESPACE_REGEX: Regex = Regex(" +")
