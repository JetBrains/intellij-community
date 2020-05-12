// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHSimpleLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField
import java.util.concurrent.CompletableFuture

class GHPRDetailsModelImpl(loadingModel: GHSimpleLoadingModel<GHPullRequest>,
                           securityService: GHPRSecurityService,
                           private val repositoryDataService: GHPRRepositoryDataService,
                           private val detailsDataProvider: GHPRDetailsDataProvider) : GHPRDetailsModel {

  private val detailsChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  private var details: GHPullRequest by observableField(loadingModel.result ?: error("Details are not loaded yet"),
                                                        detailsChangeEventDispatcher)

  init {
    loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingStarted() {
        details = loadingModel.result ?: return
      }

      override fun onLoadingCompleted() {
        details = loadingModel.result ?: return
      }
    })
  }

  override val number: String
    get() = details.number.toString()
  override val title: String
    get() = details.title
  override val baseBranch: String
    get() = details.baseRefName
  override val headBranch: String
    get() {
      with(details) {
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
    get() = details.state
  override val assignees: List<GHUser>
    get() = details.assignees
  override val reviewers: List<GHPullRequestRequestedReviewer>
    get() = details.reviewRequests.mapNotNull { it.requestedReviewer }
  override val labels: List<GHLabel>
    get() = details.labels

  override val isMetadataEditingAllowed = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)

  override fun loadPotentialReviewers(): CompletableFuture<List<GHPullRequestRequestedReviewer>> {
    val author = details.author as? GHUser
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
    SimpleEventListener.addAndInvokeListener(detailsChangeEventDispatcher, listener)
}