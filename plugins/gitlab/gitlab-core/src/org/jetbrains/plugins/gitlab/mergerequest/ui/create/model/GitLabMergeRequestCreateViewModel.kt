// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.model

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.ListenableProgressIndicator
import com.intellij.collaboration.ui.codereview.create.CodeReviewTitleDescriptionViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.awt.RelativePoint
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitPushUtil
import git4idea.GitRemoteBranch
import git4idea.config.GitSharedSettings
import git4idea.history.GitLogUtil
import git4idea.remote.hosting.changesSignalFlow
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestReviewersUtil
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabCoroutineUtil
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

internal interface GitLabMergeRequestCreateViewModel : CodeReviewTitleDescriptionViewModel {
  val projectsManager: GitLabProjectsManager
  val projectData: GitLabProject
  val avatarIconProvider: IconsProvider<GitLabUserDTO>

  val titleGenerationVm: StateFlow<GitLabMergeRequestCreateTitleGenerationViewModel?>

  val isBusy: Flow<Boolean>

  val allowsMultipleReviewers: Flow<Boolean>
  val branchState: Flow<BranchState?>

  val existingMergeRequest: Flow<String?>
  val creatingProgressText: Flow<String?>
  val commits: StateFlow<Result<List<VcsCommitMetadata>>?>

  val reviewRequirementsErrorState: Flow<MergeRequestRequirementsErrorType?>
  val reviewCreatingError: Flow<Throwable?>

  val potentialReviewers: Flow<Result<List<GitLabUserDTO>>>
  val adjustedReviewers: StateFlow<List<GitLabUserDTO>>

  val openReviewTabAction: suspend (mrIid: String) -> Unit

  fun updateBranchState(state: BranchState?)

  fun adjustReviewer(point: RelativePoint)

