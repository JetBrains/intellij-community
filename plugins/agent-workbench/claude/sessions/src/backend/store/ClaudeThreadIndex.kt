// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.common.ClaudeSessionIndexEntry
import com.intellij.agent.workbench.claude.common.ClaudeSessionThread
import com.intellij.agent.workbench.claude.common.ClaudeSessionTitleSource
import com.intellij.agent.workbench.claude.common.ClaudeUsageSnapshot
import com.intellij.agent.workbench.claude.common.ClaudeSessionUsageFile
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
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
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
  private val usageInvalidationState =
    FileBackedSessionInvalidationState<ClaudeSessionUsageFile?>(::isClaudeSessionFile)
  private val indexInvalidationState = FileBackedSessionInvalidationState<Map<String, ClaudeSessionIndexEntry>>(::isClaudeSessionIndexFile)

  fun markDirty(changeSet: FileBackedSessionChangeSet) {
    if (!changeSet.requiresFullRescan && changeSet.changedPaths.isEmpty()) {
      return
    }

    val markedThreadPaths = threadInvalidationState.markDirty(changeSet)
    val markedUsagePaths = usageInvalidationState.markDirty(changeSet)
    val markedIndexPaths = indexInvalidationState.markDirty(changeSet)
    LOG.debug {
      "Marked Claude thread cache dirty (fullRescan=${changeSet.requiresFullRescan}, threadPaths=$markedThreadPaths, usagePaths=$markedUsagePaths, indexPaths=$markedIndexPaths)"
    }
  }

  fun snapshotCachedFiles(): Map<String, FileBackedSessionCachedFile<ClaudeSessionThread?>> {
    return threadInvalidationState.snapshotCachedFiles()
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
    val allUsageFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    val allIndexFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    for (directory in directories) {
      try {
        scanJsonlFiles(directory, allJsonlFiles)
        scanUsageJsonlFiles(directory, allUsageFiles)
        scanIndexFiles(directory, allIndexFiles)
      }
      catch (_: Throwable) {
        LOG.debug { "Failed to scan Claude session files in $directory" }
      }
    }

    val threadRescanPlan = threadInvalidationState.planRescan(allJsonlFiles)
    val usageRescanPlan = usageInvalidationState.planRescan(allUsageFiles)
    val indexRescanPlan = indexInvalidationState.planRescan(allIndexFiles)
    applyThreadRescan(threadRescanPlan)
    applyUsageRescan(usageRescanPlan)
    applyIndexRescan(indexRescanPlan)
    val threads = buildBackendThreads(jsonlFilesByPath = allJsonlFiles, usageFilesByPath = allUsageFiles)

    LOG.debug {
      "Resolved Claude threads for project (directories=${directories.size}, jsonlFiles=${allJsonlFiles.size}, usageFiles=${allUsageFiles.size}, indexFiles=${allIndexFiles.size}, parsedJsonl=${threadRescanPlan.filesToParse.size}, parsedUsage=${usageRescanPlan.filesToParse.size}, parsedIndex=${indexRescanPlan.filesToParse.size}, total=${threads.size})"
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
    val usageFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    val indexFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    for (directory in directories) {
      try {
        scanIndexFiles(directory, indexFiles)
        for (sessionId in normalizedSessionIds) {
          val stat = buildFileBackedSessionFileStat(directory.resolve("$sessionId.jsonl")) ?: continue
          jsonlFiles[stat.pathKey] = stat
          scanUsageJsonlFilesForSession(directory = directory, sessionId = sessionId, result = usageFiles)
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
    val usageRescanPlan = usageInvalidationState.planRescan(usageFiles)
    val indexRescanPlan = indexInvalidationState.planRescan(indexFiles)
    applyThreadRescan(threadRescanPlan)
    applyUsageRescan(usageRescanPlan)
    applyIndexRescan(indexRescanPlan)
    val threads = buildBackendThreads(jsonlFilesByPath = jsonlFiles, usageFilesByPath = usageFiles)

    LOG.debug {
      "Resolved Claude thread subset for project (directories=${directories.size}, requestedSessions=${normalizedSessionIds.size}, " +
      "jsonlFiles=${jsonlFiles.size}, usageFiles=${usageFiles.size}, indexFiles=${indexFiles.size}, parsedJsonl=${threadRescanPlan.filesToParse.size}, " +
      "parsedUsage=${usageRescanPlan.filesToParse.size}, parsedIndex=${indexRescanPlan.filesToParse.size}, total=${threads.size})"
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

  private fun applyUsageRescan(usageRescanPlan: FileBackedSessionRescanPlan) {
    if (usageRescanPlan.filesToParse.isEmpty()) {
      return
    }

    val parsedUpdates = HashMap<String, FileBackedSessionCachedFile<ClaudeSessionUsageFile?>>(
      usageRescanPlan.filesToParse.size,
    )
    for (stat in usageRescanPlan.filesToParse) {
      parsedUpdates[stat.pathKey] = FileBackedSessionCachedFile(
        lastModifiedNs = stat.lastModifiedNs,
        sizeBytes = stat.sizeBytes,
        parsedValue = store.parseUsageJsonlFile(stat.path),
      )
    }
    usageInvalidationState.applyParsedUpdates(parsedUpdates)
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

  private fun buildBackendThreads(
    jsonlFilesByPath: Map<String, FileBackedSessionFileStat>,
    usageFilesByPath: Map<String, FileBackedSessionFileStat>,
  ): List<ClaudeBackendThread> {
    val threads = ArrayList<ClaudeBackendThread>()
    val cachedThreadsByPath = threadInvalidationState.snapshotCachedFiles()
    val cachedUsageFilesByPath = usageInvalidationState.snapshotCachedFiles()
    val cachedIndexFilesByPath = indexInvalidationState.snapshotCachedFiles()
    val usageSnapshotsBySessionId = buildUsageSnapshotsBySessionId(
      usageFilesByPath = usageFilesByPath,
      cachedUsageFilesByPath = cachedUsageFilesByPath,
    )
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
          usageSnapshots = usageSnapshotsBySessionId[parsed.id].orEmpty(),
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
      addSessionFileStat(candidate = candidate, cutoffNs = cutoffNs, result = result)
    }
  }
}

private fun scanUsageJsonlFiles(directory: Path, result: MutableMap<String, FileBackedSessionFileStat>) {
  val cutoffNs = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - MAX_AGE_NS
  Files.newDirectoryStream(directory, "*.jsonl").use { files ->
    for (candidate in files) {
      addSessionFileStat(candidate = candidate, cutoffNs = cutoffNs, result = result)
    }
  }

  Files.newDirectoryStream(directory).use { entries ->
    for (entry in entries) {
      if (!Files.isDirectory(entry)) continue
      val subagentsDir = entry.resolve("subagents")
      if (!Files.isDirectory(subagentsDir)) continue
      Files.newDirectoryStream(subagentsDir, "*.jsonl").use { subagentFiles ->
        for (candidate in subagentFiles) {
          addSessionFileStat(candidate = candidate, cutoffNs = cutoffNs, result = result)
        }
      }
    }
  }
}

private fun scanUsageJsonlFilesForSession(
  directory: Path,
  sessionId: String,
  result: MutableMap<String, FileBackedSessionFileStat>,
) {
  val cutoffNs = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - MAX_AGE_NS
  addSessionFileStat(candidate = directory.resolve("$sessionId.jsonl"), cutoffNs = cutoffNs, result = result)
  val subagentsDir = directory.resolve(sessionId).resolve("subagents")
  if (!Files.isDirectory(subagentsDir)) {
    return
  }

  Files.newDirectoryStream(subagentsDir, "*.jsonl").use { files ->
    for (candidate in files) {
      addSessionFileStat(candidate = candidate, cutoffNs = cutoffNs, result = result)
    }
  }
}

private fun scanIndexFiles(directory: Path, result: MutableMap<String, FileBackedSessionFileStat>) {
  val stat = buildFileBackedSessionFileStat(directory.resolve(CLAUDE_SESSION_INDEX_FILE)) ?: return
  result[stat.pathKey] = stat
}

private fun addSessionFileStat(
  candidate: Path,
  cutoffNs: Long,
  result: MutableMap<String, FileBackedSessionFileStat>,
) {
  val stat = buildFileBackedSessionFileStat(candidate, minLastModifiedNs = cutoffNs) ?: return
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

private fun buildUsageSnapshotsBySessionId(
  usageFilesByPath: Map<String, FileBackedSessionFileStat>,
  cachedUsageFilesByPath: Map<String, FileBackedSessionCachedFile<ClaudeSessionUsageFile?>>,
): Map<String, List<AgentSessionUsageSnapshot>> {
  if (usageFilesByPath.isEmpty()) return emptyMap()

  val usageBySessionId = LinkedHashMap<String, MutableMap<String?, UsageSnapshotAccumulator>>()
  for (pathKey in usageFilesByPath.keys) {
    val parsed = cachedUsageFilesByPath[pathKey]?.parsedValue ?: continue
    val usageByModelId = usageBySessionId.getOrPut(parsed.sessionId) { LinkedHashMap() }
    for (snapshot in parsed.usageSnapshots) {
      usageByModelId.getOrPut(snapshot.modelId) { UsageSnapshotAccumulator(modelId = snapshot.modelId) }.add(snapshot)
    }
  }

  return usageBySessionId.mapValues { (_, usageByModelId) ->
    usageByModelId.values.map(UsageSnapshotAccumulator::toSnapshot)
  }
}

private data class UsageSnapshotAccumulator(
  @JvmField var modelId: String?,
  @JvmField var inputTokens: Long = 0,
  @JvmField var outputTokens: Long = 0,
  @JvmField var cacheReadTokens: Long = 0,
  @JvmField var cacheWriteTokens: Long = 0,
  @JvmField var requestCount: Long = 0,
) {
  fun add(snapshot: ClaudeUsageSnapshot) {
    if (modelId == null) {
      modelId = snapshot.modelId
    }
    inputTokens += snapshot.inputTokens
    outputTokens += snapshot.outputTokens
    cacheReadTokens += snapshot.cacheReadTokens
    cacheWriteTokens += snapshot.cacheWriteTokens
    requestCount += snapshot.requestCount
  }

  fun toSnapshot(): AgentSessionUsageSnapshot {
    return AgentSessionUsageSnapshot(
      modelId = modelId,
      inputTokens = inputTokens,
      outputTokens = outputTokens,
      cacheReadTokens = cacheReadTokens,
      cacheWriteTokens = cacheWriteTokens,
      requestCount = requestCount,
    )
  }
}

private fun isClaudeSessionFile(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl")
}

private fun isClaudeSessionIndexFile(path: Path): Boolean {
  return path.fileName?.toString() == CLAUDE_SESSION_INDEX_FILE
}
