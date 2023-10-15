// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestEditorReviewViewModel internal constructor(
  parentCs: CoroutineScope,
  private val projectMapping: GitLabProjectMapping,
  mergeRequest: GitLabMergeRequest,
  private val projectVm: GitLabToolWindowProjectViewModel,
  private val globalReviewVm: GitLabMergeRequestReviewViewModelBase,
  private val discussions: GitLabMergeRequestDiscussionsViewModels,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestReviewViewModel by globalReviewVm {

  private val cs = parentCs.childScope(CoroutineName("GitLab Merge Request Editor Review VM"))

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

  init {
    cs.launchNow {
      mergeRequest.changes.collectLatest { changes ->
        changes.localRepositorySynced.collectLatest {
          if(it) {
            _actualChangesState.value = ChangesState.NotLoaded
            changesRequest.distinctUntilChanged().collectLatest {
              try {
                val parsed = changes.getParsedChanges()
                _actualChangesState.value = ChangesState.Loaded(parsed)
              }
              catch (ce: CancellationException) {
                throw ce
              }
              catch (e: Exception) {
                _actualChangesState.value = ChangesState.Error
              }
            }
          } else {
            _actualChangesState.value = ChangesState.OutOfSync
          }
        }
      }
    }
  }

  //TODO: do not recreate all VMs on changes change
  private fun CoroutineScope.createFilesVms(parsedChanges: GitBranchComparisonResult)
    : Map<FilePath, Flow<GitLabMergeRequestEditorReviewFileViewModel?>> {
    val vmsCs = this
    val changes = parsedChanges.changes
    val result = mutableMapOf<FilePath, Flow<GitLabMergeRequestEditorReviewFileViewModel?>>()
    for (change in changes) {
      val file = change.afterRevision?.file ?: continue
      val diffData = parsedChanges.patchesByChange[change]!!

      val vmFlow = channelFlow<GitLabMergeRequestEditorReviewFileViewModel> {
        val vm = GitLabMergeRequestEditorReviewFileViewModelImpl(diffData, discussions, discussionsViewOption, avatarIconsProvider)
        send(vm)
      }.shareIn(vmsCs, SharingStarted.WhileSubscribed(0, 0), 1)
      result[file] = vmFlow
    }
    return result
  }

  private val _isReviewModeEnabled = MutableStateFlow(true)
  val isReviewModeEnabled: StateFlow<Boolean> = _isReviewModeEnabled.asStateFlow()

  /**
   * Show merge request details in a standard view
   */
  fun showMergeRequest() {
    projectVm.showTab(GitLabReviewTab.ReviewSelected(mergeRequestIid))
    projectVm.twVm.activate()
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

  fun toggleReviewMode() {
    _isReviewModeEnabled.update { !it }
  }

  sealed interface ChangesState {
    object NotLoaded : ChangesState
    object Loading: ChangesState
    object Error: ChangesState
    object OutOfSync: ChangesState
    class Loaded(val changes: GitBranchComparisonResult): ChangesState
  }

  companion object {
    val KEY: Key<GitLabMergeRequestEditorReviewViewModel> = Key.create("GitLab.MergeRequest.Review.ViewModel")
  }
}