// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeList
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModelBase
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal interface GitLabMergeRequestChangeListViewModel : CodeReviewChangeListViewModel {
  /**
   * Request standalone diff for [changesSelection]
   */
  fun showDiff()

  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestChangeListViewModel>("GitLab.MergeRequest.Changes.ViewModel")
  }
}

internal class GitLabMergeRequestChangeListViewModelImpl(
  override val project: Project,
  parentCs: CoroutineScope,
  changeList: CodeReviewChangeList
) : CodeReviewChangeListViewModelBase(parentCs, changeList),
    GitLabMergeRequestChangeListViewModel {

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests: Flow<Unit> = _showDiffRequests.asSharedFlow()

  override fun showDiffPreview() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }

  // TODO: separate diff
  override fun showDiff() = showDiffPreview()
}
