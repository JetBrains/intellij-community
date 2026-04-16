// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.common.isClaudeArchivedThreadTitle
import com.intellij.agent.workbench.claude.common.resolveClaudeThreadTitleState
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThread
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionCachedFile
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionFileStat
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionInvalidationState
import com.intellij.agent.workbench.json.filebacked.toFileBackedSessionPathKey
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val LOG = logger<ClaudeThreadIndex>()

private val MAX_AGE_NS = TimeUnit.DAYS.toNanos(30)
private const val CLAUDE_SESSION_INDEX_FILE = "sessions-index.json"

internal class ClaudeThreadIndex(
  private val store: ClaudeSessionsStore,
) {
  private val threadInvalidationState =
    FileBackedSessionInvalidationState<com.intellij.agent.workbench.claude.common.ClaudeSessionThread?>(::isClaudeSessionFile)
  private val indexInvalidationState = FileBackedSessionInvalidationState<Map<String, String>>(::isClaudeSessionIndexFile)

  fun markDirty(changeSet: FileBackedSessionChangeSet) {
    if (!changeSet.requiresFullRescan && changeSet.changedPaths.isEmpty()) {
      return
    }

    val markedThreadPaths = threadInvalidationState.markDirty(changeSet)
    val markedIndexPaths = indexInvalidationState.markDirty(changeSet)
    LOG.debug {
      "Marked Claude thread cache dirty (fullRescan=${changeSet.requiresFullRescan}, threadPaths=$markedThreadPaths, indexPaths=$markedIndexPaths)"
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

    val allJsonlFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    val allIndexFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    for (directory in directories) {
      try {
        scanJsonlFiles(directory, allJsonlFiles)
        scanIndexFiles(directory, allIndexFiles)
      }
      catch (_: Throwable) {
        LOG.debug { "Failed to scan Claude session files in $directory" }
      }
    }

    val threadRescanPlan = threadInvalidationState.planRescan(allJsonlFiles)
    val indexRescanPlan = indexInvalidationState.planRescan(allIndexFiles)

    if (threadRescanPlan.filesToParse.isNotEmpty()) {
      val parsedUpdates = HashMap<String, FileBackedSessionCachedFile<com.intellij.agent.workbench.claude.common.ClaudeSessionThread?>>(
        threadRescanPlan.filesToParse.size,
      )
      for (stat in threadRescanPlan.filesToParse) {
        parsedUpdates[stat.pathKey] = FileBackedSessionCachedFile(
          lastModifiedNs = stat.lastModifiedNs,
          sizeBytes = stat.sizeBytes,
          parsedValue = store.parseJsonlFile(stat.path),
        )
      }
      threadInvalidationState.applyParsedUpdates(parsedUpdates)
    }

    if (indexRescanPlan.filesToParse.isNotEmpty()) {
      val parsedUpdates = HashMap<String, FileBackedSessionCachedFile<Map<String, String>>>(indexRescanPlan.filesToParse.size)
      for (stat in indexRescanPlan.filesToParse) {
        parsedUpdates[stat.pathKey] = FileBackedSessionCachedFile(
          lastModifiedNs = stat.lastModifiedNs,
          sizeBytes = stat.sizeBytes,
          parsedValue = store.parseSessionsIndex(stat.path),
        )
      }
      indexInvalidationState.applyParsedUpdates(parsedUpdates)
    }

    val threads = ArrayList<ClaudeBackendThread>()

    val cachedThreadsByPath = threadInvalidationState.snapshotCachedFiles()
    val cachedIndexFilesByPath = indexInvalidationState.snapshotCachedFiles()
    for (pathKey in allJsonlFiles.keys) {
      val parsed = cachedThreadsByPath[pathKey]?.parsedValue ?: continue
      val indexedTitle = resolveIndexedTitle(
        sessionFile = allJsonlFiles[pathKey]?.path,
        sessionId = parsed.id,
        cachedIndexFilesByPath = cachedIndexFilesByPath,
      )
      val resolvedTitle = resolveClaudeThreadTitleState(resolveTitleForArchiveState(parsed, indexedTitle), parsed.id)
      threads.add(
        ClaudeBackendThread(
          id = parsed.id,
          title = resolvedTitle.title,
          archived = resolvedTitle.archived,
          updatedAt = parsed.updatedAt,
          gitBranch = parsed.gitBranch,
          activity = parsed.activity,
        )
      )
    }

    threads.sortByDescending { it.updatedAt }

    LOG.debug {
      "Resolved Claude threads for project (directories=${directories.size}, jsonlFiles=${allJsonlFiles.size}, indexFiles=${allIndexFiles.size}, parsedJsonl=${threadRescanPlan.filesToParse.size}, parsedIndex=${indexRescanPlan.filesToParse.size}, total=${threads.size})"
    }

    return threads
  }
}

private fun scanJsonlFiles(directory: Path, result: MutableMap<String, FileBackedSessionFileStat>) {
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

      val pathKey = toFileBackedSessionPathKey(candidate)
      result[pathKey] = FileBackedSessionFileStat(
        pathKey = pathKey,
        path = candidate,
        lastModifiedNs = lastModifiedNs,
        sizeBytes = sizeBytes,
      )
    }
  }
}

private fun scanIndexFiles(directory: Path, result: MutableMap<String, FileBackedSessionFileStat>) {
  val candidate = directory.resolve(CLAUDE_SESSION_INDEX_FILE)
  if (!Files.isRegularFile(candidate)) {
    return
  }
  val lastModifiedNs = try {
    Files.getLastModifiedTime(candidate).to(TimeUnit.NANOSECONDS)
  }
  catch (_: Throwable) {
    return
  }
  val sizeBytes = try {
    Files.size(candidate)
  }
  catch (_: Throwable) {
    return
  }

  val pathKey = toFileBackedSessionPathKey(candidate)
  result[pathKey] = FileBackedSessionFileStat(
    pathKey = pathKey,
    path = candidate,
    lastModifiedNs = lastModifiedNs,
    sizeBytes = sizeBytes,
  )
}

private fun resolveIndexedTitle(
  sessionFile: Path?,
  sessionId: String,
  cachedIndexFilesByPath: Map<String, FileBackedSessionCachedFile<Map<String, String>>>,
): String? {
  val sessionDirectory = sessionFile?.parent ?: return null
  val indexPathKey = toFileBackedSessionPathKey(sessionDirectory.resolve(CLAUDE_SESSION_INDEX_FILE))
  return cachedIndexFilesByPath[indexPathKey]?.parsedValue?.get(sessionId)
}

private fun resolveTitleForArchiveState(
  parsed: com.intellij.agent.workbench.claude.common.ClaudeSessionThread,
  indexedTitle: String?,
): String {
  if (!parsed.hasCustomTitle) {
    return indexedTitle ?: parsed.title
  }
  if (isClaudeArchivedThreadTitle(parsed.title) || isClaudeArchivedThreadTitle(indexedTitle.orEmpty())) {
    return parsed.title
  }
  return indexedTitle ?: parsed.title
}

private fun isClaudeSessionFile(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl")
}

private fun isClaudeSessionIndexFile(path: Path): Boolean {
  return path.fileName?.toString() == CLAUDE_SESSION_INDEX_FILE
}
