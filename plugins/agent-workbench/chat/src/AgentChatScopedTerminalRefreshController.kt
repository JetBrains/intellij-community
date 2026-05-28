// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentChatScopedTerminalRefreshController>()
private const val AGENT_CHAT_SCOPED_REFRESH_DEBOUNCE_MS: Long = 750L
private const val AGENT_CHAT_ROLLOUT_POLL_INTERVAL_MS: Long = 1_500L
private const val AGENT_CHAT_ROLLOUT_POLL_ACTIVE_WINDOW_MS: Long = 15_000L
internal const val AGENT_CHAT_ROLLOUT_POLL_REGISTRY_KEY: String = "agent.workbench.chat.rollout.poll.enabled"
internal const val AGENT_CHAT_TERMINAL_OUTPUT_SCOPED_REFRESH_REGISTRY_KEY: String =
  "agent.workbench.chat.terminal.output.scoped.refresh.enabled"

internal fun createAgentChatScopedTerminalRefreshController(
  file: AgentChatVirtualFile,
  tab: AgentChatTerminalTab,
  descriptor: AgentSessionProviderDescriptor?,
): AgentChatScopedTerminalRefreshController? {
  val provider = file.provider ?: return null
  if (descriptor?.emitsScopedRefreshSignals != true) {
    return null
  }
  val terminalOutputChanges = if (isAgentChatTerminalOutputScopedRefreshEnabled()) {
    tab.terminalView?.let(::terminalOutputModelChangeFlow)
  }
  else {
    null
  }
  return AgentChatScopedTerminalRefreshController(
    provider = provider,
    projectPath = file.projectPath,
    threadId = resolveAgentChatScopedRefreshThreadId(file),
    outputChanges = terminalOutputChanges,
    inputChanges = tab.keyEventsFlow.map {},
    sessionState = tab.sessionState,
    parentScope = tab.coroutineScope,
    activeThreadIdProvider = { file.threadId.takeIf(String::isNotBlank) },
    activeThreadFileChangeEvents = { threadId -> descriptor.sessionSource.activeThreadFileChangeEvents(file.projectPath, threadId) },
    emitInitialRefresh = !file.isPendingThread,
  )
}

internal fun resolveAgentChatScopedRefreshThreadId(file: AgentChatVirtualFile): String? {
  return file.sessionId.takeUnless { file.isPendingThread || it.isBlank() }
}

