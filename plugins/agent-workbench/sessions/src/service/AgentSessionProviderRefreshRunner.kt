// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions.spec.md
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.collectOpenAgentChatRefreshSnapshot
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.normalizeConcreteAgentSessionThreadId
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT
import com.intellij.agent.workbench.sessions.core.providers.describeScope
import com.intellij.agent.workbench.sessions.core.providers.isUnscoped
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.hasProviderSnapshot
import com.intellij.agent.workbench.sessions.model.mergeAgentSessionThreadsForDisplay
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<AgentSessionProviderRefreshRunner>()

internal class AgentSessionProviderRefreshRunner(
  private val refreshMutex: Mutex,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val stateStore: AgentSessionsStateStore,
  private val contentRepository: AgentSessionContentRepository,
  private val archiveSuppressionSupport: AgentSessionArchiveSuppressionSupport,
  private val refreshSupportProvider: (AgentSessionProvider) -> AgentSessionThreadRebindSupport?,
  private val resolveProviderWarningMessage: (AgentSessionProvider, Throwable) -> String,
  private val openAgentChatSnapshotProvider: suspend () -> AgentChatOpenTabsRefreshSnapshot = ::collectOpenAgentChatRefreshSnapshot,
  private val presentationModel: AgentSessionThreadPresentationModel,
) {
  suspend fun refreshLoadedProviderThreads(
    provider: AgentSessionProvider,
    refreshId: Long,
    updateEvent: AgentSessionSourceUpdateEvent,
  ) {
    refreshMutex.withLock {
      LOG.debug {
        "Starting provider refresh id=$refreshId provider=${provider.value} " +
        "(${updateEvent.describeScope()}, sourceUpdate=${updateEvent.type.name.lowercase()})"
      }
      val source = sessionSourcesProvider().firstOrNull { it.provider == provider } ?: return
      val openChatSnapshot = openAgentChatSnapshotProvider()
      val selectedIdentity = openChatSnapshot.selectedChatThreadIdentity
      source.setActiveThreadId(
        if (selectedIdentity != null && selectedIdentity.first == provider) selectedIdentity.second else null
      )
      val stateSnapshot = stateStore.snapshot()
      val knownThreadIdsByPath = collectLoadedProviderThreadIdsByPath(stateSnapshot, provider)
      val targetPaths = resolveTargetPaths(
        state = stateSnapshot,
        openChatSnapshot = openChatSnapshot,
        provider = provider,
        updateEvent = updateEvent,
      )

      if (targetPaths.isEmpty()) {
        LOG.debug {
          "Provider refresh id=$refreshId provider=${provider.value} skipped (no target paths, ${updateEvent.describeScope()})"
        }
        return
      }

      LOG.debug {
        "Provider refresh id=$refreshId provider=${provider.value} targetPaths=${targetPaths.size}"
      }

      val outcomes = LinkedHashMap<String, ProviderRefreshOutcome>(targetPaths.size)
      try {
        val refreshResult = source.refreshThreads(
          AgentSessionSourceRefreshRequest(
            paths = targetPaths.toList(),
            threadIds = updateEvent.threadIds.orEmpty(),
            updateEvent = updateEvent,
          )
        )
        applyRefreshResultToOutcomes(
          provider = provider,
          targetPaths = targetPaths,
          refreshResult = refreshResult,
          outcomes = outcomes,
        )
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.warn("Failed to refresh ${provider.value} sessions for ${targetPaths.size} paths", e)
        for (path in targetPaths) {
          outcomes[path] = ProviderRefreshOutcome(
            warningMessage = resolveProviderWarningMessage(provider, e),
          )
        }
      }

      val refreshSupport = refreshSupportProvider(provider)
      val pendingTabsSnapshotByPath = openChatSnapshot.pendingTabsByPath(provider)
      val pendingTabsForRebindByPath = if (refreshSupport?.canBindPendingOpenChatTabs == true) pendingTabsSnapshotByPath else emptyMap()
      val concreteTabsSnapshotByPath = openChatSnapshot.concreteTabsAwaitingNewThreadRebindByPath(provider)

      val hintThreadIdsByPath = refreshSupport?.collectRefreshHintThreadIdsByPath(
        targetPaths = targetPaths,
        outcomes = outcomes,
        knownThreadIdsByPath = knownThreadIdsByPath,
        pendingTabsByPath = pendingTabsForRebindByPath,
        openConcreteThreadIdentitiesByPath = openChatSnapshot.concreteThreadIdentitiesByPath,
      ) ?: emptyMap()

      val refreshHintPaths = if (refreshSupport != null) {
        targetPaths
          .asSequence()
          .filter { path ->
            hintThreadIdsByPath.containsKey(path) ||
            pendingTabsForRebindByPath[path]?.isNotEmpty() == true
          }
          .toCollection(LinkedHashSet())
      }
      else {
        emptySet()
      }

      val refreshHintsByPath = prefetchRefreshHints(
        source = source,
        provider = provider,
        outcomes = outcomes,
        hintThreadIdsByPath = hintThreadIdsByPath,
        refreshHintPaths = refreshHintPaths,
        forcedThreadIds = updateEvent.threadIds,
      )

      if (refreshSupport != null && refreshHintsByPath.isNotEmpty()) {
        refreshSupport.applyPresentationHints(
          outcomes = outcomes,
          refreshHintsByPath = refreshHintsByPath,
        )
      }

      val allowedNewThreadIdsByPath = if (refreshSupport != null) {
        calculateNewProviderThreadIdsByPath(
          provider = provider,
          outcomes = outcomes,
          knownThreadIdsByPath = knownThreadIdsByPath,
        )
      }
      else {
        null
      }

      refreshSupport?.clearStaleConcreteOpenChatNewThreadRebindAnchors(
        refreshId = refreshId,
        concreteTabsByPath = concreteTabsSnapshotByPath,
      )

      refreshSupport?.bindPendingOpenChatTabs(
        outcomes = outcomes,
        refreshId = refreshId,
        allowedThreadIdsByPath = allowedNewThreadIdsByPath,
        refreshHintsByPath = refreshHintsByPath,
        pendingTabsByPath = pendingTabsForRebindByPath,
      )

      syncOpenChatTabPresentation(provider = provider, outcomes = outcomes, refreshId = refreshId)

      applyProviderOutcomesToState(
        provider = provider,
        refreshId = refreshId,
        logLabel = "Provider refresh",
        outcomes = outcomes,
      )
      contentRepository.syncWarmSnapshotsFromRuntime(targetPaths)
      LOG.debug { "Finished provider refresh id=$refreshId provider=${provider.value}" }
    }
  }

  suspend fun refreshLoadedProviderHints(
    provider: AgentSessionProvider,
    refreshId: Long,
    updateEvent: AgentSessionSourceUpdateEvent,
  ) {
    refreshMutex.withLock {
      LOG.debug {
        "Starting provider hint refresh id=$refreshId provider=${provider.value} (${updateEvent.describeScope()})"
      }
      val source = sessionSourcesProvider().firstOrNull { it.provider == provider } ?: return
      val openChatSnapshot = openAgentChatSnapshotProvider()
      val selectedIdentity = openChatSnapshot.selectedChatThreadIdentity
      source.setActiveThreadId(
        if (selectedIdentity != null && selectedIdentity.first == provider) selectedIdentity.second else null
      )
      val stateSnapshot = stateStore.snapshot()
      val targetPaths = resolveTargetPaths(
        state = stateSnapshot,
        openChatSnapshot = openChatSnapshot,
        provider = provider,
        updateEvent = updateEvent,
      )

      if (targetPaths.isEmpty()) {
        LOG.debug {
          "Provider hint refresh id=$refreshId provider=${provider.value} skipped (no target paths, ${updateEvent.describeScope()})"
        }
        return
      }

      val refreshSupport = refreshSupportProvider(provider)
      val pendingTabsSnapshotByPath = openChatSnapshot.pendingTabsByPath(provider)
      val concreteTabsSnapshotByPath = openChatSnapshot.concreteTabsAwaitingNewThreadRebindByPath(provider)

      val outcomes = collectCurrentProviderOutcomes(
        state = stateSnapshot,
        targetPaths = targetPaths,
        provider = provider,
      )
      val knownThreadIdsByPath = collectLoadedProviderThreadIdsByPath(stateSnapshot, provider)
      val hintThreadIdsByPath = refreshSupport?.collectRefreshHintThreadIdsByPath(
        targetPaths = targetPaths,
        outcomes = outcomes,
        knownThreadIdsByPath = knownThreadIdsByPath,
        pendingTabsByPath = pendingTabsSnapshotByPath,
        openConcreteThreadIdentitiesByPath = openChatSnapshot.concreteThreadIdentitiesByPath,
      ) ?: knownThreadIdsByPath.filterKeys { path -> path in targetPaths }

      val refreshHintPaths = targetPaths
        .asSequence()
        .filter { path ->
          hintThreadIdsByPath.containsKey(path) ||
          pendingTabsSnapshotByPath[path]?.isNotEmpty() == true
        }
        .toCollection(LinkedHashSet())

      val refreshHintsByPath = prefetchRefreshHints(
        source = source,
        provider = provider,
        outcomes = outcomes,
        hintThreadIdsByPath = hintThreadIdsByPath,
        refreshHintPaths = refreshHintPaths,
        forcedThreadIds = updateEvent.threadIds,
      )

      if (refreshHintsByPath.isNotEmpty()) {
        applyRefreshHintsToOutcomes(
          provider = provider,
          outcomes = outcomes,
          refreshHintsByPath = refreshHintsByPath,
        )
      }

      val missingThreadSnapshotPaths = collectMissingProviderThreadPaths(
        outcomes = outcomes,
        targetPaths = targetPaths,
        provider = provider,
        threadIds = updateEvent.threadIds,
      )
      if (missingThreadSnapshotPaths.isNotEmpty()) {
        applyThreadScopedSnapshotForMissingHints(
          source = source,
          provider = provider,
          updateEvent = updateEvent,
          targetPaths = missingThreadSnapshotPaths,
          outcomes = outcomes,
        )
      }
      refreshSupport?.clearStaleConcreteOpenChatNewThreadRebindAnchors(
        refreshId = refreshId,
        concreteTabsByPath = concreteTabsSnapshotByPath,
      )

      refreshSupport?.bindPendingOpenChatTabs(
        outcomes = outcomes,
        refreshId = refreshId,
        allowedThreadIdsByPath = null,
        refreshHintsByPath = refreshHintsByPath,
        pendingTabsByPath = pendingTabsSnapshotByPath,
      )

      syncOpenChatTabPresentation(provider = provider, outcomes = outcomes, refreshId = refreshId)

      applyProviderOutcomesToState(
        provider = provider,
        refreshId = refreshId,
        logLabel = "Provider hint refresh",
        outcomes = outcomes,
      )
      if (refreshHintsByPath.isNotEmpty()) {
        contentRepository.syncWarmSnapshotsFromRuntime(targetPaths)
      }
      LOG.debug { "Finished provider hint refresh id=$refreshId provider=${provider.value}" }
    }
  }

  private suspend fun applyThreadScopedSnapshotForMissingHints(
    source: AgentSessionSource,
    provider: AgentSessionProvider,
    updateEvent: AgentSessionSourceUpdateEvent,
    targetPaths: Set<String>,
    outcomes: MutableMap<String, ProviderRefreshOutcome>,
  ) {
    try {
      val refreshResult = source.refreshThreads(
        AgentSessionSourceRefreshRequest(
          paths = targetPaths.toList(),
          threadIds = updateEvent.threadIds.orEmpty(),
          updateEvent = updateEvent,
        )
      )
      applyRefreshResultToOutcomes(
        provider = provider,
        targetPaths = targetPaths,
        refreshResult = refreshResult,
        outcomes = outcomes,
      )
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to refresh ${provider.value} missing hinted sessions for ${targetPaths.size} paths", e)
      for (path in targetPaths) {
        outcomes[path] = ProviderRefreshOutcome(
          warningMessage = resolveProviderWarningMessage(provider, e),
        )
      }
    }
  }

  private suspend fun prefetchRefreshHints(
    source: AgentSessionSource,
    provider: AgentSessionProvider,
    outcomes: Map<String, ProviderRefreshOutcome>,
    hintThreadIdsByPath: Map<String, Set<String>>,
    refreshHintPaths: Set<String>,
    forcedThreadIds: Set<String>?,
  ): Map<String, AgentSessionRefreshHints> {
    if (refreshHintPaths.isEmpty()) {
      return emptyMap()
    }
    val refreshThreadSeedsByPath = buildRefreshThreadSeedsByPath(
      provider = provider,
      outcomes = outcomes,
      hintThreadIdsByPath = hintThreadIdsByPath.filterKeys { path -> path in refreshHintPaths },
      forcedThreadIds = forcedThreadIds,
    )
    return try {
      source.prefetchRefreshHints(
        paths = refreshHintPaths.toList(),
        refreshThreadSeedsByPath = refreshThreadSeedsByPath,
      )
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to fetch ${provider.value} refresh hints", e)
      emptyMap()
    }
  }

  private fun applyRefreshResultToOutcomes(
    provider: AgentSessionProvider,
    targetPaths: Set<String>,
    refreshResult: AgentSessionSourceRefreshResult,
    outcomes: MutableMap<String, ProviderRefreshOutcome>,
  ) {
    for ((path, threads) in refreshResult.completeThreadsByPath) {
      outcomes[path] = ProviderRefreshOutcome(
        threads = archiveSuppressionSupport.apply(path = path, provider = provider, threads = threads),
        isComplete = true,
        removedThreadIds = refreshResult.removedThreadIdsByPath[path].orEmpty(),
      )
    }
    for ((path, threads) in refreshResult.partialThreadsByPath) {
      val existing = outcomes[path]
      val removedThreadIds = linkedSetOf<String>().apply {
        addAll(existing?.removedThreadIds.orEmpty())
        addAll(refreshResult.removedThreadIdsByPath[path].orEmpty())
      }
      outcomes[path] = ProviderRefreshOutcome(
        threads = archiveSuppressionSupport.apply(path = path, provider = provider, threads = threads),
        isComplete = false,
        removedThreadIds = removedThreadIds,
      )
    }
    for ((path, removedThreadIds) in refreshResult.removedThreadIdsByPath) {
      if (path in outcomes) continue
      outcomes[path] = ProviderRefreshOutcome(
        threads = emptyList(),
        isComplete = false,
        removedThreadIds = removedThreadIds,
      )
    }
    for ((path, failure) in refreshResult.failuresByPath) {
      if (path !in targetPaths) continue
      LOG.warn("Failed to refresh ${provider.value} sessions for $path", failure)
      outcomes[path] = ProviderRefreshOutcome(
        warningMessage = resolveProviderWarningMessage(provider, failure),
      )
    }
  }

  private fun applyProviderOutcomesToState(
    provider: AgentSessionProvider,
    refreshId: Long,
    logLabel: String,
    outcomes: Map<String, ProviderRefreshOutcome>,
  ) {
    stateStore.update { state ->
      var changed = false
      val nextProjects = state.projects.map { project ->
        val shouldApplyProjectOutcome = project.isOpen || project.hasProviderSnapshot(provider)
        val updatedProject = if (shouldApplyProjectOutcome) {
          val outcome = outcomes[project.path]
          if (outcome != null) {
            val refreshedProject = project.withProviderRefreshOutcome(provider, outcome)
            if (refreshedProject != project) {
              changed = true
            }
            refreshedProject
          }
          else {
            project
          }
        }
        else {
          project
        }

        val nextWorktrees = updatedProject.worktrees.map { worktree ->
          val shouldApplyWorktreeOutcome =
            worktree.isOpen || worktree.hasProviderSnapshot(provider)
          if (!shouldApplyWorktreeOutcome) return@map worktree
          val outcome = outcomes[worktree.path] ?: return@map worktree
          val refreshedWorktree = worktree.withProviderRefreshOutcome(provider, outcome)
          if (refreshedWorktree != worktree) {
            changed = true
          }
          refreshedWorktree
        }

        if (nextWorktrees == updatedProject.worktrees) {
          updatedProject
        }
        else {
          updatedProject.copy(worktrees = nextWorktrees)
        }
      }

      if (!changed) {
        LOG.debug {
          "$logLabel id=$refreshId provider=${provider.value} finished without state changes (outcomes=${outcomes.size})"
        }
        state
      }
      else {
        LOG.debug {
          "$logLabel id=$refreshId provider=${provider.value} applied state changes (outcomes=${outcomes.size})"
        }
        state.copy(
          projects = nextProjects,
          lastUpdatedAt = System.currentTimeMillis(),
        )
      }
    }
  }

  private fun syncOpenChatTabPresentation(
    provider: AgentSessionProvider,
    outcomes: Map<String, ProviderRefreshOutcome>,
    refreshId: Long,
  ) {
    // `refreshedPaths` is the authoritative refresh scope for shared-presentation eviction.
    // Warning-only outcomes intentionally stay out of this set because state keeps the last
    // concrete threads for those paths, so evicting presentation there would regress open tabs
    // back to bootstrap titles/activity without a real provider snapshot.
    val authoritativePaths = LinkedHashSet<String>()
    val threadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      if (outcome.isComplete) {
        authoritativePaths += path
      }
      threadsByPath[path] = threads
    }

    val changeSet = presentationModel.updateProviderSnapshot(
      provider = provider,
      authoritativePaths = authoritativePaths,
      threadsByPath = threadsByPath,
    )
    val updatedPresentations = changeSet.changedKeys.size + changeSet.removedKeys.size

    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} synchronized thread presentation model " +
      "(updatedPresentations=$updatedPresentations)"
    }
  }

  private fun calculateNewProviderThreadIdsByPath(
    provider: AgentSessionProvider,
    outcomes: Map<String, ProviderRefreshOutcome>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, Set<String>> {
    val result = LinkedHashMap<String, Set<String>>()
    for ((path, outcome) in outcomes) {
      if (!knownThreadIdsByPath.containsKey(path)) {
        continue
      }
      val knownThreadIds = knownThreadIdsByPath[path].orEmpty()
      val newThreadIds = outcome.threads
        .orEmpty()
        .asSequence()
        .filter { outcome.isComplete }
        .filter { thread -> thread.provider == provider && thread.id !in knownThreadIds }
        .map { thread -> thread.id }
        .toCollection(LinkedHashSet())
      result[path] = newThreadIds
    }
    return result
  }
}

