// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.codex.common.CodexSubAgent
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<CodexRolloutThreadIndex>()

internal class CodexRolloutThreadIndex(
  private val codexHomeProvider: () -> Path,
  private val parser: CodexRolloutParser,
) {
  private val cacheLock = Any()
  private val state = State()

  fun markDirty(changeSet: CodexRolloutChangeSet) {
    // Refresh pings do not mark cache entries dirty. They only trigger a collection pass,
    // where stat diffs (mtime/size) decide which rollout files must be reparsed.
    if (!changeSet.requiresFullRescan && changeSet.changedRolloutPaths.isEmpty()) {
      return
    }

    var markedPaths = 0
    synchronized(cacheLock) {
      if (changeSet.requiresFullRescan) {
        state.forceFullRescan = true
      }

      for (path in changeSet.changedRolloutPaths) {
        val fileName = path.fileName?.toString() ?: continue
        if (!isRolloutFileName(fileName)) continue
        if (state.dirtyRolloutPathKeys.add(toPathKey(path))) {
          markedPaths++
        }
      }
    }
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

    val scannedFiles = try {
      scanRolloutFiles(sessionsDir)
    }
    catch (_: Throwable) {
      LOG.debug { "Failed to scan rollout files under $sessionsDir" }
      return emptyMap()
    }

    val filesToParse = ObjectArrayList<RolloutFileStat>()
    val changeResult = collectChangedFiles(scannedFiles, filesToParse)

    if (filesToParse.isNotEmpty()) {
      val parsedUpdates = Object2ObjectOpenHashMap<String, CachedRolloutFile>(filesToParse.size)
      for (stat in filesToParse) {
        parsedUpdates[stat.pathKey] = CachedRolloutFile(
          lastModifiedNs = stat.lastModifiedNs,
          sizeBytes = stat.sizeBytes,
          parsedThread = parser.parse(stat.path),
        )
      }
      synchronized(cacheLock) {
        for (entry in parsedUpdates.object2ObjectEntrySet()) {
          state.cachedFilesByPath[entry.key] = entry.value
        }
      }
    }

    if (changeResult.removedAny || filesToParse.isNotEmpty()) {
      synchronized(cacheLock) {
        rebuildThreadsByCwd()
      }
      LOG.debug {
        "Rollout cache updated (cwdFilters=${cwdFilters.size}, scannedFiles=${scannedFiles.size}, parsed=${filesToParse.size}, removedAny=${changeResult.removedAny}, dirtyPaths=${changeResult.dirtyPathCount}, fullRescan=${changeResult.fullRescan})"
      }
    }

    synchronized(cacheLock) {
      val result = Object2ObjectOpenHashMap<String, List<CodexBackendThread>>(cwdFilters.size)
      for (cwdFilter in cwdFilters) {
        val threads = state.threadsByCwd[cwdFilter] ?: continue
        result[cwdFilter] = ArrayList(threads)
      }
      LOG.debug {
        "Resolved Codex rollout threads for cwd filters (requested=${cwdFilters.size}, matched=${result.size})"
      }
      return result
    }
  }

  private fun clearState() {
    synchronized(cacheLock) {
      state.cachedFilesByPath.clear()
      state.threadsByCwd.clear()
      state.dirtyRolloutPathKeys.clear()
      state.forceFullRescan = false
    }
  }

  private fun collectChangedFiles(
    scannedFiles: Object2ObjectOpenHashMap<String, RolloutFileStat>,
    filesToParse: ObjectArrayList<RolloutFileStat>,
  ): ChangedFilesCollectionResult {
    var removedAny = false
    var fullRescan = false
    var dirtyPathCount = 0
    synchronized(cacheLock) {
      val iterator = state.cachedFilesByPath.object2ObjectEntrySet().iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        if (!scannedFiles.containsKey(entry.key)) {
          iterator.remove()
          removedAny = true
        }
      }

      val dirtyPathsSnapshot = if (state.dirtyRolloutPathKeys.isEmpty()) {
        null
      }
      else {
        LinkedHashSet(state.dirtyRolloutPathKeys)
      }
      fullRescan = state.forceFullRescan
      dirtyPathCount = dirtyPathsSnapshot?.size ?: 0
      state.forceFullRescan = false
      state.dirtyRolloutPathKeys.clear()

      for (entry in scannedFiles.object2ObjectEntrySet()) {
        val stat = entry.value
        val cached = state.cachedFilesByPath[entry.key]
        val parseBecauseDirty = dirtyPathsSnapshot?.contains(entry.key) == true

        // This path keeps refresh pings cheap while still catching atomic temp/rename writes:
        // they may not provide direct rollout path invalidation, but rollout file stats change.
        val parseBecauseStatsChanged = cached == null || cached.lastModifiedNs != stat.lastModifiedNs || cached.sizeBytes != stat.sizeBytes
        if (fullRescan || parseBecauseDirty || parseBecauseStatsChanged) {
          filesToParse.add(stat)
        }
      }
    }
    return ChangedFilesCollectionResult(
      removedAny = removedAny,
      fullRescan = fullRescan,
      dirtyPathCount = dirtyPathCount,
    )
  }

  private fun rebuildThreadsByCwd() {
    state.threadsByCwd.clear()
    val parsedThreadsByCwd = LinkedHashMap<String, MutableList<ParsedRolloutThread>>()
    for (entry in state.cachedFilesByPath.object2ObjectEntrySet()) {
      val parsedThread = entry.value.parsedThread ?: continue
      parsedThreadsByCwd.getOrPut(parsedThread.normalizedCwd) { ArrayList() }.add(parsedThread)
    }

    for ((cwd, parsedThreads) in parsedThreadsByCwd) {
      val topLevelThreads = ArrayList<CodexBackendThread>(parsedThreads.size)
      val subAgentsByParent = LinkedHashMap<String, LinkedHashMap<String, CodexSubAgent>>()
      val subAgentThreads = ArrayList<ParsedRolloutThread>()

      for (parsedThread in parsedThreads) {
        val parentThreadId = parsedThread.parentThreadId
        if (parentThreadId == null) {
          topLevelThreads.add(parsedThread.thread)
          continue
        }

        val subAgents = subAgentsByParent.getOrPut(parentThreadId) { LinkedHashMap() }
        val subAgent = CodexSubAgent(id = parsedThread.thread.thread.id, name = parsedThread.thread.thread.title)
        subAgents.putIfAbsent(subAgent.id, subAgent)
        subAgentThreads.add(parsedThread)
      }

      val resolvedParentIds = HashSet<String>()
      for (index in topLevelThreads.indices) {
        val parentThread = topLevelThreads[index]
        val childSubAgents = subAgentsByParent[parentThread.thread.id] ?: continue
        if (childSubAgents.isEmpty()) continue

        val mergedSubAgents = LinkedHashMap<String, CodexSubAgent>(
          parentThread.thread.subAgents.size + childSubAgents.size
        )
        parentThread.thread.subAgents.forEach { subAgent ->
          mergedSubAgents.putIfAbsent(subAgent.id, subAgent)
        }
        childSubAgents.forEach { (_, subAgent) ->
          mergedSubAgents.putIfAbsent(subAgent.id, subAgent)
        }

        topLevelThreads[index] = parentThread.copy(thread = parentThread.thread.copy(subAgents = ArrayList(mergedSubAgents.values)))
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

      state.threadsByCwd[cwd] = ObjectArrayList(topLevelThreads)
    }
  }

}

