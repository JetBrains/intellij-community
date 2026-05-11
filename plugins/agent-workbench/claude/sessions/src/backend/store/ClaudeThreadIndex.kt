// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.common.ClaudeSessionIndexEntry
import com.intellij.agent.workbench.claude.common.ClaudeSessionThread
import com.intellij.agent.workbench.claude.common.ClaudeSessionTitleSource
import com.intellij.agent.workbench.claude.common.isClaudeArchivedThreadTitle
import com.intellij.agent.workbench.claude.common.resolveClaudeThreadTitleState
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThread
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionCachedFile
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionFileStat
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionInvalidationState
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionRescanPlan
import com.intellij.agent.workbench.json.filebacked.buildFileBackedSessionFileStat
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
    FileBackedSessionInvalidationState<ClaudeSessionThread?>(::isClaudeSessionFile)
  private val indexInvalidationState = FileBackedSessionInvalidationState<Map<String, ClaudeSessionIndexEntry>>(::isClaudeSessionIndexFile)

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
    applyThreadRescan(threadRescanPlan)
    applyIndexRescan(indexRescanPlan)
    val threads = buildBackendThreads(allJsonlFiles)

    LOG.debug {
      "Resolved Claude threads for project (directories=${directories.size}, jsonlFiles=${allJsonlFiles.size}, indexFiles=${allIndexFiles.size}, parsedJsonl=${threadRescanPlan.filesToParse.size}, parsedIndex=${indexRescanPlan.filesToParse.size}, total=${threads.size})"
    }

    return threads
  }

  fun collectByProjectAndSessionIds(projectPath: String, sessionIds: Set<String>): List<ClaudeBackendThread> {
    val normalizedSessionIds = sessionIds
      .asSequence()
      .map(String::trim)
      .filter { it.isNotEmpty() && '/' !in it && '\\' !in it }
      .toCollection(LinkedHashSet())
    if (normalizedSessionIds.isEmpty()) {
      return emptyList()
    }

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

    val jsonlFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    val indexFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    for (directory in directories) {
      try {
        scanIndexFiles(directory, indexFiles)
        for (sessionId in normalizedSessionIds) {
          val stat = buildFileBackedSessionFileStat(directory.resolve("$sessionId.jsonl")) ?: continue
          jsonlFiles[stat.pathKey] = stat
        }
      }
      catch (_: Throwable) {
        LOG.debug { "Failed to resolve Claude session files in $directory" }
      }
    }

    if (jsonlFiles.isEmpty()) {
      return emptyList()
    }

    val threadRescanPlan = threadInvalidationState.planRescan(jsonlFiles)
    val indexRescanPlan = indexInvalidationState.planRescan(indexFiles)
    applyThreadRescan(threadRescanPlan)
    applyIndexRescan(indexRescanPlan)
    val threads = buildBackendThreads(jsonlFiles)

    LOG.debug {
      "Resolved Claude thread subset for project (directories=${directories.size}, requestedSessions=${normalizedSessionIds.size}, " +
      "jsonlFiles=${jsonlFiles.size}, indexFiles=${indexFiles.size}, parsedJsonl=${threadRescanPlan.filesToParse.size}, " +
      "parsedIndex=${indexRescanPlan.filesToParse.size}, total=${threads.size})"
    }

    return threads
  }

  private fun applyThreadRescan(threadRescanPlan: FileBackedSessionRescanPlan) {
    if (threadRescanPlan.filesToParse.isEmpty()) {
      return
    }

    val parsedUpdates = HashMap<String, FileBackedSessionCachedFile<ClaudeSessionThread?>>(
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

  private fun applyIndexRescan(indexRescanPlan: FileBackedSessionRescanPlan) {
    if (indexRescanPlan.filesToParse.isEmpty()) {
      return
    }

    val parsedUpdates =
      HashMap<String, FileBackedSessionCachedFile<Map<String, ClaudeSessionIndexEntry>>>(indexRescanPlan.filesToParse.size)
    for (stat in indexRescanPlan.filesToParse) {
      parsedUpdates[stat.pathKey] = FileBackedSessionCachedFile(
        lastModifiedNs = stat.lastModifiedNs,
        sizeBytes = stat.sizeBytes,
        parsedValue = store.parseSessionsIndex(stat.path),
      )
    }
    indexInvalidationState.applyParsedUpdates(parsedUpdates)
  }

  private fun buildBackendThreads(jsonlFilesByPath: Map<String, FileBackedSessionFileStat>): List<ClaudeBackendThread> {
    val threads = ArrayList<ClaudeBackendThread>()
    val cachedThreadsByPath = threadInvalidationState.snapshotCachedFiles()
    val cachedIndexFilesByPath = indexInvalidationState.snapshotCachedFiles()
    for (pathKey in jsonlFilesByPath.keys) {
      val parsed = cachedThreadsByPath[pathKey]?.parsedValue ?: continue
      val indexEntry = resolveIndexEntry(
        sessionFile = jsonlFilesByPath[pathKey]?.path,
        sessionId = parsed.id,
        cachedIndexFilesByPath = cachedIndexFilesByPath,
      )
      val resolvedTitle = resolveClaudeThreadTitleState(resolveTitleForArchiveState(parsed, indexEntry), parsed.id)
      threads.add(
        ClaudeBackendThread(
          id = parsed.id,
          title = resolvedTitle.title,
          archived = resolvedTitle.archived,
          updatedAt = parsed.updatedAt,
          gitBranch = parsed.gitBranch ?: indexEntry?.gitBranch,
          activity = parsed.activity,
          awaitingAssistantTurn = parsed.awaitingAssistantTurn,
        )
      )
    }
    threads.sortByDescending { it.updatedAt }
    return threads
  }
}

private fun scanJsonlFiles(directory: Path, result: MutableMap<String, FileBackedSessionFileStat>) {
  val cutoffNs = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - MAX_AGE_NS
  Files.newDirectoryStream(directory, "*.jsonl").use { files ->
    for (candidate in files) {
      val stat = buildFileBackedSessionFileStat(candidate, minLastModifiedNs = cutoffNs) ?: continue
      result[stat.pathKey] = stat
    }
  }
}

private fun scanIndexFiles(directory: Path, result: MutableMap<String, FileBackedSessionFileStat>) {
  val stat = buildFileBackedSessionFileStat(directory.resolve(CLAUDE_SESSION_INDEX_FILE)) ?: return
  result[stat.pathKey] = stat
}

private fun resolveIndexEntry(
  sessionFile: Path?,
  sessionId: String,
  cachedIndexFilesByPath: Map<String, FileBackedSessionCachedFile<Map<String, ClaudeSessionIndexEntry>>>,
): ClaudeSessionIndexEntry? {
  val sessionDirectory = sessionFile?.parent ?: return null
  val indexPathKey = toFileBackedSessionPathKey(sessionDirectory.resolve(CLAUDE_SESSION_INDEX_FILE))
  return cachedIndexFilesByPath[indexPathKey]?.parsedValue?.get(sessionId)
}

private fun resolveTitleForArchiveState(
  parsed: ClaudeSessionThread,
  indexEntry: ClaudeSessionIndexEntry?,
): String {
  val indexedTitle = resolveIndexedTitle(parsed, indexEntry)
  if (!parsed.hasCustomTitle) {
    return indexedTitle ?: parsed.title
  }
  if (isClaudeArchivedThreadTitle(parsed.title) || isClaudeArchivedThreadTitle(indexedTitle.orEmpty())) {
    return parsed.title
  }
  return indexedTitle ?: parsed.title
}

private fun resolveIndexedTitle(parsed: ClaudeSessionThread, indexEntry: ClaudeSessionIndexEntry?): String? {
  return indexEntry?.summary ?: if (parsed.titleSource == ClaudeSessionTitleSource.DEFAULT) indexEntry?.firstPrompt else null
}

private fun isClaudeSessionFile(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl")
}

private fun isClaudeSessionIndexFile(path: Path): Boolean {
  return path.fileName?.toString() == CLAUDE_SESSION_INDEX_FILE
}
