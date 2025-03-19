// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.Either
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.findFirstRemoteBranchTrackedByCurrent
import git4idea.remote.hosting.infoFlow
import git4idea.remote.hosting.isInCurrentHistory
import git4idea.remote.hosting.ui.ResolveConflictsLocallyCoordinates
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.getRemoteDescriptor
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabResolveConflictsLocallyError.*

typealias GitLabResolveConflictsLocallyViewModel = ResolveConflictsLocallyViewModel<GitLabResolveConflictsLocallyError>

private val REPO_MERGING_STATES = setOf(Repository.State.REBASING, Repository.State.MERGING)

fun GitLabResolveConflictsLocallyViewModel(
  parentCs: CoroutineScope,
  project: Project,
  serverPath: GitLabServerPath,
  gitRepository: GitRepository,
  mergeRequest: GitLabMergeRequest,
): GitLabResolveConflictsLocallyViewModel = ResolveConflictsLocallyViewModel.createIn(
  parentCs, project, gitRepository,
  hasConflicts =
    mergeRequest.details.map {
      it.conflicts && (it.diffRefs == null || it.diffRefs.headSha != it.diffRefs.startSha)
    },
  requestOrError =
    combine(
      gitRepository.isInCurrentHistory(
        rev = mergeRequest.details.map { it.diffRefs?.baseSha }.filterNotNull()
      ).map { it ?: false }.withInitial(false),
      mergeRequest.details, gitRepository.infoFlow()
    ) { isBaseInHistory, details, repoInfo ->
      if (repoInfo.state in REPO_MERGING_STATES) return@combine Either.left(MergeInProgress)

      val sourceProject = details.sourceProject ?: return@combine Either.left(SourceRepositoryNotFound)

      val sourceRemoteDescriptor = sourceProject.getRemoteDescriptor(serverPath)
      val targetRemoteDescriptor = details.targetProject.getRemoteDescriptor(serverPath)

      val currentRemoteBranch = repoInfo.findFirstRemoteBranchTrackedByCurrent()
      if (currentRemoteBranch != null && isBaseInHistory &&
          currentRemoteBranch.nameForRemoteOperations == details.sourceBranch &&
          currentRemoteBranch.remote == GitRemoteBranchesUtil.findRemote(gitRepository, sourceRemoteDescriptor))
        return@combine Either.left(AlreadyResolvedLocally)

      Either.right(
        ResolveConflictsLocallyCoordinates(sourceRemoteDescriptor, details.sourceBranch, targetRemoteDescriptor, details.targetBranch)
      )
    },
  initialRequestOrErrorState = Either.left(DetailsNotLoaded)
)

sealed interface GitLabResolveConflictsLocallyError {
  data object AlreadyResolvedLocally : GitLabResolveConflictsLocallyError
  data object MergeInProgress : GitLabResolveConflictsLocallyError
  data object SourceRepositoryNotFound : GitLabResolveConflictsLocallyError
  data object DetailsNotLoaded : GitLabResolveConflictsLocallyError
}
