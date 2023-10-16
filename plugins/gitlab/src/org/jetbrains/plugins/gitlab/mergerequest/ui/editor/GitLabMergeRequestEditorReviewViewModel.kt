// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.util.Key
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

  val actualChangesState: StateFlow<ChangesState>
  private val changesRequest = MutableSharedFlow<Unit>(replay = 1)

  init {
    actualChangesState = MutableStateFlow<ChangesState>(ChangesState.NotLoaded)
    cs.launchNow {
      mergeRequest.changes.collectLatest { changes ->
        changes.localRepositorySynced.collectLatest {
          if(it) {
            actualChangesState.value = ChangesState.NotLoaded
            changesRequest.distinctUntilChanged().collectLatest {
              try {
                val parsed = changes.getParsedChanges()
                actualChangesState.value = ChangesState.Loaded(parsed)
              }
              catch (ce: CancellationException) {
                throw ce
              }
              catch (e: Exception) {
                actualChangesState.value = ChangesState.Error
              }
            }
          } else {
            actualChangesState.value = ChangesState.OutOfSync
          }
        }
      }
    }
  }

  private fun startLoadingChanges() {
    changesRequest.tryEmit(Unit)
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
    if (!VfsUtilCore.isAncestor(projectMapping.remote.repository.root, virtualFile, true)) {
      return flowOf(null)
    }

    startLoadingChanges()

    return actualChangesState.mapLatest { changesState ->
      val parsedChanges = (changesState as? ChangesState.Loaded)?.changes ?: return@mapLatest null
      val cumulativeChange = parsedChanges.changes.find { it.virtualFile == virtualFile } ?: return@mapLatest null
      parsedChanges.patchesByChange[cumulativeChange]?.let { diffData ->
        GitLabMergeRequestEditorReviewFileViewModelImpl(diffData, discussions, discussionsViewOption, avatarIconsProvider)
      }
    }
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