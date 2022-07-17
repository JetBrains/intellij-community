// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModelBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

internal class GHPRSearchPanelViewModel(
  scope: CoroutineScope,
  private val repositoryDataService: GHPRRepositoryDataService,
  historyViewModel: GHPRSearchHistoryModel,
  val avatarIconsProvider: GHAvatarIconsProvider
) : ReviewListSearchPanelViewModelBase<GHPRListSearchValue>(scope, historyViewModel, GHPRListSearchValue.EMPTY, GHPRListSearchValue.DEFAULT) {

  override fun GHPRListSearchValue.withQuery(query: String?) = copy(searchQuery = query)

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
