// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
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

internal class ClaudeThreadIndex(
  private val store: ClaudeSessionsStore,
) {
  private val invalidationState = FileBackedSessionInvalidationState<com.intellij.agent.workbench.claude.common.ClaudeSessionThread?>(::isClaudeSessionFile)

  fun markDirty(changeSet: FileBackedSessionChangeSet) {
    if (!changeSet.requiresFullRescan && changeSet.changedPaths.isEmpty()) {
      return
    }

    val markedPaths = invalidationState.markDirty(changeSet)
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

    val allJsonlFiles = LinkedHashMap<String, FileBackedSessionFileStat>()
    for (directory in directories) {
      try {
        scanJsonlFiles(directory, allJsonlFiles)
      }
      catch (_: Throwable) {
        LOG.debug { "Failed to scan JSONL files in $directory" }
      }
    }

    val rescanPlan = invalidationState.planRescan(allJsonlFiles)

    if (rescanPlan.filesToParse.isNotEmpty()) {
      val parsedUpdates = HashMap<String, FileBackedSessionCachedFile<com.intellij.agent.workbench.claude.common.ClaudeSessionThread?>>(rescanPlan.filesToParse.size)
      for (stat in rescanPlan.filesToParse) {
        parsedUpdates[stat.pathKey] = FileBackedSessionCachedFile(
          lastModifiedNs = stat.lastModifiedNs,
          sizeBytes = stat.sizeBytes,
          parsedValue = store.parseJsonlFile(stat.path),
        )
      }
      invalidationState.applyParsedUpdates(parsedUpdates)
    }

    val threads = ArrayList<ClaudeBackendThread>()

    val cachedFilesByPath = invalidationState.snapshotCachedFiles()
    for (pathKey in allJsonlFiles.keys) {
      val parsed = cachedFilesByPath[pathKey]?.parsedValue ?: continue
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

    threads.sortByDescending { it.updatedAt }

    LOG.debug {
      "Resolved Claude threads for project (directories=${directories.size}, jsonlFiles=${allJsonlFiles.size}, parsed=${rescanPlan.filesToParse.size}, total=${threads.size})"
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

private fun isClaudeSessionFile(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl")
}
