// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.SingleAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val LOG = logger<AgentSessionsActivityService>()

@Service(Service.Level.PROJECT)
internal class AgentSessionsActivityService(
  private val project: Project,
  scope: CoroutineScope,
) {
  private val _summary = MutableStateFlow(AgentSessionsActivitySummary.EMPTY)
  private val _chromeSummary = MutableStateFlow(AgentSessionsActivitySummary.EMPTY)
  private val _mainToolbarSummary = MutableStateFlow(AgentSessionsActivitySummary.EMPTY)
  private val mainToolbarActivityState = AgentSessionsMainToolbarActivityState()
  private var lastStateLoaded: Boolean = false
  private val chromeRefreshAlarm = SingleAlarm.singleAlarm(0, scope) {
    updateChromeSummaries(_summary.value, lastStateLoaded)
  }

  val summary: StateFlow<AgentSessionsActivitySummary> = _summary.asStateFlow()

  init {
    scope.launch(Dispatchers.Default) {
      serviceAsync<AgentSessionReadService>().stateFlow().collect { state ->
        val nextSummary = buildAgentSessionsActivitySummary(state)
        val isLoadedState = state.lastUpdatedAt != null
        _summary.value = nextSummary
        lastStateLoaded = isLoadedState
        updateChromeSummaries(nextSummary, isLoadedState)
      }
    }
  }

  fun latestChromeSummary(): AgentSessionsActivitySummary = _chromeSummary.value

  fun latestMainToolbarSummary(): AgentSessionsActivitySummary = _mainToolbarSummary.value

  private fun updateChromeSummaries(summary: AgentSessionsActivitySummary, isLoadedState: Boolean) {
    val nowMillis = System.currentTimeMillis()
    val nextSummary = freshAgentSessionsActivitySummary(summary, nowMillis)
    val nextMainToolbarSummary = mainToolbarActivityState.update(nextSummary, isLoadedState)
    val rawCounts = summary.countsDebugText()
    val freshCounts = nextSummary.countsDebugText()
    if (rawCounts != freshCounts) {
      LOG.debug { "Filtered stale Agent activity rows raw=$rawCounts fresh=$freshCounts" }
    }
    val previousChromeSummary = _chromeSummary.value
    val previousMainToolbarSummary = _mainToolbarSummary.value
    if (previousChromeSummary != nextSummary || previousMainToolbarSummary != nextMainToolbarSummary) {
      LOG.debug {
        "Updating Agent activity summaries loaded=$isLoadedState " +
        "raw=$rawCounts " +
        "chrome=${previousChromeSummary.countsDebugText()}->$freshCounts " +
        "mainToolbar=${previousMainToolbarSummary.countsDebugText()}->${nextMainToolbarSummary.countsDebugText()}"
      }
      _chromeSummary.value = nextSummary
      _mainToolbarSummary.value = nextMainToolbarSummary
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

private fun AgentSessionsActivitySummary.countsDebugText(): String {
  return "attention=${attentionRows.size},running=${runningRows.size},done=${doneRows.size}"
}

internal class AgentSessionsMainToolbarActivityState {
  private var hasLoadedBaseline: Boolean = false
  private var bucketsByThreadKey: Map<AgentSessionsActivityThreadKey, AgentSessionsActivityBucket> = emptyMap()
  private var currentRunAttentionThreadKeys: Set<AgentSessionsActivityThreadKey> = emptySet()

  fun update(summary: AgentSessionsActivitySummary, isLoadedState: Boolean): AgentSessionsActivitySummary {
    val bucketedRows = summary.bucketedRows()
    val nextBucketsByThreadKey = bucketedRows.associate { bucketedRow -> bucketedRow.threadKey to bucketedRow.bucket }
    if (!hasLoadedBaseline) {
      if (isLoadedState) {
        hasLoadedBaseline = true
        bucketsByThreadKey = nextBucketsByThreadKey
      }
      return AgentSessionsActivitySummary.EMPTY
    }

    val nextAttentionThreadKeys = bucketedRows.asSequence()
      .filter { bucketedRow -> bucketedRow.bucket == AgentSessionsActivityBucket.ATTENTION }
      .map { bucketedRow -> bucketedRow.threadKey }
      .toSet()
    val enteredAttentionThreadKeys = nextAttentionThreadKeys.filter { threadKey ->
      bucketsByThreadKey[threadKey] != AgentSessionsActivityBucket.ATTENTION
    }
    currentRunAttentionThreadKeys = (currentRunAttentionThreadKeys + enteredAttentionThreadKeys).intersect(nextAttentionThreadKeys)
    bucketsByThreadKey = nextBucketsByThreadKey

    return if (currentRunAttentionThreadKeys.isEmpty()) AgentSessionsActivitySummary.EMPTY else summary
  }
}

private data class AgentSessionsActivityThreadKey(
  @JvmField val path: String,
  @JvmField val provider: String,
  @JvmField val threadId: String,
)

private data class AgentSessionsActivityBucketedRow(
  @JvmField val bucket: AgentSessionsActivityBucket,
  @JvmField val row: AgentSessionsActivityThreadRow,
) {
  val threadKey: AgentSessionsActivityThreadKey = AgentSessionsActivityThreadKey(
    path = row.path,
    provider = row.thread.provider.value,
    threadId = row.thread.id,
  )
}

private fun AgentSessionsActivitySummary.bucketedRows(): List<AgentSessionsActivityBucketedRow> {
  return buildList {
    attentionRows.forEach { row -> add(AgentSessionsActivityBucketedRow(AgentSessionsActivityBucket.ATTENTION, row)) }
    runningRows.forEach { row -> add(AgentSessionsActivityBucketedRow(AgentSessionsActivityBucket.RUNNING, row)) }
    doneRows.forEach { row -> add(AgentSessionsActivityBucketedRow(AgentSessionsActivityBucket.DONE, row)) }
  }
}

private fun AgentSessionsActivitySummary.nextChromeActivityExpirationDelay(nowMillis: Long): Long? {
  return sequenceOf(attentionRows, runningRows, doneRows)
    .flatten()
    .map { row -> row.thread.updatedAt + AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS + 1 - nowMillis }
    .filter { delayMillis -> delayMillis > 0 }
    .minOrNull()
}
