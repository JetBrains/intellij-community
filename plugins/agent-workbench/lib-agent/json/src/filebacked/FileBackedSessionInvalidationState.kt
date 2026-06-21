// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json.filebacked

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class FileBackedSessionFileStat(
  @JvmField val pathKey: String,
  @JvmField val path: Path,
  @JvmField val lastModifiedNs: Long,
  @JvmField val sizeBytes: Long,
)

/**
 * Reads the stat (mtime, size) of [candidate] and returns a [FileBackedSessionFileStat],
 * or `null` if the file is not a regular file, cannot be stat'd, or its mtime is below
 * [minLastModifiedNs].
 */
fun buildFileBackedSessionFileStat(
  candidate: Path,
  minLastModifiedNs: Long = Long.MIN_VALUE,
): FileBackedSessionFileStat? {
  if (!Files.isRegularFile(candidate)) return null
  val lastModifiedNs = try {
    Files.getLastModifiedTime(candidate).to(TimeUnit.NANOSECONDS)
  }
  catch (_: Throwable) {
    return null
  }
  if (lastModifiedNs < minLastModifiedNs) return null
  val sizeBytes = try {
    Files.size(candidate)
  }
  catch (_: Throwable) {
    return null
  }
  val pathKey = toFileBackedSessionPathKey(candidate)
  return FileBackedSessionFileStat(
    pathKey = pathKey,
    path = candidate,
    lastModifiedNs = lastModifiedNs,
    sizeBytes = sizeBytes,
  )
}

data class FileBackedSessionCachedFile<Parsed>(
  @JvmField val lastModifiedNs: Long,
  @JvmField val sizeBytes: Long,
  @JvmField val parsedValue: Parsed,
)

data class FileBackedSessionRescanPlan(
  @JvmField val filesToParse: List<FileBackedSessionFileStat>,
  @JvmField val removedAny: Boolean,
  @JvmField val fullRescan: Boolean,
  @JvmField val dirtyPathCount: Int,
)

class FileBackedSessionInvalidationState<Parsed>(
  private val trackedPathPredicate: (Path) -> Boolean,
) {
  private val cacheLock = Any()
  private val cachedFilesByPath = LinkedHashMap<String, FileBackedSessionCachedFile<Parsed>>()
  private val dirtyPathKeys = LinkedHashSet<String>()
  private var forceFullRescan = false

  fun markDirty(changeSet: FileBackedSessionChangeSet): Int {
    if (!changeSet.requiresFullRescan && changeSet.changedPaths.isEmpty()) {
      return 0
    }

    var markedPaths = 0
    synchronized(cacheLock) {
      forceFullRescan = forceFullRescan || changeSet.requiresFullRescan
      for (path in changeSet.changedPaths) {
        if (!trackedPathPredicate(path)) continue
        if (dirtyPathKeys.add(toFileBackedSessionPathKey(path))) {
          markedPaths++
        }
      }
    }
    return markedPaths
  }

  fun planRescan(scannedFiles: Map<String, FileBackedSessionFileStat>): FileBackedSessionRescanPlan {
    val filesToParse = ArrayList<FileBackedSessionFileStat>()
    var removedAny = false
    var fullRescan = false
    var dirtyPathCount = 0

    synchronized(cacheLock) {
      val iterator = cachedFilesByPath.entries.iterator()
      while (iterator.hasNext()) {
        if (!scannedFiles.containsKey(iterator.next().key)) {
          iterator.remove()
          removedAny = true
        }
      }

      val dirtyPathsSnapshot = dirtyPathKeys.takeIf { it.isNotEmpty() }?.let(::LinkedHashSet)
      fullRescan = forceFullRescan
      dirtyPathCount = dirtyPathsSnapshot?.size ?: 0

      forceFullRescan = false
      dirtyPathKeys.clear()

      for ((pathKey, stat) in scannedFiles) {
        val cached = cachedFilesByPath[pathKey]
        val parseBecauseDirty = dirtyPathsSnapshot?.contains(pathKey) == true
        val parseBecauseStatsChanged = cached == null || cached.lastModifiedNs != stat.lastModifiedNs || cached.sizeBytes != stat.sizeBytes
        if (fullRescan || parseBecauseDirty || parseBecauseStatsChanged) {
          filesToParse.add(stat)
        }
      }
    }

    return FileBackedSessionRescanPlan(
      filesToParse = filesToParse,
      removedAny = removedAny,
      fullRescan = fullRescan,
      dirtyPathCount = dirtyPathCount,
    )
  }

  fun applyParsedUpdates(parsedUpdates: Map<String, FileBackedSessionCachedFile<Parsed>>) {
    if (parsedUpdates.isEmpty()) return

    synchronized(cacheLock) {
      cachedFilesByPath.putAll(parsedUpdates)
    }
  }

  fun snapshotCachedFiles(): Map<String, FileBackedSessionCachedFile<Parsed>> {
    return synchronized(cacheLock) {
      LinkedHashMap(cachedFilesByPath)
    }
  }

  fun clear() {
    synchronized(cacheLock) {
      cachedFilesByPath.clear()
      dirtyPathKeys.clear()
      forceFullRescan = false
    }
  }
}
