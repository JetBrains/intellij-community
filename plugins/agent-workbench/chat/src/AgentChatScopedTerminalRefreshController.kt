// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionActivityHintSettings
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentChatScopedTerminalRefreshController>()
private const val AGENT_CHAT_SCOPED_REFRESH_DEBOUNCE_MS: Long = 750L

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
    outputChanges = tab.terminalView?.let(::terminalOutputModelChangeFlow),
    sessionState = tab.sessionState,
    parentScope = tab.coroutineScope,
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
  sessionState: StateFlow<TerminalViewSessionState>,
  parentScope: CoroutineScope,
  debounceMs: Long = AGENT_CHAT_SCOPED_REFRESH_DEBOUNCE_MS,
  emitInitialRefresh: Boolean = true,
  private val notifyRefresh: (AgentSessionProvider, String, String?, AgentThreadActivity?) -> Unit = ::notifyAgentChatTerminalOutputForRefresh,
) : AgentChatDisposableController {
  private val initialRefreshJob: Job?
  private val outputRefreshJob: Job?
  private val terminationRefreshJob: Job

  init {
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
            emitScopedRefresh("terminal output", activityHint = terminalOutputActivityHint())
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
    terminationRefreshJob.cancel()
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

  private fun terminalOutputActivityHint(): AgentThreadActivity? {
    return if (AgentSessionActivityHintSettings.isOptimisticActivityHintsEnabled()) AgentThreadActivity.PROCESSING else null
  }
}
