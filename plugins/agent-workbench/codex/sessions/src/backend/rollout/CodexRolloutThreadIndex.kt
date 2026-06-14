// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.codex.common.CodexSubAgent
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionCachedFile
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionFileStat
import com.intellij.agent.workbench.json.filebacked.buildFileBackedSessionFileStat
import com.intellij.agent.workbench.json.filebacked.toFileBackedSessionPathKey
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
  private val parseRollout: (Path) -> ParsedRolloutThread?,
) {
  private val cacheLock = Any()
  private val cachedFilesByPath = LinkedHashMap<String, FileBackedSessionCachedFile<ParsedRolloutThread?>>()
  private val relatedThreadIdsByPath = LinkedHashMap<String, Set<String>>()
  private val cwdByPath = LinkedHashMap<String, String?>()
  private val pathKeysByThreadId = LinkedHashMap<String, MutableSet<String>>()
  private val dirtyPathKeys = LinkedHashSet<String>()
  private val pendingStatRefreshPathKeys = LinkedHashSet<String>()
  private var forceFullRescan = false
  private var initialized = false
  private val threadsLock = Any()
  private val threadsByCwd = Object2ObjectOpenHashMap<String, ObjectArrayList<CodexBackendThread>>()

  fun markDirty(changeSet: FileBackedSessionChangeSet) {
    var markedPaths = 0
    synchronized(cacheLock) {
      if (changeSet.requiresFullRescan) {
        forceFullRescan = true
      }
      if (changeSet.changedPaths.isEmpty()) {
        pendingStatRefreshPathKeys.addAll(cachedFilesByPath.keys)
      }
      else {
        for (path in changeSet.changedPaths) {
          if (!isRolloutPath(path)) continue
          if (dirtyPathKeys.add(toFileBackedSessionPathKey(path))) {
            markedPaths++
          }
        }
      }
    }
    LOG.debug {
      "Marked Codex rollout cache dirty (fullRescan=${changeSet.requiresFullRescan}, dirtyPaths=$markedPaths, statRefresh=${changeSet.changedPaths.isEmpty() && !changeSet.requiresFullRescan})"
    }
  }

  fun snapshotCachedFiles(): Map<String, FileBackedSessionCachedFile<ParsedRolloutThread?>> {
    return synchronized(cacheLock) {
      LinkedHashMap(cachedFilesByPath)
    }
  }

  fun collectByCwd(cwdFilters: Set<String>): Map<String, List<CodexBackendThread>> {
    if (cwdFilters.isEmpty()) return emptyMap()

    refreshIndex(
      targetCwds = cwdFilters,
      targetThreadIds = emptySet(),
      allowUnknownDirtyPaths = true,
    )

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

    return synchronized(cacheLock) {
      val result = LinkedHashSet<Path>()
      pathKeysByThreadId[normalizedThreadId].orEmpty().forEach { pathKey ->
        if (cwdByPath[pathKey] == cwdFilter) {
          result.add(Path.of(pathKey))
        }
      }
      ArrayList(result)
    }
  }

  fun collectByCwdAndThreadIds(cwdFilter: String, threadIds: Set<String>): List<CodexBackendThread> {
    if (threadIds.isEmpty()) return emptyList()

    refreshIndex(
      targetCwds = setOf(cwdFilter),
      targetThreadIds = threadIds,
      allowUnknownDirtyPaths = false,
    )

    synchronized(threadsLock) {
      val threads = threadsByCwd[cwdFilter] ?: return emptyList()
      return threads.filterTo(ArrayList()) { thread ->
        thread.thread.id in threadIds || thread.thread.subAgents.any { subAgent -> subAgent.id in threadIds }
      }
    }
  }

  private fun refreshIndex(
    targetCwds: Set<String>,
    targetThreadIds: Set<String>,
    allowUnknownDirtyPaths: Boolean,
  ) {
    val sessionsDir = codexHomeProvider().resolve("sessions")
    if (!Files.isDirectory(sessionsDir)) {
      clearState()
      return
    }

    val initialScanNeeded = synchronized(cacheLock) { !initialized || forceFullRescan }
    if (initialScanNeeded) {
      performFullScan(sessionsDir)
      return
    }

    val discoveredPathKeys = if (targetThreadIds.isEmpty()) discoverSiblingRolloutPaths(targetCwds) else emptySet()

    val refreshPlan = synchronized(cacheLock) {
      val pathsToRefresh = LinkedHashSet<String>()
      val dirtySnapshot = LinkedHashSet(dirtyPathKeys)
      val pendingStatSnapshot = LinkedHashSet(pendingStatRefreshPathKeys)
      val requestedPathKeys = targetThreadIds.asSequence()
        .flatMap { threadId -> pathKeysByThreadId[threadId].orEmpty().asSequence() }
        .toCollection(LinkedHashSet())

      if (targetThreadIds.isEmpty()) {
        cwdByPath.forEach { (pathKey, knownCwd) ->
          if (knownCwd == null || knownCwd in targetCwds) {
            pathsToRefresh.add(pathKey)
          }
        }
        for (pathKey in discoveredPathKeys) {
          if (pathKey !in cachedFilesByPath) {
            pathsToRefresh.add(pathKey)
          }
        }
      }
      else {
        pathsToRefresh.addAll(requestedPathKeys)
      }

      for (pathKey in dirtySnapshot) {
        val knownCwd = cwdByPath[pathKey]
        val isRequestedThreadPath = pathKey in requestedPathKeys
        if (isRequestedThreadPath || knownCwd in targetCwds || (knownCwd == null && allowUnknownDirtyPaths)) {
          pathsToRefresh.add(pathKey)
        }
      }
      for (pathKey in pendingStatSnapshot) {
        if (pathKey in requestedPathKeys || cwdByPath[pathKey] in targetCwds) {
          pathsToRefresh.add(pathKey)
        }
      }
      RolloutRefreshPlan(
        pathKeys = pathsToRefresh,
        dirtyPathKeys = dirtySnapshot.intersect(pathsToRefresh),
      )
    }
    if (refreshPlan.pathKeys.isEmpty()) {
      return
    }

    val parsedUpdates = LinkedHashMap<String, FileBackedSessionCachedFile<ParsedRolloutThread?>>()
    val removedPathKeys = LinkedHashSet<String>()
    for (pathKey in refreshPlan.pathKeys) {
      val path = Path.of(pathKey)
      val stat = buildFileBackedSessionFileStat(path)
      if (stat == null) {
        removedPathKeys.add(pathKey)
        continue
      }

      val cached = synchronized(cacheLock) { cachedFilesByPath[pathKey] }
      val parseBecauseDirty = pathKey in refreshPlan.dirtyPathKeys
      val parseBecauseStatsChanged = cached == null || cached.lastModifiedNs != stat.lastModifiedNs || cached.sizeBytes != stat.sizeBytes
      if (!parseBecauseDirty && !parseBecauseStatsChanged) {
        synchronized(cacheLock) {
          dirtyPathKeys.remove(pathKey)
          pendingStatRefreshPathKeys.remove(pathKey)
        }
        continue
      }

      parsedUpdates[pathKey] = FileBackedSessionCachedFile(
        lastModifiedNs = stat.lastModifiedNs,
        sizeBytes = stat.sizeBytes,
        parsedValue = parseRollout(stat.path),
      )
    }

    applyIncrementalUpdates(parsedUpdates = parsedUpdates, removedPathKeys = removedPathKeys)
  }

  private fun discoverSiblingRolloutPaths(targetCwds: Set<String>): Set<String> {
    if (targetCwds.isEmpty()) {
      return emptySet()
    }

    val directoriesToProbe = synchronized(cacheLock) {
      buildSet {
        cwdByPath.forEach { (pathKey, knownCwd) ->
          if (knownCwd !in targetCwds) {
            return@forEach
          }
          Path.of(pathKey).parent?.let(::add)
        }
      }
    }
    if (directoriesToProbe.isEmpty()) {
      return emptySet()
    }

    val discoveredPathKeys = LinkedHashSet<String>()
    for (directory in directoriesToProbe) {
      val children = try {
        Files.newDirectoryStream(directory)
      }
      catch (_: Throwable) {
        LOG.debug { "Failed to probe rollout directory $directory" }
        continue
      }

      children.use { stream ->
        for (candidate in stream) {
          if (!isRolloutPath(candidate)) {
            continue
          }
          discoveredPathKeys.add(toFileBackedSessionPathKey(candidate))
        }
      }
    }

    return discoveredPathKeys
  }

  private fun performFullScan(sessionsDir: Path) {
    val scannedFiles: Map<String, FileBackedSessionFileStat> = try {
      scanRolloutFiles(sessionsDir)
    }
    catch (_: Throwable) {
      LOG.debug { "Failed to scan rollout files under $sessionsDir" }
      return
    }

    val parsedFiles = LinkedHashMap<String, FileBackedSessionCachedFile<ParsedRolloutThread?>>(scannedFiles.size)
    for ((pathKey, stat) in scannedFiles) {
      parsedFiles[pathKey] = FileBackedSessionCachedFile(
        lastModifiedNs = stat.lastModifiedNs,
        sizeBytes = stat.sizeBytes,
        parsedValue = parseRollout(stat.path),
      )
    }

    synchronized(cacheLock) {
      cachedFilesByPath.clear()
      relatedThreadIdsByPath.clear()
      cwdByPath.clear()
      pathKeysByThreadId.clear()
      dirtyPathKeys.clear()
      pendingStatRefreshPathKeys.clear()
      cachedFilesByPath.putAll(parsedFiles)
      parsedFiles.forEach { (pathKey, cachedFile) ->
        indexPath(pathKey = pathKey, parsedThread = cachedFile.parsedValue)
      }
      initialized = true
      forceFullRescan = false
    }

    rebuildThreadsByCwd(parsedFiles)
    LOG.debug {
      "Rollout cache fully scanned (files=${parsedFiles.size})"
    }
  }

  private fun applyIncrementalUpdates(
    parsedUpdates: Map<String, FileBackedSessionCachedFile<ParsedRolloutThread?>>,
    removedPathKeys: Set<String>,
  ) {
    if (parsedUpdates.isEmpty() && removedPathKeys.isEmpty()) {
      return
    }

    val snapshot = synchronized(cacheLock) {
      removedPathKeys.forEach(::removePath)
      parsedUpdates.forEach { (pathKey, cachedFile) ->
        removePath(pathKey)
        cachedFilesByPath[pathKey] = cachedFile
        indexPath(pathKey = pathKey, parsedThread = cachedFile.parsedValue)
      }
      dirtyPathKeys.removeAll(parsedUpdates.keys)
      dirtyPathKeys.removeAll(removedPathKeys)
      pendingStatRefreshPathKeys.removeAll(parsedUpdates.keys)
      pendingStatRefreshPathKeys.removeAll(removedPathKeys)
      initialized = true
      LinkedHashMap(cachedFilesByPath)
    }

    rebuildThreadsByCwd(snapshot)
    LOG.debug {
      "Rollout cache incrementally updated (parsed=${parsedUpdates.size}, removed=${removedPathKeys.size})"
    }
  }

  private fun clearState() {
    synchronized(cacheLock) {
      cachedFilesByPath.clear()
      relatedThreadIdsByPath.clear()
      cwdByPath.clear()
      pathKeysByThreadId.clear()
      dirtyPathKeys.clear()
      pendingStatRefreshPathKeys.clear()
      initialized = false
      forceFullRescan = false
    }
    synchronized(threadsLock) {
      threadsByCwd.clear()
    }
  }

  private fun indexPath(pathKey: String, parsedThread: ParsedRolloutThread?) {
    val relatedThreadIds = parsedThread.relatedThreadIds()
    relatedThreadIdsByPath[pathKey] = relatedThreadIds
    cwdByPath[pathKey] = parsedThread?.normalizedCwd
    for (threadId in relatedThreadIds) {
      pathKeysByThreadId.getOrPut(threadId) { LinkedHashSet() }.add(pathKey)
    }
  }

  private fun removePath(pathKey: String) {
    cachedFilesByPath.remove(pathKey)
    cwdByPath.remove(pathKey)
    relatedThreadIdsByPath.remove(pathKey)?.forEach { threadId ->
      val pathKeys = pathKeysByThreadId[threadId] ?: return@forEach
      pathKeys.remove(pathKey)
      if (pathKeys.isEmpty()) {
        pathKeysByThreadId.remove(threadId)
      }
    }
  }

  private fun rebuildThreadsByCwd(
    cachedFilesByPath: Map<String, FileBackedSessionCachedFile<ParsedRolloutThread?>>,
  ) {
    synchronized(threadsLock) {
      threadsByCwd.clear()
      val parsedThreads = cachedFilesByPath.values.mapNotNull { cachedFile -> cachedFile.parsedValue }
      for ((cwd, threads) in mergeParsedRolloutThreadsByCwd(parsedThreads)) {
        threadsByCwd[cwd] = ObjectArrayList(threads)
      }
    }
  }

}

