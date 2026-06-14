// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexSubAgent
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.CodexThreadPathIndex
import com.intellij.agent.workbench.codex.sessions.InMemoryCodexThreadPathIndex
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThreadRefreshResult
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.isResponseRequired
import com.intellij.agent.workbench.codex.sessions.backend.toCodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.agent.workbench.sessions.core.normalizeConcreteAgentSessionThreadId
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<CodexAppServerSessionBackend>()
private const val PREFETCH_FETCH_PARALLELISM = 4
private const val MAX_TRACKED_ORPHAN_SUB_AGENT_IDS = 4_096

internal class CodexAppServerSessionBackend(
  private val listThreadsForProject: suspend (Path) -> List<CodexThread> = { projectPath ->
    serviceAsync<SharedCodexAppServerService>().listThreads(projectPath)
  },
  private val listArchivedThreadsForProject: suspend (Path) -> List<CodexThread> = { projectPath ->
    serviceAsync<SharedCodexAppServerService>().listArchivedThreads(projectPath)
  },
  private val readThread: suspend (String) -> CodexThread? = { threadId ->
    serviceAsync<SharedCodexAppServerService>().readThread(threadId)
  },
  private val archiveThread: suspend (String) -> Unit = { threadId ->
    serviceAsync<SharedCodexAppServerService>().archiveThread(threadId)
  },
  private val orphanArchiveAttemptRecorder: (String) -> Boolean = InMemoryOrphanArchiveAttemptRecorder()::markArchiveAttempted,
  private val threadPathIndex: CodexThreadPathIndex = InMemoryCodexThreadPathIndex(),
) : CodexSessionBackend {
  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    val workingDirectory = resolveProjectDirectoryFromPath(path) ?: return emptyList()
    val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
    val threads = listThreadsForProject(workingDirectory)
    rememberThreadMetadata(threads)
    val byPath = buildThreadsByCwd(
      threads = threads,
      targetCwds = setOf(cwdFilter),
      archiveThread = archiveThread,
      orphanArchiveAttemptRecorder = orphanArchiveAttemptRecorder,
    )
    return byPath[cwdFilter].orEmpty()
  }

  override suspend fun listArchivedThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    val workingDirectory = resolveProjectDirectoryFromPath(path) ?: return emptyList()
    val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
    val threads = listArchivedThreadsForProject(workingDirectory)
    rememberThreadMetadata(threads)
    return buildThreadsByCwd(
      threads = threads,
      targetCwds = setOf(cwdFilter),
      archiveThread = {},
      orphanArchiveAttemptRecorder = { false },
      includeOrphanSubAgents = true,
    )[cwdFilter].orEmpty()
  }

  override suspend fun refreshThreads(path: String, threadIds: Set<String>, openProject: Project?): CodexBackendThreadRefreshResult? {
    if (threadIds.isEmpty()) {
      return null
    }
    val workingDirectory = resolveProjectDirectoryFromPath(path) ?: return CodexBackendThreadRefreshResult()
    val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
    val fetchedThreadsById = LinkedHashMap<String, CodexThread>(threadIds.size)
    val parentThreadIdsToFetch = LinkedHashSet<String>()
    for (threadId in threadIds) {
      val concreteThreadId = normalizeConcreteAgentSessionThreadId(threadId) ?: continue
      val thread = readThreadIfLoaded(concreteThreadId) ?: continue
      if (thread.cwd != cwdFilter) {
        continue
      }
      rememberThreadMetadata(listOf(thread))
      if (thread.shouldBeGroupedAsSubAgentChild()) {
        thread.parentThreadId?.trim()?.takeIf { it.isNotEmpty() }?.let(parentThreadIdsToFetch::add)
        fetchedThreadsById[thread.id] = thread
        continue
      }
      fetchedThreadsById[thread.id] = thread
    }
    for (parentThreadId in parentThreadIdsToFetch) {
      val concreteParentThreadId = normalizeConcreteAgentSessionThreadId(parentThreadId) ?: continue
      if (concreteParentThreadId in fetchedThreadsById) {
        continue
      }
      val parentThread = readThreadIfLoaded(concreteParentThreadId) ?: continue
      if (parentThread.cwd == cwdFilter) {
        rememberThreadMetadata(listOf(parentThread))
        fetchedThreadsById[parentThread.id] = parentThread
      }
    }
    val threads = buildThreadsByCwd(
      threads = ArrayList(fetchedThreadsById.values),
      targetCwds = setOf(cwdFilter),
      archiveThread = {},
      orphanArchiveAttemptRecorder = { false },
    )[cwdFilter].orEmpty()
    return CodexBackendThreadRefreshResult(threads = threads, isComplete = false)
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> {
    if (paths.isEmpty()) return emptyMap()

    val pathFilters = resolvePathFilters(paths)
    if (pathFilters.isEmpty()) return emptyMap()

    val filtersByCwd = LinkedHashMap<String, MutableList<PathFilter>>()
    for (filter in pathFilters) {
      filtersByCwd.getOrPut(filter.cwdFilter) { ArrayList() }.add(filter)
    }

    LOG.debug {
      "Codex app-server prefetch requestedPaths=${paths.size}, resolvedPaths=${pathFilters.size}, uniqueCwds=${filtersByCwd.size}"
    }

    val semaphore = Semaphore(PREFETCH_FETCH_PARALLELISM)
    val fetchedByCwd = coroutineScope {
      filtersByCwd.map { (cwdFilter, filters) ->
        async {
          semaphore.withPermit {
            val workingDirectory = filters.first().workingDirectory
            try {
              cwdFilter to listThreadsForProject(workingDirectory)
            }
            catch (t: Throwable) {
              LOG.warn("Failed to prefetch Codex threads for cwd $cwdFilter", t)
              cwdFilter to null
            }
          }
        }
      }.awaitAll()
    }

    val prefetchedThreads = ArrayList<CodexThread>()
    val succeededCwds = LinkedHashSet<String>()
    var failedCwds = 0
    for ((cwdFilter, threads) in fetchedByCwd) {
      if (threads == null) {
        failedCwds++
        continue
      }
      succeededCwds.add(cwdFilter)
      rememberThreadMetadata(threads)
      prefetchedThreads.addAll(threads)
    }

    if (succeededCwds.isEmpty()) {
      LOG.debug {
        "Codex app-server prefetch finished without successful cwd fetches (resolvedPaths=${pathFilters.size}, failedCwds=$failedCwds)"
      }
      return emptyMap()
    }

    val threadsByCwd = buildThreadsByCwd(
      threads = prefetchedThreads,
      targetCwds = succeededCwds,
      archiveThread = archiveThread,
      orphanArchiveAttemptRecorder = orphanArchiveAttemptRecorder,
    )

    val result = LinkedHashMap<String, List<CodexBackendThread>>(pathFilters.size)
    pathFilters.forEach { filter ->
      if (filter.cwdFilter !in succeededCwds) {
        return@forEach
      }
      result[filter.path] = threadsByCwd[filter.cwdFilter].orEmpty()
    }

    LOG.debug {
      "Codex app-server prefetch resolvedPaths=${pathFilters.size}, succeededCwds=${succeededCwds.size}, " +
      "failedCwds=$failedCwds, returnedPaths=${result.size}"
    }

    return result
  }

  private fun rememberThreadMetadata(threads: Iterable<CodexThread>) {
    threadPathIndex.recordThreads(threads)
  }

  private suspend fun readThreadIfLoaded(threadId: String): CodexThread? {
    return try {
      readThread(threadId)
    }
    catch (e: Throwable) {
      if (e is CancellationException) {
        throw e
      }
      if (e.isCodexThreadNotLoadedError()) {
        LOG.debug { "Skipped Codex app-server refresh for unloaded threadId=$threadId: ${e.message}" }
        null
      }
      else {
        throw e
      }
    }
  }
}

