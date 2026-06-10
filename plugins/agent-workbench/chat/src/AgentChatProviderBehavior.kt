// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.isClaudeMenuCommandPrompt
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.isBusyForExistingThreadPlanMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.RegistryManager
import kotlin.math.min

private class AgentChatProviderBehaviorLog

private val LOG = logger<AgentChatProviderBehaviorLog>()

internal fun resolveAgentChatProviderBehavior(provider: AgentSessionProvider?): AgentChatProviderBehavior {
  return when (provider) {
    AgentSessionProvider.CLAUDE -> ClaudeAgentChatProviderBehavior
    AgentSessionProvider.CODEX -> CodexAgentChatProviderBehavior
    AgentSessionProvider.JUNIE -> JunieAgentChatProviderBehavior
    else -> DefaultAgentChatProviderBehavior
  }
}

internal fun shouldInstallAgentChatPatchFolding(provider: AgentSessionProvider?): Boolean {
  return resolveAgentChatProviderBehavior(provider).shouldInstallPatchFolding()
}

internal interface AgentChatProviderBehavior {
  val semanticRegionDetector: AgentChatSemanticRegionDetector?
    get() = null

  fun supportsPendingThreadRefreshRetry(file: AgentChatVirtualFile): Boolean = false

  fun pendingThreadRefreshRetryDelayMs(file: AgentChatVirtualFile, currentTimeMs: Long, retryIntervalMs: Long): Long? = null

  fun supportsConcreteNewThreadRebind(
    file: AgentChatVirtualFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean = false

  fun isConcreteNewThreadRebindCommand(command: String): Boolean = false

  fun shouldUseBracketedPasteMode(text: String): Boolean = true

  suspend fun beforeInitialMessageSend(
    file: AgentChatVirtualFile,
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision = AgentChatInitialMessageRetryDecision.PROCEED

  suspend fun isInitialMessageDispatchAlreadySatisfied(
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
  ): Boolean = false

  fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatch): Boolean = false

  fun afterInitialMessageSendObservation(
    file: AgentChatVirtualFile,
    dispatch: AgentChatInitialMessageDispatch,
    outputText: String,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision = AgentChatInitialMessageRetryDecision.PROCEED

  fun shouldInstallSemanticRegionNavigation(): Boolean {
    if (semanticRegionDetector == null) {
      return false
    }
    if (ApplicationManager.getApplication() == null) {
      return false
    }
    return RegistryManager.getInstance().`is`(AGENT_CHAT_PROPOSED_PLAN_NAVIGATION_REGISTRY_KEY)
  }

  fun createSemanticRegionController(tab: AgentChatTerminalTab): AgentChatSemanticRegionController? {
    val detector = semanticRegionDetector?.takeIf { shouldInstallSemanticRegionNavigation() } ?: return null
    val terminalView = tab.terminalView ?: return null
    return AgentChatSemanticRegionController(
      terminalView = terminalView,
      sessionState = tab.sessionState,
      detector = detector,
      parentScope = tab.coroutineScope,
    )
  }

  fun shouldInstallPatchFolding(): Boolean = false

  fun createPatchFoldController(tab: AgentChatTerminalTab): AgentChatDisposableController? = null
}

private object DefaultAgentChatProviderBehavior : AgentChatProviderBehavior

private object ClaudeAgentChatProviderBehavior : AgentChatProviderBehavior {
  override fun shouldUseBracketedPasteMode(text: String): Boolean {
    return !text.isClaudeMenuCommandPrompt()
  }
}

private object JunieAgentChatProviderBehavior : AgentChatProviderBehavior {
  override suspend fun beforeInitialMessageSend(
    file: AgentChatVirtualFile,
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
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
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
  ): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE && isJuniePlanModeVisible(tab.readRecentOutputTail())
  }

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatch): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatVirtualFile,
    dispatch: AgentChatInitialMessageDispatch,
    outputText: String,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (dispatch.action != AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    return if (isJuniePlanModeVisible(outputText)) {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
    else {
      AgentChatInitialMessageRetryDecision.Stop
    }
  }
}

private object CodexAgentChatProviderBehavior : AgentChatProviderBehavior {
  override val semanticRegionDetector: AgentChatSemanticRegionDetector
    get() = CodexSemanticRegionDetector

