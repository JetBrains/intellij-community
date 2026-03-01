// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.model

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.async.extensionListFlow
import com.intellij.collaboration.async.mapNullableScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.ListenableProgressIndicator
import com.intellij.collaboration.ui.codereview.create.CodeReviewTitleDescriptionViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.collaboration.util.collectIncrementallyTo
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitPushUtil
import git4idea.GitRemoteBranch
import git4idea.config.GitSharedSettings
import git4idea.history.GitLogUtil
import git4idea.remote.hosting.changesSignalFlow
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLabel
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

internal interface GitLabMergeRequestCreateViewModel : CodeReviewTitleDescriptionViewModel {
  val projectMapping: GitLabProjectMapping
  val avatarIconProvider: IconsProvider<GitLabUserDTO>

  val isBusy: StateFlow<Boolean>

  val titleGenerationVm: StateFlow<GitLabMergeRequestCreateTitleGenerationViewModel?>

  val allowsMultipleAssignees: StateFlow<Boolean>
  val allowsMultipleReviewers: StateFlow<Boolean>
  val branchState: StateFlow<BranchState?>

  val existingMergeRequest: StateFlow<String?>
  val creatingProgressText: StateFlow<String?>
  val commits: StateFlow<Result<List<VcsCommitMetadata>>?>

  val reviewRequirementsErrorState: StateFlow<MergeRequestRequirementsErrorType?>
  val reviewCreatingError: StateFlow<Throwable?>

  val projectMembers: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>>
  val reviewers: StateFlow<List<GitLabUserDTO>>
  val assignees: StateFlow<List<GitLabUserDTO>>

  val projectLabels: StateFlow<IncrementallyComputedValue<List<GitLabLabel>>>
  val labels: StateFlow<List<GitLabLabel>>

  val squashBeforeMergeReadOnly: Boolean
  val squashBeforeMerge: StateFlow<Boolean>
  val removeSourceBranch: StateFlow<Boolean>

  fun updateBranchState(state: BranchState?)

  fun getAllKnownProjects(): List<GitLabProjectMapping>

  fun setReviewers(reviewers: List<GitLabUserDTO>)
  fun clearReviewers()
  fun setAssignees(assignees: List<GitLabUserDTO>)
  fun clearAssignees()
  fun setLabels(labels: List<GitLabLabel>)
  fun clearLabels()

  fun setSquashBeforeMerge(value: Boolean)
  fun setRemoveSourceBranch(value: Boolean)

  fun openExistingReview()

  fun createMergeRequest()
}

