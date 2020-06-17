// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.util.CollectionDelta
import java.util.concurrent.CompletableFuture

interface GHPRDetailsDataProvider {

  @get:CalledInAwt
  val loadedDetails: GHPullRequest?

  @CalledInAwt
  fun loadDetails(): CompletableFuture<GHPullRequest>

  @CalledInAwt
  fun reloadDetails()

  @CalledInAwt
  fun addDetailsReloadListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun loadDetails(disposable: Disposable, consumer: (CompletableFuture<GHPullRequest>) -> Unit) {
    addDetailsReloadListener(disposable) {
      consumer(loadDetails())
    }
    consumer(loadDetails())
  }

  @CalledInAwt
  fun addDetailsLoadedListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun getDescriptionMarkdownBody(indicator: ProgressIndicator): CompletableFuture<String>

  @CalledInAwt
  fun updateDetails(indicator: ProgressIndicator, description: String? = null): CompletableFuture<GHPullRequest>

  @CalledInAwt
  fun adjustReviewers(indicator: ProgressIndicator, delta: CollectionDelta<GHPullRequestRequestedReviewer>)
    : CompletableFuture<Unit>

  @CalledInAwt
  fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>)
    : CompletableFuture<Unit>

  @CalledInAwt
  fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>)
    : CompletableFuture<Unit>
}