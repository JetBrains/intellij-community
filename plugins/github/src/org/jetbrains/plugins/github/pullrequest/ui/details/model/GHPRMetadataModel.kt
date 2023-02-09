// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import java.util.concurrent.CompletableFuture

interface GHPRMetadataModel {
  val assignees: List<GHUser>
  val reviewers: List<GHPullRequestRequestedReviewer>
  val labels: List<GHLabel>
  val reviews: List<GHPullRequestReview>

  val isEditingAllowed: Boolean

  fun getAuthor(): GHUser?

  fun loadPotentialReviewers(): CompletableFuture<List<GHPullRequestRequestedReviewer>>
  fun adjustReviewers(indicator: ProgressIndicator, delta: CollectionDelta<GHPullRequestRequestedReviewer>): CompletableFuture<Unit>

  fun loadPotentialAssignees(): CompletableFuture<List<GHUser>>
  fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>): CompletableFuture<Unit>

  fun loadAssignableLabels(): CompletableFuture<List<GHLabel>>
  fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>): CompletableFuture<Unit>

  fun addAndInvokeChangesListener(listener: () -> Unit)
}