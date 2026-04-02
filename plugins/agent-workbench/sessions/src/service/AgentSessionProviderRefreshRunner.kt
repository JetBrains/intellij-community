// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.collectOpenAgentChatRefreshSnapshot
import com.intellij.agent.workbench.chat.updateOpenAgentChatTabPresentation
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        LOG.debug { "Provider refresh id=$refreshId provider=${provider.value} skipped (no target paths)" }
        return
      }

      LOG.debug {
        "Provider refresh id=$refreshId provider=${provider.value} targetPaths=${targetPaths.size}"
      }

      val prefetched = try {
        source.prefetchThreads(targetPaths.toList())
      }
      catch (_: Throwable) {
        emptyMap()
      }

      LOG.debug {
        "Provider refresh id=$refreshId provider=${provider.value} prefetchedPaths=${prefetched.size}"
      }

      val outcomes = LinkedHashMap<String, ProviderRefreshOutcome>(targetPaths.size)
      for (path in targetPaths) {
        val prefetchedThreads = prefetched[path]
        if (prefetchedThreads != null) {
          outcomes[path] = ProviderRefreshOutcome(
            threads = archiveSuppressionSupport.apply(path = path, provider = provider, threads = prefetchedThreads),
          )
          continue
        }

        try {
          outcomes[path] = ProviderRefreshOutcome(
            threads = archiveSuppressionSupport.apply(
              path = path,
              provider = provider,
              threads = source.listThreadsFromClosedProject(path),
            ),
          )
        }
        catch (e: Throwable) {
          if (e is CancellationException) throw e
          LOG.warn("Failed to refresh ${provider.value} sessions for $path", e)
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
        try {
          source.prefetchRefreshHints(
            paths = refreshHintPaths.toList(),
            knownThreadIdsByPath = hintThreadIdsByPath.filterKeys { it in refreshHintPaths },
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
    val titleByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, String>()
    val activityByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, AgentThreadActivity>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      for (thread in threads) {
        if (thread.provider != provider) continue
        val identityKey = path to buildAgentSessionIdentity(thread.provider, thread.id)
        titleByPathAndThreadIdentity[identityKey] = thread.title
        activityByPathAndThreadIdentity[identityKey] = thread.activity
      }
    }

    if (titleByPathAndThreadIdentity.isEmpty() && activityByPathAndThreadIdentity.isEmpty()) {
      return
    }

    val updatedTabs = openAgentChatTabPresentationUpdater(
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
  if (updateEvent.isUnscoped()) {
    return collectFullRefreshTargetPaths(state, openChatSnapshot)
  }

  val targetPaths = LinkedHashSet<String>()
  updateEvent.scopedPaths?.let(targetPaths::addAll)
  targetPaths.addAll(resolvePathsForThreadIds(state, openChatSnapshot, provider, updateEvent.threadIds))
  if (targetPaths.isNotEmpty()) {
    return targetPaths
  }

  return collectFullRefreshTargetPaths(state, openChatSnapshot)
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

private fun AgentProjectSessions.withProviderRefreshOutcome(
  provider: AgentSessionProvider,
  outcome: ProviderRefreshOutcome,
): AgentProjectSessions {
  val mergedThreads = outcome.threads?.let { threads ->
    mergeThreadsForProvider(this.threads, provider, threads)
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
    mergeThreadsForProvider(this.threads, provider, threads)
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
