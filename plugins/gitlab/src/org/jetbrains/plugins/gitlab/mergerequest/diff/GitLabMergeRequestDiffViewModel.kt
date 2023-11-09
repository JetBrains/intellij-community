// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffViewModelComputer
import com.intellij.collaboration.ui.codereview.diff.model.ComputedDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffProducersViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.createVcsChange
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase

internal interface GitLabMergeRequestDiffViewModel : GitLabMergeRequestReviewViewModel, ComputedDiffViewModel {
  fun getViewModelFor(change: RefComparisonChange): Flow<GitLabMergeRequestDiffReviewViewModel?>

  companion object {
    val KEY: Key<GitLabMergeRequestDiffViewModel> = Key.create("GitLab.MergeRequest.Diff.ViewModel")
  }
}

internal class GitLabMergeRequestDiffViewModelImpl(
  private val project: Project,
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

  private val helper = CodeReviewDiffViewModelComputer(
    mergeRequest.changes.mapScoped { async { it.loadRevisionsAndParseChanges() } }
  ) { changesBundle, change ->
    val changeDiffProducer = ChangeDiffRequestProducer.create(project, change.createVcsChange(project))
                             ?: error("Could not create diff producer from $change")
    CodeReviewDiffRequestProducer(project, change, changeDiffProducer, changesBundle.patchesByChange[change]?.getDiffComputer())
  }

  override val diffVm: StateFlow<ComputedResult<DiffProducersViewModel?>> =
    helper.diffVm.stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  init {
    cs.launchNow {
      diffBridge.displayedChanges.collectLatest {
        helper.showChanges(it)
      }
    }
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

private suspend fun GitLabMergeRequestChanges.loadRevisionsAndParseChanges(): GitBranchComparisonResult =
  coroutineScope {
    launch {
      ensureAllRevisionsFetched()
    }
    getParsedChanges()
  }
