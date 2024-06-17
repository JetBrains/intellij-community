// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.async.*
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.computeEmitting
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.push.PushSpec
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.*
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.push.GitPushOperation
import git4idea.push.GitPushSource
import git4idea.push.GitPushSupport
import git4idea.push.GitPushTarget
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRemote
import git4idea.validators.GitRefNameValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTab
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel.*
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.component.LabeledListPanelViewModel
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private typealias NewBranchNameCallback = suspend (suggestedName: String) -> String

@ApiStatus.Experimental
internal interface GHPRCreateViewModel {
  val project: Project
  val avatarIconsProvider: GHAvatarIconsProvider

  val repositoryName: @NlsSafe String

  val repositories: Collection<GHGitRepositoryMapping>
  val branches: StateFlow<BranchesState>

  val commits: StateFlow<ComputedResult<List<VcsCommitMetadata>>?>

  val titleText: StateFlow<String>
  val descriptionText: StateFlow<String>
  val templateLoadingState: StateFlow<ComputedResult<String?>>

  val assigneesVm: LabeledListPanelViewModel<GHUser>
  val reviewersVm: LabeledListPanelViewModel<GHPullRequestRequestedReviewer>
  val labelsVm: LabeledListPanelViewModel<GHLabel>

  val branchesCheckState: StateFlow<ComputedResult<BranchesCheckResult>?>
  val creationProgress: StateFlow<CreationState?>

  var remoteBranchNameCallback: NewBranchNameCallback?

  fun setTitle(text: String)
  fun setDescription(text: String)

  fun setHead(repo: GHGitRepositoryMapping?, branch: GitBranch?)
  fun setBaseBranch(branch: GitRemoteBranch?)

  fun create(isDraft: Boolean)

  data class BranchesState(
    val baseRepo: GHGitRepositoryMapping,
    val baseBranch: GitRemoteBranch?,
    val headRepo: GHGitRepositoryMapping?,
    val headBranch: GitBranch?
  )

  sealed interface BranchesCheckResult {
    data class NoChanges(val baseBranch: GitRemoteBranch, val headBranch: GitBranch) : BranchesCheckResult
    data class AlreadyExists(val open: () -> Unit) : BranchesCheckResult
    data object OK : BranchesCheckResult
  }

  sealed interface CreationState {
    data object CollectingData : CreationState
    data object Pushing : CreationState
    data object CallingAPI : CreationState
    data object SettingMetadata : CreationState
    data object Created : CreationState
    data class Error(val error: Throwable) : CreationState
  }
}

