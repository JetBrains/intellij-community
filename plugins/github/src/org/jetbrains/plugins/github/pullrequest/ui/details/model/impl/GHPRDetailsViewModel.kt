// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.collaboration.ui.codereview.issues.processIssueIdsHtml
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel

@ApiStatus.Experimental
interface GHPRDetailsViewModel : CodeReviewDetailsViewModel {
  val securityService: GHPRSecurityService
  val avatarIconsProvider: IconsProvider<String>

  val isUpdating: StateFlow<Boolean>

  val branchesVm: GHPRBranchesViewModel
  val changesVm: GHPRChangesViewModel
  val statusVm: GHPRStatusViewModelImpl
  val reviewFlowVm: GHPRReviewFlowViewModelImpl

  fun openPullRequestInfoAndTimeline(number: Long)
}

internal class GHPRDetailsViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  details: GHPullRequest
) : GHPRDetailsViewModel {
  private val cs = parentCs.childScope()

  private val detailsState = MutableStateFlow(details)

  override val number: String = "#${detailsState.value.number}"
  override val url: String = detailsState.value.url

  override val title: SharedFlow<String> = detailsState.map { it.title }
    .map { title -> title.convertToHtml(project) }
    .shareIn(cs, SharingStarted.Lazily, 1)

  override val description: SharedFlow<String> = detailsState.map { it.body }
    .map { description -> processIssueIdsHtml(project, description) }
    .shareIn(cs, SharingStarted.Lazily, 1)

  override val reviewRequestState: SharedFlow<ReviewRequestState> = detailsState.map { details ->
    if (details.isDraft) return@map ReviewRequestState.DRAFT
    when (details.state) {
      GHPullRequestState.CLOSED -> ReviewRequestState.CLOSED
      GHPullRequestState.MERGED -> ReviewRequestState.MERGED
      GHPullRequestState.OPEN -> ReviewRequestState.OPENED
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  override val isUpdating = MutableStateFlow(false)

  private val twVm by lazy { project.service<GHPRToolWindowViewModel>() }
  override val securityService: GHPRSecurityService = dataContext.securityService
  override val avatarIconsProvider: IconsProvider<String> = dataContext.avatarIconsProvider
  override val branchesVm = GHPRBranchesViewModel(cs, project, dataContext.repositoryDataService.repositoryMapping, detailsState)

  private val reviewVmHelper = GHPRReviewViewModelHelper(cs, dataProvider)
  override val changesVm = GHPRChangesViewModelImpl(cs, project, dataContext, dataProvider)

  private val serverPath = dataContext.repositoryDataService.repositoryMapping.repository.serverPath
  private val gitRepository = dataContext.repositoryDataService.repositoryMapping.gitRepository
  override val statusVm = GHPRStatusViewModelImpl(cs, project, serverPath, gitRepository, dataProvider, detailsState)

  override val reviewFlowVm =
    GHPRReviewFlowViewModelImpl(cs,
                                project,
                                detailsState,
                                dataContext.repositoryDataService,
                                dataContext.securityService,
                                dataContext.avatarIconsProvider,
                                dataProvider.detailsData,
                                dataProvider.changesData,
                                reviewVmHelper)

  fun update(details: GHPullRequest) {
    detailsState.value = details
  }

  override fun openPullRequestInfoAndTimeline(number: Long) {
    twVm.projectVm.value?.openPullRequestInfoAndTimeline(number)
  }

  suspend fun destroy() = cs.cancelAndJoinSilently()
}