private fun resolveTargetPaths(
  state: AgentSessionsState,
  openChatSnapshot: AgentChatOpenTabsRefreshSnapshot,
  provider: AgentSessionProvider,
  updateEvent: AgentSessionSourceUpdateEvent,
): Set<String> {
  val fullTargetPaths = collectFullRefreshTargetPaths(state, openChatSnapshot)
  if (updateEvent.isUnscoped()) {
    return if (updateEvent.type == AgentSessionSourceUpdate.THREADS_CHANGED) {
      fullTargetPaths
    }
    else {
      emptySet()
    }
  }

  val targetPaths = LinkedHashSet<String>()
  updateEvent.scopedPaths?.let { scopedPaths ->
    val resolvedScopedPaths = resolveScopedPaths(scopedPaths = scopedPaths, knownTargetPaths = fullTargetPaths)
    targetPaths.addAll(resolvedScopedPaths.orEmpty())
  }
  targetPaths.addAll(resolvePathsForThreadIds(state, openChatSnapshot, provider, updateEvent.threadIds))
  if (targetPaths.isNotEmpty()) {
    return targetPaths
  }

  if (updateEvent.threadIds?.isNotEmpty() == true || updateEvent.scopedPaths != null) {
    return emptySet()
  }

  return if (updateEvent.type == AgentSessionSourceUpdate.THREADS_CHANGED) {
    fullTargetPaths
  }
  else {
    emptySet()
  }
}

