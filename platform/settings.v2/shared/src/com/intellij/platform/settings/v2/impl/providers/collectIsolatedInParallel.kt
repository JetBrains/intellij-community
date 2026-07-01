// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

import com.intellij.openapi.diagnostic.rethrowControlFlowException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Runs [collect] on every item of [items] concurrently and flattens the results. It isolates a failure: an item whose
 * [collect] throws goes to [onFailure] and contributes nothing, and its siblings go on. So one unreadable project,
 * directory or config does not stop the rest. A control-flow exception and a cancellation still propagate, because
 * they cancel the whole load. [BaseLayerProvider.collectFromProjects] uses it per project, and `FileLayerService` of
 * the backend module per directory.
 */
suspend fun <I, O> collectIsolatedInParallel(
  items: List<I>,
  onFailure: (I, Throwable) -> Unit,
  collect: suspend (I) -> List<O>,
): List<O> = coroutineScope {
  items.map { item ->
    async {
      try {
        collect(item)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        onFailure(item, e)
        emptyList()
      }
    }
  }.awaitAll().flatten()
}
