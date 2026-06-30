// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-codex-rollout-source.spec.md
// @spec community/plugins/agent-workbench/spec/thread-view/agent-thread-view-structure.spec.md

import com.intellij.platform.ai.agent.codex.common.CodexThread
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexBackendThread
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHints
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexSessionActivity
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexSessionBackend
import com.intellij.platform.ai.agent.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProvider
import com.intellij.platform.ai.agent.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.platform.ai.agent.codex.sessions.backend.createDefaultCodexSessionBackend
import com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexExactRolloutThreadLoader
import com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexRolloutDiscoveryProvider
import com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexRolloutParser
import com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.platform.ai.agent.codex.sessions.backend.toAgentSessionRefreshHints
import com.intellij.platform.ai.agent.codex.sessions.backend.toAgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionCostKind
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.platform.ai.agent.filewatch.agentWorkbenchImmediateFileChangeFlow
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageCostCalculators
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.platform.ai.agent.sessions.core.cost.aggregateAgentSessionUsageCost
import com.intellij.platform.ai.agent.sessions.core.normalizeConcreteAgentSessionThreadId
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionActiveThreadUpdateSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionCostSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPrefetchSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionOutlineForkResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshHintsSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineForkSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionUpdateSource
import com.intellij.platform.ai.agent.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import java.nio.file.Path

private val LOG = logger<CodexSessionSource>()