private fun resolveScopedPaths(scopedPaths: Set<String>, knownTargetPaths: Set<String>): Set<String>? {
  if (scopedPaths.isEmpty()) {
    return emptySet()
  }

  val knownPathsByVariant = buildKnownPathsByVariant(knownTargetPaths)
  val resolvedPaths = LinkedHashSet<String>()
  for (scopedPath in scopedPaths) {
    val matches = collectPathVariants(scopedPath)
      .asSequence()
      .flatMap { variant -> knownPathsByVariant[variant].orEmpty().asSequence() }
      .toCollection(LinkedHashSet())
    if (matches.isEmpty()) {
      return null
    }
    resolvedPaths.addAll(matches)
  }
  return resolvedPaths
}

private fun buildKnownPathsByVariant(paths: Set<String>): Map<String, Set<String>> {
  val result = LinkedHashMap<String, LinkedHashSet<String>>()
  for (path in paths) {
    for (variant in collectPathVariants(path)) {
      result.getOrPut(variant) { LinkedHashSet() }.add(path)
    }
  }
  return result
}

private fun collectPathVariants(path: String): Set<String> {
  val variants = LinkedHashSet<String>()
  fun addPathVariant(value: String?) {
    val normalized = value?.let(::normalizeAgentWorkbenchPath)?.takeIf { it.isNotBlank() } ?: return
    variants.add(normalized)
  }

  fun addPathVariant(value: Path?) {
    val normalizedPath = value?.normalize() ?: return
    addPathVariant(normalizedPath.invariantSeparatorsPathString)
    runCatching { normalizedPath.toRealPath().invariantSeparatorsPathString }.getOrNull()?.let(::addPathVariant)
  }

  addPathVariant(path)
  val parsedPath = parseAgentWorkbenchPathOrNull(normalizeAgentWorkbenchPath(path)) ?: return variants
  addPathVariant(parsedPath)
  addPathVariant(projectDirectoryVariant(parsedPath))
  return variants
}

