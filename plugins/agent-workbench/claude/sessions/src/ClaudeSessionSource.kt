// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.core.AgentThreadActivity
import com.intellij.agent.workbench.core.AgentThreadActivityReport
import com.intellij.agent.workbench.core.session.AgentSessionCost
import com.intellij.agent.workbench.core.session.AgentSessionProvider
import com.intellij.agent.workbench.core.session.AgentSessionOutlineItemKind
import com.intellij.agent.workbench.core.session.AgentSessionThread
import com.intellij.agent.workbench.core.session.AgentSessionThreadOutline
import com.intellij.agent.workbench.sessions.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.cost.LiteLlmPriceCatalogService
import com.intellij.agent.workbench.sessions.cost.aggregateAgentSessionUsageCost
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionOutlineForkResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.resolveReadTrackedActivity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ClaudeSessionSource internal constructor(
  private val backend: ClaudeSessionBackend,
  private val calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost,
  private val executableResolver: suspend () -> String = ClaudeCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
  private val hookSettingsProvider: (String) -> String? = ClaudeHookBridge::createLaunchSettingsArgument,
) : BaseAgentSessionSource(provider = AgentSessionProvider.CLAUDE) {
  constructor(
    backend: ClaudeSessionBackend = createDefaultClaudeSessionBackend(),
  ) : this(
    backend = backend,
    calculateCost = { usage -> service<LiteLlmPriceCatalogService>().calculateCost(usage) },
    executableResolver = ClaudeCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
    hookSettingsProvider = ClaudeHookBridge::createLaunchSettingsArgument,
  )

  private val observedUpdatedAtByThreadId: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
  private val completedUnreadUpdatedAtByThreadId: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
  private val latestForkablePromptByThreadKey: ConcurrentHashMap<ClaudeThreadOutlineKey, ClaudeForkablePrompt> = ConcurrentHashMap()

  override val supportsUpdates: Boolean get() = true

  override val supportsArchivedThreads: Boolean get() = true

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = merge(
      backend.sessionUpdates,
      readStateUpdateEvents,
    )

  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    return backend.activeThreadUpdateEvents(path = path, threadId = threadId)
  }

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val threads = backend.listThreads(path = path, openProject = openProject)
    val visibleThreads = threads.filterNot(ClaudeBackendThread::archived)
    rememberActiveNonReadyThreadRead(visibleThreads)
    val agentThreads = visibleThreads.map {
      it.toAgentSessionThread(readTracker, completedUnreadUpdatedAtByThreadId, calculateCost = calculateCost, includeCost = false)
    }
    rememberObservedThreadUpdates(visibleThreads)
    return agentThreads
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val archivedThreads = backend.listThreads(path = path, openProject = openProject)
      .filter(ClaudeBackendThread::archived)
    return archivedThreads.map {
      it.toAgentSessionThread(readTracker, completedUnreadUpdatedAtByThreadId, calculateCost = calculateCost)
    }
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

        val visibleThreads = backendResult.threads.filterNot(ClaudeBackendThread::archived)
        rememberActiveNonReadyThreadRead(visibleThreads)
        val archivedThreadIds = backendResult.threads
          .asSequence()
          .filter(ClaudeBackendThread::archived)
          .map(ClaudeBackendThread::id)
          .toCollection(LinkedHashSet())
        val removedThreadIds = LinkedHashSet<String>().apply {
          addAll(backendResult.removedThreadIds)
          addAll(archivedThreadIds)
        }
        val threads = visibleThreads.map { thread ->
          thread.toAgentSessionThread(
            readTracker = readTracker,
            completedUnreadUpdatedAtByThreadId = completedUnreadUpdatedAtByThreadId,
            observedUpdatedAtByThreadId = observedUpdatedAtByThreadId,
            calculateCost = calculateCost,
            includeCost = false,
          )
        }
        rememberObservedThreadUpdates(visibleThreads)
        if (backendResult.isComplete) {
          completeThreadsByPath[path] = threads
        }
        else {
          partialThreadsByPath[path] = threads
        }
        if (removedThreadIds.isNotEmpty()) {
          removedThreadIdsByPath[path] = removedThreadIds
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
    if (paths.isEmpty()) return emptyMap()

    val result = LinkedHashMap<String, AgentSessionRefreshHints>(paths.size)
    for (path in paths) {
      val threads = try {
        backend.listThreads(path = path, openProject = null)
      }
      catch (_: Throwable) {
        continue
      }
      val visibleThreads = threads.filterNot(ClaudeBackendThread::archived)
      val knownIds = refreshThreadSeedsByPath[path].orEmpty().asSequence().map { it.threadId }.toCollection(LinkedHashSet())
      val activityHintsByThreadId = LinkedHashMap<String, AgentThreadActivity>()
      for (thread in visibleThreads) {
        if (thread.id in knownIds) {
          activityHintsByThreadId[thread.id] = thread.effectiveActivity(
            readTracker = readTracker,
            completedUnreadUpdatedAtByThreadId = completedUnreadUpdatedAtByThreadId,
            observedUpdatedAtByThreadId = observedUpdatedAtByThreadId,
          )
        }
      }
      val rebindCandidates = visibleThreads
        .filter { it.id !in knownIds }
        .map { thread ->
          AgentSessionRebindCandidate(
            threadId = thread.id,
            title = thread.title,
            updatedAt = thread.updatedAt,
            activity = thread.effectiveActivity(
              readTracker = readTracker,
              completedUnreadUpdatedAtByThreadId = completedUnreadUpdatedAtByThreadId,
              observedUpdatedAtByThreadId = observedUpdatedAtByThreadId,
            ),
          )
        }
      if (rebindCandidates.isNotEmpty() || activityHintsByThreadId.isNotEmpty()) {
        result[path] = AgentSessionRefreshHints(
          rebindCandidates = rebindCandidates,
          activityUpdatesByThreadId = activityHintsByThreadId.mapValues { (_, activity) ->
            AgentSessionThreadActivityUpdate(
              activityReport = AgentThreadActivityReport(activity),
              updatesChromeActivity = false,
            )
          },
        )
      }
    }

    return result
  }

  override suspend fun loadThreadCosts(
    path: String,
    threads: List<AgentSessionThread>,
  ): Map<String, AgentSessionCost?> {
    if (threads.isEmpty()) {
      return emptyMap()
    }

    val requestedThreadIds = threads.asSequence()
      .map(AgentSessionThread::id)
      .toCollection(LinkedHashSet())
    val refreshedThreads = backend.refreshThreads(path = path, threadIds = requestedThreadIds, openProject = null)?.threads
                           ?: backend.listThreads(path = path, openProject = null)
    return refreshedThreads.asSequence()
      .filterNot(ClaudeBackendThread::archived)
      .filter { thread -> thread.id in requestedThreadIds }
      .associate { thread ->
        thread.id to thread.usageSnapshots.aggregateAgentSessionUsageCost(calculateCost)
      }
  }

  override suspend fun loadThreadOutline(path: String, threadId: String, subAgentId: String?): AgentSessionThreadOutline? {
    val outline = if (subAgentId == null) backend.loadThreadOutline(path = path, threadId = threadId) else null
    rememberLatestForkablePrompt(path = path, threadId = threadId, outline = outline)
    return outline
  }

  override fun canShowThreadOutlineForkAction(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): Boolean {
    return canForkThreadFromOutlineItem(path = path, threadId = threadId, itemId = itemId, subAgentId = subAgentId, tabKey = tabKey)
  }

  override fun canForkThreadFromOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): Boolean {
    return subAgentId == null &&
           itemId.isClaudeTranscriptUuid() &&
           latestForkablePromptByThreadKey[ClaudeThreadOutlineKey(path, threadId)]?.itemId == itemId
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
    val sourcePrompt = latestForkablePromptByThreadKey[ClaudeThreadOutlineKey(path, threadId)] ?: return null
    val forkSessionId = UUID.randomUUID().toString()
    val launchSpec = buildClaudeForkResumeLaunchSpec(
      sourceSessionId = threadId,
      forkSessionId = forkSessionId,
      executable = executableResolver(),
      hookSettingsArgument = hookSettingsProvider(forkSessionId),
    )
    return AgentSessionOutlineForkResult(
      thread = AgentSessionThread(
        id = forkSessionId,
        title = sourcePrompt.threadTitle,
        updatedAt = System.currentTimeMillis(),
        archived = false,
        activity = AgentThreadActivity.PROCESSING,
        provider = AgentSessionProvider.CLAUDE,
      ),
      launchSpecOverride = launchSpec,
    )
  }

  private fun rememberActiveNonReadyThreadRead(threads: Iterable<ClaudeBackendThread>) {
    rememberActiveThreadRead(
      threads = threads,
      id = ClaudeBackendThread::id,
      updatedAt = ClaudeBackendThread::updatedAt,
      shouldRemember = { it.activity != ClaudeSessionActivity.READY },
    )
  }

  private fun rememberObservedThreadUpdates(threads: Iterable<ClaudeBackendThread>) {
    for ((id, _, _, updatedAt) in threads) {
      observedUpdatedAtByThreadId.merge(id, updatedAt, ::maxOf)
    }
  }

  private fun rememberLatestForkablePrompt(path: String, threadId: String, outline: AgentSessionThreadOutline?) {
    val key = ClaudeThreadOutlineKey(path = path, threadId = threadId)
    val latestPrompt = outline
      ?.items
      ?.asReversed()
      ?.firstOrNull { item -> item.kind == AgentSessionOutlineItemKind.USER_PROMPT && item.id.isClaudeTranscriptUuid() }
    if (latestPrompt == null) {
      latestForkablePromptByThreadKey.remove(key)
    }
    else {
      latestForkablePromptByThreadKey[key] = ClaudeForkablePrompt(itemId = latestPrompt.id, threadTitle = outline.title)
    }
  }
}

