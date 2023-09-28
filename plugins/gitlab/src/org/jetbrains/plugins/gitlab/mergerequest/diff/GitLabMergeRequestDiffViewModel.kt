// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.associateBy
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.CODE_REVIEW_CHANGE_HASHING_STRATEGY
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModel.SubmittableReview
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.getSubmittableReview

internal interface GitLabMergeRequestDiffViewModel {
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>
  val discussionsViewOption: StateFlow<DiscussionsViewOption>

  val submittableReview: StateFlow<SubmittableReview?>
  var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)?

  // TODO: rework to hide details
  val changes: SharedFlow<GitLabMergeRequestChanges>
  val changesToShow: SharedFlow<ChangesSelection>

  fun getViewModelFor(change: Change): Flow<GitLabMergeRequestDiffChangeViewModel?>

  fun setDiscussionsViewOption(viewOption: DiscussionsViewOption)

  /**
   * Request the start of a submission process
   */
  fun submitReview()

  fun showChanges(changes: ChangesSelection)

  companion object {
    val KEY: Key<GitLabMergeRequestDiffViewModel> = Key.create("GitLab.MergeRequest.Diff.ViewModel")
    val DATA_KEY: DataKey<GitLabMergeRequestDiffViewModel> = DataKey.create("GitLab.MergeRequest.Diff.Review.ViewModel")
  }
}

private val LOG = logger<GitLabMergeRequestDiffViewModel>()

internal class GitLabMergeRequestDiffViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val diffBridge: GitLabMergeRequestDiffBridge,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestDiffViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineName("GitLab Merge Request Review Diff VM"))

  override val changes: SharedFlow<GitLabMergeRequestChanges> =
    mergeRequest.changes.modelFlow(cs, LOG)
  override val changesToShow: SharedFlow<ChangesSelection> =
    diffBridge.displayedChanges.modelFlow(cs, LOG)

  private val vmsByChange: Flow<Map<Change, GitLabMergeRequestDiffChangeViewModel>> =
    createVmsByChangeFlow().modelFlow(cs, LOG)

  private fun createVmsByChangeFlow(): Flow<Map<Change, GitLabMergeRequestDiffChangeViewModel>> =
    mergeRequest.changes
      .map(GitLabMergeRequestChanges::getParsedChanges)
      .map { it.patchesByChange.asIterable() }
      .associateBy(
        { (change, _) -> change },
        { (_, diffData) ->
          GitLabMergeRequestDiffChangeViewModelImpl(project, this, currentUser, mergeRequest, diffData, discussionsViewOption)
        },
        { destroy() },
        customHashingStrategy = CODE_REVIEW_CHANGE_HASHING_STRATEGY
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

  override fun showChanges(changes: ChangesSelection) {
    diffBridge.setChanges(changes)
  }

  override fun submitReview() {
    cs.launchNow {
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
