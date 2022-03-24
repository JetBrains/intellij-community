// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.util.CollectionDelta
import java.util.concurrent.CompletableFuture

interface GHPRDetailsDataProvider {

  @get:RequiresEdt
  val loadedDetails: GHPullRequest?

  @RequiresEdt
  fun loadDetails(): CompletableFuture<GHPullRequest>

  @RequiresEdt
  fun reloadDetails()

  @RequiresEdt
  fun addDetailsReloadListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun loadDetails(disposable: Disposable, consumer: (CompletableFuture<GHPullRequest>) -> Unit) {
    addDetailsReloadListener(disposable) {
      consumer(loadDetails())
    }
    consumer(loadDetails())
  }

  @RequiresEdt
  fun addDetailsLoadedListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun updateDetails(indicator: ProgressIndicator, title: String? = null, description: String? = null): CompletableFuture<GHPullRequest>

  @RequiresEdt
  fun adjustReviewers(indicator: ProgressIndicator, delta: CollectionDelta<GHPullRequestRequestedReviewer>)
    : CompletableFuture<Unit>

  @RequiresEdt
  fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>)
    : CompletableFuture<Unit>

  @RequiresEdt
  fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>)
    : CompletableFuture<Unit>
}