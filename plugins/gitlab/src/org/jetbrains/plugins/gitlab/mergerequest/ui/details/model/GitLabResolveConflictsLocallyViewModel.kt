// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.Either
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.infoFlow
import git4idea.remote.hosting.ui.BaseResolveConflictsLocallyViewModel
import git4idea.remote.hosting.ui.ResolveConflictsLocallyCoordinates
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.getRemoteDescriptor
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabResolveConflictsLocallyError.*

interface GitLabResolveConflictsLocallyViewModel : ResolveConflictsLocallyViewModel<GitLabResolveConflictsLocallyError>

class GitLabResolveConflictsLocallyViewModelImpl(
  parentCs: CoroutineScope,
  project: Project,
  private val serverPath: GitLabServerPath,
  gitRepository: GitRepository,
  mergeRequest: GitLabMergeRequest,
) : BaseResolveConflictsLocallyViewModel<GitLabResolveConflictsLocallyError>(parentCs, project, gitRepository),
    GitLabResolveConflictsLocallyViewModel {
  override val hasConflicts: StateFlow<Boolean> = mergeRequest.details.map {
    it.conflicts && (it.diffRefs == null || it.diffRefs.headSha != it.diffRefs.startSha)
  }.stateIn(cs, SharingStarted.Lazily, false)

  override val requestOrError: StateFlow<Either<GitLabResolveConflictsLocallyError, ResolveConflictsLocallyCoordinates>> =
    mergeRequest.details.combine(gitRepository.infoFlow()) { details, repoInfo ->
      if (repoInfo.state in REPO_MERGING_STATES) return@combine Either.left(MergeInProgress)

      val sourceProject = details.sourceProject ?: return@combine Either.left(SourceRepositoryNotFound)

      val sourceRemoteDescriptor = sourceProject.getRemoteDescriptor(serverPath)
      val targetRemoteDescriptor = details.targetProject.getRemoteDescriptor(serverPath)

      Either.right(
        ResolveConflictsLocallyCoordinates(sourceRemoteDescriptor, details.sourceBranch, targetRemoteDescriptor, details.targetBranch)
      )
    }.stateIn(cs, SharingStarted.Lazily, Either.left(DetailsNotLoaded))

  companion object {
    private val REPO_MERGING_STATES = setOf(Repository.State.REBASING, Repository.State.MERGING)
  }
}

sealed interface GitLabResolveConflictsLocallyError {
  data object MergeInProgress : GitLabResolveConflictsLocallyError
  data object SourceRepositoryNotFound : GitLabResolveConflictsLocallyError
  data object DetailsNotLoaded : GitLabResolveConflictsLocallyError
}
