// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class AgentChatPendingThreadRefreshController(
  private val file: AgentChatVirtualFile,
  private val behavior: AgentChatProviderBehavior,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  private val retryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
) : AgentChatDisposableController {
  private var retryJob: Job? = null

  fun attach(tab: AgentChatTerminalTab) {
    subscribePendingFirstInput(tab)
    resumePendingScopedRefreshRetries(tab)
  }

  override fun dispose() {
    retryJob?.cancel()
    retryJob = null
  }

  private fun resumePendingScopedRefreshRetries(tab: AgentChatTerminalTab) {
    if (file.pendingFirstInputAtMs == null) {
      return
    }
    ensurePendingScopedRefreshRetries(tab, emitImmediately = true)
  }

  private fun subscribePendingFirstInput(tab: AgentChatTerminalTab) {
    val provider = file.provider ?: return
    if (!behavior.supportsPendingThreadRefreshRetry(file)) {
      return
    }
    tab.coroutineScope.launch {
      tab.keyEventsFlow.collectLatest {
        if (!file.markPendingFirstInputAtMsIfAbsent(currentTimeProvider())) {
          return@collectLatest
        }
        tabSnapshotWriter.upsert(file.toSnapshot())
        notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = file.projectPath)
        ensurePendingScopedRefreshRetries(tab, emitImmediately = false)
      }
    }
  }

  private fun ensurePendingScopedRefreshRetries(
    tab: AgentChatTerminalTab,
    emitImmediately: Boolean,
  ) {
    val provider = file.provider ?: return
    if (!behavior.supportsPendingThreadRefreshRetry(file)) {
      return
    }
    if (pendingScopedRefreshRetryDelayMs() == null || retryJob?.isActive == true) {
      return
    }
    val job = tab.coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      if (emitImmediately) {
        notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = file.projectPath)
      }
      while (isActive) {
        val retryDelayMs = pendingScopedRefreshRetryDelayMs() ?: break
        if (retryDelayMs > 0L) {
          delay(retryDelayMs.milliseconds)
        }
        if (!behavior.supportsPendingThreadRefreshRetry(file)) {
          break
        }
        notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = file.projectPath)
      }
    }
    retryJob = job
    job.invokeOnCompletion {
      if (retryJob === job) {
        retryJob = null
      }
    }
  }

  private fun pendingScopedRefreshRetryDelayMs(): Long? {
    return behavior.pendingThreadRefreshRetryDelayMs(
      file = file,
      currentTimeMs = currentTimeProvider(),
      retryIntervalMs = retryIntervalMs,
    )
  }
}