private class InMemoryOrphanArchiveAttemptRecorder {
  private val attemptedThreadIds = LinkedHashSet<String>()

  @Synchronized
  fun markArchiveAttempted(threadId: String): Boolean {
    val normalizedThreadId = threadId.trim()
    if (normalizedThreadId.isEmpty()) {
      return false
    }
    if (!attemptedThreadIds.add(normalizedThreadId)) {
      return false
    }

    while (attemptedThreadIds.size > MAX_TRACKED_ORPHAN_SUB_AGENT_IDS) {
      val iterator = attemptedThreadIds.iterator()
      if (!iterator.hasNext()) {
        break
      }
      iterator.next()
      iterator.remove()
    }
    return true
  }
}

private suspend fun buildThreadsByCwd(
  threads: List<CodexThread>,
  targetCwds: Set<String>,
  archiveThread: suspend (String) -> Unit,
  orphanArchiveAttemptRecorder: (String) -> Boolean,
  includeOrphanSubAgents: Boolean = false,
): Map<String, List<CodexBackendThread>> {
  if (threads.isEmpty() || targetCwds.isEmpty()) {
    return emptyMap()
  }

  val parentsByCwd = LinkedHashMap<String, LinkedHashMap<String, CodexThread>>()
  val childrenByCwdAndParent = LinkedHashMap<String, LinkedHashMap<String, MutableList<CodexThread>>>()
  val subAgentsWithoutParentByCwd = LinkedHashMap<String, MutableList<CodexThread>>()

  for (thread in threads) {
    val cwd = thread.cwd ?: continue
    if (cwd !in targetCwds) continue
    if (thread.shouldBeGroupedAsSubAgentChild()) {
      val parentThreadId = thread.parentThreadId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
      if (parentThreadId == null) {
        subAgentsWithoutParentByCwd
          .getOrPut(cwd) { ArrayList() }
          .add(thread)
      }
      else {
        childrenByCwdAndParent
          .getOrPut(cwd) { LinkedHashMap() }
          .getOrPut(parentThreadId) { ArrayList() }
          .add(thread)
      }
      continue
    }

    val cwdParents = parentsByCwd.getOrPut(cwd) { LinkedHashMap() }
    val existing = cwdParents[thread.id]
    if (existing == null || thread.updatedAt >= existing.updatedAt) {
      cwdParents[thread.id] = thread
    }
  }

  val result = LinkedHashMap<String, List<CodexBackendThread>>(targetCwds.size)
  val orphanCandidates = ArrayList<CodexThread>()
  for (cwd in targetCwds) {
    val parents = parentsByCwd[cwd].orEmpty()
    val childrenByParent = childrenByCwdAndParent[cwd].orEmpty()

    val threadsForCwd = ArrayList<CodexBackendThread>(parents.size)
    parents.values.forEach { parent ->
      val children = childrenByParent[parent.id]
        .orEmpty()
        .sortedByDescending { it.updatedAt }
      val parentActivity = parent.toCodexSessionActivity()
      val subAgents = children.map { child ->
        CodexSubAgent(id = child.id, name = child.toSubAgentName())
      }
      val subAgentActivitiesById = children.associate { child -> child.id to child.toCodexSessionActivity() }
      threadsForCwd.add(
        CodexBackendThread(
          thread = parent.copy(subAgents = subAgents),
          activity = parentActivity,
          requiresResponse = parent.activeFlags.isResponseRequired(),
          summaryActivity = if (parent.isSubAgentSourceKind()) null else parentActivity,
          subAgentActivitiesById = subAgentActivitiesById,
        )
      )
    }

    val orphanCandidatesForCwd = ArrayList<CodexThread>()
    for ((parentThreadId, children) in childrenByParent) {
      if (parentThreadId !in parents) {
        orphanCandidatesForCwd.addAll(children)
      }
    }
    orphanCandidatesForCwd.addAll(subAgentsWithoutParentByCwd[cwd].orEmpty())
    if (includeOrphanSubAgents) {
      orphanCandidatesForCwd
        .mapTo(threadsForCwd) { orphan -> orphan.toStandaloneBackendThread() }
    }
    else {
      orphanCandidates.addAll(orphanCandidatesForCwd)
    }
    threadsForCwd.sortByDescending { it.thread.updatedAt }
    result[cwd] = threadsForCwd
  }

  archiveSingleOrphan(
    orphanCandidates = orphanCandidates,
    archiveThread = archiveThread,
    orphanArchiveAttemptRecorder = orphanArchiveAttemptRecorder,
  )

  return result
}

