// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.associateBy
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModel.SubmittableReview
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.getSubmittableReview

internal interface GitLabMergeRequestDiffReviewViewModel {
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>
  val discussionsViewOption: StateFlow<DiscussionsViewOption>

  val submittableReview: StateFlow<SubmittableReview?>
  var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)?

  fun getViewModelFor(change: Change): Flow<GitLabMergeRequestDiffChangeViewModel?>

  fun setDiscussionsViewOption(viewOption: DiscussionsViewOption)

  /**
   * Request the start of a submission process
   */
  fun submitReview()

  companion object {
    val KEY: Key<GitLabMergeRequestDiffReviewViewModel> = Key.create("GitLab.Diff.Review.ViewModel")
    val DATA_KEY: DataKey<GitLabMergeRequestDiffReviewViewModel> = DataKey.create("GitLab.Diff.Review.ViewModel")
  }
}

private val LOG = logger<GitLabMergeRequestDiffReviewViewModel>()

internal class GitLabMergeRequestDiffReviewViewModelImpl(
  parentCs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestDiffReviewViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineName("GitLab Merge Request Review Diff VM"))

  private val vmsByChange: Flow<Map<Change, GitLabMergeRequestDiffChangeViewModel>> =
    createVmsByChangeFlow().modelFlow(cs, LOG)

  private fun createVmsByChangeFlow(): Flow<Map<Change, GitLabMergeRequestDiffChangeViewModel>> =
    mergeRequest.changes
      .map(GitLabMergeRequestChanges::getParsedChanges)
      .map { it.patchesByChange.asIterable() }
      .associateBy(
        { (change, _) -> change },
        { cs, (_, diffData) -> GitLabMergeRequestDiffChangeViewModelImpl(cs, currentUser, mergeRequest, diffData, discussionsViewOption) },
        { destroy() },
        customHashingStrategy = GitBranchComparisonResult.REVISION_COMPARISON_HASHING_STRATEGY
      )

  private val _discussionsViewOption: MutableStateFlow<DiscussionsViewOption> = MutableStateFlow(DiscussionsViewOption.UNRESOLVED_ONLY)
  override val discussionsViewOption: StateFlow<DiscussionsViewOption> = _discussionsViewOption.asStateFlow()

  override val submittableReview: StateFlow<SubmittableReview?> =
    mergeRequest.getSubmittableReview(currentUser)
      .stateIn(cs, SharingStarted.Eagerly, null) // need Eagerly for action update to work properly

  override var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)? = null

  override fun getViewModelFor(change: Change): Flow<GitLabMergeRequestDiffChangeViewModel?> =
    vmsByChange.map { it[change] }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    _discussionsViewOption.value = viewOption
  }

  override fun submitReview() {
    cs.launchNow {
      check(submittableReview.first() != null)
      val ctx = currentCoroutineContext()
      val vm = GitLabMergeRequestSubmitReviewViewModelImpl(this, mergeRequest, currentUser) {
        ctx.cancel()
      }
      submitReviewInputHandler?.invoke(vm)
    }
  }
}