  fun createMergeRequest()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestCreateViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  override val projectsManager: GitLabProjectsManager,
  override val projectData: GitLabProject,
  override val avatarIconProvider: IconsProvider<GitLabUserDTO>,
  override val openReviewTabAction: suspend (mrIid: String) -> Unit,
  private val onReviewCreated: () -> Unit,
) : GitLabMergeRequestCreateViewModel {
  private val cs: CoroutineScope = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val isBusy: Flow<Boolean> = taskLauncher.busy

  override val allowsMultipleReviewers: Flow<Boolean> = suspend {
    try {
      projectData.isMultipleReviewersAllowed()
    }
    catch (e: Exception) {
      false
    }
  }.asFlow()

  private val listenableProgressIndicator = ListenableProgressIndicator()
  override val creatingProgressText: Flow<String?> = callbackFlow {
    val listenerDisposable = Disposer.newDisposable()
    listenableProgressIndicator.addAndInvokeListener(listenerDisposable) {
      trySend(listenableProgressIndicator.text)
    }
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }

  private val _branchState: MutableStateFlow<BranchState?> = MutableStateFlow(null)
  override val branchState: Flow<BranchState?> = _branchState.asSharedFlow()

  override val existingMergeRequest: Flow<String?> = branchState.map { state ->
    state ?: return@map null
    val sourceProject = state.headRepo
    val sourceBranch = state.headBranch.name
    val targetProject = state.baseRepo
    val targetBranch = state.baseBranch.nameForRemoteOperations

    projectData.mergeRequests.findByBranches(GitLabMergeRequestState.OPENED, sourceBranch, targetBranch).find {
      it.targetProject.fullPath == targetProject.repository.projectPath.fullPath() &&
      it.sourceProject?.fullPath == sourceProject.repository.projectPath.fullPath()
    }?.iid
  }

  private val gitRepository: GitRepository = projectData.projectMapping.gitRepository
  private val commitRevisionComparisonFlow: Flow<CommitRevisionComparison?> =
    combine(gitRepository.changesSignalFlow().withInitial(Unit), branchState) { _, state -> state }
      .map { state ->
        state ?: return@map null
        CommitRevisionComparison.create(gitRepository, state.headBranch, state.baseBranch)
      }
      .distinctUntilChangedBy { it }

  override val commits: StateFlow<Result<List<VcsCommitMetadata>>?> = commitRevisionComparisonFlow.transformLatest { revisionComparison ->
    if (revisionComparison?.baseRevision == null || revisionComparison.headRevision == null) {
      emit(Result.success(emptyList()))
      return@transformLatest
    }

    emit(null)
    val result = withContext(Dispatchers.IO) {
      runCatchingUser {
        val revisionRange = "${revisionComparison.baseRevision}..${revisionComparison.headRevision}"
        val metadata = GitLogUtil.collectMetadata(project, gitRepository.root, revisionRange)
        metadata.commits
      }
    }
    emit(result)
  }.modelFlow(cs, thisLogger())
    .stateInNow(cs, null)

  override val reviewRequirementsErrorState: Flow<MergeRequestRequirementsErrorType?> =
    combine(branchState, commits) { branchState, commits ->
      branchState ?: return@combine null
      commits ?: return@combine null

      checkDirection(branchState)
      ?: checkChanges(commits)
      ?: checkProtectedBranch(branchState, project)
    }

  private val _reviewCreatingError: MutableStateFlow<Throwable?> = MutableStateFlow(null)
  override val reviewCreatingError: StateFlow<Throwable?> = _reviewCreatingError.asStateFlow()

  override val potentialReviewers: Flow<Result<List<GitLabUserDTO>>> =
    GitLabCoroutineUtil.batchesResultsFlow(projectData.dataReloadSignal, projectData::getMembersBatches)

  private val _adjustedReviewers: MutableStateFlow<List<GitLabUserDTO>> = MutableStateFlow(listOf())
  override val adjustedReviewers: StateFlow<List<GitLabUserDTO>> = _adjustedReviewers.asStateFlow()

  private val _title: MutableStateFlow<String> = MutableStateFlow("")
  override val titleText: StateFlow<String> = _title.asStateFlow()

  private val _description: MutableStateFlow<String> = MutableStateFlow("")
  override val descriptionText: StateFlow<String> = _description.asStateFlow()

  // TODO: Implement templates for this plugin
  override val isTemplateLoading: StateFlow<Boolean> = MutableStateFlow(false)

  override val titleGenerationVm: StateFlow<GitLabMergeRequestCreateTitleGenerationViewModel?> =
    commits.combine(GitLabTitleAndDescriptionGeneratorExtension.EP_NAME.extensionListFlow()) { commits, extensions ->
      val commits = commits?.getOrNull() ?: return@combine null
      if (commits.isEmpty()) return@combine null

      val extension = extensions.firstOrNull() ?: return@combine null

      commits to extension
    }.mapNullableScoped { (commits, extension) ->
      GitLabMergeRequestCreateTitleGenerationViewModelImpl(this, project, extension, commits, ::setTitle, ::setDescription)
    }.stateIn(cs, SharingStarted.Lazily, null)

  init {
    cs.launch {
      val baseRepo = projectData.projectMapping
      val baseGitRepo = baseRepo.gitRepository
      val defaultBranch = projectData.getDefaultBranch() ?: return@launch
      val baseBranch = baseGitRepo.getBranchTrackInfo(defaultBranch)?.remoteBranch ?: return@launch
      val currentBranch = baseGitRepo.currentBranch ?: return@launch

      _branchState.value = BranchState(baseRepo, baseBranch, baseRepo, currentBranch)
    }

    cs.launch {
      combine(commits, branchState) { commitsResult, branchState ->
        val commits = commitsResult?.getOrNull()
        if (commits != null && commits.size == 1 && titleText.value.isEmpty()) {
          setTitle(commits.first().subject.lines().firstOrNull() ?: return@combine)
        }
        else if (titleText.value.isEmpty()) {
          setTitle(when (val branch = branchState?.headBranch ?: return@combine) {
                     is GitRemoteBranch -> branch.nameForRemoteOperations
                     else -> branch.name
                   })
        }
      }
    }
  }

  override fun setTitle(text: String) {
    _title.value = text
  }

  override fun setDescription(text: String) {
    _description.value = text
  }

  override fun updateBranchState(state: BranchState?) {
    _branchState.value = state
  }

  override fun adjustReviewer(point: RelativePoint) {
    cs.launchNow(Dispatchers.Main) {
      val allowsMultipleReviewers = allowsMultipleReviewers.first()
      val originalReviewersIds = adjustedReviewers.value.mapTo(mutableSetOf<String>(), GitLabUserDTO::id)
      val updatedReviewers = if (allowsMultipleReviewers)
        GitLabMergeRequestReviewersUtil.selectReviewers(point, originalReviewersIds, potentialReviewers, avatarIconProvider)
      else
        GitLabMergeRequestReviewersUtil.selectReviewer(point, originalReviewersIds, potentialReviewers, avatarIconProvider)

      updatedReviewers ?: return@launchNow
      _adjustedReviewers.value = updatedReviewers
      GitLabStatistics.logMrCreationReviewersAdjusted(project)
    }
  }

  override fun createMergeRequest() {
    taskLauncher.launch {
      GitLabStatistics.logMrCreationStarted(project)
      val (_, baseBranch, headRepo, headBranch) = _branchState.value ?: return@launch
      _reviewCreatingError.value = null
      try {
        val gitRemoteBranch = findGitRemoteBranch(headRepo, headBranch)
        val mergeRequest = projectData.createMergeRequestAndAwaitCompletion(
          sourceBranch = gitRemoteBranch.nameForRemoteOperations,
          targetBranch = baseBranch.nameForRemoteOperations,
          title = titleText.value.ifBlank { gitRemoteBranch.nameForRemoteOperations },
          description = descriptionText.value.ifBlank { null }
        )
        projectData.adjustReviewers(mergeRequest.iid, adjustedReviewers.value)
        openReviewTabAction(mergeRequest.iid)
        onReviewCreated()
        GitLabStatistics.logMrCreationSucceeded(project)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        _reviewCreatingError.value = e
        val errorCode = (e as? HttpStatusErrorException)?.statusCode ?: -1
        GitLabStatistics.logMrCreationFailed(project, errorCode)
      }
    }
  }

  private suspend fun findGitRemoteBranch(headRepo: GitLabProjectMapping, headBranch: GitBranch): GitRemoteBranch {
    return withContext(Dispatchers.Main) {
      when (headBranch) {
        is GitRemoteBranch -> headBranch
        is GitLocalBranch -> {
          val dialogMessages = GitPushUtil.BranchNameInputDialogMessages(
            GitLabBundle.message("merge.request.create.branch.dialog.title"),
            GitLabBundle.message("merge.request.create.branch.dialog.message"),
            GitLabBundle.message("merge.request.create.branch.dialog.comment")
          )
          GitPushUtil.findOrPushRemoteBranch(
            project, listenableProgressIndicator,
            headRepo.gitRepository, headRepo.gitRemote, headBranch,
            dialogMessages
          ).await()
        }
        else -> error("No more inheritances")
      }
    }
  }
}

