// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.model.AgentArchivedSessionsState
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_THREAD_COUNT
import com.intellij.agent.workbench.sessions.util.agentSessionCliMissingMessageKey
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val ARCHIVED_LOG = logger<AgentArchivedSessionsService>()

@Suppress("DuplicatedCode")
@Service(Service.Level.APP)
class AgentArchivedSessionsService internal constructor(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    sessionSourcesProvider = AgentSessionProviders::sessionSources,
    projectEntriesProvider = AgentSessionProjectCatalog()::collectProjects,
  )

  private val mutableState = MutableStateFlow(AgentArchivedSessionsState())
  private val refreshMutex = Mutex()

  fun stateFlow(): StateFlow<AgentArchivedSessionsState> = mutableState.asStateFlow()

  fun snapshot(): AgentArchivedSessionsState = mutableState.value

  fun ensureLoaded() {
    val state = mutableState.value
    if (state.lastUpdatedAt == null && !state.projects.anyPathLoading()) {
      refresh()
    }
  }

  fun refreshIfLoaded() {
    if (mutableState.value.lastUpdatedAt != null) {
      refresh()
    }
  }

  fun refresh() {
    serviceScope.launch(Dispatchers.IO) {
      refreshMutex.withLock {
        refreshNow()
      }
    }
  }

  fun showMoreProjects() {
    mutableState.update { state ->
      state.copy(visibleClosedProjectCount = state.visibleClosedProjectCount + DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT)
    }
  }

  fun showMoreThreads(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    mutableState.update { state ->
      val current = state.visibleThreadCounts[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to current + DEFAULT_VISIBLE_THREAD_COUNT))
    }
  }

  fun ensureProjectVisible(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    mutableState.update { state ->
      val requiredClosedProjectCount = requiredVisibleClosedProjectCount(state.projects, normalizedPath) ?: return@update state
      if (requiredClosedProjectCount <= state.visibleClosedProjectCount) {
        state
      }
      else {
        state.copy(visibleClosedProjectCount = requiredClosedProjectCount)
      }
    }
  }

  fun ensureThreadVisible(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    mutableState.update { state ->
      val threadIndex = findThreadIndex(
        projects = state.projects,
        normalizedPath = normalizedPath,
        provider = provider,
        threadId = threadId,
      ) ?: return@update state
      val currentVisible = state.visibleThreadCounts[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
      if (threadIndex < currentVisible) {
        return@update state
      }
      val minVisible = threadIndex + 1
      var nextVisible = currentVisible
      while (nextVisible < minVisible) {
        nextVisible += DEFAULT_VISIBLE_THREAD_COUNT
      }
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to nextVisible))
    }
  }

  private suspend fun refreshNow() {
    val entries = projectEntriesProvider()
    val previous = mutableState.value
    val previousProjectsByPath = previous.projects.associateBy { project -> normalizeAgentWorkbenchPath(project.path) }
    val pathRequests = buildArchivedPathRequests(entries)
    val knownPaths = pathRequests.mapTo(LinkedHashSet()) { it.path }
    val initialProjects = buildInitialArchivedProjects(entries, previousProjectsByPath)

    mutableState.update { state ->
      state.copy(
        projects = initialProjects,
        visibleThreadCounts = retainVisibleThreadCounts(knownPaths, state.visibleThreadCounts),
      )
    }

    val resultsByPath = coroutineScope {
      pathRequests.map { request ->
        async {
          request.path to loadArchivedThreads(path = request.path, project = request.project)
        }
      }.awaitAll().toMap()
    }

    mutableState.update { state ->
      state.copy(
        projects = applyArchivedResults(state.projects, resultsByPath),
        lastUpdatedAt = System.currentTimeMillis(),
      )
    }
  }

  private suspend fun loadArchivedThreads(path: String, project: Project?): AgentSessionLoadResult {
    val sources = sessionSourcesProvider().filter { source -> source.supportsArchivedThreads }
    if (sources.isEmpty()) {
      return AgentSessionLoadResult(threads = emptyList())
    }
    val sourceResults = coroutineScope {
      sources.map { source ->
        async {
          val result = try {
            Result.success(
              if (project != null) {
                source.listArchivedThreadsFromOpenProject(path = path, project = project)
              }
              else {
                source.listArchivedThreadsFromClosedProject(path = path)
              }
            )
          }
          catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            ARCHIVED_LOG.warn("Failed to load archived ${source.provider.value} sessions for $path", throwable)
            Result.failure(throwable)
          }
          AgentSessionSourceLoadResult(
            provider = source.provider,
            result = result,
            hasUnknownTotal = false,
          )
        }
      }.awaitAll()
    }
    return mergeAgentSessionSourceLoadResults(
      sourceResults = sourceResults,
      resolveErrorMessage = ::resolveArchivedErrorMessage,
      resolveWarningMessage = ::resolveArchivedProviderWarningMessage,
    )
  }
}

