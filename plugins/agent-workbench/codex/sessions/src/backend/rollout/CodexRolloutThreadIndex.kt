// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.codex.common.CodexSubAgent
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionCachedFile
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionFileStat
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionInvalidationState
import com.intellij.agent.workbench.json.filebacked.buildFileBackedSessionFileStat
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<CodexRolloutThreadIndex>()

internal class CodexRolloutThreadIndex(
  private val codexHomeProvider: () -> Path,
  private val parser: CodexRolloutParser,
) {
  private val invalidationState = FileBackedSessionInvalidationState<ParsedRolloutThread?>(::isRolloutPath)
  private val threadsLock = Any()
  private val threadsByCwd = Object2ObjectOpenHashMap<String, ObjectArrayList<CodexBackendThread>>()

  fun markDirty(changeSet: FileBackedSessionChangeSet) {
    // Refresh pings do not mark cache entries dirty. They only trigger a collection pass,
    // where stat diffs (mtime/size) decide which rollout files must be reparsed.
    if (!changeSet.requiresFullRescan && changeSet.changedPaths.isEmpty()) {
      return
    }

    val markedPaths = invalidationState.markDirty(changeSet)
    LOG.debug {
      "Marked Codex rollout cache dirty (fullRescan=${changeSet.requiresFullRescan}, paths=$markedPaths)"
    }
  }

  fun collectByCwd(cwdFilters: Set<String>): Map<String, List<CodexBackendThread>> {
    if (cwdFilters.isEmpty()) return emptyMap()

    val sessionsDir = codexHomeProvider().resolve("sessions")
    if (!Files.isDirectory(sessionsDir)) {
      clearState()
      return emptyMap()
    }

    val scannedFiles: Map<String, FileBackedSessionFileStat> = try {
      scanRolloutFiles(sessionsDir)
    }
    catch (_: Throwable) {
      LOG.debug { "Failed to scan rollout files under $sessionsDir" }
      return emptyMap()
    }

    val rescanPlan = invalidationState.planRescan(scannedFiles)

    if (rescanPlan.filesToParse.isNotEmpty()) {
      val parsedUpdates = HashMap<String, FileBackedSessionCachedFile<ParsedRolloutThread?>>(rescanPlan.filesToParse.size)
      for (stat in rescanPlan.filesToParse) {
        parsedUpdates[stat.pathKey] = FileBackedSessionCachedFile(
          lastModifiedNs = stat.lastModifiedNs,
          sizeBytes = stat.sizeBytes,
          parsedValue = parser.parse(stat.path),
        )
      }
      invalidationState.applyParsedUpdates(parsedUpdates)
    }

    if (rescanPlan.removedAny || rescanPlan.filesToParse.isNotEmpty()) {
      rebuildThreadsByCwd(invalidationState.snapshotCachedFiles())
      LOG.debug {
        "Rollout cache updated (cwdFilters=${cwdFilters.size}, scannedFiles=${scannedFiles.size}, parsed=${rescanPlan.filesToParse.size}, removedAny=${rescanPlan.removedAny}, dirtyPaths=${rescanPlan.dirtyPathCount}, fullRescan=${rescanPlan.fullRescan})"
      }
    }

    synchronized(threadsLock) {
      val result = Object2ObjectOpenHashMap<String, List<CodexBackendThread>>(cwdFilters.size)
      for (cwdFilter in cwdFilters) {
        val threads = threadsByCwd[cwdFilter] ?: continue
        result[cwdFilter] = ArrayList(threads)
      }
      LOG.debug {
        "Resolved Codex rollout threads for cwd filters (requested=${cwdFilters.size}, matched=${result.size})"
      }
      return result
    }
  }

  fun resolveThreadFilePaths(path: String, threadId: String): List<Path> {
    val normalizedThreadId = threadId.trim().takeIf { id -> id.isNotEmpty() && '/' !in id && '\\' !in id } ?: return emptyList()
    val workingDirectory = resolveProjectDirectoryFromPath(path) ?: return emptyList()
    val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
    collectByCwd(setOf(cwdFilter))

    val result = LinkedHashSet<Path>()
    for ((_, _, parsedThread) in invalidationState.snapshotCachedFiles().values) {
      if (parsedThread == null) continue
      if (parsedThread.normalizedCwd == cwdFilter && parsedThread.thread.thread.id == normalizedThreadId) {
        result.add(parsedThread.path)
      }
    }
    return ArrayList(result)
  }

  private fun clearState() {
    invalidationState.clear()
    synchronized(threadsLock) {
      threadsByCwd.clear()
    }
  }

  private fun rebuildThreadsByCwd(
    cachedFilesByPath: Map<String, FileBackedSessionCachedFile<ParsedRolloutThread?>>,
  ) {
    synchronized(threadsLock) {
      threadsByCwd.clear()
      val parsedThreadsByCwd = LinkedHashMap<String, MutableList<ParsedRolloutThread>>()
      for ((_, cachedFile) in cachedFilesByPath) {
        val parsedThread = cachedFile.parsedValue ?: continue
        parsedThreadsByCwd.getOrPut(parsedThread.normalizedCwd) { ArrayList() }.add(parsedThread)
      }

      for ((cwd, parsedThreads) in parsedThreadsByCwd) {
        val topLevelThreads = ArrayList<CodexBackendThread>(parsedThreads.size)
        val subAgentThreadsByParent = LinkedHashMap<String, MutableList<ParsedRolloutThread>>()
        val subAgentThreads = ArrayList<ParsedRolloutThread>()

        for (parsedThread in parsedThreads) {
          val parentThreadId = parsedThread.parentThreadId
          if (parentThreadId == null) {
            topLevelThreads.add(parsedThread.thread)
            continue
          }

          subAgentThreadsByParent.getOrPut(parentThreadId) { ArrayList() }.add(parsedThread)
          subAgentThreads.add(parsedThread)
        }

        val resolvedParentIds = HashSet<String>()
        for (index in topLevelThreads.indices) {
          val parentThread = topLevelThreads[index]
          val childThreads = subAgentThreadsByParent[parentThread.thread.id].orEmpty()
          if (childThreads.isEmpty()) continue

          val mergedSubAgents = LinkedHashMap<String, CodexSubAgent>(
            parentThread.thread.subAgents.size + childThreads.size
          )
          parentThread.thread.subAgents.forEach { subAgent ->
            mergedSubAgents.putIfAbsent(subAgent.id, subAgent)
          }
          childThreads.forEach { childThread ->
            val subAgent = CodexSubAgent(id = childThread.thread.thread.id, name = childThread.thread.thread.title)
            mergedSubAgents.putIfAbsent(subAgent.id, subAgent)
          }

          val mergedUsageSnapshots = ArrayList<AgentSessionUsageSnapshot>(
            parentThread.usageSnapshots.size + childThreads.sumOf { childThread -> childThread.thread.usageSnapshots.size }
          )
          mergedUsageSnapshots.addAll(parentThread.usageSnapshots)
          childThreads.forEach { childThread ->
            mergedUsageSnapshots.addAll(childThread.thread.usageSnapshots)
          }

          topLevelThreads[index] = parentThread.copy(
            thread = parentThread.thread.copy(subAgents = ArrayList(mergedSubAgents.values)),
            usageSnapshots = mergedUsageSnapshots,
          )
          resolvedParentIds.add(parentThread.thread.id)
        }

        // Preserve previous behavior when parent rollout is missing for a discovered sub-agent session.
        for (parsedThread in subAgentThreads) {
          val parentThreadId = parsedThread.parentThreadId ?: continue
          if (resolvedParentIds.contains(parentThreadId)) continue
          topLevelThreads.add(parsedThread.thread)
        }

        topLevelThreads.sortWith(Comparator { left, right ->
          right.thread.updatedAt.compareTo(left.thread.updatedAt)
        })

        threadsByCwd[cwd] = ObjectArrayList(topLevelThreads)
      }
    }
  }

}

private fun scanRolloutFiles(sessionsDir: Path): Map<String, FileBackedSessionFileStat> {
  val scannedFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
  Files.walk(sessionsDir).use { stream ->
    val iterator = stream.iterator()
    while (iterator.hasNext()) {
      val candidate = iterator.next()
      val fileName = candidate.fileName?.toString() ?: continue
      if (!isRolloutFileName(fileName)) continue
      val stat = buildFileBackedSessionFileStat(candidate) ?: continue
      scannedFiles[stat.pathKey] = stat
    }
  }

  return scannedFiles
}

private fun isRolloutPath(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return isRolloutFileName(fileName)
}