private fun projectDirectoryVariant(path: Path): Path? {
  val fileName = path.fileName?.toString() ?: return null
  val parentName = path.parent?.fileName?.toString()
  return when {
    fileName == ".idea" -> path.parent
    parentName == ".idea" -> path.parent?.parent
    fileName.endsWith(".ipr", ignoreCase = true) -> path.parent
    fileName.endsWith(".iws", ignoreCase = true) -> path.parent
    else -> null
  }
}

private fun resolvePathsForThreadIds(
  state: AgentSessionsState,
  openChatSnapshot: AgentChatOpenTabsRefreshSnapshot,
  provider: AgentSessionProvider,
  threadIds: Set<String>?,
): Set<String> {
  if (threadIds == null) {
    return emptySet()
  }

  val resolvedPaths = LinkedHashSet<String>()
  state.forEachPathContent { content ->
    if (content.hasProviderSnapshot(provider) && content.threads.any { thread -> thread.matchesProviderThreadIds(provider, threadIds) }) {
      resolvedPaths.add(content.path)
    }
  }

  val threadIdentities = threadIds
    .asSequence()
    .map { threadId -> buildAgentSessionIdentity(provider, threadId) }
    .toCollection(LinkedHashSet())
  for ((path, identities) in openChatSnapshot.concreteThreadIdentitiesByPath) {
    if (identities.any { identity -> identity in threadIdentities }) {
      resolvedPaths.add(path)
    }
  }
  return resolvedPaths
}

