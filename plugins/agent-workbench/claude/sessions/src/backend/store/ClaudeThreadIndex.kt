// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThread
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<ClaudeThreadIndex>()

private val MAX_AGE_NS = TimeUnit.DAYS.toNanos(30)

internal class ClaudeThreadIndex(
  private val store: ClaudeSessionsStore,
) {
  private val cacheLock = Any()
  private var state = State()

  fun markDirty(changeSet: ClaudeChangeSet) {
    if (!changeSet.requiresFullRescan && changeSet.changedJsonlPaths.isEmpty()) {
      return
    }

    var markedPaths = 0
    synchronized(cacheLock) {
      val newDirtyKeys = LinkedHashSet(state.dirtyPathKeys)
      for (path in changeSet.changedJsonlPaths) {
        val fileName = path.fileName?.toString() ?: continue
        if (!fileName.endsWith(".jsonl")) continue
        if (newDirtyKeys.add(toPathKey(path))) {
          markedPaths++
        }
      }

      state = state.copy(
        forceFullRescan = state.forceFullRescan || changeSet.requiresFullRescan,
        dirtyPathKeys = newDirtyKeys,
      )
    }
    LOG.debug {
      "Marked Claude thread cache dirty (fullRescan=${changeSet.requiresFullRescan}, paths=$markedPaths)"
    }
  }

  fun collectByProject(projectPath: String): List<ClaudeBackendThread> {
    val directories = try {
      store.findMatchingDirectories(projectPath)
    }
    catch (_: Throwable) {
      LOG.debug { "Failed to find matching directories for $projectPath" }
      return emptyList()
    }

    if (directories.isEmpty()) {
      return emptyList()
    }

    val allJsonlFiles = LinkedHashMap<String, JsonlFileStat>()
    for (directory in directories) {
      try {
        scanJsonlFiles(directory, allJsonlFiles)
      }
      catch (_: Throwable) {
        LOG.debug { "Failed to scan JSONL files in $directory" }
      }
    }

    val filesToParse = ArrayList<JsonlFileStat>()
    collectChangedFiles(allJsonlFiles, filesToParse)

    if (filesToParse.isNotEmpty()) {
      val parsedUpdates = HashMap<String, CachedClaudeFile>(filesToParse.size)
      for (stat in filesToParse) {
        parsedUpdates[stat.pathKey] = CachedClaudeFile(
          lastModifiedNs = stat.lastModifiedNs,
          sizeBytes = stat.sizeBytes,
          parsedThread = store.parseJsonlFile(stat.path),
        )
      }
      synchronized(cacheLock) {
        state = state.copy(cachedFilesByPath = state.cachedFilesByPath + parsedUpdates)
      }
    }

    val threads = ArrayList<ClaudeBackendThread>()

    synchronized(cacheLock) {
      val cachedFilesByPath = state.cachedFilesByPath
      for (pathKey in allJsonlFiles.keys) {
        val parsed = cachedFilesByPath[pathKey]?.parsedThread ?: continue
        threads.add(
          ClaudeBackendThread(
            id = parsed.id,
            title = parsed.title,
            updatedAt = parsed.updatedAt,
            gitBranch = parsed.gitBranch,
            activity = parsed.activity,
          )
        )
      }
    }

    threads.sortByDescending { it.updatedAt }

    LOG.debug {
      "Resolved Claude threads for project (directories=${directories.size}, jsonlFiles=${allJsonlFiles.size}, parsed=${filesToParse.size}, total=${threads.size})"
    }

    return threads
  }

  private fun collectChangedFiles(
    scannedFiles: Map<String, JsonlFileStat>,
    filesToParse: MutableList<JsonlFileStat>,
  ) {
    synchronized(cacheLock) {
      val currentCache = state.cachedFilesByPath
      val needsPruning = currentCache.keys.any { !scannedFiles.containsKey(it) }
      val prunedCache = if (needsPruning) currentCache.filterKeys { scannedFiles.containsKey(it) } else currentCache

      val dirtyPathsSnapshot = state.dirtyPathKeys.takeIf { it.isNotEmpty() }
      val fullRescan = state.forceFullRescan

      state = state.copy(
        cachedFilesByPath = prunedCache,
        forceFullRescan = false,
        dirtyPathKeys = emptySet(),
      )

      for ((pathKey, stat) in scannedFiles) {
        val cached = prunedCache[pathKey]
        val parseBecauseDirty = dirtyPathsSnapshot?.contains(pathKey) == true
        val parseBecauseStatsChanged = cached == null || cached.lastModifiedNs != stat.lastModifiedNs || cached.sizeBytes != stat.sizeBytes
        if (fullRescan || parseBecauseDirty || parseBecauseStatsChanged) {
          filesToParse.add(stat)
        }
      }
    }
  }
}

private fun scanJsonlFiles(directory: Path, result: MutableMap<String, JsonlFileStat>) {
  val cutoffNs = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - MAX_AGE_NS
  Files.newDirectoryStream(directory, "*.jsonl").use { files ->
    for (candidate in files) {
      if (!Files.isRegularFile(candidate)) continue
      val lastModifiedNs = try {
        Files.getLastModifiedTime(candidate).to(TimeUnit.NANOSECONDS)
      }
      catch (_: Throwable) {
        continue
      }
      if (lastModifiedNs < cutoffNs) continue
      val sizeBytes = try {
        Files.size(candidate)
      }
      catch (_: Throwable) {
        continue
      }

      val pathKey = toPathKey(candidate)
      result[pathKey] = JsonlFileStat(
        pathKey = pathKey,
        path = candidate,
        lastModifiedNs = lastModifiedNs,
        sizeBytes = sizeBytes,
      )
    }
  }
}

private fun toPathKey(path: Path): String {
  return normalizeFilePath(path).invariantSeparatorsPathString
}

internal fun normalizeFilePath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}

private data class State(
  val cachedFilesByPath: Map<String, CachedClaudeFile> = emptyMap(),
  val dirtyPathKeys: Set<String> = emptySet(),
  val forceFullRescan: Boolean = false,
)

private data class JsonlFileStat(
  @JvmField val pathKey: String,
  @JvmField val path: Path,
  @JvmField val lastModifiedNs: Long,
  @JvmField val sizeBytes: Long,
)

private data class CachedClaudeFile(
  @JvmField val lastModifiedNs: Long,
  @JvmField val sizeBytes: Long,
  @JvmField val parsedThread: com.intellij.agent.workbench.claude.common.ClaudeSessionThread?,
)
