// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService

class GHPRMetadataModelImpl(private val valueModel: SingleValueModel<GHPullRequest>,
                            securityService: GHPRSecurityService,
                            repositoryDataService: GHPRRepositoryDataService,
                            private val detailsDataProvider: GHPRDetailsDataProvider) : GHPRMetadataModelBase(repositoryDataService) {

  override val assignees: List<GHUser>
    get() = valueModel.value.assignees
  override val reviewers: List<GHPullRequestRequestedReviewer>
    get() = valueModel.value.reviewRequests.mapNotNull { it.requestedReviewer }
  override val labels: List<GHLabel>
    get() = valueModel.value.labels
  override val reviews: List<GHPullRequestReview>
    get() = valueModel.value.reviews

  override fun getAuthor() = valueModel.value.author as? GHUser

  override val isEditingAllowed = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)

  override fun adjustReviewers(indicator: ProgressIndicator, delta: CollectionDelta<GHPullRequestRequestedReviewer>) =
    detailsDataProvider.adjustReviewers(indicator, delta)

  override fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>) =
    detailsDataProvider.adjustAssignees(indicator, delta)

  override fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>) =
    detailsDataProvider.adjustLabels(indicator, delta)

  override fun addAndInvokeChangesListener(listener: () -> Unit) =
    valueModel.addAndInvokeListener { listener() }
}