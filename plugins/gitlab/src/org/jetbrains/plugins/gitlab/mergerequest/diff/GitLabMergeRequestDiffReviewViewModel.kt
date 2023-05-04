// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.associateBy
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*

interface GitLabMergeRequestDiffReviewViewModel {
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  fun getViewModelFor(change: Change): Flow<GitLabMergeRequestDiffChangeViewModel?>

  companion object {
    val KEY: Key<GitLabMergeRequestDiffReviewViewModel> = Key.create("GitLab.Diff.Review.Discussions.ViewModel")
    val DATA_KEY: DataKey<GitLabMergeRequestDiffChangeViewModel?> = DataKey.create("GitLab.Diff.Review.Discussions.ViewModel")
  }
}

private val LOG = logger<GitLabMergeRequestDiffReviewViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiffReviewViewModelImpl(
  parentCs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  private val projectData: GitLabProject,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val mrId: GitLabMergeRequestId
) : GitLabMergeRequestDiffReviewViewModel {

  private val cs = parentCs.childScope()

  private val vmsByChange: Flow<Map<Change, GitLabMergeRequestDiffChangeViewModel>> =
    createVmsByChangeFlow().modelFlow(cs, LOG)

  private fun createVmsByChangeFlow(): Flow<Map<Change, GitLabMergeRequestDiffChangeViewModel>> =
    projectData.mergeRequests.getShared(mrId)
      .mapNotNull(Result<GitLabMergeRequest>::getOrNull)
      .flatMapLatest { mr ->
        mr.changes
          .map(GitLabMergeRequestChanges::getParsedChanges)
          .map { it.patchesByChange.asIterable() }
          .associateBy(
            { (change, _) -> change },
            { cs, (_, diffData) -> GitLabMergeRequestDiffChangeViewModelImpl(cs, currentUser, mr, diffData) },
            { destroy() },
            customHashingStrategy = GitBranchComparisonResult.REVISION_COMPARISON_HASHING_STRATEGY
          )
      }

  override fun getViewModelFor(change: Change): Flow<GitLabMergeRequestDiffChangeViewModel?> =
    vmsByChange.map { it[change] }
}