private fun AgentSessionThread.matchesProviderThreadIds(provider: AgentSessionProvider, threadIds: Set<String>): Boolean {
  return this.provider == provider && (id in threadIds || subAgents.any { subAgent -> subAgent.id in threadIds })
}

private fun collectFullRefreshTargetPaths(
  state: AgentSessionsState,
  openChatSnapshot: AgentChatOpenTabsRefreshSnapshot,
): Set<String> {
  val targetPaths = LinkedHashSet<String>()
  targetPaths.addAll(collectOpenOrLoadedPaths(state))
  targetPaths.addAll(openChatSnapshot.openProjectPaths)
  return targetPaths
}

internal data class ProviderRefreshOutcome(
  @JvmField val threads: List<AgentSessionThread>? = null,
  @JvmField val isComplete: Boolean = true,
  @JvmField val removedThreadIds: Set<String> = emptySet(),
  @JvmField val warningMessage: String? = null,
)

private fun collectOpenOrLoadedPaths(state: AgentSessionsState): List<String> {
  val paths = LinkedHashSet<String>()
  state.forEachPathContent { content ->
    if (content.isOpen || content.hasAnyProviderSnapshot) {
      paths.add(content.path)
    }
  }
  return ArrayList(paths)
}

private fun collectLoadedProviderThreadIdsByPath(
  state: AgentSessionsState,
  provider: AgentSessionProvider,
): Map<String, Set<String>> {
  val result = LinkedHashMap<String, Set<String>>()
  state.forEachPathContent { content ->
    if (content.hasProviderSnapshot(provider)) {
      result[content.path] = content.threads.normalizedProviderThreadIds(provider)
    }
  }
  return result
}

