// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import com.intellij.collaboration.util.CollectionDelta
import java.util.concurrent.CompletableFuture

interface GHPRMetadataModel {
  val assignees: List<GHUser>
  val reviewers: List<GHPullRequestRequestedReviewer>
  val labels: List<GHLabel>

  val isEditingAllowed: Boolean

  fun loadPotentialReviewers(): CompletableFuture<List<GHPullRequestRequestedReviewer>>
  fun adjustReviewers(indicator: ProgressIndicator, delta: CollectionDelta<GHPullRequestRequestedReviewer>): CompletableFuture<Unit>

  fun loadPotentialAssignees(): CompletableFuture<List<GHUser>>
  fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>): CompletableFuture<Unit>

  fun loadAssignableLabels(): CompletableFuture<List<GHLabel>>
  fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>): CompletableFuture<Unit>

  fun addAndInvokeChangesListener(listener: () -> Unit)
}