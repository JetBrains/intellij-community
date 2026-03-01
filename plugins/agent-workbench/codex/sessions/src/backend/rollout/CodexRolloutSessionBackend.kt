// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.codex.sessions.backend.rollout

// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<CodexRolloutSessionBackend>()

internal class CodexRolloutSessionBackend(
  private val codexHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".codex") },
  rolloutChangeSource: (() -> Flow<CodexRolloutChangeSet>)? = null,
) : CodexSessionBackend {
  private val parser = CodexRolloutParser()
  private val threadIndex = CodexRolloutThreadIndex(codexHomeProvider = codexHomeProvider, parser = parser)

  override val updates: Flow<Unit> = (rolloutChangeSource?.invoke() ?: createWatcherUpdates())
    .map { changeSet ->
      threadIndex.markDirty(changeSet)
    }
    .conflate()

  private fun createWatcherUpdates(): Flow<CodexRolloutChangeSet> = callbackFlow {
    LOG.debug { "Initializing Codex rollout updates watcher (codexHome=${codexHomeProvider()})" }
    val watcher = runCatching {
      CodexRolloutSessionsWatcher(
        codexHomeProvider = codexHomeProvider,
        scope = this,
      ) { changeSet ->
        LOG.debug {
          "Rollout watcher signaled change; emitting update (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedRolloutPaths.size})"
        }
        trySend(changeSet)
      }
    }.onFailure { t ->
      LOG.warn("Failed to initialize Codex rollout watcher", t)
    }.getOrNull()

    if (watcher == null) {
      LOG.debug { "Codex rollout updates watcher was not initialized; updates flow will stay idle" }
      awaitClose { }
      return@callbackFlow
    }

    val initialRefreshEmitted = trySend(CodexRolloutChangeSet()).isSuccess
    LOG.debug {
      "Codex rollout watcher initialized; initial refresh ping emitted=$initialRefreshEmitted"
    }

    awaitClose {
      LOG.debug { "Closing Codex rollout updates watcher" }
      watcher.close()
    }
  }

  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    return withContext(Dispatchers.IO) {
      val workingDirectory = resolveProjectDirectoryFromPath(path)
        ?: return@withContext emptyList()
      val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
      threadIndex.collectByCwd(setOf(cwdFilter))[cwdFilter].orEmpty()
    }
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> {
    return withContext(Dispatchers.IO) {
      val pathFilters = resolvePathFilters(paths)
      if (pathFilters.isEmpty()) return@withContext emptyMap()

      val threadsByCwd = threadIndex.collectByCwd(pathFilters.mapTo(HashSet(pathFilters.size)) { (_, cwdFilter) -> cwdFilter })
      pathFilters.associate { (path, cwdFilter) ->
        path to threadsByCwd.get(cwdFilter).orEmpty()
      }
    }
  }
}

private fun resolvePathFilters(paths: List<String>): List<Pair<String, String>> {
  return paths.mapNotNull { path ->
    resolveProjectDirectoryFromPath(path)?.let { directory ->
      path to normalizeRootPath(directory.invariantSeparatorsPathString)
    }
  }
}
