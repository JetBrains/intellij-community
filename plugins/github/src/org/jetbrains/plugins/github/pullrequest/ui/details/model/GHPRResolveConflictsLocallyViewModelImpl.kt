// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.Either
import com.intellij.collaboration.util.getOrNull
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.findFirstRemoteBranchTrackedByCurrent
import git4idea.remote.hosting.infoFlow
import git4idea.remote.hosting.isInCurrentHistory
import git4idea.remote.hosting.ui.BaseOrHead
import git4idea.remote.hosting.ui.BaseOrHead.Base
import git4idea.remote.hosting.ui.BaseOrHead.Head
import git4idea.remote.hosting.ui.BaseResolveConflictsLocallyViewModel
import git4idea.remote.hosting.ui.ResolveConflictsLocallyCoordinates
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.detailsComputationFlow
import org.jetbrains.plugins.github.pullrequest.data.provider.mergeabilityStateComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel.Companion.getRemoteDescriptor
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRResolveConflictsLocallyError.*

interface GHPRResolveConflictsLocallyViewModel : ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError>

class GHPRResolveConflictsLocallyViewModelImpl(
  parentCs: CoroutineScope,
  project: Project,
  private val server: GithubServerPath,
  gitRepository: GitRepository,
  detailsData: GHPRDetailsDataProvider,
) : BaseResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError>(parentCs, project, gitRepository),
    GHPRResolveConflictsLocallyViewModel {
  override val hasConflicts: StateFlow<Boolean?> = detailsData.mergeabilityStateComputationFlow
    .filter { !it.isInProgress }
    .map { it.getOrNull()?.hasConflicts }
    .stateIn(cs, SharingStarted.Lazily, false)

  private val isBaseInHistory: StateFlow<Boolean> =
    gitRepository.isInCurrentHistory(
      rev = detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.map { it.baseRefOid }
    ).map { it ?: false }.stateInNow(cs, false)

  override val requestOrError: StateFlow<Either<GHPRResolveConflictsLocallyError, ResolveConflictsLocallyCoordinates>> =
    combine(isBaseInHistory, detailsData.detailsComputationFlow, gitRepository.infoFlow()) { isBaseInHistory, detailsResult, repoState ->
      if (repoState.state in REPO_MERGING_STATES) return@combine Either.left(MergeInProgress)

      val details = detailsResult.getOrNull() ?: return@combine Either.left(DetailsNotLoaded)

      val headRemoteDescriptor = details.headRepository?.getRemoteDescriptor(server)
                                 ?: return@combine Either.left(RepositoryNotFound(Head))
      val baseRemoteDescriptor = details.baseRepository?.getRemoteDescriptor(server)
                                 ?: return@combine Either.left(RepositoryNotFound(Base))

      val currentRemoteBranch = repoState.findFirstRemoteBranchTrackedByCurrent()
      if (currentRemoteBranch != null && isBaseInHistory &&
          currentRemoteBranch.nameForRemoteOperations == details.headRefName &&
          currentRemoteBranch.remote == GitRemoteBranchesUtil.findRemote(gitRepository, headRemoteDescriptor))
        return@combine Either.left(AlreadyResolvedLocally)

      Either.right(
        ResolveConflictsLocallyCoordinates(headRemoteDescriptor, details.headRefName, baseRemoteDescriptor, details.baseRefName)
      )
    }.stateIn(cs, SharingStarted.Lazily, Either.left(DetailsNotLoaded))

  companion object {
    private val REPO_MERGING_STATES = setOf(Repository.State.REBASING, Repository.State.MERGING)
  }
}

sealed interface GHPRResolveConflictsLocallyError {
  data object AlreadyResolvedLocally : GHPRResolveConflictsLocallyError
  data object MergeInProgress : GHPRResolveConflictsLocallyError
  data object DetailsNotLoaded : GHPRResolveConflictsLocallyError
  data class RepositoryNotFound(val baseOrHead: BaseOrHead) : GHPRResolveConflictsLocallyError
  data class RemoteNotFound(val baseOrHead: BaseOrHead, val coordinates: GHRepositoryCoordinates) : GHPRResolveConflictsLocallyError
}