  override fun supportsPendingThreadRefreshRetry(file: AgentChatVirtualFile): Boolean {
    return file.isPendingThread && file.subAgentId == null && file.provider == AgentSessionProvider.CODEX
  }

  override fun pendingThreadRefreshRetryDelayMs(file: AgentChatVirtualFile, currentTimeMs: Long, retryIntervalMs: Long): Long? {
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
    file: AgentChatVirtualFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean {
    return descriptor?.supportsNewThreadRebind == true && !file.isPendingThread && file.subAgentId == null
  }

  override fun isConcreteNewThreadRebindCommand(command: String): Boolean = command == "/new"

  override suspend fun beforeInitialMessageSend(
    file: AgentChatVirtualFile,
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (dispatch.completionPolicy != AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    return if (file.threadActivity.isBusyForExistingThreadPlanMode() || isCodexPlanModeUnsafeTerminalTail(tab.readRecentOutputTail())) {
      AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
    }
    else {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
  }

  override suspend fun isInitialMessageDispatchAlreadySatisfied(
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
  ): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE && isCodexPlanModeVisible(tab.readRecentOutputTail())
  }

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatch): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE ||
           dispatch.completionPolicy == AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatVirtualFile,
    dispatch: AgentChatInitialMessageDispatch,
    outputText: String,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE) {
      if (isCodexPlanModeVisible(outputText)) {
        return AgentChatInitialMessageRetryDecision.PROCEED
      }
      if (retryAttempt < CODEX_PLAN_MODE_CONFIRMATION_RETRY_LIMIT) {
        return AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
      }
      LOG.debug("Codex plan mode was not confirmed after ${retryAttempt + 1} attempts; continuing with prompt dispatch")
      return AgentChatInitialMessageRetryDecision.ProceedAndResetReadiness
    }
    if (dispatch.completionPolicy != AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY) {
      return AgentChatInitialMessageRetryDecision.PROCEED
    }
    return if (isCodexPlanModeBusyOutput(outputText)) {
      AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(calculateCodexPlanModeRetryBackoffMs(retryAttempt))
    }
    else {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
  }

  override fun shouldInstallPatchFolding(): Boolean {
    return ApplicationManager.getApplication() != null && RegistryManager.getInstance().`is`(CODEX_TUI_PATCH_FOLDING_REGISTRY_KEY)
  }