internal data class BranchState(
  val baseRepo: GitLabProjectMapping,
  val baseBranch: GitRemoteBranch,
  val headRepo: GitLabProjectMapping,
  val headBranch: GitBranch,
) {
  companion object {
    internal fun fromDirectionModel(directionModel: GitLabMergeRequestCreateDirectionModel): BranchState? = with(directionModel) {
      BranchState(
        baseRepo,
        baseBranch ?: return null,
        headRepo ?: return null,
        headBranch ?: return null,
      )
    }
  }
}

internal enum class MergeRequestRequirementsErrorType {
  WRONG_DIRECTION,
  NO_CHANGES,
  PROTECTED_BRANCH
}

private fun checkDirection(branchState: BranchState): MergeRequestRequirementsErrorType? {
  val headBranch = branchState.headBranch
  val baseBranch = branchState.baseBranch
  val baseRepo = branchState.baseRepo

  val trackInfo = baseRepo.gitRepository.getBranchTrackInfo(baseBranch.nameForRemoteOperations) ?: return null
  return if (trackInfo.localBranch == headBranch) MergeRequestRequirementsErrorType.WRONG_DIRECTION else null
}

private fun checkChanges(loadingCommits: Result<List<VcsCommitMetadata>>): MergeRequestRequirementsErrorType? {
  return loadingCommits.fold(
    onSuccess = { commits ->
      if (commits.isNotEmpty()) return@fold null
      MergeRequestRequirementsErrorType.NO_CHANGES
    },
    onFailure = {
      null
    }
  )
}

private fun checkProtectedBranch(branchState: BranchState, project: Project): MergeRequestRequirementsErrorType? {
  val settings = GitSharedSettings.getInstance(project)
  val localBranchName = if (branchState.headBranch is GitRemoteBranch)
    branchState.headBranch.nameForRemoteOperations
  else
    branchState.headBranch.name

  if (!settings.isBranchProtected(localBranchName)) return null

  return MergeRequestRequirementsErrorType.PROTECTED_BRANCH
}

private data class CommitRevisionComparison(val headRevision: Hash?, val baseRevision: Hash?) {
  companion object {
    fun create(repository: GitRepository, headBranch: GitBranch, baseBranch: GitRemoteBranch): CommitRevisionComparison {
      val info = repository.info
      val headRevision = if (headBranch.isRemote) {
        info.remoteBranchesWithHashes[headBranch]
      }
      else {
        info.localBranchesWithHashes[headBranch]
      }
      val baseRevision = info.remoteBranchesWithHashes[baseBranch]
      return CommitRevisionComparison(headRevision, baseRevision)
    }
  }
}