private data class ClaudeThreadOutlineKey(
  @JvmField val path: String,
  @JvmField val threadId: String,
)

private data class ClaudeForkablePrompt(
  @JvmField val itemId: String,
  @JvmField val threadTitle: String,
)

private fun String.isClaudeTranscriptUuid(): Boolean {
  return try {
    UUID.fromString(this).toString() == this
  }
  catch (_: IllegalArgumentException) {
    false
  }
}

private fun ClaudeBackendThread.toAgentSessionThread(
  readTracker: Map<String, Long>,
  completedUnreadUpdatedAtByThreadId: MutableMap<String, Long>,
  observedUpdatedAtByThreadId: Map<String, Long> = emptyMap(),
  calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost,
  includeCost: Boolean = true,
): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    provider = AgentSessionProvider.CLAUDE,
    originBranch = gitBranch,
    activity = effectiveActivity(
      readTracker = readTracker,
      completedUnreadUpdatedAtByThreadId = completedUnreadUpdatedAtByThreadId,
      observedUpdatedAtByThreadId = observedUpdatedAtByThreadId,
    ),
    cost = if (includeCost) usageSnapshots.aggregateAgentSessionUsageCost(calculateCost) else null,
  )
}

private fun ClaudeBackendThread.effectiveActivity(
  readTracker: Map<String, Long>,
  completedUnreadUpdatedAtByThreadId: MutableMap<String, Long>,
  observedUpdatedAtByThreadId: Map<String, Long> = emptyMap(),
): AgentThreadActivity {
  return when (activity) {
    ClaudeSessionActivity.PROCESSING -> AgentThreadActivity.PROCESSING
    ClaudeSessionActivity.NEEDS_INPUT -> AgentThreadActivity.NEEDS_INPUT
    ClaudeSessionActivity.READY -> {
      val lastSeenAt = readTracker[id]
      if (lastSeenAt != null) {
        return if (!awaitingAssistantTurn) resolveReadTrackedActivity(readTracker, id, updatedAt) else AgentThreadActivity.READY
      }

      if (!awaitingAssistantTurn && completedUnreadUpdatedAtByThreadId[id] == updatedAt) {
        return AgentThreadActivity.UNREAD
      }

      val observedUpdatedAt = observedUpdatedAtByThreadId[id] ?: return AgentThreadActivity.READY
      if (!awaitingAssistantTurn && updatedAt > observedUpdatedAt) {
        completedUnreadUpdatedAtByThreadId[id] = updatedAt
        AgentThreadActivity.UNREAD
      }
      else {
        AgentThreadActivity.READY
      }
    }
  }
}