@OptIn(FlowPreview::class)
internal class AgentChatScopedTerminalRefreshController(
  private val provider: AgentSessionProvider,
  private val projectPath: String,
  private val threadId: String? = null,
  outputChanges: Flow<Unit>?,
  inputChanges: Flow<Unit>? = null,
  sessionState: StateFlow<TerminalViewSessionState>,
  parentScope: CoroutineScope,
  debounceMs: Long = AGENT_CHAT_SCOPED_REFRESH_DEBOUNCE_MS,
  rolloutPollIntervalMs: Long = AGENT_CHAT_ROLLOUT_POLL_INTERVAL_MS,
  rolloutPollActiveWindowMs: Long = AGENT_CHAT_ROLLOUT_POLL_ACTIVE_WINDOW_MS,
  rolloutPollEnabled: Boolean = isAgentChatRolloutPollEnabled(),
  activeThreadIdProvider: () -> String? = { threadId },
  activeThreadFileChangeEvents: ((String) -> Flow<Unit>)? = null,
  emitInitialRefresh: Boolean = true,
  private val notifyRefresh: (AgentSessionProvider, String, String?, AgentThreadActivity?) -> Unit = ::notifyAgentChatTerminalOutputForRefresh,
) : AgentChatDisposableController {
  private val initialRefreshJob: Job?
  private val outputRefreshJob: Job?
  private val rolloutActivityJob: Job?
  private val terminationRefreshJob: Job
  private val activeThreadFileWatchJob: Job?
  // Polls the rollout source shortly after terminal activity, compensating for macOS FSEvents
  // silence on codex's long-lived O_APPEND fd without refreshing idle running terminals.
  private val rolloutPollJob: Job?
  private val rolloutPollActivitySignals: Channel<Long>?

  init {
    val terminalActivityChanges = mergeTerminalActivityChanges(outputChanges, inputChanges)
    if (rolloutPollEnabled && terminalActivityChanges != null && rolloutPollIntervalMs > 0 && rolloutPollActiveWindowMs > 0) {
      val activitySignals = Channel<Long>(Channel.CONFLATED)
      rolloutPollActivitySignals = activitySignals
      rolloutActivityJob = parentScope.launch {
        terminalActivityChanges.collect {
          activitySignals.trySend(System.currentTimeMillis())
        }
      }
      rolloutPollJob = parentScope.launch {
        sessionState
          .map { it == TerminalViewSessionState.Running }
          .distinctUntilChanged()
          .collectLatest { isRunning ->
            if (!isRunning) return@collectLatest
            collectActivityArmedRolloutPoll(
              activitySignals = activitySignals,
              rolloutPollIntervalMs = rolloutPollIntervalMs,
              rolloutPollActiveWindowMs = rolloutPollActiveWindowMs,
            )
          }
      }
    }
    else {
      rolloutPollActivitySignals = null
      rolloutActivityJob = null
      rolloutPollJob = null
    }

    activeThreadFileWatchJob = activeThreadFileChangeEvents?.let { fileChangeEvents ->
      parentScope.launch {
        sessionState
          .map { it == TerminalViewSessionState.Running }
          .distinctUntilChanged()
          .collectLatest { isRunning ->
            if (!isRunning) return@collectLatest
            collectActiveThreadFileChanges(
              restartChanges = terminalActivityChanges,
              activeThreadIdProvider = activeThreadIdProvider,
              activeThreadFileChangeEvents = fileChangeEvents,
            )
          }
      }
    }

    initialRefreshJob = if (emitInitialRefresh) {
      parentScope.launch {
        emitScopedRefresh("attach")
      }
    }
    else {
      null
    }
    outputRefreshJob = outputChanges?.let { changes ->
      parentScope.launch {
        changes
          .debounce(debounceMs.milliseconds)
          .collect {
            emitScopedRefresh("terminal output")
          }
      }
    }
    terminationRefreshJob = parentScope.launch {
      sessionState
        .filter { state -> state == TerminalViewSessionState.Terminated }
        .collect {
          emitScopedRefresh("terminal termination")
        }
    }
  }

  override fun dispose() {
    initialRefreshJob?.cancel()
    outputRefreshJob?.cancel()
    rolloutActivityJob?.cancel()
    terminationRefreshJob.cancel()
    activeThreadFileWatchJob?.cancel()
    rolloutPollJob?.cancel()
    rolloutPollActivitySignals?.close()
  }

  private suspend fun collectActiveThreadFileChanges(
    restartChanges: Flow<Unit>?,
    activeThreadIdProvider: () -> String?,
    activeThreadFileChangeEvents: (String) -> Flow<Unit>,
  ) {
    val watchRequests = if (restartChanges == null) flowOf(Unit) else merge(flowOf(Unit), restartChanges)
    coroutineScope watchScope@{
      var watchedThreadId: String? = null
      var watchJob: Job? = null

      suspend fun stopActiveWatch() {
        val job = watchJob
        watchJob = null
        watchedThreadId = null
        job?.cancelAndJoin()
      }

      watchRequests.collect {
        val activeThreadId = activeThreadIdProvider()?.takeIf(String::isNotBlank)
        if (activeThreadId == null) {
          stopActiveWatch()
          return@collect
        }
        val currentJob = watchJob
        if (watchedThreadId == activeThreadId && currentJob?.isActive == true) {
          return@collect
        }

        currentJob?.cancelAndJoin()
        watchedThreadId = activeThreadId
        watchJob = this@watchScope.launch {
          activeThreadFileChangeEvents(activeThreadId).collect {
            emitScopedRefresh("active session file")
          }
        }
      }
      watchJob?.join()
    }
  }

  private suspend fun collectActivityArmedRolloutPoll(
    activitySignals: ReceiveChannel<Long>,
    rolloutPollIntervalMs: Long,
    rolloutPollActiveWindowMs: Long,
  ) {
    var activeUntilMs = 0L
    while (currentCoroutineContext().isActive) {
      val nowMs = System.currentTimeMillis()
      if (nowMs >= activeUntilMs) {
        activeUntilMs = activitySignals.receive() + rolloutPollActiveWindowMs
        continue
      }

      val waitMs = minOf(rolloutPollIntervalMs, activeUntilMs - nowMs)
      val activityAtMs = withTimeoutOrNull(waitMs.milliseconds) {
        activitySignals.receive()
      }
      if (activityAtMs != null) {
        activeUntilMs = maxOf(activeUntilMs, activityAtMs + rolloutPollActiveWindowMs)
      }
      else if (System.currentTimeMillis() < activeUntilMs) {
        emitScopedRefresh("rollout poll")
      }
    }
  }

  private fun emitScopedRefresh(reason: String, activityHint: AgentThreadActivity? = null) {
    if (projectPath.isBlank()) {
      return
    }
    LOG.debug {
      "Emitting ${provider.value} scoped refresh from agent chat terminal ($reason, path=$projectPath, threadId=${threadId != null})"
    }
    notifyRefresh(provider, projectPath, threadId, activityHint)
  }
}

private fun mergeTerminalActivityChanges(outputChanges: Flow<Unit>?, inputChanges: Flow<Unit>?): Flow<Unit>? {
  return when {
    outputChanges != null && inputChanges != null -> merge(outputChanges, inputChanges)
    outputChanges != null -> outputChanges
    else -> inputChanges
  }
}

private fun isAgentChatRolloutPollEnabled(): Boolean {
  return Registry.`is`(AGENT_CHAT_ROLLOUT_POLL_REGISTRY_KEY, false)
}

internal fun isAgentChatTerminalOutputScopedRefreshEnabled(): Boolean {
  return Registry.`is`(AGENT_CHAT_TERMINAL_OUTPUT_SCOPED_REFRESH_REGISTRY_KEY, false)
}
