// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.collectOpenAgentChatRefreshSnapshot
import com.intellij.agent.workbench.chat.updateOpenAgentChatTabPresentation
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT
import com.intellij.agent.workbench.sessions.core.providers.describeScope
import com.intellij.agent.workbench.sessions.core.providers.isUnscoped
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
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
  private val openAgentChatTabPresentationUpdater: suspend (
    AgentSessionProvider,
    Set<String>,
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = ::updateOpenAgentChatTabPresentation,
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
      val pendingCodexTabsSnapshotByPath = openChatSnapshot.pendingTabsByPath(provider)
      val concreteCodexTabsSnapshotByPath = openChatSnapshot.concreteTabsAwaitingNewThreadRebindByPath(provider)
      val openConcreteChatThreadIdentitiesByPath = LinkedHashMap<String, MutableSet<String>>()
      for ((path, identities) in openChatSnapshot.concreteThreadIdentitiesByPath) {
        openConcreteChatThreadIdentitiesByPath[path] = LinkedHashSet(identities)
      }

      val hintThreadIdsByPath = refreshSupport?.collectRefreshHintThreadIdsByPath(
        targetPaths = targetPaths,
        outcomes = outcomes,
        knownThreadIdsByPath = knownThreadIdsByPath,
        pendingTabsByPath = pendingCodexTabsSnapshotByPath,
        concreteTabsByPath = concreteCodexTabsSnapshotByPath,
        openConcreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPath,
      ) ?: emptyMap()

      val refreshHintPaths = if (refreshSupport != null) {
        targetPaths
          .asSequence()
          .filter { path ->
            hintThreadIdsByPath.containsKey(path) ||
            pendingCodexTabsSnapshotByPath[path]?.isNotEmpty() == true ||
            concreteCodexTabsSnapshotByPath[path]?.isNotEmpty() == true
          }
          .toCollection(LinkedHashSet())
      }
      else {
        emptySet()
      }

      val refreshHintsByPath = if (refreshHintPaths.isEmpty()) {
        emptyMap()
      }
      else {
        val refreshThreadSeedsByPath = buildRefreshThreadSeedsByPath(
          provider = provider,
          outcomes = outcomes,
          hintThreadIdsByPath = hintThreadIdsByPath.filterKeys { it in refreshHintPaths },
          forcedThreadIds = updateEvent.threadIds,
        )
        try {
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

      if (refreshSupport != null && refreshHintsByPath.isNotEmpty()) {
        refreshSupport.applyActivityHints(
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

      refreshSupport?.bindConcreteOpenChatTabsAwaitingNewThread(
        refreshId = refreshId,
        refreshHintsByPath = refreshHintsByPath,
        concreteTabsByPath = concreteCodexTabsSnapshotByPath,
        openConcreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPath,
      )

      val pendingCodexBindOutcome = refreshSupport?.bindPendingOpenChatTabs(
        outcomes = outcomes,
        refreshId = refreshId,
        allowedThreadIdsByPath = allowedNewThreadIdsByPath,
        refreshHintsByPath = refreshHintsByPath,
        pendingTabsByPath = pendingCodexTabsSnapshotByPath,
        openConcreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPath,
      )

      val pendingCodexTabsForProjectionByPath =
        pendingCodexBindOutcome?.pendingTabsForProjectionByPath ?: pendingCodexTabsSnapshotByPath

      syncOpenChatTabPresentation(provider = provider, outcomes = outcomes, refreshId = refreshId)

      val pendingProjectionPaths = refreshSupport?.mergePendingThreadsFromOpenTabs(
        outcomes = outcomes,
        targetPaths = targetPaths,
        refreshId = refreshId,
        pendingTabsByPath = pendingCodexTabsForProjectionByPath,
      ) ?: emptySet()

      stateStore.update { state ->
        var changed = false
        val nextProjects = state.projects.map { project ->
          val shouldApplyProjectOutcome = project.hasLoaded || project.path in pendingProjectionPaths
          val updatedProject = if (shouldApplyProjectOutcome) {
            val outcome = outcomes[project.path]
            if (outcome != null) {
              changed = true
              project.withProviderRefreshOutcome(provider, outcome)
            }
            else {
              project
            }
          }
          else {
            project
          }

          val nextWorktrees = updatedProject.worktrees.map { worktree ->
            val shouldApplyWorktreeOutcome = worktree.hasLoaded || worktree.path in pendingProjectionPaths
            if (!shouldApplyWorktreeOutcome) return@map worktree
            val outcome = outcomes[worktree.path] ?: return@map worktree
            changed = true
            worktree.withProviderRefreshOutcome(provider, outcome)
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
            "Provider refresh id=$refreshId provider=${provider.value} finished without state changes (outcomes=${outcomes.size})"
          }
          state
        }
        else {
          LOG.debug {
            "Provider refresh id=$refreshId provider=${provider.value} applied state changes (outcomes=${outcomes.size})"
          }
          state.copy(
            projects = nextProjects,
            lastUpdatedAt = System.currentTimeMillis(),
          )
        }
      }
      contentRepository.syncWarmSnapshotsFromRuntime(targetPaths)
      LOG.debug { "Finished provider refresh id=$refreshId provider=${provider.value}" }
    }
  }

  private suspend fun syncOpenChatTabPresentation(
    provider: AgentSessionProvider,
    outcomes: Map<String, ProviderRefreshOutcome>,
    refreshId: Long,
  ) {
    // `refreshedPaths` is the authoritative refresh scope for shared-presentation eviction.
    // Warning-only outcomes intentionally stay out of this set because state keeps the last
    // concrete threads for those paths, so evicting presentation there would regress open tabs
    // back to bootstrap titles/activity without a real provider snapshot.
    val authoritativePaths = LinkedHashSet<String>()
    val titleByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, String>()
    val activityByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, AgentThreadActivity>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      if (outcome.isComplete) {
        authoritativePaths += path
      }
      for (thread in threads) {
        if (thread.provider != provider) continue
        val identityKey = path to buildAgentSessionIdentity(thread.provider, thread.id)
        titleByPathAndThreadIdentity[identityKey] = thread.title
        activityByPathAndThreadIdentity[identityKey] = thread.activity
      }
    }

    val updatedTabs = openAgentChatTabPresentationUpdater(
      provider,
      authoritativePaths,
      titleByPathAndThreadIdentity,
      activityByPathAndThreadIdentity,
    )

    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} synchronized open chat tab presentation (updatedTabs=$updatedTabs)"
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
    return fullTargetPaths
  }

  val targetPaths = LinkedHashSet<String>()
  updateEvent.scopedPaths?.let { scopedPaths ->
    val resolvedScopedPaths = resolveScopedPaths(scopedPaths = scopedPaths, knownTargetPaths = fullTargetPaths)
    if (resolvedScopedPaths == null) {
      return fullTargetPaths
    }
    targetPaths.addAll(resolvedScopedPaths)
  }
  targetPaths.addAll(resolvePathsForThreadIds(state, openChatSnapshot, provider, updateEvent.threadIds))
  if (targetPaths.isNotEmpty()) {
    return targetPaths
  }

  if (updateEvent.threadIds?.isNotEmpty() == true) {
    return emptySet()
  }

  return fullTargetPaths
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
  for (project in state.projects) {
    if (project.hasLoaded && project.threads.any { thread -> thread.matchesProviderThreadIds(provider, threadIds) }) {
      resolvedPaths.add(project.path)
    }
    for (worktree in project.worktrees) {
      if (worktree.hasLoaded && worktree.threads.any { thread -> thread.matchesProviderThreadIds(provider, threadIds) }) {
        resolvedPaths.add(worktree.path)
      }
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
  targetPaths.addAll(collectLoadedPaths(state))
  targetPaths.addAll(openChatSnapshot.openProjectPaths)
  return targetPaths
}

internal data class ProviderRefreshOutcome(
  @JvmField val threads: List<AgentSessionThread>? = null,
  @JvmField val isComplete: Boolean = true,
  @JvmField val removedThreadIds: Set<String> = emptySet(),
  @JvmField val warningMessage: String? = null,
)

private fun collectLoadedPaths(state: AgentSessionsState): List<String> {
  val paths = LinkedHashSet<String>()
  for (project in state.projects) {
    if (project.hasLoaded) {
      paths.add(project.path)
    }
    for (worktree in project.worktrees) {
      if (worktree.hasLoaded) {
        paths.add(worktree.path)
      }
    }
  }
  return ArrayList(paths)
}

private fun collectLoadedProviderThreadIdsByPath(
  state: AgentSessionsState,
  provider: AgentSessionProvider,
): Map<String, Set<String>> {
  val result = LinkedHashMap<String, Set<String>>()
  for (project in state.projects) {
    if (project.hasLoaded) {
      result[project.path] = project.threads
        .asSequence()
        .filter { it.provider == provider }
        .map { it.id }
        .toCollection(LinkedHashSet())
    }
    for (worktree in project.worktrees) {
      if (!worktree.hasLoaded) {
        continue
      }
      result[worktree.path] = worktree.threads
        .asSequence()
        .filter { it.provider == provider }
        .map { it.id }
        .toCollection(LinkedHashSet())
    }
  }
  return result
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

  val forcedThreadIds = forcedThreadIds.orEmpty()
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
      seeds.add(
        AgentSessionRefreshThreadSeed(
          threadId = threadId,
          updatedAt = updatedAtByThreadId.getLong(threadId),
          forceRefresh = threadId in forcedThreadIds,
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
  return copy(
    threads = mergedThreads,
    providerWarnings = replaceProviderWarning(this.providerWarnings, provider, outcome.warningMessage),
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
  return copy(
    threads = mergedThreads,
    providerWarnings = replaceProviderWarning(this.providerWarnings, provider, outcome.warningMessage),
  )
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
  val mergedThreads = ArrayList<AgentSessionThread>(existingThreads.size + newProviderThreads.size)
  existingThreads.filterTo(mergedThreads) { it.provider != provider }
  mergedThreads.addAll(newProviderThreads)
  mergedThreads.sortByDescending { it.updatedAt }
  return mergedThreads
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
  mergedThreads.sortByDescending { it.updatedAt }
  return mergedThreads
}

private fun mergeThreadUpdate(existing: AgentSessionThread, update: AgentSessionThread): AgentSessionThread {
  if (update.subAgents.isEmpty()) {
    if (existing.subAgents.isEmpty()) return update
    return update.copy(subAgents = existing.subAgents)
  }
  if (existing.subAgents.isEmpty()) {
    return update
  }

  val mergedSubAgents = LinkedHashMap<String, AgentSubAgent>(
    existing.subAgents.size + update.subAgents.size
  )
  existing.subAgents.forEach { subAgent -> mergedSubAgents[subAgent.id] = subAgent }
  update.subAgents.forEach { subAgent -> mergedSubAgents[subAgent.id] = subAgent }
  return update.copy(subAgents = ArrayList(mergedSubAgents.values))
}