internal fun mergeParsedRolloutThreadsByCwd(parsedThreads: Collection<ParsedRolloutThread>): Map<String, List<CodexBackendThread>> {
  if (parsedThreads.isEmpty()) {
    return emptyMap()
  }

  val parsedThreadsByCwd = LinkedHashMap<String, MutableList<ParsedRolloutThread>>()
  for (parsedThread in parsedThreads) {
    parsedThreadsByCwd.getOrPut(parsedThread.normalizedCwd) { ArrayList() }.add(parsedThread)
  }

  val threadsByCwd = LinkedHashMap<String, List<CodexBackendThread>>(parsedThreadsByCwd.size)
  for ((cwd, cwdThreads) in parsedThreadsByCwd) {
    val topLevelThreads = ArrayList<CodexBackendThread>(cwdThreads.size)
    val subAgentThreadsByParent = LinkedHashMap<String, MutableList<ParsedRolloutThread>>()
    val subAgentThreads = ArrayList<ParsedRolloutThread>()

    for (parsedThread in cwdThreads) {
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
      if (childThreads.isEmpty()) {
        continue
      }

      val mergedSubAgents = LinkedHashMap<String, CodexSubAgent>(parentThread.thread.subAgents.size + childThreads.size)
      parentThread.thread.subAgents.forEach { subAgent ->
        mergedSubAgents.putIfAbsent(subAgent.id, subAgent)
      }
      val mergedSubAgentActivitiesById = LinkedHashMap<String, CodexSessionActivity>(
        parentThread.subAgentActivitiesById.size + childThreads.size
      )
      mergedSubAgentActivitiesById.putAll(parentThread.subAgentActivitiesById)
      childThreads.forEach { childThread ->
        val subAgent = CodexSubAgent(id = childThread.thread.thread.id, name = childThread.thread.thread.title)
        mergedSubAgents.putIfAbsent(subAgent.id, subAgent)
        mergedSubAgentActivitiesById.putIfAbsent(subAgent.id, childThread.thread.activity)
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
        subAgentActivitiesById = mergedSubAgentActivitiesById,
        usageSnapshots = mergedUsageSnapshots,
      )
      resolvedParentIds.add(parentThread.thread.id)
    }

    subAgentThreads.forEach { parsedThread ->
      val parentThreadId = parsedThread.parentThreadId ?: return@forEach
      if (resolvedParentIds.contains(parentThreadId)) {
        return@forEach
      }
      topLevelThreads.add(parsedThread.thread)
    }

    topLevelThreads.sortByDescending { thread -> thread.thread.updatedAt }
    threadsByCwd[cwd] = topLevelThreads
  }

  return threadsByCwd
}

internal fun CodexBackendThread.matchesRequestedThreadIds(threadIds: Set<String>): Boolean {
  return thread.id in threadIds || thread.subAgents.any { subAgent -> subAgent.id in threadIds }
}

private data class RolloutRefreshPlan(
  @JvmField val pathKeys: Set<String>,
  @JvmField val dirtyPathKeys: Set<String>,
)

private fun ParsedRolloutThread?.relatedThreadIds(): Set<String> {
  if (this == null) {
    return emptySet()
  }
  return buildSet {
    add(thread.thread.id)
    parentThreadId?.let(::add)
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
