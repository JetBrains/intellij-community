// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json.filebacked

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun createFileBackedSessionChangeFlow(
  logger: Logger,
  watcherName: String,
  initContext: (() -> String)? = null,
  emitInitialRefreshPing: Boolean = false,
  createWatcher: (CoroutineScope, onChange: (FileBackedSessionChangeSet) -> Unit) -> AutoCloseable?,
): Flow<FileBackedSessionChangeSet> = callbackFlow {
  val context = initContext?.invoke()?.takeUnless { it.isBlank() }
  logger.debug {
    "Initializing $watcherName updates watcher${context?.let { " ($it)" } ?: ""}"
  }

  val watcher = runCatching {
    createWatcher(this) { changeSet ->
      logger.debug {
        "$watcherName watcher signaled change; emitting update (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedPaths.size})"
      }
      trySend(changeSet)
    }
  }.onFailure { t ->
    logger.warn("Failed to initialize $watcherName watcher", t)
  }.getOrNull()

  if (watcher == null) {
    logger.debug { "$watcherName updates watcher was not initialized; updates flow will stay idle" }
    awaitClose { }
    return@callbackFlow
  }

  if (emitInitialRefreshPing) {
    val initialRefreshEmitted = trySend(FileBackedSessionChangeSet()).isSuccess
    logger.debug {
      "$watcherName watcher initialized; initial refresh ping emitted=$initialRefreshEmitted"
    }
  }

  awaitClose {
    logger.debug { "Closing $watcherName updates watcher" }
    watcher.close()
  }
}
