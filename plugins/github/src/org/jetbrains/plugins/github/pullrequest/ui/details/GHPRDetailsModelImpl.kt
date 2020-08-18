// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.CollectionDelta
import java.util.concurrent.CompletableFuture

class GHPRDetailsModelImpl(private val valueModel: SingleValueModel<GHPullRequest>,
                           securityService: GHPRSecurityService,
                           private val repositoryDataService: GHPRRepositoryDataService,
                           private val detailsDataProvider: GHPRDetailsDataProvider) : GHPRDetailsModel {

  override val number: String
    get() = valueModel.value.number.toString()
  override val title: String
    get() = valueModel.value.title
  override val baseBranch: String
    get() = valueModel.value.baseRefName
  override val headBranch: String
    get() {
      with(valueModel.value) {
        if (headRepository == null) return headRefName
        if (headRepository.isFork || baseRefName == headRefName) {
          return headRepository.owner.login + ":" + headRefName
        }
        else {
          return headRefName
        }
      }
    }
  override val state: GHPullRequestState
    get() = valueModel.value.state
  override val isDraft: Boolean
    get() = valueModel.value.isDraft
  override val assignees: List<GHUser>
    get() = valueModel.value.assignees
  override val reviewers: List<GHPullRequestRequestedReviewer>
    get() = valueModel.value.reviewRequests.mapNotNull { it.requestedReviewer }
  override val labels: List<GHLabel>
    get() = valueModel.value.labels

  override val isMetadataEditingAllowed = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)

  override fun loadPotentialReviewers(): CompletableFuture<List<GHPullRequestRequestedReviewer>> {
    val author = valueModel.value.author as? GHUser
    return repositoryDataService.potentialReviewers.thenApply { reviewers ->
      reviewers.mapNotNull { if (it == author) null else it }
    }
  }

  override fun adjustReviewers(indicator: ProgressIndicator, delta: CollectionDelta<GHPullRequestRequestedReviewer>) =
    detailsDataProvider.adjustReviewers(indicator, delta)

  override fun loadPotentialAssignees() = repositoryDataService.issuesAssignees

  override fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>) =
    detailsDataProvider.adjustAssignees(indicator, delta)

  override fun loadAssignableLabels() = repositoryDataService.labels

  override fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>) =
    detailsDataProvider.adjustLabels(indicator, delta)

  override fun addAndInvokeDetailsChangedListener(listener: () -> Unit) =
    valueModel.addAndInvokeValueChangedListener(listener)
}