// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.MutableCodeReviewChangeListViewModel
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal interface GitLabMergeRequestChangeListViewModel : CodeReviewChangeListViewModel {
  /**
   * Request diff for [changesSelection]
   */
  fun showDiff()

  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestChangeListViewModel>("GitLab.MergeRequest.Changes.ViewModel")
  }
}

internal class GitLabMergeRequestChangeListViewModelImpl(parentCs: CoroutineScope)
  : MutableCodeReviewChangeListViewModel(parentCs),
    GitLabMergeRequestChangeListViewModel {

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests: Flow<Unit> = _showDiffRequests.asSharedFlow()

  override fun showDiff() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }
}
