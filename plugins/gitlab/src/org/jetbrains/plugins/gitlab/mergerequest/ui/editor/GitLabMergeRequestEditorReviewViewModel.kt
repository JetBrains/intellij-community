// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.withLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import git4idea.branch.GitBranchSyncStatus
import git4idea.changes.GitBranchComparisonResult
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.remote.hosting.changesSignalFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridge
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestBranchUtil
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestEditorReviewViewModel internal constructor(
  parentCs: CoroutineScope,
  private val project: Project,
  private val projectMapping: GitLabProjectMapping,
  currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val diffBridge: GitLabMergeRequestDiffBridge,
  private val projectVm: GitLabToolWindowProjectViewModel,
  private val discussions: GitLabMergeRequestDiscussionsViewModels,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestReviewViewModelBase(
  parentCs.childScope(CoroutineName("GitLab Merge Request Editor Review VM")),
  currentUser, mergeRequest
) {
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  val mergeRequestIid: String = mergeRequest.iid

  private val _actualChangesState = MutableStateFlow<ChangesState>(ChangesState.NotLoaded)
  private val changesRequest = MutableSharedFlow<Unit>(replay = 1)

  val actualChangesState: StateFlow<ChangesState> = _actualChangesState.asStateFlow()

  /**
   * Contains the state of filePath to shared file vm mapping
   * Will only store paths which belong to the review
   * VMs are stored as flows to allow for on-demand creation
   */
  private val filesVmsState: Flow<Map<FilePath, Flow<GitLabMergeRequestEditorReviewFileViewModel?>>> by lazy {
    changesRequest.tryEmit(Unit)
    actualChangesState.mapScoped {
      (it as? ChangesState.Loaded)?.changes?.let { createFilesVms(it) } ?: emptyMap()
    }.shareIn(cs, SharingStarted.Eagerly, 1)
  }

  val localRepositorySyncStatus: StateFlow<GitBranchSyncStatus?> = run {
    val repository = projectMapping.remote.repository
    val changesFlow = _actualChangesState.map { (it as? ChangesState.Loaded)?.changes }.distinctUntilChanged()
    val currentRevisionFlow = repository.changesSignalFlow().withInitial(Unit).map { repository.currentRevision }.distinctUntilChanged()
    combine(changesFlow, currentRevisionFlow) { changes, currentRev ->
      if (changes == null || currentRev == null) null
      else checkSyncState(changes, currentRev)
    }.stateIn(cs, SharingStarted.Lazily, null)
  }

  private suspend fun checkSyncState(changes: GitBranchComparisonResult, currentRev: String): GitBranchSyncStatus {
    if (currentRev == changes.headSha) return GitBranchSyncStatus.SYNCED
    if (changes.commits.mapTo(mutableSetOf()) { it.sha }.contains(currentRev)) return GitBranchSyncStatus(true, false)
    if (testCurrentBranchContains(changes.headSha)) return GitBranchSyncStatus(false, true)
    return GitBranchSyncStatus(true, true)
  }

  private suspend fun testCurrentBranchContains(sha: String): Boolean =
    coroutineToIndicator {
      val h = GitLineHandler(projectMapping.gitRepository.project, projectMapping.remote.repository.root, GitCommand.MERGE_BASE)
      h.setSilent(true)
      h.addParameters("--is-ancestor", sha, "HEAD")
      Git.getInstance().runCommand(h).success()
    }

  init {
    cs.launchNow {
      mergeRequest.changes.collectLatest { changes ->
        _actualChangesState.value = ChangesState.NotLoaded
        changesRequest.distinctUntilChanged().collectLatest {
          try {
            _actualChangesState.value = ChangesState.Loading
            val parsed = changes.getParsedChanges()
            _actualChangesState.value = ChangesState.Loaded(parsed)
          }
          catch (ce: CancellationException) {
            _actualChangesState.value = ChangesState.NotLoaded
          }
          catch (e: Exception) {
            _actualChangesState.value = ChangesState.Error
          }
        }
      }
    }
    if (!preferences.editorReviewEnabled) {
      setDiscussionsViewOption(DiscussionsViewOption.DONT_SHOW)
    }
  }

  //TODO: do not recreate all VMs on changes change
  private fun CoroutineScope.createFilesVms(parsedChanges: GitBranchComparisonResult)
    : Map<FilePath, Flow<GitLabMergeRequestEditorReviewFileViewModel?>> {
    val vmsCs = this
    val changes = parsedChanges.changes
    val result = mutableMapOf<FilePath, Flow<GitLabMergeRequestEditorReviewFileViewModel?>>()
    for (change in changes) {
      val file = change.filePathAfter ?: continue
      val changeSelection = ChangesSelection.Precise(changes, change)
      val diffData = parsedChanges.patchesByChange[change]!!

      val vmFlow = channelFlow<GitLabMergeRequestEditorReviewFileViewModel> {
        val vm = GitLabMergeRequestEditorReviewFileViewModelImpl(this, project, mergeRequest, change, diffData, discussions,
                                                                 discussionsViewOption, avatarIconsProvider)
        launchNow {
          vm.showDiffRequests.collect {
            val selection = if (it != null) changeSelection.withLocation(DiffLineLocation(Side.RIGHT, it)) else changeSelection
            diffBridge.setChanges(selection)
            withContext(Dispatchers.Main) {
              projectVm.filesController.openDiff(mergeRequestIid, true)
            }
          }
        }
        send(vm)
      }.shareIn(vmsCs, SharingStarted.WhileSubscribed(0, 0), 1)
      result[file] = vmFlow
    }
    return result
  }

  /**
   * Show merge request details in a standard view
   */
  fun showMergeRequest(place: GitLabStatistics.ToolWindowOpenTabActionPlace) {
    projectVm.showTab(GitLabReviewTab.ReviewSelected(mergeRequestIid), place)
    projectVm.twVm.activate()
  }

  fun updateBranch() {
    cs.launch {
      val details = mergeRequest.refreshDataNow()
      GitLabMergeRequestBranchUtil.fetchAndCheckoutBranch(projectMapping, details)
    }
  }

  fun toggleReviewMode() {
    val currentOption = discussionsViewOption.value
    val newOption = if (currentOption != DiscussionsViewOption.DONT_SHOW) {
      DiscussionsViewOption.DONT_SHOW
    }
    else {
      DiscussionsViewOption.UNRESOLVED_ONLY
    }
    setDiscussionsViewOption(newOption)
  }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    super.setDiscussionsViewOption(viewOption)
    preferences.editorReviewEnabled = viewOption != DiscussionsViewOption.DONT_SHOW
  }

  /**
   * A view model for [virtualFile] review
   */
  fun getFileVm(virtualFile: VirtualFile): Flow<GitLabMergeRequestEditorReviewFileViewModel?> {
    if (!virtualFile.isValid || virtualFile.isDirectory ||
        !VfsUtilCore.isAncestor(projectMapping.remote.repository.root, virtualFile, true)) {
      return flowOf(null)
    }
    val filePath = VcsContextFactory.getInstance().createFilePathOn(virtualFile)
    return filesVmsState.flatMapLatest { it[filePath] ?: flowOf(null) }
  }

  sealed interface ChangesState {
    data object NotLoaded : ChangesState
    data object Loading : ChangesState
    data object Error : ChangesState
    class Loaded(val changes: GitBranchComparisonResult) : ChangesState
  }

  companion object {
    val KEY: Key<GitLabMergeRequestEditorReviewViewModel> = Key.create("GitLab.MergeRequest.Review.ViewModel")
  }
}