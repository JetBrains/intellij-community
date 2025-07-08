// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRFilesService

internal class GHPRViewedStateDataProviderImpl(
  parentCs: CoroutineScope,
  private val filesService: GHPRFilesService,
  private val pullRequestId: GHPRIdentifier,
) : GHPRViewedStateDataProvider {
  private val cs = parentCs.childScope()

  private val loader = LoaderWithMutableCache(cs) {
    filesService.loadFiles(pullRequestId).associateBy({ it.path }, { it.viewerViewedState })
  }
  override val viewedStateNeedsReloadSignal: Flow<Unit> = loader.updatedSignal

  override suspend fun loadViewedState(): Map<String, GHPullRequestFileViewedState> = loader.load()

  override suspend fun updateViewedState(paths: Iterable<String>, isViewed: Boolean) {
    try {
      loader.updateLoaded {
        it.toMutableMap().apply {
          val newState = if (isViewed) GHPullRequestFileViewedState.VIEWED else GHPullRequestFileViewedState.UNVIEWED
          paths.forEach { path -> put(path, newState) }
        }.toMap()
      }

      coroutineScope {
        paths.map { path ->
          async {
            filesService.updateViewedState(pullRequestId, path, isViewed)
          }
        }.awaitAll()
      }
    }
    catch (ce: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw ce
    }
    catch (_: Exception) {
      signalViewedStateNeedsReload()
    }
  }

  override suspend fun signalViewedStateNeedsReload() {
    loader.clearCache()
  }
}