private fun collectCurrentProviderOutcomes(
  state: AgentSessionsState,
  targetPaths: Set<String>,
  provider: AgentSessionProvider,
): MutableMap<String, ProviderRefreshOutcome> {
  val outcomes = LinkedHashMap<String, ProviderRefreshOutcome>()
  state.forEachPathContent { content ->
    if (content.path in targetPaths && content.hasProviderSnapshot(provider)) {
      outcomes[content.path] = ProviderRefreshOutcome(
        threads = content.threads.filter { thread -> thread.provider == provider },
        isComplete = false,
      )
    }
  }
  return outcomes
}

private fun List<AgentSessionThread>.normalizedProviderThreadIds(provider: AgentSessionProvider): Set<String> {
  return asSequence()
    .filter { thread -> thread.provider == provider }
    .map { thread -> thread.id }
    .mapNotNull(::normalizeConcreteAgentSessionThreadId)
    .toCollection(LinkedHashSet())
}

private fun collectMissingProviderThreadPaths(
  outcomes: Map<String, ProviderRefreshOutcome>,
  targetPaths: Set<String>,
  provider: AgentSessionProvider,
  threadIds: Set<String>?,
): Set<String> {
  if (threadIds.isNullOrEmpty()) {
    return emptySet()
  }
  val missingPaths = LinkedHashSet<String>()
  for (path in targetPaths) {
    val hasThread = outcomes[path]
      ?.threads
      .orEmpty()
      .any { thread -> thread.matchesProviderThreadIds(provider, threadIds) }
    if (!hasThread) {
      missingPaths.add(path)
    }
  }
  return missingPaths
}

