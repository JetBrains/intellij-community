// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentation
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.isTerminal
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.application.ApplicationManager
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private val LOG = logger<AgentSessionsActivityService>()
private const val MAX_ACTIVITY_DEBUG_ROWS: Int = 20

internal data class AgentSessionsActivitySnapshot(
  @JvmField val rawSummary: AgentSessionsActivitySummary = AgentSessionsActivitySummary.EMPTY,
  @JvmField val chromeSummary: AgentSessionsActivitySummary = AgentSessionsActivitySummary.EMPTY,
  @JvmField val mainToolbarSummary: AgentSessionsActivitySummary = AgentSessionsActivitySummary.EMPTY,
  @JvmField val isLoadedState: Boolean = false,
)

@Service(Service.Level.APP)
internal class AgentSessionsActivityModel(
  scope: CoroutineScope,
) {
  private val mutableSnapshot = MutableStateFlow(AgentSessionsActivitySnapshot())
  private val mainToolbarActivityState = AgentSessionsMainToolbarActivityState()
  private val chromeRefreshAlarm = SingleAlarm.singleAlarm(0, scope) {
    updateChromeSummaries(mutableSnapshot.value.rawSummary, mutableSnapshot.value.isLoadedState)
  }

  val snapshot: StateFlow<AgentSessionsActivitySnapshot> = mutableSnapshot.asStateFlow()

  init {
    scope.launch(Dispatchers.Default) {
      val readService = serviceAsync<AgentSessionReadService>()
      val presentationModel = serviceAsync<AgentSessionThreadPresentationModel>()
      combine(readService.stateFlow(), presentationModel.state) { state, presentationsByKey ->
        ActivityModelInput(state = state, presentationsByKey = presentationsByKey)
      }.collect { input ->
        val nextSummary = buildAgentSessionsActivitySummary(input.state, input.presentationsByKey)
        val isLoadedState = input.state.hasLoadedActivityBaseline()
        updateChromeSummaries(nextSummary, isLoadedState)
      }
    }
  }

  fun latestChromeSummary(): AgentSessionsActivitySummary = mutableSnapshot.value.chromeSummary

  fun latestMainToolbarSummary(): AgentSessionsActivitySummary = mutableSnapshot.value.mainToolbarSummary

  private fun updateChromeSummaries(summary: AgentSessionsActivitySummary, isLoadedState: Boolean) {
    val nowMillis = System.currentTimeMillis()
    val nextSummary = freshAgentSessionsActivitySummary(summary, nowMillis)
    val nextMainToolbarSummary = mainToolbarActivityState.update(nextSummary, isLoadedState)
    val rawCounts = summary.countsDebugText()
    val freshCounts = nextSummary.countsDebugText()
    val previousSnapshot = mutableSnapshot.value
    val previousChromeSummary = previousSnapshot.chromeSummary
    val previousMainToolbarSummary = previousSnapshot.mainToolbarSummary
    val chromeChanged = previousChromeSummary != nextSummary
    val mainToolbarChanged = previousMainToolbarSummary != nextMainToolbarSummary
    if (summary.hasActivityRows() || nextSummary.hasActivityRows() || nextMainToolbarSummary.hasActivityRows() || chromeChanged || mainToolbarChanged) {
      LOG.debug {
        "Agent activity summary tick loaded=$isLoadedState " +
        "raw=$rawCounts " +
        "fresh=$freshCounts " +
        "chrome=${previousChromeSummary.countsDebugText()}->$freshCounts changed=$chromeChanged " +
        "mainToolbar=${previousMainToolbarSummary.countsDebugText()}->${nextMainToolbarSummary.countsDebugText()} changed=$mainToolbarChanged" +
        activityRowsDebugSuffix(
          rawSummary = summary,
          freshSummary = nextSummary,
          mainToolbarSummary = nextMainToolbarSummary,
        )
      }
    }
    val nextSnapshot = AgentSessionsActivitySnapshot(
      rawSummary = summary,
      chromeSummary = nextSummary,
      mainToolbarSummary = nextMainToolbarSummary,
      isLoadedState = isLoadedState,
    )
    if (previousSnapshot != nextSnapshot) {
      mutableSnapshot.value = nextSnapshot
    }
    if (chromeChanged || mainToolbarChanged) {
      ActivityTracker.getInstance().inc()
    }
    scheduleChromeSummaryRefresh(summary, nowMillis)
  }

  private fun scheduleChromeSummaryRefresh(summary: AgentSessionsActivitySummary, nowMillis: Long) {
    chromeRefreshAlarm.cancel()
    val delayMillis = summary.nextChromeActivityExpirationDelay(nowMillis) ?: return
    chromeRefreshAlarm.requestWithCustomDelay(delayMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
  }
}

@Service(Service.Level.PROJECT)
internal class AgentSessionsActivityService(
  private val project: Project,
  scope: CoroutineScope,
) {
  init {
    scope.launch(Dispatchers.Default) {
      serviceAsync<AgentSessionsActivityModel>().snapshot.collect {
        if (!project.isDisposed) {
          project.service<AgentSessionsStripeIconUpdater>().scheduleUpdate()
        }
      }
    }
  }

  fun latestChromeSummary(): AgentSessionsActivitySummary {
    return ApplicationManager.getApplication().service<AgentSessionsActivityModel>().latestChromeSummary()
  }

  fun latestMainToolbarSummary(): AgentSessionsActivitySummary {
    return ApplicationManager.getApplication().service<AgentSessionsActivityModel>().latestMainToolbarSummary()
  }
}

private data class ActivityModelInput(
  @JvmField val state: AgentSessionsState,
  @JvmField val presentationsByKey: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>,
)

internal fun AgentSessionsState.hasLoadedActivityBaseline(): Boolean {
  if (lastUpdatedAt == null) return false
  val loadStates = projects.asSequence()
    .flatMap { project ->
      sequenceOf(project.providerLoadStates.values.asSequence()) +
      project.worktrees.asSequence().map { worktree -> worktree.providerLoadStates.values.asSequence() }
    }
    .flatten()
    .toList()
  return loadStates.isEmpty() || loadStates.all { state -> state.isTerminal }
}

private fun AgentSessionsActivitySummary.countsDebugText(): String {
  return "attention=${attentionRows.size},running=${runningRows.size},done=${doneRows.size}"
}

private fun AgentSessionsActivitySummary.hasActivityRows(): Boolean {
  return attentionRows.isNotEmpty() || runningRows.isNotEmpty() || doneRows.isNotEmpty()
}

private fun activityRowsDebugSuffix(
  rawSummary: AgentSessionsActivitySummary,
  freshSummary: AgentSessionsActivitySummary,
  mainToolbarSummary: AgentSessionsActivitySummary,
): String {
  if (rawSummary.countsDebugText() == freshSummary.countsDebugText() &&
      freshSummary.countsDebugText() == mainToolbarSummary.countsDebugText()) {
    return ""
  }
  return " rawRows=${rawSummary.rowsDebugText()}" +
         " freshRows=${freshSummary.rowsDebugText()}" +
         " mainToolbarRows=${mainToolbarSummary.rowsDebugText()}"
}

private fun AgentSessionsActivitySummary.rowsDebugText(): String {
  val rows = bucketedRows()
  val postfix = if (rows.size > MAX_ACTIVITY_DEBUG_ROWS) ",...+${rows.size - MAX_ACTIVITY_DEBUG_ROWS}]" else "]"
  return rows.take(MAX_ACTIVITY_DEBUG_ROWS).joinToString(prefix = "[", postfix = postfix) { bucketedRow ->
    val row = bucketedRow.row
    "${bucketedRow.bucket}:${row.path}:${row.thread.provider.value}:${row.thread.id}:" +
    "activity=${row.thread.activity}:summaryActivity=${row.thread.summaryActivity}:updatedAt=${row.thread.updatedAt}"
  }
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
