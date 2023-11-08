// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase

internal interface GitLabMergeRequestDiffViewModel : GitLabMergeRequestReviewViewModel {
  // TODO: rework to hide details
  val changes: SharedFlow<GitLabMergeRequestChanges>
  val changesToShow: SharedFlow<ChangesSelection>

  fun getViewModelFor(change: RefComparisonChange): Flow<GitLabMergeRequestDiffReviewViewModel?>

  fun showChanges(changes: ChangesSelection)

  companion object {
    val KEY: Key<GitLabMergeRequestDiffViewModel> = Key.create("GitLab.MergeRequest.Diff.ViewModel")
  }
}

private val LOG = logger<GitLabMergeRequestDiffViewModel>()

internal class GitLabMergeRequestDiffViewModelImpl(
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val diffBridge: GitLabMergeRequestDiffBridge,
  private val discussions: GitLabMergeRequestDiscussionsViewModels,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestDiffViewModel, GitLabMergeRequestReviewViewModelBase(
  parentCs.childScope(CoroutineName("GitLab Merge Request Diff Review VM")),
  currentUser, mergeRequest
) {

  override val changes: SharedFlow<GitLabMergeRequestChanges> =
    mergeRequest.changes.modelFlow(cs, LOG)
  override val changesToShow: SharedFlow<ChangesSelection> =
    diffBridge.displayedChanges.modelFlow(cs, LOG)

  override fun showChanges(changes: ChangesSelection) {
    diffBridge.setChanges(changes)
  }

  override fun getViewModelFor(change: RefComparisonChange): Flow<GitLabMergeRequestDiffReviewViewModel?> {
    return mergeRequest.changes.map {
      it.getParsedChanges()
    }.map { parsedChanges ->
      parsedChanges.patchesByChange[change]?.let { diffData ->
        GitLabMergeRequestDiffReviewViewModelImpl(diffData, discussions, discussionsViewOption, avatarIconsProvider)
      }
    }.catch { emit(null) }
  }
}