private fun applyRefreshHintsToOutcomes(
  provider: AgentSessionProvider,
  outcomes: MutableMap<String, ProviderRefreshOutcome>,
  refreshHintsByPath: Map<String, AgentSessionRefreshHints>,
) {
  if (outcomes.isEmpty() || refreshHintsByPath.isEmpty()) {
    return
  }

  for ((path, outcome) in ArrayList(outcomes.entries)) {
    val threads = outcome.threads ?: continue
    val refreshHints = refreshHintsByPath[path] ?: continue
    val presentationUpdatesByThreadId = refreshHints.resolvePresentationUpdatesByThreadId()
    if (presentationUpdatesByThreadId.isEmpty()) {
      continue
    }

    var changed = false
    val updatedThreads = threads.map { thread ->
      if (thread.provider != provider) {
        return@map thread
      }
      val presentationUpdate = presentationUpdatesByThreadId[thread.id] ?: return@map thread
      val resolvedUpdate = resolveAgentThreadPresentationUpdate(thread = thread, presentationUpdate = presentationUpdate)
      if (resolvedUpdate.title == thread.title && resolvedUpdate.activityReport == thread.activityReport && resolvedUpdate.updatedAt == thread.updatedAt) {
        return@map thread
      }
      changed = true
      thread.copy(title = resolvedUpdate.title, activityReport = resolvedUpdate.activityReport, updatedAt = resolvedUpdate.updatedAt)
    }
    if (changed) {
      outcomes[path] = outcome.copy(threads = updatedThreads)
    }
  }
}

private fun buildRefreshThreadSeedsByPath(
  provider: AgentSessionProvider,
  outcomes: Map<String, ProviderRefreshOutcome>,
  hintThreadIdsByPath: Map<String, Set<String>>,
  forcedThreadIds: Set<String>?,
): Map<String, Set<AgentSessionRefreshThreadSeed>> {
  if (hintThreadIdsByPath.isEmpty()) {
    return emptyMap()
  }

  val forcedThreadIds = forcedThreadIds.orEmpty().asSequence()
    .mapNotNull(::normalizeConcreteAgentSessionThreadId)
    .toCollection(LinkedHashSet())
  val result = LinkedHashMap<String, Set<AgentSessionRefreshThreadSeed>>(hintThreadIdsByPath.size)
  for ((path, threadIds) in hintThreadIdsByPath) {
    val updatedAtByThreadId = Object2LongOpenHashMap<String>()
    updatedAtByThreadId.defaultReturnValue(UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT)
    outcomes[path]
      ?.threads
      .orEmpty()
      .asSequence()
      .filter { thread -> thread.provider == provider }
      .forEach { thread ->
        updatedAtByThreadId.put(thread.id, thread.updatedAt)
      }

    val seeds = LinkedHashSet<AgentSessionRefreshThreadSeed>(threadIds.size)
    threadIds.forEach { threadId ->
      val concreteThreadId = normalizeConcreteAgentSessionThreadId(threadId) ?: return@forEach
      seeds.add(
        AgentSessionRefreshThreadSeed(
          threadId = concreteThreadId,
          updatedAt = updatedAtByThreadId.getLong(concreteThreadId),
          forceRefresh = concreteThreadId in forcedThreadIds,
        )
      )
    }
    result[path] = seeds
  }
  return result
}

private fun AgentProjectSessions.withProviderRefreshOutcome(
  provider: AgentSessionProvider,
  outcome: ProviderRefreshOutcome,
): AgentProjectSessions {
  val mergedThreads = outcome.threads?.let { threads ->
    if (outcome.isComplete) {
      mergeThreadsForProvider(this.threads, provider, threads)
    }
    else {
      mergeThreadUpdatesForProvider(this.threads, provider, threads, outcome.removedThreadIds)
    }
  } ?: this.threads
  val providerLoadMetadata = updateProviderLoadMetadata(
    currentProviderLoadStates = this.providerLoadStates,
    currentProvidersWithUnknownThreadCount = this.providersWithUnknownThreadCount,
    provider = provider,
    providerLoadState = outcome.providerLoadState,
    providerHasUnknownThreadCount = if (outcome.warningMessage == null) null else false,
  )
  return copy(
    threads = mergedThreads,
    providerWarnings = replaceProviderWarning(this.providerWarnings, provider, outcome.warningMessage),
    providerLoadStates = providerLoadMetadata.providerLoadStates,
    providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
  )
}

