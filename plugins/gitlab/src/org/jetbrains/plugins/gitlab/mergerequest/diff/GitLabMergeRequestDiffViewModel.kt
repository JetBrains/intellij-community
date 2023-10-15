// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.associateCachingBy
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.CODE_REVIEW_CHANGE_HASHING_STRATEGY
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase

internal interface GitLabMergeRequestDiffViewModel : GitLabMergeRequestReviewViewModel {
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  // TODO: rework to hide details
  val changes: SharedFlow<GitLabMergeRequestChanges>
  val changesToShow: SharedFlow<ChangesSelection>

  fun getViewModelFor(change: Change): Flow<GitLabMergeRequestChangeViewModel?>

  fun showChanges(changes: ChangesSelection)

  companion object {
    val KEY: Key<GitLabMergeRequestDiffViewModel> = Key.create("GitLab.MergeRequest.Diff.ViewModel")
  }
}

private val LOG = logger<GitLabMergeRequestDiffViewModel>()

internal class GitLabMergeRequestDiffViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  mergeRequest: GitLabMergeRequest,
  private val diffBridge: GitLabMergeRequestDiffBridge,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestReviewViewModelBase(parentCs, currentUser, mergeRequest),
    GitLabMergeRequestDiffViewModel {

  override val changes: SharedFlow<GitLabMergeRequestChanges> =
    mergeRequest.changes.modelFlow(cs, LOG)
  override val changesToShow: SharedFlow<ChangesSelection> =
    diffBridge.displayedChanges.modelFlow(cs, LOG)

  private val vmsByChange: Flow<Map<Change, GitLabMergeRequestChangeViewModel>> =
    createVmsByChangeFlow().modelFlow(cs, LOG)

  private fun createVmsByChangeFlow(): Flow<Map<Change, GitLabMergeRequestChangeViewModel>> =
    mergeRequest.changes
      .map(GitLabMergeRequestChanges::getParsedChanges)
      .map { it.patchesByChange.asIterable() }
      .associateCachingBy(
        { it.key },
        CODE_REVIEW_CHANGE_HASHING_STRATEGY,
        { (_, diffData) ->
          GitLabMergeRequestChangeViewModelImpl(project, this, currentUser, mergeRequest, diffData, avatarIconsProvider,
                                                discussionsViewOption)
        },
        { destroy() }
      )

  override fun getViewModelFor(change: Change): Flow<GitLabMergeRequestChangeViewModel?> =
    vmsByChange.map { it[change] }

  override fun showChanges(changes: ChangesSelection) {
    diffBridge.setChanges(changes)
  }
}
