// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.filters

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.list.search.ReviewListQuickFilter
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModelBase
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListQuickFilter.*

@ApiStatus.Experimental
class GHPRSearchPanelViewModel internal constructor(
  scope: CoroutineScope,
  private val project: Project,
  private val repositoryDataService: GHPRRepositoryDataService,
  historyViewModel: GHPRSearchHistoryModel,
  currentUser: GHUser
) :
  ReviewListSearchPanelViewModelBase<GHPRListSearchValue, GHPRListQuickFilter>(
    scope, historyViewModel,
    emptySearch = GHPRListSearchValue.EMPTY,
    defaultFilter = AssignedToYou(currentUser).filter
  ) {

  override fun GHPRListSearchValue.withQuery(query: String?) = copy(searchQuery = query)

  override val quickFilters: List<GHPRListQuickFilter> = listOf(
    Open(currentUser),
    YourPullRequests(currentUser),
    AssignedToYou(currentUser),
    ReviewRequests(currentUser)
  )

  val stateFilterState = searchState.partialState(GHPRListSearchValue::state) {
    copy(state = it)
  }

  val authorFilterState = searchState.partialState(GHPRListSearchValue::author) {
    copy(author = it)
  }

  val labelFilterState = searchState.partialState(GHPRListSearchValue::label) {
    copy(label = it)
  }

  val assigneeFilterState = searchState.partialState(GHPRListSearchValue::assignee) {
    copy(assignee = it)
  }

  val reviewFilterState = searchState.partialState(GHPRListSearchValue::reviewState) {
    copy(reviewState = it)
  }

  val sortFilterState = searchState.partialState(GHPRListSearchValue::sort) {
    copy(sort = it)
  }

  init {
    @OptIn(FlowPreview::class)
    scope.launchNow {
      // with debounce to avoid collecting intermediate state
      searchState.drop(1).debounce(5000).collect {
        GHPRStatisticsCollector.logListFiltersApplied(project, it)
      }
    }
  }

  suspend fun getAuthors(): List<GHUser> = repositoryDataService.loadCollaborators()
  suspend fun getAssignees(): List<GHUser> = repositoryDataService.loadIssuesAssignees()
  suspend fun getLabels(): List<GHLabel> = repositoryDataService.loadLabels()
}

@ApiStatus.Experimental
sealed class GHPRListQuickFilter(user: GHUser) : ReviewListQuickFilter<GHPRListSearchValue> {
  protected val userLogin = user.login

  data class Open(val user: GHUser) : GHPRListQuickFilter(user) {
    override val filter = GHPRListSearchValue(state = GHPRListSearchValue.State.OPEN)
  }

  data class YourPullRequests(val user: GHUser) : GHPRListQuickFilter(user) {
    override val filter = GHPRListSearchValue(state = GHPRListSearchValue.State.OPEN, author = userLogin)
  }

  data class AssignedToYou(val user: GHUser) : GHPRListQuickFilter(user) {
    override val filter = GHPRListSearchValue(state = GHPRListSearchValue.State.OPEN, assignee = userLogin)
  }

  data class ReviewRequests(val user: GHUser) : GHPRListQuickFilter(user) {
    override val filter = GHPRListSearchValue(
      state = GHPRListSearchValue.State.OPEN,
      reviewState = GHPRListSearchValue.ReviewState.AWAITING_REVIEW
    )
  }
}