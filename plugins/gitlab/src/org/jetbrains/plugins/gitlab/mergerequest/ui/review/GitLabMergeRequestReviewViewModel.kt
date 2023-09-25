// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.getSubmittableReview

interface GitLabMergeRequestReviewViewModel {
  val discussionsViewOption: StateFlow<DiscussionsViewOption>

  val submittableReview: StateFlow<GitLabMergeRequestSubmitReviewViewModel.SubmittableReview?>
  var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)?

  fun setDiscussionsViewOption(viewOption: DiscussionsViewOption)

  /**
   * Request the start of a submission process
   */
  fun submitReview()

  companion object {
    val DATA_KEY: DataKey<GitLabMergeRequestReviewViewModel> = DataKey.create("GitLab.MergeRequest.Diff.Review.ViewModel")
  }
}

internal open class GitLabMergeRequestReviewViewModelBase(
  parentCs: CoroutineScope,
  protected val currentUser: GitLabUserDTO,
  protected val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestReviewViewModel {

  protected val cs = parentCs.childScope(Dispatchers.Default + CoroutineName("GitLab Merge Request Review VM"))

  private val _discussionsViewOption: MutableStateFlow<DiscussionsViewOption> = MutableStateFlow(DiscussionsViewOption.UNRESOLVED_ONLY)
  final override val discussionsViewOption: StateFlow<DiscussionsViewOption> = _discussionsViewOption.asStateFlow()

  final override val submittableReview: StateFlow<GitLabMergeRequestSubmitReviewViewModel.SubmittableReview?> =
    mergeRequest.getSubmittableReview(currentUser)
      .stateIn(cs, SharingStarted.Eagerly, null) // need Eagerly for action update to work properly

  final override var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)? = null

  final override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    _discussionsViewOption.value = viewOption
  }

  final override fun submitReview() {
    cs.launch {
      check(submittableReview.first() != null)
      val handler = submitReviewInputHandler
      check(handler != null)
      val ctx = currentCoroutineContext()
      val vm = GitLabMergeRequestSubmitReviewViewModelImpl(this, mergeRequest, currentUser) {
        ctx.cancel()
      }
      handler.invoke(vm)
    }
  }
}