// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import java.util.concurrent.CompletableFuture

abstract class GHPRMetadataModelBase(private val repositoryDataService: GHPRRepositoryDataService) : GHPRMetadataModel {

  override fun loadPotentialReviewers(): CompletableFuture<List<GHPullRequestRequestedReviewer>> {
    val author = getAuthor()
    return repositoryDataService.loadPotentialReviewersAsync().asCompletableFuture()
      .thenApply { reviewers ->
        reviewers.mapNotNull { if (it == author) null else it }
      }
  }

  override fun loadPotentialAssignees(): CompletableFuture<List<GHUser>> =
    repositoryDataService.loadIssuesAssigneesAsync().asCompletableFuture()

  override fun loadAssignableLabels(): CompletableFuture<List<GHLabel>> =
    repositoryDataService.loadLabelsAsync().asCompletableFuture()
}