internal class CodexSessionSource internal constructor(
  private val backend: CodexSessionBackend,
  private val appServerRefreshHintsProvider: CodexRefreshHintsProvider,
  private val rolloutDiscoveryProvider: CodexRefreshHintsProvider,
  private val rolloutBackend: CodexSessionBackend? = null,
  private val calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost = AgentSessionUsageCostCalculators::calculateCost,
  private val threadPathIndex: CodexThreadPathIndex = InMemoryCodexThreadPathIndex(),
  private val exactRolloutThreadLoader: CodexExactRolloutThreadLoader = CodexExactRolloutThreadLoader(),
) : BaseAgentSessionSource(provider = CODEX_AGENT_SESSION_PROVIDER, canReportExactThreadCount = false),
    AgentSessionUpdateSource,
    AgentSessionActiveThreadUpdateSource,
    AgentSessionArchivedSource,
    AgentSessionPrefetchSource,
    AgentSessionRefreshSource,
    AgentSessionRefreshHintsSource,
    AgentSessionCostSource,
    AgentSessionThreadOutlineSource,
    AgentSessionThreadOutlineForkSource {
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
    rolloutDiscoveryProvider = CodexRolloutDiscoveryProvider(
      rolloutBackend = rolloutBackend,
      activeFileChangeFlow = { paths ->
        // App-server fs/watch covers the supported app-server file watch contract, including
        // replace/rename updates. The macOS immediate watcher remains necessary for active
        // rollout files because Codex can append project-file evidence to a long-lived fd and
        // the UI must invalidate before that writer closes the file.
        merge(
          sharedAppServerService.watchPathChanges(paths),
          agentWorkbenchImmediateFileChangeFlow(paths),
        )
      },
    ),
    rolloutBackend = rolloutBackend,
    calculateCost = AgentSessionUsageCostCalculators::calculateCost,
    threadPathIndex = threadPathIndex,
  )

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = merge(
      backend.updates.map { AgentSessionSourceUpdateEvent.threadsChanged() },
      appServerRefreshHintsProvider.updateEvents,
      rolloutDiscoveryProvider.updateEvents.mapNotNull(::toRolloutDiscoveryUpdateEvent),
      readStateUpdateEvents,
    )

  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    return rolloutDiscoveryProvider.activeThreadUpdateEvents(path = path, threadId = threadId)
      .mapNotNull(::toRolloutDiscoveryUpdateEvent)
  }

  private fun toRolloutDiscoveryUpdateEvent(updateEvent: AgentSessionSourceUpdateEvent): AgentSessionSourceUpdateEvent? {
    if (updateEvent.scopedPaths.isNullOrEmpty() && !updateEvent.mayHaveChangedProjectFiles) {
      return null
    }
    return if (updateEvent.mayHaveChangedProjectFiles) {
      AgentSessionSourceUpdateEvent.projectFilesChanged(
        scopedPaths = updateEvent.scopedPaths,
        changedProjectFilePaths = updateEvent.changedProjectFilePaths,
      )
    }
    else {
      AgentSessionSourceUpdateEvent.discoveryChanged(
        scopedPaths = updateEvent.scopedPaths,
      )
    }
  }

  override suspend fun loadThreads(path: String, openProject: Project?): List<AgentSessionThread> {
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

  override suspend fun loadThreadOutline(path: String, threadId: String, subAgentId: String?): AgentSessionThreadOutline? {
    return loadThreadOutlineFromBackend(backend, path, threadId)
           ?: rolloutBackend
             ?.takeIf { fallbackBackend -> fallbackBackend !== backend }
             ?.let { fallbackBackend -> loadThreadOutlineFromBackend(fallbackBackend, path, threadId) }
           ?: loadIndexedRolloutThreadOutline(threadId)
  }

  override fun canForkThreadFromOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): Boolean {
    return subAgentId == null && parseCodexUserPromptOutlineItemIndex(itemId) != null
  }

  override suspend fun forkThreadFromOutlineItem(
    project: Project,
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): AgentSessionOutlineForkResult? {
    if (!canForkThreadFromOutlineItem(path = path, threadId = threadId, itemId = itemId, subAgentId = subAgentId, tabKey = tabKey)) {
      return null
    }
    val selectedUserPromptIndex = parseCodexUserPromptOutlineItemIndex(itemId) ?: return null
    val outline = loadThreadOutline(path = path, threadId = threadId, subAgentId = subAgentId) ?: return null
    val userPromptIndexes = outline.items.collectCodexUserPromptIndexes()
    if (selectedUserPromptIndex !in userPromptIndexes) {
      return null
    }
    val userPromptCount = (userPromptIndexes.maxOrNull() ?: return null) + 1
    val rollbackTurns = userPromptCount - selectedUserPromptIndex
    if (rollbackTurns < 1) {
      return null
    }
    val forkedThread = backend.forkThread(
      path = path,
      threadId = threadId,
      rollbackTurns = rollbackTurns,
      openProject = project,
    ) ?: return null
    rememberThreadMetadata(listOf(forkedThread))
    return AgentSessionOutlineForkResult(thread = toAgentSessionThread(forkedThread))
  }

  private suspend fun loadThreadOutlineFromBackend(
    backend: CodexSessionBackend,
    path: String,
    threadId: String,
  ): AgentSessionThreadOutline? {
    return try {
      backend.loadThreadOutline(path = path, threadId = threadId)?.takeIf { outline -> outline.threadId == threadId }
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to load Codex backend outline", e)
      null
    }
  }

  private fun loadIndexedRolloutThreadOutline(threadId: String): AgentSessionThreadOutline? {
    val rolloutPath = threadPathIndex.entry(threadId)?.rolloutPath ?: return null
    return try {
      CodexRolloutParser().parseOutline(Path.of(rolloutPath))?.takeIf { outline -> outline.threadId == threadId }
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to load Codex rollout outline", e)
      null
    }
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
      return refreshThreadsByListing(request)
    }

    val partialThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val completeThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val removedThreadIdsByPath = LinkedHashMap<String, Set<String>>()
    val failuresByPath = LinkedHashMap<String, Throwable>()

    for (path in request.paths) {
      val sourcePath = request.sourcePathFor(path)
      try {
        val backendResult = backend.refreshThreads(path = sourcePath, threadIds = request.threadIds, openProject = null)
        if (backendResult == null) {
          completeThreadsByPath[path] = listThreads(path = sourcePath, openProject = null)
          continue
        }

        rememberThreadMetadata(backendResult.threads)
        trackActiveThreadRead(backendResult.threads)
        val threads = mapBackendThreads(backendResult.threads)
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
    val appServerHints = appServerRefreshHintsProvider.prefetchRefreshHints(
      paths = paths,
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
    )
    absorbActiveThreadReads(appServerHints)
    return filterCodexRefreshHints(appServerHints).mapValues { (_, hints) ->
      hints.toAgentSessionRefreshHints()
    }
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

    val aggregateThreadIds = fullyMappedThreads.asSequence()
      .filter { thread -> thread.subAgentIds.isNotEmpty() }
      .mapTo(LinkedHashSet()) { thread -> thread.threadId }
    val cwdFilter = resolveProjectDirectoryFromPath(path)
      ?.let { workingDirectory ->
        com.intellij.platform.ai.agent.codex.common.normalizeRootPath(workingDirectory.toString().replace('\\', '/'))
      }
    return exactRolloutThreadLoader.loadThreads(
      cwdFilter = cwdFilter,
      threadIds = fullyMappedThreads.mapTo(LinkedHashSet()) { thread -> thread.threadId },
      aggregateThreadIds = aggregateThreadIds,
      rolloutPaths = rolloutPaths,
    )
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

      if (filteredActivityHintsByThreadId.isEmpty() && hints.presentationUpdatesByThreadId.isEmpty()) {
        continue
      }
      filtered[path] = CodexRefreshHints(
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

private fun Iterable<AgentSessionOutlineItem>.collectCodexUserPromptIndexes(): Set<Int> {
  val result = LinkedHashSet<Int>()
  val stack = ArrayDeque<AgentSessionOutlineItem>()
  forEach(stack::addLast)
  while (!stack.isEmpty()) {
    val item = stack.removeFirst()
    parseCodexUserPromptOutlineItemIndex(item.id)?.let(result::add)
    item.children.forEach(stack::addLast)
  }
  return result
}

private fun List<AgentSessionUsageSnapshot>?.toFrozenThreadCost(
  threadId: String,
  updatedAt: Long,
  calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost,
  threadPathIndex: CodexThreadPathIndex,
): AgentSessionCost? {
  val usageSnapshots = this?.takeIf(List<AgentSessionUsageSnapshot>::isNotEmpty) ?: return null
  val cost = usageSnapshots.aggregateAgentSessionUsageCost(calculateCost)
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
    provider = CODEX_AGENT_SESSION_PROVIDER,
    subAgents = thread.subAgents.map { subAgent ->
      AgentSubAgent(
        id = subAgent.id,
        name = subAgent.name,
        activity = subAgentActivitiesById[subAgent.id]?.toAgentThreadActivity() ?: AgentThreadActivity.READY,
      )
    },
    originBranch = thread.gitBranch,
    activityReport = AgentThreadActivityReport(
      rowActivity = activity.toAgentThreadActivity(),
      chromeActivity = summaryActivity?.toAgentThreadActivity(),
    ),
    cost = cost,
  )
}