private fun CodexThread.toStandaloneBackendThread(): CodexBackendThread {
  return CodexBackendThread(
    thread = copy(subAgents = emptyList()),
    activity = toCodexSessionActivity(),
    requiresResponse = activeFlags.isResponseRequired(),
    summaryActivity = null,
  )
}

private suspend fun archiveSingleOrphan(
  orphanCandidates: List<CodexThread>,
  archiveThread: suspend (String) -> Unit,
  orphanArchiveAttemptRecorder: (String) -> Boolean,
) {
  val candidate = orphanCandidates
                    .asSequence()
                    .sortedByDescending { it.updatedAt }
                    .firstOrNull { thread ->
                      // Record before RPC so each orphan is attempted at most once across refresh cycles.
                      thread.id.isNotBlank() && orphanArchiveAttemptRecorder(thread.id)
                    } ?: return

  try {
    archiveThread(candidate.id)
  }
  catch (t: Throwable) {
    LOG.warn("Failed to archive orphan sub-agent thread ${candidate.id}", t)
  }
}

private fun CodexThread.toSubAgentName(): String {
  val nickname = agentNickname?.trim().orEmpty()
  val role = agentRole?.trim().orEmpty()
  val resolvedTitle = title.trim()
  return when {
    nickname.isNotEmpty() && role.isNotEmpty() -> "$nickname ($role)"
    nickname.isNotEmpty() -> nickname
    resolvedTitle.isNotEmpty() -> resolvedTitle
    role.isNotEmpty() -> role
    else -> "Sub-agent ${id.take(8)}"
  }
}

