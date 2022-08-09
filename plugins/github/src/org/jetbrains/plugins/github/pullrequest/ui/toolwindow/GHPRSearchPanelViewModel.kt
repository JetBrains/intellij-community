// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.ReviewListQuickFilter
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModelBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRListQuickFilter.*

internal class GHPRSearchPanelViewModel(
  scope: CoroutineScope,
  private val repositoryDataService: GHPRRepositoryDataService,
  historyViewModel: GHPRSearchHistoryModel,
  currentUser: GHUser
) :
  ReviewListSearchPanelViewModelBase<GHPRListSearchValue, GHPRListQuickFilter>(
    scope, historyViewModel,
    emptySearch = GHPRListSearchValue.EMPTY,
    defaultQuickFilter = Open(currentUser)
  ) {

  override fun GHPRListSearchValue.withQuery(query: String?) = copy(searchQuery = query)

  override val quickFilters: List<GHPRListQuickFilter> = listOf(
    Open(currentUser),
    YourPullRequests(currentUser),
    AssignedToYou(currentUser)
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

  suspend fun getAuthors(): List<GHUser> = repositoryDataService.collaborators.await()

  suspend fun getAssignees(): List<GHUser> = repositoryDataService.issuesAssignees.await()
  suspend fun getLabels(): List<GHLabel> = repositoryDataService.labels.await()
}

internal sealed class GHPRListQuickFilter(user: GHUser) : ReviewListQuickFilter<GHPRListSearchValue> {
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
}