internal class GHPRCreateViewModelImpl(override val project: Project,
                                       parentCs: CoroutineScope,
                                       private val repositoryManager: GHHostedRepositoriesManager,
                                       private val settings: GithubPullRequestsProjectUISettings,
                                       private val dataContext: GHPRDataContext,
                                       private val projectVm: GHPRToolWindowProjectViewModel)
  : GHPRCreateViewModel, Disposable {
  private val cs = parentCs.childScope(classAsCoroutineName())
  override val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider
  private val repoData = dataContext.repositoryDataService

  override val repositoryName: String
    get() = GHUIUtil.getRepositoryDisplayName(repositories.mapTo(mutableSetOf()) { it.repository },
                                              repoData.repositoryCoordinates)

  override val repositories: Collection<GHGitRepositoryMapping>
    get() = repositoryManager.knownRepositories

  private val branchesWithProgress = MutableStateFlow(BranchesStateWithProgress(getDefaultBranches()))
  override val branches: StateFlow<BranchesState> = branchesWithProgress.map { it.branches }
    .stateInNow(cs, branchesWithProgress.value.branches)

  override val commits: StateFlow<ComputedResult<List<VcsCommitMetadata>>?> =
    branchesWithProgress.map { it.commitsRequest }.optionalComputationState()
      .stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val titleText: MutableStateFlow<String> = MutableStateFlow("")
  override val descriptionText: MutableStateFlow<String> = MutableStateFlow("")
  override val templateLoadingState: StateFlow<ComputedResult<String?>> = computationStateFlow(flowOf(Unit)) {
    dataContext.repositoryDataService.loadTemplate()
  }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val assigneesVm: LabeledListPanelViewModel<GHUser> = MetadataListViewModel(cs) {
    dataContext.repositoryDataService.loadIssuesAssignees()
  }
  override val reviewersVm: LabeledListPanelViewModel<GHPullRequestRequestedReviewer> = MetadataListViewModel(cs) {
    dataContext.repositoryDataService.loadPotentialReviewers().filter { it.id != dataContext.securityService.currentUser.id }
  }
  override val labelsVm: LabeledListPanelViewModel<GHLabel> = MetadataListViewModel(cs) {
    dataContext.repositoryDataService.loadLabels()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val branchesCheckState: StateFlow<ComputedResult<BranchesCheckResult>?> = branchesWithProgress.transformLatest { branchesWithProgress ->
    checkBranchesEmitting(branchesWithProgress)
  }.stateIn(cs, SharingStarted.Lazily, null)

  private val _creationProgress = MutableStateFlow<CreationState?>(null)
  override val creationProgress: StateFlow<CreationState?> = _creationProgress.asStateFlow()

  private val titleLock = ReentrantLock()

  @Volatile
  private var titleSetAutomatically: Boolean = true

  override var remoteBranchNameCallback: NewBranchNameCallback? = null

  init {
    cs.launchNow {
      val template = templateLoadingState.mapNotNull { it.result }.map { it.getOrNull() }.first()
      descriptionText.value = template.orEmpty()
    }

    cs.launchNow {
      branchesWithProgress.collectScoped {
        if (!titleSetAutomatically) {
          currentCoroutineContext().cancel()
          return@collectScoped
        }
        val headBranch = it.branches.headBranch ?: return@collectScoped
        val commits = runCatching {
          it.commitsRequest?.await()
        }.getOrNull() ?: return@collectScoped
        setTitleFromFirstCommitOrBranch(headBranch, commits)
      }
    }
  }

  private fun setTitleFromFirstCommitOrBranch(headBranch: GitBranch, commits: List<VcsCommitMetadata>) {
    titleLock.withLock {
      val singleCommit = commits.singleOrNull()
      titleText.value = if (singleCommit != null) {
        singleCommit.fullMessage.split("\n\n").firstOrNull().orEmpty()
      }
      else {
        headBranch.name
      }
    }
  }

  override fun setTitle(text: String) {
    titleLock.withLock {
      if (titleText.value == text) return@withLock
      titleSetAutomatically = false
      titleText.value = text
    }
  }

  override fun setDescription(text: String) {
    descriptionText.value = text
  }

  override fun setHead(repo: GHGitRepositoryMapping?, branch: GitBranch?) {
    branchesWithProgress.update {
      if (it.branches.headRepo != repo || it.branches.headBranch != branch) {
        it.cancel()
        BranchesStateWithProgress(it.branches.copy(headRepo = repo, headBranch = branch))
      }
      else {
        it
      }
    }
  }

  override fun setBaseBranch(branch: GitRemoteBranch?) {
    branchesWithProgress.update {
      if (it.branches.baseBranch != branch) {
        it.cancel()
        BranchesStateWithProgress(it.branches.copy(baseBranch = branch))
      }
      else {
        it
      }
    }
  }

  override fun create(isDraft: Boolean) {
    val branchesWithProgress = branchesWithProgress.value
    if (branchesWithProgress.commitsRequest?.isCompleted != true) return
    if (branchesWithProgress.existingPrRequest?.isCompleted != true) return

    val branches = branchesWithProgress.branches

    val baseBranch = branches.baseBranch ?: return
    val headRepo = branches.headRepo ?: return
    val headBranch = branches.headBranch ?: return
    val title = titleText.value
    val description = descriptionText.value

    val reviewers = reviewersVm.items.value
    val assignees = assigneesVm.items.value
    val labels = labelsVm.items.value

    val prevState = _creationProgress.getAndUpdate {
      if (it == null || it is CreationState.Error) {
        CreationState.CollectingData
      }
      else {
        it
      }
    }
    if (prevState != null && prevState !is CreationState.Error) return
    cs.launch {
      try {
        val remoteHeadBranch = when (headBranch) {
          is GitRemoteBranch -> headBranch
          is GitLocalBranch -> {
            val pushTarget = GitPushUtil.findPushTarget(headRepo.gitRepository, headRepo.gitRemote, headBranch)
                             ?: createPushTarget(headRepo.gitRemote, headBranch)
            val needToPush = with(headRepo.gitRepository.branches) { getHash(headBranch) != getHash(pushTarget.branch) }
            if (needToPush) {
              _creationProgress.value = CreationState.Pushing
              pushBranch(headRepo, headBranch, pushTarget)
            }
            pushTarget.branch
          }
          else -> error("Unknown branch type")
        }

        _creationProgress.value = CreationState.CallingAPI
        val pullRequest = dataContext.creationService.createPullRequest(baseBranch, headRepo, remoteHeadBranch,
                                                                        title, description, isDraft)

        if (reviewers.isNotEmpty() || assignees.isNotEmpty() || labels.isNotEmpty()) {
          _creationProgress.value = CreationState.SettingMetadata
          val detailsService = dataContext.detailsService
          detailsService.adjustReviewers(pullRequest.prId, CollectionDelta(emptyList(), reviewers))
          detailsService.adjustAssignees(pullRequest.prId, CollectionDelta(emptyList(), assignees))
          detailsService.adjustLabels(pullRequest.prId, CollectionDelta(emptyList(), labels))
        }

        _creationProgress.value = CreationState.Created

        projectVm.viewPullRequest(pullRequest.prId)
        settings.recentNewPullRequestHead = headRepo.repository
        projectVm.refreshPrOnCurrentBranch()
        projectVm.closeTab(GHPRToolWindowTab.NewPullRequest)
      }
      catch (ce: CancellationException) {
        _creationProgress.value = null
      }
      catch (e: Exception) {
        _creationProgress.value = CreationState.Error(e)
      }
    }
  }

  private suspend fun createPushTarget(remote: GitRemote, localBranch: GitLocalBranch): GitPushTarget {
    val branchName = remoteBranchNameCallback?.invoke(localBranch.name)?.let {
      GitRefNameValidator.getInstance().cleanUpBranchName(it)
    } ?: error("Missing branch name supplier")
    val remoteBranch = GitStandardRemoteBranch(remote, branchName)
    return GitPushTarget(remoteBranch, true)
  }

  private suspend fun pushBranch(headRepo: GHGitRepositoryMapping, localBranch: GitLocalBranch, pushTarget: GitPushTarget) {
    val pushSupport = DvcsUtil.getPushSupport(GitVcs.getInstance(project)) as GitPushSupport
    val pushSpec = PushSpec(GitPushSource.create(localBranch), pushTarget)
    val pushOperation = GitPushOperation(project, pushSupport, mapOf(headRepo.gitRepository to pushSpec), null, false, false)
    val pushResult = withContext(Dispatchers.IO) {
      coroutineToIndicator {
        pushOperation.execute().results[headRepo.gitRepository] ?: error("Missing push result")
      }
    }
    check(pushResult.error == null) {
      GitBundle.message("push.failed.error.message", pushResult.error.orEmpty())
    }
  }

  private suspend fun FlowCollector<ComputedResult<BranchesCheckResult>?>.checkBranchesEmitting(branchesWithProgress: BranchesStateWithProgress) {
    val baseBranch = branchesWithProgress.branches.baseBranch ?: run {
      emit(null)
      return
    }
    val headRepo = branchesWithProgress.branches.headRepo ?: run {
      emit(null)
      return
    }
    val headBranch = branchesWithProgress.branches.headBranch ?: run {
      emit(null)
      return
    }

    val trackedHead = when (headBranch) {
      is GitRemoteBranch -> headBranch
      is GitLocalBranch -> headRepo.gitRepository.getBranchTrackInfo(headBranch.name)?.remoteBranch
      else -> null
    }
    if (baseBranch == trackedHead) {
      emit(ComputedResult.success(BranchesCheckResult.NoChanges(baseBranch, headBranch)))
      return
    }

    val commitsRequest = branchesWithProgress.commitsRequest ?: run {
      emit(null)
      return
    }
    val existingPrRequest = branchesWithProgress.existingPrRequest ?: run {
      emit(null)
      return
    }

    computeEmitting {
      checkBranches(baseBranch, headBranch, commitsRequest, existingPrRequest)
    }
  }

  private suspend fun checkBranches(baseBranch: GitRemoteBranch,
                                    headBranch: GitBranch,
                                    commitsRequest: Deferred<List<VcsCommitMetadata>>,
                                    existingPrRequest: Deferred<GHPRIdentifier?>): BranchesCheckResult {
    val commits = commitsRequest.await()
    if (commits.isEmpty()) {
      return BranchesCheckResult.NoChanges(baseBranch, headBranch)
    }
    val existingPr = existingPrRequest.await()
    if (existingPr != null) {
      return BranchesCheckResult.AlreadyExists { projectVm.viewPullRequest(existingPr, true) }
    }
    return BranchesCheckResult.OK
  }

  override fun dispose() {
    cs.cancel()
  }

  private inner class BranchesStateWithProgress(val branches: BranchesState) {
    private val loadingCs = cs.childScope("BranchStatesLoader", Dispatchers.IO)

    val commitsRequest: Deferred<List<VcsCommitMetadata>>? = loadCommitsAsync()
    val existingPrRequest: Deferred<GHPRIdentifier?>? = findExistingPrAsync()

    private fun loadCommitsAsync(): Deferred<List<VcsCommitMetadata>>? {
      val repository = branches.headRepo?.gitRepository ?: return null
      val baseBranch = branches.baseBranch ?: return null
      val headBranch = branches.headBranch ?: return null

      return loadingCs.async(start = CoroutineStart.LAZY) {
        coroutineToIndicator {
          GitLogUtil.collectMetadata(repository.project, repository.root, "${baseBranch.name}..${headBranch.name}").commits
        }
      }
    }

    private fun findExistingPrAsync(): Deferred<GHPRIdentifier?>? {
      val headRepo = branches.headRepo ?: return null
      val baseBranch = branches.baseBranch ?: return null
      val headBranch = branches.headBranch ?: return null
      val remoteHeadBranch = when (headBranch) {
                               is GitRemoteBranch -> headBranch
                               is GitLocalBranch -> headRepo.gitRepository.getBranchTrackInfo(headBranch.name)?.remoteBranch
                               else -> null
                             } ?: return CompletableDeferred(value = null)
      return loadingCs.async(start = CoroutineStart.LAZY) {
        dataContext.creationService.findOpenPullRequest(baseBranch, headRepo.repository.repositoryPath, remoteHeadBranch)
      }
    }

    fun cancel() {
      loadingCs.cancel()
    }
  }

  private fun getDefaultBranches(): BranchesState {
    val baseRepo = repoData.repositoryMapping
    val baseGitRepo = baseRepo.gitRepository
    val defaultBranch = repoData.getDefaultRemoteBranch()
    val currentBranch = baseGitRepo.currentBranch
    val currentBranchTrackInfo = currentBranch?.let { baseGitRepo.getBranchTrackInfo(it.name) }
    val headRepo = currentBranchTrackInfo?.remote?.let { remote -> repositories.find { it.remote.remote == remote } } ?: baseRepo
    return BranchesState(baseRepo, defaultBranch, headRepo, currentBranch)
  }
}

private class MetadataListViewModel<T>(cs: CoroutineScope, itemsLoader: suspend () -> List<T>) : LabeledListPanelViewModel<T> {
  override val isEditingAllowed: Boolean = true
  override val items = MutableStateFlow(emptyList<T>())
  override val selectableItems: StateFlow<ComputedResult<List<T>>> =
    computationStateFlow(flowOf(Unit)) { itemsLoader() }
      .stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())
  override val adjustmentProcessState: StateFlow<ComputedResult<Unit>?> = MutableStateFlow(null)
  override val editRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  override fun requestEdit() {
    editRequests.tryEmit(Unit)
  }

  override fun adjustList(newList: List<T>) {
    items.value = newList
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<Deferred<T>?>.optionalComputationState(): Flow<ComputedResult<T>?> =
  transformLatest { request ->
    if (request == null) {
      emit(null)
      return@transformLatest
    }
    flowOf(request).computationState().collect(this)
  }