  override fun createPatchFoldController(tab: AgentChatTerminalTab): AgentChatDisposableController? {
    if (!shouldInstallPatchFolding()) {
      return null
    }
    val terminalView = tab.terminalView ?: return null
    return CodexTuiPatchFoldController(
      terminalView = terminalView,
      sessionState = tab.sessionState,
      parentScope = tab.coroutineScope,
    )
  }
}

private fun calculateCodexPlanModeRetryBackoffMs(retryAttempt: Int): Long {
  val cappedAttempt = retryAttempt.coerceIn(0, 2)
  return (CODEX_PLAN_MODE_RETRY_BACKOFF_MS * (1L shl cappedAttempt)).coerceAtMost(CODEX_PLAN_MODE_MAX_RETRY_BACKOFF_MS)
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

private fun isCodexPlanModeBusyOutput(text: String): Boolean {
  return normalizeCodexTerminalOutput(text).contains(CODEX_PLAN_MODE_BUSY_MESSAGE, ignoreCase = true)
}

internal fun isCodexPlanModeVisible(text: String): Boolean {
  return normalizeCodexTerminalOutput(text).contains(CODEX_PLAN_MODE_VISIBLE_MARKER, ignoreCase = true)
}

private fun isCodexPlanModeUnsafeTerminalTail(text: String): Boolean {
  val tailLines = codexTerminalTailLines(text)
  val latestLine = tailLines.lastOrNull() ?: return false
  if (latestLine.contains(CODEX_PLAN_MODE_BUSY_MESSAGE, ignoreCase = true)) {
    return true
  }
  if (isCodexHookRunningInTerminalTail(tailLines)) {
    return true
  }
  return tailLines.any { line ->
    line.contains(CODEX_PLAN_MODE_MCP_STARTUP_SINGLE_MESSAGE, ignoreCase = true) ||
    line.contains(CODEX_PLAN_MODE_MCP_STARTUP_MULTI_MESSAGE, ignoreCase = true) ||
    line.contains(CODEX_PLAN_MODE_QUEUE_HINT_MESSAGE, ignoreCase = true) ||
    line.contains(CODEX_PLAN_MODE_WORKING_STATUS_MARKER, ignoreCase = true)
  }
}

private fun isCodexHookRunningInTerminalTail(tailLines: List<String>): Boolean {
  val latestHookStatusLine = tailLines.lastOrNull { line ->
    CODEX_PLAN_MODE_HOOK_RUNNING_STATUS_REGEX.containsMatchIn(line) ||
    CODEX_PLAN_MODE_HOOK_TERMINAL_STATUS_REGEX.containsMatchIn(line)
  }
  return latestHookStatusLine?.let(CODEX_PLAN_MODE_HOOK_RUNNING_STATUS_REGEX::containsMatchIn) == true
}

private fun codexTerminalTailLines(text: String): List<String> {
  return stripCodexTerminalAnsi(text)
    .replace("\r", "\n")
    .lineSequence()
    .map(::sanitizeCodexTerminalText)
    .filter(String::isNotEmpty)
    .toList()
    .takeLast(CODEX_TERMINAL_TAIL_LINE_SCAN_LIMIT)
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
private const val CODEX_PLAN_MODE_CONFIRMATION_RETRY_LIMIT: Int = 2
private const val CODEX_TERMINAL_TAIL_LINE_SCAN_LIMIT: Int = 8
private const val JUNIE_PROMPT_READINESS_RETRY_BACKOFF_MS: Long = 250
private const val JUNIE_PROMPT_READINESS_MAX_RETRY_BACKOFF_MS: Long = 1_000
private const val CODEX_PLAN_MODE_BUSY_MESSAGE: String = "'/plan' is disabled while a task is in progress."
private const val CODEX_PLAN_MODE_MCP_STARTUP_SINGLE_MESSAGE: String = "Booting MCP server:"
private const val CODEX_PLAN_MODE_MCP_STARTUP_MULTI_MESSAGE: String = "Starting MCP servers"
private const val CODEX_PLAN_MODE_QUEUE_HINT_MESSAGE: String = "tab to queue"
private const val CODEX_PLAN_MODE_WORKING_STATUS_MARKER: String = "Working ("
private const val CODEX_PLAN_MODE_VISIBLE_MARKER: String = "Plan mode"
private const val JUNIE_PLAN_MODE_VISIBLE_MARKER: String = "Plan Mode"
private val CODEX_PLAN_MODE_HOOK_RUNNING_STATUS_REGEX: Regex = Regex("(?:^| )Running .+ hook(?::|$)", RegexOption.IGNORE_CASE)
private val CODEX_PLAN_MODE_HOOK_TERMINAL_STATUS_REGEX: Regex = Regex(
  "(?:^| ).+ hook \\((?:completed|failed|blocked|stopped)\\)",
  RegexOption.IGNORE_CASE,
)
private val JUNIE_PROMPT_INPUT_MARKERS: List<String> = listOf(
  "Type your prompt",
)

private val CODEX_TERMINAL_ANSI_ESCAPE_REGEX: Regex = Regex("\\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val CODEX_TERMINAL_WHITESPACE_REGEX: Regex = Regex(" +")
private val JUNIE_TERMINAL_WHITESPACE_REGEX: Regex = Regex(" +")
