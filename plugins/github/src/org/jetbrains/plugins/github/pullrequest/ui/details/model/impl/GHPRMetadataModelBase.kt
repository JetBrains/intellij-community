// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import java.util.concurrent.CompletableFuture

abstract class GHPRMetadataModelBase(private val repositoryDataService: GHPRRepositoryDataService) : GHPRMetadataModel {

  override fun loadPotentialReviewers(): CompletableFuture<List<GHPullRequestRequestedReviewer>> {
    val author = getAuthor()
    return repositoryDataService.potentialReviewers.thenApply { reviewers ->
      reviewers.mapNotNull { if (it == author) null else it }
    }
  }

  override fun loadPotentialAssignees() = repositoryDataService.issuesAssignees
  override fun loadAssignableLabels() = repositoryDataService.labels
}