private val LOG = logger<GitLabMergeRequestCreateViewModelImpl>()

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestCreateViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val projectsManager: GitLabProjectsManager,
  private val projectData: GitLabProject,
  override val avatarIconProvider: IconsProvider<GitLabUserDTO>,
  private val openReviewTabAction: suspend (mrIid: String) -> Unit,
  private val onReviewCreated: () -> Unit,
) : GitLabMergeRequestCreateViewModel {
  private val cs: CoroutineScope = parentCs.childScope(this::class)
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val allowsMultipleAssignees: StateFlow<Boolean> = suspend {
    try {
      projectData.isMultipleAssigneesAllowed()
    }
    catch (_: Exception) {
      false
    }
  }.asFlow().stateIn(cs, SharingStarted.Eagerly, false)

  override val allowsMultipleReviewers: StateFlow<Boolean> = suspend {
    try {
      projectData.isMultipleReviewersAllowed()
    }
    catch (_: Exception) {
      false
    }
  }.asFlow().stateIn(cs, SharingStarted.Eagerly, false)

  private val listenableProgressIndicator = ListenableProgressIndicator()
  override val creatingProgressText: StateFlow<String?> = callbackFlow {
    val listenerDisposable = Disposer.newDisposable()
    listenableProgressIndicator.addAndInvokeListener(listenerDisposable) {
      trySend(listenableProgressIndicator.text)
    }
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }.stateIn(cs, SharingStarted.Eagerly, null)

  private val _branchState: MutableStateFlow<BranchState?> = MutableStateFlow(null)
  override val branchState: StateFlow<BranchState?> = _branchState.asStateFlow()

  override val existingMergeRequest: StateFlow<String?> = branchState.map { state ->
    state ?: return@map null
    val sourceProject = state.headRepo
    val sourceBranch = state.headBranch.name
    val targetProject = state.baseRepo
    val targetBranch = state.baseBranch.nameForRemoteOperations

    runCatching {
      projectData.mergeRequests.findByBranches(GitLabMergeRequestState.OPENED, sourceBranch, targetBranch).find {
        it.targetProject.fullPath == targetProject.repository.projectPath.fullPath() &&
        it.sourceProject?.fullPath == sourceProject.repository.projectPath.fullPath()
      }?.iid
    }.getOrHandleException {
      LOG.warn("Failed to check for existing merge request", it)
    }
  }.stateIn(cs, SharingStarted.Eagerly, null)

  private val gitRepository: GitRepository = projectData.gitRemote.repository
  override val projectMapping: GitLabProjectMapping = GitLabProjectMapping(projectData.projectCoordinates, projectData.gitRemote)

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

  override val reviewRequirementsErrorState: StateFlow<MergeRequestRequirementsErrorType?> =
    combine(branchState, commits) { branchState, commits ->
      branchState ?: return@combine null
      commits ?: return@combine null

      checkDirection(branchState)
      ?: checkChanges(commits)
      ?: checkProtectedBranch(branchState, project)
    }.stateIn(cs, SharingStarted.Eagerly, null)

  private val _reviewCreatingError: MutableStateFlow<Throwable?> = MutableStateFlow(null)
  override val reviewCreatingError: StateFlow<Throwable?> = _reviewCreatingError.asStateFlow()

  override val projectMembers: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>> =
    projectData.dataReloadSignal.withInitial(Unit).transformLatest {
      projectData.getMembersBatches().collectIncrementallyTo(this)
    }.stateIn(cs, SharingStarted.Lazily, IncrementallyComputedValue.loading())

  private val _reviewers: MutableStateFlow<List<GitLabUserDTO>> = MutableStateFlow(listOf())
  override val reviewers: StateFlow<List<GitLabUserDTO>> = _reviewers.asStateFlow()

  private val _assignees: MutableStateFlow<List<GitLabUserDTO>> = MutableStateFlow(listOf())
  override val assignees: StateFlow<List<GitLabUserDTO>> = _assignees.asStateFlow()

  override val projectLabels: StateFlow<IncrementallyComputedValue<List<GitLabLabel>>> =
    projectData.dataReloadSignal.withInitial(Unit).transformLatest {
      projectData.getLabelsBatches().collectIncrementallyTo(this)
    }.stateIn(cs, SharingStarted.Lazily, IncrementallyComputedValue.loading())

  private val _labels: MutableStateFlow<List<GitLabLabel>> = MutableStateFlow(listOf())
  override val labels: StateFlow<List<GitLabLabel>> = _labels.asStateFlow()

  private val _title: MutableStateFlow<TitleState> = MutableStateFlow(TitleState("", true))
  override val titleText: StateFlow<String> = _title.mapState { it.title }

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
      GitLabMergeRequestCreateTitleGenerationViewModelImpl(this, project, extension, commits, {
        _title.value = TitleState(it, true)
      }, ::setDescription)
    }.stateIn(cs, SharingStarted.Lazily, null)

  override val squashBeforeMergeReadOnly: Boolean = projectData.squashMergeRequestBeforeMergeReadOnly
  private val _squashBeforeMerge = MutableStateFlow(projectData.squashMergeRequestBeforeMerge)
  override val squashBeforeMerge: StateFlow<Boolean> = _squashBeforeMerge.asStateFlow()
  private val _removeSourceBranch = MutableStateFlow(projectData.removeMergeRequestSourceBranch)
  override val removeSourceBranch: StateFlow<Boolean> = _removeSourceBranch.asStateFlow()

  init {
    initBranchState()

    cs.launch {
      combineAndCollect(commits, branchState) { commitsResult, branchState ->
        val state = _title.value
        if (!state.generated && !state.title.isBlank()) {
          return@combineAndCollect
        }

        val commits = commitsResult?.getOrNull() // await commits loading
        if (commits != null) {
          val title = if (commits.size == 1) {
            commits.first().subject.lines().firstOrNull() ?: return@combineAndCollect
          }
          else {
            when (val branch = branchState?.headBranch ?: return@combineAndCollect) {
              is GitRemoteBranch -> branch.nameForRemoteOperations
              else -> branch.name
            }
          }
          _title.compareAndSet(state, state.copy(title = title))
        }
      }
    }
  }

  private fun initBranchState() {
    val baseRepo = GitLabProjectMapping(projectData.projectCoordinates, projectData.gitRemote)
    val baseGitRepo = baseRepo.gitRepository
    val defaultBranch = projectData.defaultBranch ?: return
    val baseBranch = baseGitRepo.getBranchTrackInfo(defaultBranch)?.remoteBranch ?: return
    val currentBranch = baseGitRepo.currentBranch ?: return
    val currentBranchTrackInfo = currentBranch.let { baseGitRepo.getBranchTrackInfo(it.name) }
    val projects = projectsManager.knownRepositoriesState.value
    val headRepo = currentBranchTrackInfo?.remote?.let { remote ->
      projects.find { it.gitRepository.root == baseGitRepo.root && it.remote.remote == remote }
    } ?: baseRepo

    _branchState.value = BranchState(baseRepo, baseBranch, headRepo, currentBranch)
  }

  override fun setTitle(text: String) {
    _title.value = TitleState(text, false)
  }

  override fun setDescription(text: String) {
    _description.value = text
  }

  override fun updateBranchState(state: BranchState?) {
    titleGenerationVm.value?.stopGeneration()
    _branchState.value = state
  }

  override fun getAllKnownProjects(): List<GitLabProjectMapping> = projectsManager.knownRepositories.toList()

  override fun setReviewers(reviewers: List<GitLabUserDTO>) {
    _reviewers.value = reviewers
    GitLabStatistics.logMrCreationReviewersAdjusted(project)
  }

  override fun clearReviewers() {
    _reviewers.value = listOf()
  }

  override fun setAssignees(assignees: List<GitLabUserDTO>) {
    _assignees.value = assignees
  }

  override fun clearAssignees() {
    _assignees.value = listOf()
  }

  override fun setLabels(labels: List<GitLabLabel>) {
    _labels.value = labels
  }

  override fun clearLabels() {
    _labels.value = listOf()
  }

  override fun setSquashBeforeMerge(value: Boolean) {
    _squashBeforeMerge.value = value
  }

  override fun setRemoveSourceBranch(value: Boolean) {
    _removeSourceBranch.value = value
  }

  override fun openExistingReview() {
    val mrIid = existingMergeRequest.value ?: return
    taskLauncher.launch {
      openReviewTabAction(mrIid)
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
          description = descriptionText.value.ifBlank { null },
          reviewers = reviewers.value,
          assignees = assignees.value,
          labels = labels.value,
          // because we're not always sure about the exact value (hello GQL)
          // don't pass the value here and hope it's handled by default
          squashBeforeMerge = squashBeforeMerge.value.takeIf { !squashBeforeMergeReadOnly },
          removeSourceBranch = removeSourceBranch.value
        )
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

private data class TitleState(
  val title: String,
  val generated: Boolean,
)

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