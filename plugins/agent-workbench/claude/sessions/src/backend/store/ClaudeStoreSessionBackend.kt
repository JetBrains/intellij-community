// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThread
import com.intellij.agent.workbench.claude.sessions.ClaudeSessionBackend
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

private val LOG = logger<ClaudeStoreSessionBackend>()

internal class ClaudeStoreSessionBackend(
  private val claudeHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".claude") },
  changeSource: (() -> Flow<ClaudeChangeSet>)? = null,
) : ClaudeSessionBackend {
  private val store = ClaudeSessionsStore(claudeHomeProvider)
  private val threadIndex = ClaudeThreadIndex(store = store)

  override val updates: Flow<Unit> = (changeSource?.invoke() ?: createWatcherUpdates())
    .map { changeSet ->
      threadIndex.markDirty(changeSet)
    }
    .conflate()

  private fun createWatcherUpdates(): Flow<ClaudeChangeSet> = callbackFlow {
    LOG.debug { "Initializing Claude sessions updates watcher (claudeHome=${claudeHomeProvider()})" }
    val watcher = runCatching {
      ClaudeSessionsWatcher(
        claudeHomeProvider = claudeHomeProvider,
        scope = this,
      ) { changeSet ->
        LOG.debug {
          "Claude watcher signaled change; emitting update (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedJsonlPaths.size})"
        }
        trySend(changeSet)
      }
    }.onFailure { t ->
      LOG.warn("Failed to initialize Claude sessions watcher", t)
    }.getOrNull()

    if (watcher == null) {
      LOG.debug { "Claude sessions watcher was not initialized; updates flow will stay idle" }
      awaitClose { }
      return@callbackFlow
    }

    awaitClose {
      LOG.debug { "Closing Claude sessions updates watcher" }
      watcher.close()
    }
  }

  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<ClaudeBackendThread> {
    return withContext(Dispatchers.IO) {
      threadIndex.collectByProject(path)
    }
  }
}
