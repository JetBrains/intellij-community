// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class AgentChatConcreteThreadRebindController(
  private val file: AgentChatVirtualFile,
  private val behavior: AgentChatProviderBehavior,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  private val retryIntervalMs: Long = AgentSessionThreadRebindPolicy.CONCRETE_CODEX_NEW_THREAD_REFRESH_RETRY_INTERVAL_MS,
  private val notifyRefresh: (AgentSessionProvider, String) -> Unit = { provider, projectPath ->
    notifyAgentChatScopedRefresh(provider = provider, projectPath = projectPath)
  },
) : AgentChatDisposableController {
  private var commandJob: Job? = null
  private var retryJob: Job? = null

  fun attach(tab: AgentChatTerminalTab, descriptor: AgentSessionProviderDescriptor?) {
    val provider = file.provider ?: return
    if (!behavior.supportsConcreteNewThreadRebind(file, descriptor)) {
      return
    }
    commandJob = tab.coroutineScope.launch {
      val commandTracker = AgentChatTerminalCommandTracker()
      tab.keyEventsFlow.collectLatest { event ->
        val executedCommand = commandTracker.record(event.awtEvent) ?: return@collectLatest
        if (!behavior.isConcreteNewThreadRebindCommand(executedCommand)) {
          return@collectLatest
        }
        if (!file.updateNewThreadRebindRequestedAtMs(currentTimeProvider())) {
          return@collectLatest
        }
        tabSnapshotWriter.upsert(file.toSnapshot())
        notifyRefresh(provider, file.projectPath)
        ensureConcreteScopedRefreshRetries(
          tab = tab,
          descriptor = descriptor,
          emitImmediately = false,
        )
      }
    }
    resumeConcreteScopedRefreshRetries(tab = tab, descriptor = descriptor)
  }

  override fun dispose() {
    commandJob?.cancel()
    commandJob = null
    retryJob?.cancel()
    retryJob = null
  }

  private fun resumeConcreteScopedRefreshRetries(tab: AgentChatTerminalTab, descriptor: AgentSessionProviderDescriptor?) {
    if (file.newThreadRebindRequestedAtMs == null) {
      return
    }
    ensureConcreteScopedRefreshRetries(
      tab = tab,
      descriptor = descriptor,
      emitImmediately = true,
    )
  }

  private fun ensureConcreteScopedRefreshRetries(
    tab: AgentChatTerminalTab,
    descriptor: AgentSessionProviderDescriptor?,
    emitImmediately: Boolean,
  ) {
    val provider = file.provider ?: return
    if (!behavior.supportsConcreteNewThreadRebind(file, descriptor)) {
      return
    }
    if (tab.sessionState.value == TerminalViewSessionState.Terminated || retryJob?.isActive == true) {
      return
    }
    val job = tab.coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      var retryDelayMs = concreteScopedRefreshRetryDelayMsOrClearStaleAnchor(descriptor) ?: return@launch
      if (emitImmediately) {
        notifyRefresh(provider, file.projectPath)
      }
      while (isActive) {
        if (retryDelayMs > 0L) {
          delay(retryDelayMs.milliseconds)
        }
        if (tab.sessionState.value == TerminalViewSessionState.Terminated) {
          break
        }
        retryDelayMs = concreteScopedRefreshRetryDelayMsOrClearStaleAnchor(descriptor) ?: break
        notifyRefresh(provider, file.projectPath)
      }
    }
    retryJob = job
    job.invokeOnCompletion {
      if (retryJob === job) {
        retryJob = null
      }
    }
  }

  private suspend fun concreteScopedRefreshRetryDelayMsOrClearStaleAnchor(descriptor: AgentSessionProviderDescriptor?): Long? {
    if (!behavior.supportsConcreteNewThreadRebind(file, descriptor)) {
      return null
    }
    val rebindRequestedAtMs = file.newThreadRebindRequestedAtMs ?: return null
    val currentTimeMs = currentTimeProvider()
    if (!AgentSessionThreadRebindPolicy.isConcreteCodexNewThreadRebindAnchorActive(rebindRequestedAtMs, currentTimeMs)) {
      clearConcreteScopedRefreshAnchor()
      return null
    }
    return AgentSessionThreadRebindPolicy.concreteCodexNewThreadRefreshRetryDelayMs(
      rebindRequestedAtMs = rebindRequestedAtMs,
      currentTimeMs = currentTimeMs,
      retryIntervalMs = retryIntervalMs,
    )
  }

  private suspend fun clearConcreteScopedRefreshAnchor() {
    if (!file.updateNewThreadRebindRequestedAtMs(newThreadRebindRequestedAtMs = null)) {
      return
    }
    tabSnapshotWriter.upsert(file.toSnapshot())
  }
}
