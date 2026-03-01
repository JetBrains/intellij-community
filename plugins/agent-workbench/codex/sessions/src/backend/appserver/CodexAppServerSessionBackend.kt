// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexSubAgent
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<CodexAppServerSessionBackend>()
private const val PREFETCH_FETCH_PARALLELISM = 4

class CodexAppServerSessionBackend(
  private val listThreadsForProject: suspend (Path) -> List<CodexThread> = { projectPath ->
    service<SharedCodexAppServerService>().listThreads(projectPath)
  },
  private val archiveThread: suspend (String) -> Unit = { threadId ->
    service<SharedCodexAppServerService>().archiveThread(threadId)
  },
  private val orphanArchiveAttemptRecorder: (String) -> Boolean = { threadId ->
    service<CodexSubAgentArchiveStateService>().markArchiveAttempted(threadId)
  },
) : CodexSessionBackend {
  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    val workingDirectory = resolveProjectDirectoryFromPath(path) ?: return emptyList()
    val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
    val threads = listThreadsForProject(workingDirectory)
    val byPath = buildThreadsByCwd(
      threads = threads,
      targetCwds = setOf(cwdFilter),
      archiveThread = archiveThread,
      orphanArchiveAttemptRecorder = orphanArchiveAttemptRecorder,
    )
    return byPath[cwdFilter].orEmpty()
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
    for (filter in pathFilters) {
      if (filter.cwdFilter !in succeededCwds) {
        continue
      }
      result[filter.path] = threadsByCwd[filter.cwdFilter].orEmpty()
    }

    LOG.debug {
      "Codex app-server prefetch resolvedPaths=${pathFilters.size}, succeededCwds=${succeededCwds.size}, " +
      "failedCwds=$failedCwds, returnedPaths=${result.size}"
    }

    return result
  }
}

private suspend fun buildThreadsByCwd(
  threads: List<CodexThread>,
  targetCwds: Set<String>,
  archiveThread: suspend (String) -> Unit,
  orphanArchiveAttemptRecorder: (String) -> Boolean,
): Map<String, List<CodexBackendThread>> {
  if (threads.isEmpty() || targetCwds.isEmpty()) {
    return emptyMap()
  }

  val parentsByCwd = LinkedHashMap<String, LinkedHashMap<String, CodexThread>>()
  val childrenByCwdAndParent = LinkedHashMap<String, LinkedHashMap<String, MutableList<CodexThread>>>()
  val subAgentsWithoutParent = ArrayList<CodexThread>()

  for (thread in threads) {
    val cwd = thread.cwd ?: continue
    if (cwd !in targetCwds) continue
    if (thread.shouldBeGroupedAsSubAgentChild()) {
      val parentThreadId = thread.parentThreadId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
      if (parentThreadId == null) {
        subAgentsWithoutParent.add(thread)
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
    for (parent in parents.values) {
      val children = childrenByParent[parent.id]
        .orEmpty()
        .sortedByDescending { it.updatedAt }
      val subAgents = children.map { child ->
        CodexSubAgent(id = child.id, name = child.toSubAgentName())
      }
      val activity = foldSessionActivity(
        parent.toSessionActivity(),
        children.asSequence().map(CodexThread::toSessionActivity),
      )
      threadsForCwd.add(
        CodexBackendThread(
          thread = parent.copy(subAgents = subAgents),
          activity = activity,
        )
      )
    }
    threadsForCwd.sortByDescending { it.thread.updatedAt }
    result[cwd] = threadsForCwd

    for ((parentThreadId, children) in childrenByParent) {
      if (parentThreadId !in parents) {
        orphanCandidates.addAll(children)
      }
    }
  }
  orphanCandidates.addAll(subAgentsWithoutParent)

  archiveSingleOrphan(
    orphanCandidates = orphanCandidates,
    archiveThread = archiveThread,
    orphanArchiveAttemptRecorder = orphanArchiveAttemptRecorder,
  )

  return result
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

private fun CodexThread.toSessionActivity(): CodexSessionActivity {
  return when (statusKind) {
    CodexThreadStatusKind.ACTIVE -> {
      when {
        CodexThreadActiveFlag.WAITING_ON_USER_INPUT in activeFlags -> CodexSessionActivity.UNREAD
        CodexThreadActiveFlag.WAITING_ON_APPROVAL in activeFlags -> CodexSessionActivity.REVIEWING
        else -> CodexSessionActivity.PROCESSING
      }
    }
    else -> CodexSessionActivity.READY
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

private fun foldSessionActivity(base: CodexSessionActivity, children: Sequence<CodexSessionActivity>): CodexSessionActivity {
  var current = base
  for (child in children) {
    current = when {
      child == CodexSessionActivity.UNREAD || current == CodexSessionActivity.UNREAD -> CodexSessionActivity.UNREAD
      child == CodexSessionActivity.REVIEWING || current == CodexSessionActivity.REVIEWING -> CodexSessionActivity.REVIEWING
      child == CodexSessionActivity.PROCESSING || current == CodexSessionActivity.PROCESSING -> CodexSessionActivity.PROCESSING
      else -> CodexSessionActivity.READY
    }
  }
  return current
}

private data class PathFilter(
  val path: String,
  val cwdFilter: String,
  val workingDirectory: Path,
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
