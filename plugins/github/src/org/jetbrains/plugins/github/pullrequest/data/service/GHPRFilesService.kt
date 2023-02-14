// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestChangedFile
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.concurrent.CompletableFuture

interface GHPRFilesService {

  @CalledInAny
  fun loadFiles(
    progressIndicator: ProgressIndicator,
    pullRequestId: GHPRIdentifier
  ): CompletableFuture<List<GHPullRequestChangedFile>>

  @CalledInAny
  fun updateViewedState(
    progressIndicator: ProgressIndicator,
    pullRequestId: GHPRIdentifier,
    path: String,
    isViewed: Boolean
  ): CompletableFuture<Unit>
}