private data class ArchivedPathRequest(
  @JvmField val path: String,
  @JvmField val project: Project?,
)

private fun buildArchivedPathRequests(entries: List<ProjectEntry>): List<ArchivedPathRequest> {
  val requests = ArrayList<ArchivedPathRequest>()
  val seenPaths = LinkedHashSet<String>()

  fun add(path: String, project: Project?) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    if (seenPaths.add(normalizedPath)) {
      requests.add(ArchivedPathRequest(path = normalizedPath, project = project))
    }
  }

  entries.forEach { entry ->
    add(entry.path, entry.project)
    entry.worktreeEntries.forEach { worktree -> add(worktree.path, worktree.project) }
  }
  return requests
}

private fun buildInitialArchivedProjects(
  entries: List<ProjectEntry>,
  previousProjectsByPath: Map<String, AgentProjectSessions>,
): List<AgentProjectSessions> {
  return entries.map { entry ->
    val normalizedPath = normalizeAgentWorkbenchPath(entry.path)
    val previous = previousProjectsByPath[normalizedPath]
    AgentProjectSessions(
      path = normalizedPath,
      name = entry.name,
      branch = entry.branch,
      buildSystemBadge = entry.buildSystemBadge,
      isOpen = entry.project != null,
      threads = previous?.threads.orEmpty(),
      isLoading = true,
      hasLoaded = previous?.hasLoaded ?: false,
      hasUnknownThreadCount = previous?.hasUnknownThreadCount ?: false,
      errorMessage = null,
      providerWarnings = emptyList(),
      worktrees = entry.worktreeEntries.map { worktree ->
        val normalizedWorktreePath = normalizeAgentWorkbenchPath(worktree.path)
        val previousWorktree = previous?.worktrees?.firstOrNull { candidate -> candidate.path == normalizedWorktreePath }
        AgentWorktree(
          path = normalizedWorktreePath,
          name = worktree.name,
          branch = worktree.branch,
          isOpen = worktree.project != null,
          threads = previousWorktree?.threads.orEmpty(),
          isLoading = true,
          hasLoaded = previousWorktree?.hasLoaded ?: false,
          hasUnknownThreadCount = previousWorktree?.hasUnknownThreadCount ?: false,
          errorMessage = null,
          providerWarnings = emptyList(),
        )
      },
    )
  }
}

private fun applyArchivedResults(
  projects: List<AgentProjectSessions>,
  resultsByPath: Map<String, AgentSessionLoadResult>,
): List<AgentProjectSessions> {
  return projects.map { project ->
    val result = resultsByPath[project.path]
    project.copy(
      isLoading = false,
      hasLoaded = true,
      hasUnknownThreadCount = result?.hasUnknownThreadCount ?: false,
      threads = result?.threads.orEmpty(),
      errorMessage = result?.errorMessage,
      providerWarnings = result?.providerWarnings ?: emptyList(),
      worktrees = project.worktrees.map { worktree ->
        val worktreeResult = resultsByPath[worktree.path]
        worktree.copy(
          isLoading = false,
          hasLoaded = true,
          hasUnknownThreadCount = worktreeResult?.hasUnknownThreadCount ?: false,
          threads = worktreeResult?.threads.orEmpty(),
          errorMessage = worktreeResult?.errorMessage,
          providerWarnings = worktreeResult?.providerWarnings ?: emptyList(),
        )
      },
    )
  }
}