private fun CodexThread.shouldBeGroupedAsSubAgentChild(): Boolean {
  return when (sourceKind) {
    CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN,
      -> true

    CodexThreadSourceKind.SUB_AGENT,
    CodexThreadSourceKind.SUB_AGENT_REVIEW,
    CodexThreadSourceKind.SUB_AGENT_COMPACT,
    CodexThreadSourceKind.SUB_AGENT_OTHER,
      -> !parentThreadId.isNullOrBlank()

    else -> false
  }
}

private fun CodexThread.isSubAgentSourceKind(): Boolean {
  return when (sourceKind) {
    CodexThreadSourceKind.SUB_AGENT,
    CodexThreadSourceKind.SUB_AGENT_REVIEW,
    CodexThreadSourceKind.SUB_AGENT_COMPACT,
    CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN,
    CodexThreadSourceKind.SUB_AGENT_OTHER,
      -> true

    CodexThreadSourceKind.CLI,
    CodexThreadSourceKind.VSCODE,
    CodexThreadSourceKind.EXEC,
    CodexThreadSourceKind.APP_SERVER,
    CodexThreadSourceKind.UNKNOWN,
      -> false
  }
}

private data class PathFilter(
  @JvmField val path: String,
  @JvmField val cwdFilter: String,
  @JvmField val workingDirectory: Path,
)

private fun resolvePathFilters(paths: List<String>): List<PathFilter> {
  return paths.mapNotNull { path ->
    resolveProjectDirectoryFromPath(path)?.let { directory ->
      PathFilter(
        path = path,
        cwdFilter = normalizeRootPath(directory.invariantSeparatorsPathString),
        workingDirectory = directory,
      )
    }
  }
}
