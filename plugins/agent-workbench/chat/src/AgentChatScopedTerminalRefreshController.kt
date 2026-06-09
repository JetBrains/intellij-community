// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

private val LOG = logger<AgentChatScopedTerminalRefreshController>()

internal fun createAgentChatScopedTerminalRefreshController(
  file: AgentChatVirtualFile,
  tab: AgentChatTerminalTab,
  descriptor: AgentSessionProviderDescriptor?,
): AgentChatScopedTerminalRefreshController? {
  val provider = file.provider ?: return null
  if (descriptor?.emitsScopedRefreshSignals != true) {
    return null
  }
  return AgentChatScopedTerminalRefreshController(
    provider = provider,
    projectPath = file.projectPath,
    threadId = resolveAgentChatScopedRefreshThreadId(file),
    inputChanges = tab.keyEventsFlow.map {},
    sessionState = tab.sessionState,
    parentScope = tab.coroutineScope,
    activeThreadIdProvider = { file.threadId.takeIf(String::isNotBlank) },
    activeThreadUpdateEvents = { threadId -> descriptor.sessionSource.activeThreadUpdateEvents(file.projectPath, threadId) },
    emitInitialRefresh = !file.isPendingThread,
  )
}

internal fun resolveAgentChatScopedRefreshThreadId(file: AgentChatVirtualFile): String? {
  return file.sessionId.takeUnless { file.isPendingThread || it.isBlank() }
}

internal class AgentChatScopedTerminalRefreshController(
  private val provider: AgentSessionProvider,
  private val projectPath: String,
  private val threadId: String? = null,
  inputChanges: Flow<Unit>? = null,
  sessionState: StateFlow<TerminalViewSessionState>,
  parentScope: CoroutineScope,
  activeThreadIdProvider: () -> String? = { threadId },
  activeThreadUpdateEvents: ((String) -> Flow<AgentSessionSourceUpdateEvent>)? = null,
  emitInitialRefresh: Boolean = true,
  private val notifyRefresh: (AgentSessionProvider, String, String?, AgentThreadActivityReport?) -> Unit = ::notifyAgentChatScopedRefresh,
  private val notifyUpdate: (AgentSessionProvider, AgentSessionSourceUpdateEvent) -> Unit = ::notifyAgentChatScopedRefresh,
) : AgentChatDisposableController {
  private val initialRefreshJob: Job?
  private val terminationRefreshJob: Job
  private val activeThreadFileWatchJob: Job?

  init {
    activeThreadFileWatchJob = activeThreadUpdateEvents?.let { updateEvents ->
      parentScope.launch {
        sessionState
          .map { it == TerminalViewSessionState.Running }
          .distinctUntilChanged()
          .collectLatest { isRunning ->
            if (!isRunning) return@collectLatest
            collectActiveThreadFileChanges(
              restartChanges = inputChanges,
              activeThreadIdProvider = activeThreadIdProvider,
              activeThreadUpdateEvents = updateEvents,
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
    terminationRefreshJob.cancel()
    activeThreadFileWatchJob?.cancel()
  }

  private suspend fun collectActiveThreadFileChanges(
    restartChanges: Flow<Unit>?,
    activeThreadIdProvider: () -> String?,
    activeThreadUpdateEvents: (String) -> Flow<AgentSessionSourceUpdateEvent>,
  ) {
    val watchRequests = if (restartChanges == null) flowOf(Unit) else merge(flowOf(Unit), restartChanges)
    coroutineScope watchScope@{
      var watchedThreadId: String? = null
      var watchJob: Job? = null

      suspend fun stopActiveWatch() {
        val job = watchJob
        val threadId = watchedThreadId
        watchJob = null
        watchedThreadId = null
        if (threadId != null) {
          LOG.debug {
            "Stopping ${provider.value} active session file watch from agent chat terminal (path=$projectPath, threadId=$threadId)"
          }
        }
        job?.cancelAndJoin()
      }

      watchRequests.collect {
        val activeThreadId = activeThreadIdProvider()?.takeIf(String::isNotBlank)
        if (activeThreadId == null) {
          LOG.debug {
            "Skipping ${provider.value} active session file watch from agent chat terminal: no active thread id (path=$projectPath)"
          }
          stopActiveWatch()
          return@collect
        }
        val currentJob = watchJob
        if (watchedThreadId == activeThreadId && currentJob?.isActive == true) {
          return@collect
        }

        if (currentJob != null) {
          stopActiveWatch()
        }
        watchedThreadId = activeThreadId
        LOG.debug {
          "Starting ${provider.value} active session file watch from agent chat terminal (path=$projectPath, threadId=$activeThreadId)"
        }
        watchJob = this@watchScope.launch {
          activeThreadUpdateEvents(activeThreadId).collect { updateEvent ->
            LOG.debug {
              "Received ${provider.value} active session update from agent chat terminal (path=$projectPath, threadId=$activeThreadId)"
            }
            notifyUpdate(provider, updateEvent)
          }
        }
      }
      watchJob?.join()
    }
  }

  private fun emitScopedRefresh(reason: String, threadId: String? = this.threadId, activityReport: AgentThreadActivityReport? = null) {
    if (projectPath.isBlank()) {
      return
    }
    LOG.debug {
      "Emitting ${provider.value} scoped refresh from agent chat terminal ($reason, path=$projectPath, threadId=${threadId != null})"
    }
    notifyRefresh(provider, projectPath, threadId, activityReport)
  }
}
