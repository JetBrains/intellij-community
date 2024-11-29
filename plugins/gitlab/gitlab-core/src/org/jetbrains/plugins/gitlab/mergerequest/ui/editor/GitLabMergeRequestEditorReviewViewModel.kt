// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapNullableScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInEditorViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.selectedItem
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.Side
import com.intellij.openapi.ListSelection
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import git4idea.branch.GitBranchSyncStatus
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.remote.hosting.localCommitsSyncStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestBranchUtil
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.util.*

private val LOG = logger<GitLabMergeRequestEditorReviewViewModel>()

internal class GitLabMergeRequestEditorReviewViewModel internal constructor(
  parentCs: CoroutineScope,
  private val project: Project,
  private val projectMapping: GitLabProjectMapping,
  currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val projectVm: GitLabToolWindowProjectViewModel,
  private val discussions: GitLabMergeRequestDiscussionsViewModels,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
) : GitLabMergeRequestReviewViewModelBase(
  parentCs.childScope("GitLab Merge Request Editor Review VM"),
  currentUser, mergeRequest
), CodeReviewInEditorViewModel {
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  val mergeRequestIid: String = mergeRequest.iid

  private val _actualChangesState = MutableStateFlow<ChangesState>(ChangesState.NotLoaded)
  private val changesRequest = MutableSharedFlow<Unit>(replay = 1)

  val actualChangesState: StateFlow<ChangesState> = _actualChangesState.asStateFlow()

  private val filesVms: MutableMap<FilePath, Flow<GitLabMergeRequestEditorReviewFileViewModel?>> = mutableMapOf()
  private val diffRequestsMulticaster = EventDispatcher.create(DiffRequestListener::class.java)

  @OptIn(ExperimentalCoroutinesApi::class)
  val localRepositorySyncStatus: StateFlow<ComputedResult<GitBranchSyncStatus?>?> by lazy {
    val repository = projectMapping.remote.repository
    _actualChangesState.map {
      (it as? ChangesState.Loaded)?.changes?.commits?.map { it.sha }
    }.distinctUntilChanged().transformLatest {
      if (it == null) emit(null)
      else flowOf(it).localCommitsSyncStatus(repository).collect(this)
    }.stateIn(cs, SharingStarted.Eagerly, null)
  }

  override val updateRequired: StateFlow<Boolean> = localRepositorySyncStatus.mapState {
    it?.getOrNull()?.incoming == true
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

  /**
   * Show merge request details in a standard view
   */
  fun showMergeRequest(place: GitLabStatistics.ToolWindowOpenTabActionPlace) {
    projectVm.showTab(GitLabReviewTab.ReviewSelected(mergeRequestIid), place)
    projectVm.twVm.activate()
  }

  override fun updateBranch() {
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
    changesRequest.tryEmit(Unit)
    //TODO: do not recreate VMs on changes change
    return filesVms.getOrPut(filePath) {
      actualChangesState.mapNotNull {
        (it as? ChangesState.Loaded)?.changes
      }.distinctUntilChangedBy {
        it.baseSha + it.headSha + it.mergeBaseSha
      }.transform { parsedChanges ->
        val change = parsedChanges.changes.find { it.filePathAfter == filePath }
        if (change == null) {
          emit(null)
          return@transform
        }
        val diffData = parsedChanges.patchesByChange[change] ?: run {
          LOG.info("Diff data not found for change $change")
          emit(null)
          return@transform
        }
        val changeSelection = ListSelection.create(parsedChanges.changes, change)
        emit(changeSelection to diffData)
      }.mapNullableScoped { (change, diffData) ->
        createChangeVm(change, diffData)
      }
    }
  }

  suspend fun handleDiffRequests(listener: (ListSelection<RefComparisonChange>, DiffLineLocation?) -> Unit): Nothing {
    val actualListener = DiffRequestListener { list, location -> listener(list, location) }
    try {
      diffRequestsMulticaster.addListener(actualListener)
      awaitCancellation()
    }
    finally {
      diffRequestsMulticaster.removeListener(actualListener)
    }
  }

  private fun CoroutineScope.createChangeVm(change: ListSelection<RefComparisonChange>, diffData: GitTextFilePatchWithHistory) =
    GitLabMergeRequestEditorReviewFileViewModelImpl(this, project, mergeRequest, change.selectedItem!!, diffData,
                                                    discussions,
                                                    discussionsViewOption, avatarIconsProvider).also { vm ->
      launchNow {
        vm.showDiffRequests.collect { line ->
          diffRequestsMulticaster.multicaster.onChangesSelectionChanged(change, line?.let { DiffLineLocation(Side.RIGHT, it) })
          withContext(Dispatchers.Main) {
            projectVm.filesController.openDiff(mergeRequestIid, true)
          }
        }
      }
    }

  sealed interface ChangesState {
    data object NotLoaded : ChangesState
    data object Loading : ChangesState
    data object Error : ChangesState
    class Loaded(val changes: GitBranchComparisonResult) : ChangesState
  }
}

private fun interface DiffRequestListener : EventListener {
  fun onChangesSelectionChanged(changes: ListSelection<RefComparisonChange>, location: DiffLineLocation?)
}