// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.agent.workbench.codex.sessions.backend.createDefaultCodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexExactRolloutThreadLoader
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.toAgentSessionRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.isWorking
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.core.cost.OpenRouterPriceCatalogService
import com.intellij.agent.workbench.sessions.core.normalizeConcreteAgentSessionThreadId
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.mergeAgentSessionThreadPresentationUpdates
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.math.BigDecimal

private val LOG = logger<CodexSessionSource>()

internal class CodexSessionSource internal constructor(
  private val backend: CodexSessionBackend,
  private val appServerRefreshHintsProvider: CodexRefreshHintsProvider,
  private val rolloutRefreshHintsProvider: CodexRefreshHintsProvider,
  private val rolloutBackend: CodexSessionBackend? = null,
  private val calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost = { AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE) },
  private val threadPathIndex: CodexThreadPathIndex = InMemoryCodexThreadPathIndex(),
  private val exactRolloutThreadLoader: CodexExactRolloutThreadLoader = CodexExactRolloutThreadLoader(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CODEX, canReportExactThreadCount = false) {
  constructor(
    threadPathIndex: CodexThreadPathIndex = service<CodexThreadPathIndexService>(),
    backend: CodexSessionBackend = createDefaultCodexSessionBackend(threadPathIndex = threadPathIndex),
    sharedAppServerService: SharedCodexAppServerService = service(),
    rolloutBackend: CodexRolloutSessionBackend = CodexRolloutSessionBackend(),
  ) : this(
    backend = backend,
    appServerRefreshHintsProvider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = sharedAppServerService::readThreadActivitySnapshot,
      notifications = sharedAppServerService.notifications,
    ),
    rolloutRefreshHintsProvider = CodexRolloutRefreshHintsProvider(rolloutBackend = rolloutBackend),
    rolloutBackend = rolloutBackend,
    calculateCost = service<OpenRouterPriceCatalogService>()::calculateCost,
    threadPathIndex = threadPathIndex,
  )

  override val supportsUpdates: Boolean
    get() = true

  override val supportsArchivedThreads: Boolean
    get() = true

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = merge(
      backend.updates.map { AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED) },
      appServerRefreshHintsProvider.updateEvents,
      rolloutRefreshHintsProvider.updateEvents,
      readStateUpdateEvents,
    )

  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    return rolloutRefreshHintsProvider.activeThreadUpdateEvents(path = path, threadId = threadId)
  }

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val threads = backend.listThreads(path = path, openProject = openProject)
    rememberThreadMetadata(threads)
    trackActiveThreadRead(threads)
    return mapBackendThreads(threads)
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val archivedThreads = backend.listArchivedThreads(path = path, openProject = openProject)
    rememberThreadMetadata(archivedThreads)
    return archivedThreads.map { thread ->
      val cost = threadPathIndex.frozenCost(thread.thread.id, thread.thread.updatedAt)
                 ?: thread.usageSnapshots.toFrozenThreadCost(
                   threadId = thread.thread.id,
                   updatedAt = thread.thread.updatedAt,
                   calculateCost = calculateCost,
                   threadPathIndex = threadPathIndex,
                 )
      toAgentSessionThread(thread = thread, cost = cost)
    }
  }

  override suspend fun loadThreadCosts(
    path: String,
    threads: List<AgentSessionThread>,
  ): Map<String, AgentSessionCost?> {
    if (threads.isEmpty()) {
      return emptyMap()
    }

    val requestedThreads = threads.asSequence()
      .map(AgentSessionThread::toRequestedCostThread)
      .mapNotNull { thread ->
        val concreteThreadId = normalizeConcreteAgentSessionThreadId(thread.threadId) ?: return@mapNotNull null
        if (concreteThreadId == thread.threadId) thread else thread.copy(threadId = concreteThreadId)
      }
      .toList()
    if (requestedThreads.isEmpty()) {
      return emptyMap()
    }

    val costsByThreadId = LinkedHashMap<String, AgentSessionCost?>()
    val unresolvedThreads = ArrayList<RequestedCodexThreadCost>(requestedThreads.size)
    for (thread in requestedThreads) {
      val frozenCost = threadPathIndex.frozenCost(thread.threadId, thread.updatedAt)
      if (frozenCost != null) {
        costsByThreadId[thread.threadId] = frozenCost
      }
      else {
        unresolvedThreads.add(thread)
      }
    }
    if (unresolvedThreads.isEmpty()) {
      return costsByThreadId
    }

    val exactRolloutThreadsById = loadExactRolloutThreads(path = path, threads = unresolvedThreads)
    val threadsWithoutExactRollout = unresolvedThreads.filter { thread -> thread.threadId !in exactRolloutThreadsById }
    val requestedThreadIds = threadsWithoutExactRollout.asSequence()
      .map(RequestedCodexThreadCost::threadId)
      .mapNotNull(::normalizeConcreteAgentSessionThreadId)
      .toCollection(LinkedHashSet())
    val backendThreadsById = loadBackendThreads(path = path, threadIds = requestedThreadIds)
    val threadsWithoutExactCost = threadsWithoutExactRollout.filter { thread ->
      backendThreadsById[thread.threadId]?.usageSnapshots.isNullOrEmpty()
    }
    val recoveredRolloutThreadsById = recoverRolloutThreads(path = path, threads = threadsWithoutExactCost)
    unresolvedThreads.forEach { thread ->
      val cost = exactRolloutThreadsById[thread.threadId]?.usageSnapshots.toFrozenThreadCost(
        threadId = thread.threadId,
        updatedAt = thread.updatedAt,
        calculateCost = calculateCost,
        threadPathIndex = threadPathIndex,
      )
                 ?: backendThreadsById[thread.threadId]?.usageSnapshots.toFrozenThreadCost(
                   threadId = thread.threadId,
                   updatedAt = thread.updatedAt,
                   calculateCost = calculateCost,
                   threadPathIndex = threadPathIndex,
                 )
                 ?: recoveredRolloutThreadsById[thread.threadId]?.usageSnapshots.toFrozenThreadCost(
                   threadId = thread.threadId,
                   updatedAt = thread.updatedAt,
                   calculateCost = calculateCost,
                   threadPathIndex = threadPathIndex,
                 )
                 ?: AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
                     .also { unavailableCost -> threadPathIndex.recordFrozenCost(thread.threadId, thread.updatedAt, unavailableCost) }
      costsByThreadId[thread.threadId] = cost
    }
    return costsByThreadId
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    val prefetched = backend.prefetchThreads(paths)
    if (prefetched.isEmpty()) return emptyMap()

    prefetched.values.flatten().let(::rememberThreadMetadata)
    prefetched.values.forEach(::trackActiveThreadRead)
    return prefetched.mapValues { (_, backendThreads) -> mapBackendThreads(backendThreads) }
  }

  override suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
    if (!request.isThreadScoped) {
      return super.refreshThreads(request)
    }

    val partialThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val completeThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val removedThreadIdsByPath = LinkedHashMap<String, Set<String>>()
    val failuresByPath = LinkedHashMap<String, Throwable>()

    for (path in request.paths) {
      try {
        val backendResult = backend.refreshThreads(path = path, threadIds = request.threadIds, openProject = null)
        if (backendResult == null) {
          completeThreadsByPath[path] = listThreads(path = path, openProject = null)
          continue
        }

        rememberThreadMetadata(backendResult.threads)
        trackActiveThreadRead(backendResult.threads)
        val threads = mapBackendThreadsWithRolloutFallback(mapOf(path to backendResult.threads))[path].orEmpty()
        if (backendResult.isComplete) {
          completeThreadsByPath[path] = threads
        }
        else {
          partialThreadsByPath[path] = threads
        }
        if (backendResult.removedThreadIds.isNotEmpty()) {
          removedThreadIdsByPath[path] = backendResult.removedThreadIds
        }
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        failuresByPath[path] = e
      }
    }

    return AgentSessionSourceRefreshResult(
      completeThreadsByPath = completeThreadsByPath,
      partialThreadsByPath = partialThreadsByPath,
      removedThreadIdsByPath = removedThreadIdsByPath,
      failuresByPath = failuresByPath,
    )
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, AgentSessionRefreshHints> {
    val rolloutHints = rolloutRefreshHintsProvider.prefetchRefreshHints(
      paths = paths,
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
    )
    val appServerHints = prefetchAppServerHintsWithRolloutVerification(
      paths = paths,
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
      rolloutHintsByPath = rolloutHints,
    )
    val mergedHints = mergeCodexRefreshHints(
      appServerHintsByPath = appServerHints,
      rolloutHintsByPath = rolloutHints,
    )
    absorbActiveThreadReads(mergedHints)
    return filterCodexRefreshHints(mergedHints).mapValues { (_, hints) ->
      hints.toAgentSessionRefreshHints()
    }
  }

  private suspend fun mapBackendThreadsWithRolloutFallback(
    backendThreadsByPath: Map<String, List<CodexBackendThread>>,
  ): Map<String, List<AgentSessionThread>> {
    if (backendThreadsByPath.isEmpty()) {
      return emptyMap()
    }

    val agentThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>(backendThreadsByPath.size)
    val refreshThreadSeedsByPath = LinkedHashMap<String, Set<AgentSessionRefreshThreadSeed>>()
    for ((path, backendThreads) in backendThreadsByPath) {
      val agentThreads = backendThreads.map { backendThread ->
        toAgentSessionThread(thread = backendThread)
      }
      agentThreadsByPath[path] = agentThreads
      if (agentThreads.isNotEmpty()) {
        refreshThreadSeedsByPath[path] = agentThreads.asSequence()
          .map { thread -> AgentSessionRefreshThreadSeed(threadId = thread.id, updatedAt = thread.updatedAt) }
          .toCollection(LinkedHashSet())
      }
    }
    if (refreshThreadSeedsByPath.isEmpty()) {
      return agentThreadsByPath
    }

    val rolloutHintsByPath = try {
      rolloutRefreshHintsProvider.prefetchRefreshHints(
        paths = refreshThreadSeedsByPath.keys.toList(),
        refreshThreadSeedsByPath = refreshThreadSeedsByPath,
      )
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to fetch Codex rollout activity fallback hints", e)
      return agentThreadsByPath
    }
    if (rolloutHintsByPath.isEmpty()) {
      return agentThreadsByPath
    }

    val verifiedRolloutHintsByPath = prefetchVerifiedRolloutFallbackHints(
      rolloutHintsByPath = rolloutHintsByPath,
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
    )

    absorbActiveThreadReads(verifiedRolloutHintsByPath)

    val result = LinkedHashMap<String, List<AgentSessionThread>>(agentThreadsByPath.size)
    for ((path, agentThreads) in agentThreadsByPath) {
      val rolloutHints = verifiedRolloutHintsByPath[path]
      val activityHintsByThreadId = rolloutHints?.activityHintsByThreadId.orEmpty()
      if (activityHintsByThreadId.isEmpty()) {
        result[path] = agentThreads
        continue
      }

      val backendThreadsById = backendThreadsByPath[path]
        .orEmpty()
        .associateBy { backendThread -> backendThread.thread.id }
      var appliedActivityUpdates = 0
      val threads = agentThreads.map { thread ->
        val backendThread = backendThreadsById[thread.id]
        val activityHint = activityHintsByThreadId[thread.id] ?: return@map thread
        if (!shouldKeepRefreshHint(threadId = thread.id, hint = activityHint)) {
          return@map thread
        }
        val hintedSummaryActivity = resolveHintedSummaryActivity(thread = thread, hint = activityHint)
        if (activityHint.verifiedFresh) {
          if (thread.activity == activityHint.activity &&
              thread.summaryActivity == hintedSummaryActivity &&
              thread.updatedAt >= activityHint.updatedAt) {
            return@map thread
          }

          appliedActivityUpdates += 1
          LOG.debug {
            "Applied Codex app-server activity verification " +
            "path=$path threadId=${thread.id} appServerActivity=${thread.activity} verifiedActivity=${activityHint.activity} " +
            "verifiedUpdatedAt=${activityHint.updatedAt} verifiedResponseRequired=${activityHint.responseRequired}"
          }
          return@map thread.copy(
            activityReport = AgentThreadActivityReport(rowActivity = activityHint.activity, chromeActivity = hintedSummaryActivity),
            updatedAt = maxOf(thread.updatedAt, activityHint.updatedAt),
          )
        }

        val currentHint = CodexRefreshActivityHint(
          activity = thread.activity,
          updatedAt = backendThread?.thread?.updatedAt ?: thread.updatedAt,
          responseRequired = backendThread?.requiresResponse == true,
          summaryActivity = thread.summaryActivity,
        )
        if (!shouldApplyRolloutActivityFallback(currentHint = currentHint, rolloutHint = activityHint)) {
          return@map thread
        }

        appliedActivityUpdates += 1
        LOG.debug {
          "Applied Codex rollout activity fallback " +
          "path=$path threadId=${thread.id} appServerActivity=${thread.activity} rolloutActivity=${activityHint.activity} " +
          "appServerUpdatedAt=${currentHint.updatedAt} rolloutUpdatedAt=${activityHint.updatedAt} " +
          "appServerResponseRequired=${currentHint.responseRequired} rolloutResponseRequired=${activityHint.responseRequired}"
        }
        thread.copy(
          activityReport = AgentThreadActivityReport(rowActivity = activityHint.activity, chromeActivity = hintedSummaryActivity),
          updatedAt = maxOf(thread.updatedAt, activityHint.updatedAt),
        )
      }
      if (appliedActivityUpdates > 0) {
        LOG.debug {
          "Applied Codex rollout activity updates path=$path count=$appliedActivityUpdates hints=${activityHintsByThreadId.size}"
        }
      }
      result[path] = threads
    }
    return result
  }

  private fun mapBackendThreads(backendThreads: List<CodexBackendThread>): List<AgentSessionThread> {
    return backendThreads.map { backendThread ->
      toAgentSessionThread(thread = backendThread)
    }
  }

  private suspend fun loadBackendThreads(path: String, threadIds: Set<String>): Map<String, CodexBackendThread> {
    if (threadIds.isEmpty()) {
      return emptyMap()
    }

    return try {
      val refreshedThreads = backend.refreshThreads(path = path, threadIds = threadIds, openProject = null)?.threads
                           ?: backend.listThreads(path = path, openProject = null)
      rememberThreadMetadata(refreshedThreads)
      refreshedThreads.asSequence()
        .filter { thread -> thread.thread.id in threadIds }
        .associateBy { thread -> thread.thread.id }
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to load Codex app-server cost snapshot", e)
      emptyMap()
    }
  }

  private suspend fun recoverRolloutThreads(
    path: String,
    threads: List<RequestedCodexThreadCost>,
  ): Map<String, CodexBackendThread> {
    if (threads.isEmpty()) {
      return emptyMap()
    }

    val missingThreadIds = threads.mapTo(LinkedHashSet()) { thread -> thread.threadId }

    val rolloutBackend = rolloutBackend ?: return emptyMap()
    val recoveredThreads = try {
      val refreshedThreads = rolloutBackend.refreshThreads(path = path, threadIds = missingThreadIds, openProject = null)?.threads
                           ?: rolloutBackend.listThreads(path = path, openProject = null)
      refreshedThreads.asSequence()
        .filter { thread -> thread.thread.id in missingThreadIds }
        .associateBy { thread -> thread.thread.id }
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to recover Codex rollout cost snapshot", e)
      emptyMap()
    }
    return recoveredThreads
  }

  private fun loadExactRolloutThreads(
    path: String,
    threads: List<RequestedCodexThreadCost>,
  ): Map<String, CodexBackendThread> {
    val workingDirectory = resolveProjectDirectoryFromPath(path) ?: return emptyMap()
    val cwdFilter = com.intellij.agent.workbench.codex.common.normalizeRootPath(workingDirectory.toString().replace('\\', '/'))
    val fullyMappedThreads = threads.filter { thread ->
      thread.relatedThreadIds().all { threadId -> threadPathIndex.entry(threadId)?.rolloutPath != null }
    }
    if (fullyMappedThreads.isEmpty()) {
      return emptyMap()
    }

    val rolloutPaths = fullyMappedThreads.asSequence()
      .flatMap { thread -> thread.relatedThreadIds().asSequence() }
      .mapNotNull { threadId -> threadPathIndex.entry(threadId)?.rolloutPath }
      .toCollection(LinkedHashSet())
    if (rolloutPaths.isEmpty()) {
      return emptyMap()
    }

    return exactRolloutThreadLoader.loadThreads(
      cwdFilter = cwdFilter,
      threadIds = fullyMappedThreads.mapTo(LinkedHashSet()) { thread -> thread.threadId },
      rolloutPaths = rolloutPaths,
    )
  }

  private suspend fun prefetchAppServerHintsWithRolloutVerification(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
    rolloutHintsByPath: Map<String, CodexRefreshHints>,
  ): Map<String, CodexRefreshHints> {
    val verificationSeedsByPath = buildRolloutVerificationSeedsByPath(
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
      rolloutHintsByPath = rolloutHintsByPath,
    )
    val appServerSeedsByPath = mergeForcedRefreshThreadSeeds(
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
      forcedRefreshThreadSeedsByPath = verificationSeedsByPath,
    )
    return appServerRefreshHintsProvider.prefetchRefreshHints(
      paths = paths,
      refreshThreadSeedsByPath = appServerSeedsByPath,
    )
  }

  private suspend fun prefetchVerifiedRolloutFallbackHints(
    rolloutHintsByPath: Map<String, CodexRefreshHints>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, CodexRefreshHints> {
    val verificationSeedsByPath = buildRolloutVerificationSeedsByPath(
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
      rolloutHintsByPath = rolloutHintsByPath,
    )
    if (verificationSeedsByPath.isEmpty()) {
      return rolloutHintsByPath
    }

    val appServerHints = try {
      appServerRefreshHintsProvider.prefetchRefreshHints(
        paths = verificationSeedsByPath.keys.toList(),
        refreshThreadSeedsByPath = verificationSeedsByPath,
      )
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to verify Codex rollout activity fallback with app server", e)
      return rolloutHintsByPath
    }
    return mergeCodexRefreshHints(
      appServerHintsByPath = appServerHints,
      rolloutHintsByPath = rolloutHintsByPath,
    )
  }

  private fun buildRolloutVerificationSeedsByPath(
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
    rolloutHintsByPath: Map<String, CodexRefreshHints>,
  ): Map<String, Set<AgentSessionRefreshThreadSeed>> {
    if (refreshThreadSeedsByPath.isEmpty() || rolloutHintsByPath.isEmpty()) {
      return emptyMap()
    }

    val verificationSeedsByPath = LinkedHashMap<String, Set<AgentSessionRefreshThreadSeed>>()
    for ((path, refreshThreadSeeds) in refreshThreadSeedsByPath) {
      val activityHintsByThreadId = rolloutHintsByPath[path]?.activityHintsByThreadId.orEmpty()
      if (activityHintsByThreadId.isEmpty()) {
        continue
      }

      val verificationSeeds = refreshThreadSeeds.asSequence()
        .filter { refreshThreadSeed ->
          activityHintsByThreadId[refreshThreadSeed.threadId]?.shouldVerifyWithAppServer() == true
        }
        .mapTo(LinkedHashSet()) { refreshThreadSeed -> refreshThreadSeed.copy(forceRefresh = true) }
      if (verificationSeeds.isNotEmpty()) {
        verificationSeedsByPath[path] = verificationSeeds
      }
    }
    return verificationSeedsByPath
  }

  private fun mergeForcedRefreshThreadSeeds(
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
    forcedRefreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, Set<AgentSessionRefreshThreadSeed>> {
    if (forcedRefreshThreadSeedsByPath.isEmpty()) {
      return refreshThreadSeedsByPath
    }

    val mergedSeedsByPath = LinkedHashMap<String, Set<AgentSessionRefreshThreadSeed>>(refreshThreadSeedsByPath.size)
    for ((path, refreshThreadSeeds) in refreshThreadSeedsByPath) {
      val forcedRefreshThreadIds = forcedRefreshThreadSeedsByPath[path]
        .orEmpty()
        .mapTo(HashSet()) { refreshThreadSeed -> refreshThreadSeed.threadId }
      if (forcedRefreshThreadIds.isEmpty()) {
        mergedSeedsByPath[path] = refreshThreadSeeds
        continue
      }

      mergedSeedsByPath[path] = refreshThreadSeeds.mapTo(LinkedHashSet()) { refreshThreadSeed ->
        if (refreshThreadSeed.threadId in forcedRefreshThreadIds) refreshThreadSeed.copy(forceRefresh = true) else refreshThreadSeed
      }
    }
    return mergedSeedsByPath
  }

  private fun trackActiveThreadRead(threads: Iterable<CodexBackendThread>) {
    rememberActiveThreadRead(threads, { it.thread.id }, { it.thread.updatedAt })
  }

  private fun rememberThreadMetadata(threads: Iterable<CodexBackendThread>) {
    threadPathIndex.recordThreads(threads.map(CodexBackendThread::thread))
  }

  /**
   * Merges the active thread's hint updatedAt into [readTracker] when it is
   * observed without an outstanding response requirement. Must run before
   * [filterCodexRefreshHints] so the filter sees the up-to-date tracker.
   */
  private fun absorbActiveThreadReads(hintsByPath: Map<String, CodexRefreshHints>) {
    val currentActiveId = activeThreadId ?: return
    for ((_, hints) in hintsByPath) {
      val hint = hints.activityHintsByThreadId[currentActiveId] ?: continue
      if (!hint.responseRequired) {
        readTracker.merge(currentActiveId, hint.updatedAt, ::maxOf)
      }
    }
  }

  private fun filterCodexRefreshHints(hintsByPath: Map<String, CodexRefreshHints>): Map<String, CodexRefreshHints> {
    if (hintsByPath.isEmpty()) {
      return emptyMap()
    }

    val filtered = LinkedHashMap<String, CodexRefreshHints>(hintsByPath.size)
    for ((path, hints) in hintsByPath) {
      val filteredActivityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>(hints.activityHintsByThreadId.size)
      for ((threadId, hint) in hints.activityHintsByThreadId) {
        if (shouldKeepRefreshHint(threadId = threadId, hint = hint)) {
          filteredActivityHintsByThreadId[threadId] = hint
        }
      }

      if (hints.rebindCandidates.isEmpty() && filteredActivityHintsByThreadId.isEmpty() && hints.presentationUpdatesByThreadId.isEmpty()) {
        continue
      }
      filtered[path] = CodexRefreshHints(
        rebindCandidates = hints.rebindCandidates,
        activityHintsByThreadId = filteredActivityHintsByThreadId,
        presentationUpdatesByThreadId = hints.presentationUpdatesByThreadId,
      )
    }
    return filtered
  }

  private fun shouldKeepRefreshHint(threadId: String, hint: CodexRefreshActivityHint): Boolean {
    if (hint.activity != AgentThreadActivity.UNREAD || hint.responseRequired) {
      return true
    }

    val lastReadAt = readTracker[threadId] ?: return true
    return hint.updatedAt > lastReadAt
  }
}

internal fun mergeCodexRefreshHints(
  appServerHintsByPath: Map<String, CodexRefreshHints>,
  rolloutHintsByPath: Map<String, CodexRefreshHints>,
): Map<String, CodexRefreshHints> {
  if (appServerHintsByPath.isEmpty()) {
    return rolloutHintsByPath
  }
  if (rolloutHintsByPath.isEmpty()) {
    return appServerHintsByPath
  }

  val merged = LinkedHashMap<String, CodexRefreshHints>()
  val allPaths = LinkedHashSet<String>(appServerHintsByPath.keys.size + rolloutHintsByPath.keys.size)
  allPaths.addAll(appServerHintsByPath.keys)
  allPaths.addAll(rolloutHintsByPath.keys)

  for (path in allPaths) {
    val appHints = appServerHintsByPath[path]
    val rolloutHints = rolloutHintsByPath[path]
    val mergedRebindCandidates = mergeRebindCandidates(
      primary = appHints?.rebindCandidates.orEmpty(),
      fallback = rolloutHints?.rebindCandidates.orEmpty(),
    )
    val mergedActivityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>()
    appHints?.activityHintsByThreadId?.forEach { (threadId, hint) ->
      mergedActivityHintsByThreadId[threadId] = hint
    }
    rolloutHints?.activityHintsByThreadId?.forEach { (threadId, hint) ->
      if (shouldApplyRolloutActivityFallback(
          currentHint = mergedActivityHintsByThreadId[threadId],
          rolloutHint = hint,
        )) {
        // Rollout can keep TUI-backed working activity ahead of cached app-server state.
        mergedActivityHintsByThreadId[threadId] = hint
      }
    }
    val mergedPresentationUpdatesByThreadId = mergeCodexPresentationUpdates(
      appHints?.presentationUpdatesByThreadId.orEmpty(),
      rolloutHints?.presentationUpdatesByThreadId.orEmpty(),
    )

    if (mergedRebindCandidates.isEmpty() && mergedActivityHintsByThreadId.isEmpty() && mergedPresentationUpdatesByThreadId.isEmpty()) {
      continue
    }
    merged[path] = CodexRefreshHints(
      rebindCandidates = mergedRebindCandidates,
      activityHintsByThreadId = mergedActivityHintsByThreadId,
      presentationUpdatesByThreadId = mergedPresentationUpdatesByThreadId,
    )
  }
  return merged
}

private fun mergeCodexPresentationUpdates(
  primary: Map<String, AgentSessionThreadPresentationUpdate>,
  fallback: Map<String, AgentSessionThreadPresentationUpdate>,
): Map<String, AgentSessionThreadPresentationUpdate> {
  val merged = LinkedHashMap<String, AgentSessionThreadPresentationUpdate>(primary.size + fallback.size)
  for (threadId in primary.keys + fallback.keys) {
    val primaryUpdate = primary[threadId]
    val fallbackUpdate = fallback[threadId]
    merged[threadId] = when {
      primaryUpdate == null -> checkNotNull(fallbackUpdate)
      fallbackUpdate == null -> primaryUpdate
      else -> mergeAgentSessionThreadPresentationUpdates(primaryUpdate, fallbackUpdate)
    }
  }
  return merged
}

private fun shouldApplyRolloutActivityFallback(
  currentHint: CodexRefreshActivityHint?,
  rolloutHint: CodexRefreshActivityHint,
): Boolean {
  return when {
    currentHint == null -> true
    rolloutHint.responseRequired -> rolloutHint.updatedAt > currentHint.updatedAt
    currentHint.responseRequired -> rolloutHint.updatedAt > currentHint.updatedAt
    rolloutHint.activity.isWorking && !currentHint.activity.isWorking -> true
    !rolloutHint.activity.isWorking && currentHint.activity.isWorking -> rolloutHint.updatedAt >= currentHint.updatedAt
    rolloutHint.activity == AgentThreadActivity.UNREAD -> rolloutHint.updatedAt > currentHint.updatedAt
    !rolloutHint.activity.isWorking -> false
    rolloutHint.updatedAt <= currentHint.updatedAt -> false
    else -> true
  }
}

private fun CodexRefreshActivityHint.shouldVerifyWithAppServer(): Boolean {
  return responseRequired || activity.isWorking
}

private fun resolveHintedSummaryActivity(thread: AgentSessionThread, hint: CodexRefreshActivityHint): AgentThreadActivity? {
  return when {
    hint.hasSummaryActivityHint -> hint.summaryActivity
    thread.summaryActivity == null -> null
    else -> hint.activity
  }
}

private fun mergeRebindCandidates(
  primary: List<AgentSessionRebindCandidate>,
  fallback: List<AgentSessionRebindCandidate>,
): List<AgentSessionRebindCandidate> {
  if (primary.isEmpty()) return fallback
  if (fallback.isEmpty()) return primary

  val mergedByThreadId = LinkedHashMap<String, AgentSessionRebindCandidate>(primary.size + fallback.size)
  primary.forEach { candidate ->
    mergedByThreadId[candidate.threadId] = candidate
  }
  fallback.forEach { candidate ->
    mergedByThreadId.putIfAbsent(candidate.threadId, candidate)
  }
  return mergedByThreadId.values.toList()
}

private data class RequestedCodexThreadCost(
  @JvmField val threadId: String,
  @JvmField val updatedAt: Long,
  @JvmField val subAgentIds: List<String> = emptyList(),
)

private fun AgentSessionThread.toRequestedCostThread(): RequestedCodexThreadCost {
  return RequestedCodexThreadCost(
    threadId = id,
    updatedAt = updatedAt,
    subAgentIds = subAgents.map(AgentSubAgent::id),
  )
}

private fun RequestedCodexThreadCost.relatedThreadIds(): Set<String> {
  return LinkedHashSet<String>(1 + subAgentIds.size).apply {
    add(threadId)
    addAll(subAgentIds)
  }
}

private fun List<AgentSessionUsageSnapshot>?.toFrozenThreadCost(
  threadId: String,
  updatedAt: Long,
  calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost,
  threadPathIndex: CodexThreadPathIndex,
): AgentSessionCost? {
  val usageSnapshots = this?.takeIf(List<AgentSessionUsageSnapshot>::isNotEmpty) ?: return null
  val cost = usageSnapshots.toAgentSessionCost(calculateCost)
             ?: AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
  threadPathIndex.recordFrozenCost(threadId, updatedAt, cost)
  return cost
}

private fun toAgentSessionThread(thread: CodexBackendThread, cost: AgentSessionCost? = null): AgentSessionThread {
  return toAgentSessionThread(
    thread = thread.thread,
    activity = thread.activity,
    summaryActivity = thread.summaryActivity,
    subAgentActivitiesById = thread.subAgentActivitiesById,
    cost = cost,
  )
}

private fun List<AgentSessionUsageSnapshot>.toAgentSessionCost(
  calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost,
): AgentSessionCost? {
  if (isEmpty()) return null

  val componentCosts = map(calculateCost)
  if (componentCosts.any { it.amountUsd == null }) {
    return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
  }

  val totalAmount = componentCosts.fold(BigDecimal.ZERO) { acc, cost ->
    acc + checkNotNull(cost.amountUsd)
  }
  val kind = if (componentCosts.all { it.kind == AgentSessionCostKind.EXACT }) {
    AgentSessionCostKind.EXACT
  }
  else {
    AgentSessionCostKind.ESTIMATED
  }
  val matchedModelId = componentCosts.mapNotNull(AgentSessionCost::matchedModelId).distinct().singleOrNull()
  return AgentSessionCost(amountUsd = totalAmount, kind = kind, matchedModelId = matchedModelId)
}

private fun toAgentSessionThread(
  thread: CodexThread,
  activity: CodexSessionActivity,
  summaryActivity: CodexSessionActivity?,
  subAgentActivitiesById: Map<String, CodexSessionActivity> = emptyMap(),
  cost: AgentSessionCost? = null,
): AgentSessionThread {
  return AgentSessionThread(
    id = thread.id,
    title = thread.title,
    updatedAt = thread.updatedAt,
    archived = thread.archived,
    provider = AgentSessionProvider.CODEX,
    subAgents = thread.subAgents.map { subAgent ->
      AgentSubAgent(
        id = subAgent.id,
        name = subAgent.name,
        activity = subAgentActivitiesById[subAgent.id]?.toAgentThreadActivity() ?: AgentThreadActivity.READY,
      )
    },
    originBranch = thread.gitBranch,
    activity = activity.toAgentThreadActivity(),
    summaryActivity = summaryActivity?.toAgentThreadActivity(),
    cost = cost,
  )
}