private fun retainVisibleThreadCounts(knownPaths: Set<String>, visibleThreadCounts: Map<String, Int>): Map<String, Int> {
  val result = LinkedHashMap<String, Int>()
  visibleThreadCounts.forEach { (path, count) ->
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    if (normalizedPath in knownPaths && count > DEFAULT_VISIBLE_THREAD_COUNT) {
      result[normalizedPath] = count
    }
  }
  return result
}

private fun List<AgentProjectSessions>.anyPathLoading(): Boolean {
  return any { project -> project.isLoading || project.worktrees.any(AgentWorktree::isLoading) }
}

private fun resolveArchivedErrorMessage(provider: AgentSessionProvider, throwable: Throwable): String {
  return if (isCliMissingError(provider, throwable)) {
    AgentSessionsBundle.message(agentSessionCliMissingMessageKey(provider))
  }
  else {
    AgentSessionsBundle.message("toolwindow.error")
  }
}

private fun resolveArchivedProviderWarningMessage(provider: AgentSessionProvider, throwable: Throwable): String {
  return if (isCliMissingError(provider, throwable)) {
    AgentSessionsBundle.message(agentSessionCliMissingMessageKey(provider))
  }
  else {
    AgentSessionsBundle.message("toolwindow.warning.provider.unavailable", resolveProviderLabel(provider))
  }
}

private fun isCliMissingError(provider: AgentSessionProvider, throwable: Throwable): Boolean {
  return AgentSessionProviders.find(provider)?.isCliMissingError(throwable) == true
}

private fun resolveProviderLabel(provider: AgentSessionProvider): String {
  val descriptor = AgentSessionProviders.find(provider)
  return if (descriptor != null) AgentSessionsBundle.message(descriptor.displayNameKey) else provider.value
}

@Suppress("DuplicatedCode")
private fun findThreadIndex(
  projects: List<AgentProjectSessions>,
  normalizedPath: String,
  provider: AgentSessionProvider,
  threadId: String,
): Int? {
  val projectThreads = projects.firstOrNull { it.path == normalizedPath }?.threads
  if (projectThreads != null) {
    val index =
      projectThreads.indexOfFirst { thread -> thread.matchesProviderAndThreadOrSubAgent(provider = provider, threadId = threadId) }
    if (index >= 0) return index
  }

  projects.forEach { project ->
    val worktreeThreads = project.worktrees.firstOrNull { it.path == normalizedPath }?.threads ?: return@forEach
    val index =
      worktreeThreads.indexOfFirst { thread -> thread.matchesProviderAndThreadOrSubAgent(provider = provider, threadId = threadId) }
    if (index >= 0) return index
  }

  return null
}

private fun AgentSessionThread.matchesProviderAndThreadOrSubAgent(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && (id == threadId || subAgents.any { subAgent -> subAgent.id == threadId })
}

@Suppress("DuplicatedCode")
private fun requiredVisibleClosedProjectCount(projects: List<AgentProjectSessions>, normalizedPath: String): Int? {
  var closedProjectCount = 0
  for (project in projects) {
    val isAlwaysVisible = project.isOpen || project.worktrees.any { worktree -> worktree.isOpen }
    if (!isAlwaysVisible) {
      closedProjectCount++
    }
    if (normalizeAgentWorkbenchPath(project.path) == normalizedPath) {
      return if (isAlwaysVisible) 0 else closedProjectCount
    }
  }
  return null
}
