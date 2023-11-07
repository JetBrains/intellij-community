// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRFilesService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

internal class GHPRViewedStateDataProviderImpl(
  private val filesService: GHPRFilesService,
  private val pullRequestId: GHPRIdentifier,
) : GHPRViewedStateDataProvider,
    Disposable {

  private val viewedState = LazyCancellableBackgroundProcessValue.create { indicator ->
    filesService
      .loadFiles(indicator, pullRequestId)
      .thenApply { files -> files.associateBy({ it.path }, { it.viewerViewedState }) }
  }

  override fun loadViewedState(): CompletableFuture<Map<String, GHPullRequestFileViewedState>> = viewedState.value

  override fun getViewedState(): Map<String, GHPullRequestFileViewedState>? {
    if (!viewedState.isCached) return null

    return runCatching { viewedState.value.getNow(emptyMap()) }.getOrDefault(emptyMap())
  }

  override fun updateViewedState(path: String, isViewed: Boolean) {
    val result = filesService.updateViewedState(EmptyProgressIndicator(), pullRequestId, path, isViewed)

    viewedState.combineResult(result) { files, _ ->
      val newState = if (isViewed) GHPullRequestFileViewedState.VIEWED else GHPullRequestFileViewedState.UNVIEWED

      files + (path to newState)
    }
  }

  override fun addViewedStateListener(parent: Disposable, listener: () -> Unit) =
    viewedState.addDropEventListener(parent, listener)

  override fun reset() = viewedState.drop()

  override fun dispose() = reset()
}