private fun scanRolloutFiles(sessionsDir: Path): Object2ObjectOpenHashMap<String, RolloutFileStat> {
  val scannedFiles = Object2ObjectOpenHashMap<String, RolloutFileStat>()
  Files.walk(sessionsDir).use { stream ->
    val iterator = stream.iterator()
    while (iterator.hasNext()) {
      val candidate = iterator.next()
      if (!Files.isRegularFile(candidate)) continue
      val fileName = candidate.fileName?.toString() ?: continue
      if (!isRolloutFileName(fileName)) continue
      val lastModifiedNs = try {
        Files.getLastModifiedTime(candidate).to(TimeUnit.NANOSECONDS)
      }
      catch (_: Throwable) {
        continue
      }
      val sizeBytes = try {
        Files.size(candidate)
      }
      catch (_: Throwable) {
        continue
      }

      val pathKey = toPathKey(candidate)
      scannedFiles[pathKey] = RolloutFileStat(
        pathKey = pathKey,
        path = candidate,
        lastModifiedNs = lastModifiedNs,
        sizeBytes = sizeBytes,
      )
    }
  }

  return scannedFiles
}

private fun toPathKey(path: Path): String {
  return normalizeRolloutPath(path).invariantSeparatorsPathString
}

private fun normalizeRolloutPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}

private data class State(
  @JvmField val cachedFilesByPath: Object2ObjectOpenHashMap<String, CachedRolloutFile> = Object2ObjectOpenHashMap(),
  @JvmField val threadsByCwd: Object2ObjectOpenHashMap<String, ObjectArrayList<CodexBackendThread>> = Object2ObjectOpenHashMap(),
  @JvmField val dirtyRolloutPathKeys: LinkedHashSet<String> = LinkedHashSet(),
  @JvmField var forceFullRescan: Boolean = false,
)

private data class ChangedFilesCollectionResult(
  @JvmField val removedAny: Boolean,
  @JvmField val fullRescan: Boolean,
  @JvmField val dirtyPathCount: Int,
)

private data class RolloutFileStat(
  @JvmField val pathKey: String,
  @JvmField val path: Path,
  @JvmField val lastModifiedNs: Long,
  @JvmField val sizeBytes: Long,
)

private data class CachedRolloutFile(
  @JvmField val lastModifiedNs: Long,
  @JvmField val sizeBytes: Long,
  @JvmField val parsedThread: ParsedRolloutThread?,
)