private fun AgentWorktree.withProviderRefreshOutcome(
  provider: AgentSessionProvider,
  outcome: ProviderRefreshOutcome,
): AgentWorktree {
  val mergedThreads = outcome.threads?.let { threads ->
    if (outcome.isComplete) {
      mergeThreadsForProvider(this.threads, provider, threads)
    }
    else {
      mergeThreadUpdatesForProvider(this.threads, provider, threads, outcome.removedThreadIds)
    }
  } ?: this.threads
  val providerLoadMetadata = updateProviderLoadMetadata(
    currentProviderLoadStates = this.providerLoadStates,
    currentProvidersWithUnknownThreadCount = this.providersWithUnknownThreadCount,
    provider = provider,
    providerLoadState = outcome.providerLoadState,
    providerHasUnknownThreadCount = if (outcome.warningMessage == null) null else false,
  )
  return copy(
    threads = mergedThreads,
    providerWarnings = replaceProviderWarning(this.providerWarnings, provider, outcome.warningMessage),
    providerLoadStates = providerLoadMetadata.providerLoadStates,
    providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
  )
}

private val ProviderRefreshOutcome.providerLoadState: AgentSessionProviderLoadState?
  get() = when {
    warningMessage != null -> AgentSessionProviderLoadState.FAILED
    threads != null && isComplete -> AgentSessionProviderLoadState.LOADED
    else -> null
  }

private fun replaceProviderWarning(
  warnings: List<AgentSessionProviderWarning>,
  provider: AgentSessionProvider,
  warningMessage: String?,
): List<AgentSessionProviderWarning> {
  val withoutProvider = warnings.filterNot { it.provider == provider }
  return if (warningMessage == null) {
    withoutProvider
  }
  else {
    withoutProvider + AgentSessionProviderWarning(provider = provider, message = warningMessage)
  }
}

private fun mergeThreadsForProvider(
  existingThreads: List<AgentSessionThread>,
  provider: AgentSessionProvider,
  newProviderThreads: List<AgentSessionThread>,
): List<AgentSessionThread> {
  val providerThreadsWithPreservedCosts = preserveThreadCosts(existingThreads, newProviderThreads)
  val mergedThreads = ArrayList<AgentSessionThread>(existingThreads.size + newProviderThreads.size)
  existingThreads.filterTo(mergedThreads) { it.provider != provider }
  mergedThreads.addAll(providerThreadsWithPreservedCosts)
  return mergeAgentSessionThreadsForDisplay(existingThreads, mergedThreads)
}

private fun mergeThreadUpdatesForProvider(
  existingThreads: List<AgentSessionThread>,
  provider: AgentSessionProvider,
  updatedProviderThreads: List<AgentSessionThread>,
  removedThreadIds: Set<String>,
): List<AgentSessionThread> {
  val updatesById = LinkedHashMap<String, AgentSessionThread>(updatedProviderThreads.size)
  for (thread in updatedProviderThreads) {
    if (thread.provider == provider) {
      updatesById[thread.id] = thread
    }
  }

  val mergedThreads = ArrayList<AgentSessionThread>(existingThreads.size + updatesById.size)
  for (thread in existingThreads) {
    if (thread.provider != provider) {
      mergedThreads.add(thread)
      continue
    }
    if (thread.id in removedThreadIds) {
      continue
    }
    val update = updatesById.remove(thread.id)
    mergedThreads.add(if (update == null) thread else mergeThreadUpdate(existing = thread, update = update))
  }
  mergedThreads.addAll(updatesById.values)
  return mergeAgentSessionThreadsForDisplay(existingThreads, mergedThreads)
}

private fun mergeThreadUpdate(existing: AgentSessionThread, update: AgentSessionThread): AgentSessionThread {
  val mergedThread = if (update.subAgents.isEmpty()) {
    if (existing.subAgents.isEmpty()) update else update.copy(subAgents = existing.subAgents)
  }
  else if (existing.subAgents.isEmpty()) {
    update
  }
  else {
    val mergedSubAgents = LinkedHashMap<String, AgentSubAgent>(
      existing.subAgents.size + update.subAgents.size
    )
    existing.subAgents.forEach { subAgent -> mergedSubAgents[subAgent.id] = subAgent }
    update.subAgents.forEach { subAgent -> mergedSubAgents[subAgent.id] = subAgent }
    update.copy(subAgents = ArrayList(mergedSubAgents.values))
  }

  return preserveThreadCost(existing = existing, updated = mergedThread)
}
