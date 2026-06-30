// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.openapi.components.serviceOrNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class AgentThreadViewPendingThreadRefreshController(
  private val file: AgentThreadViewVirtualFile,
  private val behavior: AgentThreadViewProviderBehavior,
  private val tabSnapshotWriter: AgentThreadViewTabSnapshotWriter,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  private val retryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
) : AgentThreadViewDisposableController {
  private var retryJob: Job? = null

  fun attach(tab: AgentThreadViewTerminalTab) {
    subscribePendingFirstInput(tab)
    resumePendingScopedRefreshRetries(tab)
  }

  override fun dispose() {
    retryJob?.cancel()
    retryJob = null
  }

  private fun resumePendingScopedRefreshRetries(tab: AgentThreadViewTerminalTab) {
    if (file.pendingFirstInputAtMs == null) {
      return
    }
    ensurePendingScopedRefreshRetries(tab, emitImmediately = true)
  }

  private fun subscribePendingFirstInput(tab: AgentThreadViewTerminalTab) {
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
        serviceOrNull<AgentThreadViewOpenTabsPresentationStateService>()?.refreshOpenTabs()
        notifyAgentThreadViewScopedRefresh(provider = provider, projectPath = file.projectPath)
        ensurePendingScopedRefreshRetries(tab, emitImmediately = false)
      }
    }
  }

  private fun ensurePendingScopedRefreshRetries(
    tab: AgentThreadViewTerminalTab,
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
        notifyAgentThreadViewScopedRefresh(provider = provider, projectPath = file.projectPath)
      }
      while (isActive) {
        val retryDelayMs = pendingScopedRefreshRetryDelayMs() ?: break
        if (retryDelayMs > 0L) {
          delay(retryDelayMs.milliseconds)
        }
        if (!behavior.supportsPendingThreadRefreshRetry(file)) {
          break
        }
        notifyAgentThreadViewScopedRefresh(provider = provider, projectPath = file.projectPath)
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
