// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.util.SingleAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class AgentSessionsActivityService(
  private val project: Project,
  scope: CoroutineScope,
) {
  private val _summary = MutableStateFlow(AgentSessionsActivitySummary.EMPTY)
  private val _chromeSummary = MutableStateFlow(AgentSessionsActivitySummary.EMPTY)
  private val chromeRefreshAlarm = SingleAlarm.singleAlarm(0, scope) {
    updateChromeSummary(_summary.value)
  }

  val summary: StateFlow<AgentSessionsActivitySummary> = _summary.asStateFlow()

  init {
    scope.launch(Dispatchers.Default) {
      serviceAsync<AgentSessionReadService>().stateFlow().collect { state ->
        val nextSummary = buildAgentSessionsActivitySummary(state)
        _summary.value = nextSummary
        updateChromeSummary(nextSummary)
      }
    }
  }

  fun latestChromeSummary(): AgentSessionsActivitySummary = _chromeSummary.value

  private fun updateChromeSummary(summary: AgentSessionsActivitySummary) {
    val nowMillis = System.currentTimeMillis()
    val nextSummary = freshAgentSessionsActivitySummary(summary, nowMillis)
    if (_chromeSummary.value != nextSummary) {
      _chromeSummary.value = nextSummary
      ActivityTracker.getInstance().inc()
      project.service<AgentSessionsStripeIconUpdater>().scheduleUpdate()
    }
    scheduleChromeSummaryRefresh(summary, nowMillis)
  }

  private fun scheduleChromeSummaryRefresh(summary: AgentSessionsActivitySummary, nowMillis: Long) {
    chromeRefreshAlarm.cancel()
    val delayMillis = summary.nextChromeActivityExpirationDelay(nowMillis) ?: return
    chromeRefreshAlarm.requestWithCustomDelay(delayMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
  }
}

private fun AgentSessionsActivitySummary.nextChromeActivityExpirationDelay(nowMillis: Long): Long? {
  return sequenceOf(attentionRows, runningRows, doneRows)
    .flatten()
    .map { row -> row.thread.updatedAt + AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS + 1 - nowMillis }
    .filter { delayMillis -> delayMillis > 0 }
    .minOrNull()
}
