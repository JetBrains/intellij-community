// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiscussionsViewModel
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

interface GitLabMergeRequestReviewViewModel : CodeReviewDiscussionsViewModel {
  val submittableReview: StateFlow<GitLabMergeRequestSubmitReviewViewModel.SubmittableReview?>
  var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)?

  /**
   * Request the start of a submission process
   */
  fun submitReview()

  companion object {
    val DATA_KEY: DataKey<GitLabMergeRequestReviewViewModel> = DataKey.create("GitLab.MergeRequest.Review.ViewModel")
  }
}

@ApiStatus.Internal
open class GitLabMergeRequestReviewViewModelBase(
  protected val cs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  initialViewOption: DiscussionsViewOption,
) : GitLabMergeRequestReviewViewModel {

  private val _discussionsViewOption: MutableStateFlow<DiscussionsViewOption> = MutableStateFlow(initialViewOption)
  override val discussionsViewOption: StateFlow<DiscussionsViewOption> = _discussionsViewOption.asStateFlow()

  override val submittableReview: StateFlow<GitLabMergeRequestSubmitReviewViewModel.SubmittableReview?> =
    mergeRequest.getSubmittableReview(currentUser)
      .stateIn(cs, SharingStarted.Eagerly, null) // need Eagerly for action update to work properly

  override var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)? = null

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    _discussionsViewOption.value = viewOption
  }

  override fun submitReview() {
    val review = submittableReview.value ?: return
    cs.launch {
      val handler = submitReviewInputHandler
      check(handler != null)
      val ctx = currentCoroutineContext()
      val vm = GitLabMergeRequestSubmitReviewViewModelImpl(this, mergeRequest, currentUser, review) {
        ctx.cancel()
      }
      handler.invoke(vm)
    }
  }
}