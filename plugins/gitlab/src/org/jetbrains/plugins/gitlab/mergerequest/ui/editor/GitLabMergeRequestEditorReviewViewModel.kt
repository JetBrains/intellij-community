// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.diff.util.Side
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestEditorReviewViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  private val projectMapping: GitLabProjectMapping,
  private val mergeRequest: GitLabMergeRequest,
  private val projectVm: GitLabToolWindowProjectViewModel,
  private val currentUser: GitLabUserDTO,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) {
  private val cs = parentCs.childScope()

  val mergeRequestIid: String = mergeRequest.iid

  val actualChangesState: StateFlow<ChangesState>
  private val changesRequest = MutableSharedFlow<Unit>(replay = 1)

  init {
    actualChangesState = MutableStateFlow<ChangesState>(ChangesState.NotLoaded)
    cs.launchNow {
      mergeRequest.changes.collectLatest { changes ->
        changes.localRepositorySynced.collectLatest {
          if(it) {
            changesRequest.collectLatest {
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
  fun getFileVm(virtualFile: VirtualFile): Flow<GitLabMergeRequestChangeViewModel?> {
    if (!VfsUtilCore.isAncestor(projectMapping.remote.repository.root, virtualFile, true)) {
      return flowOf(null)
    }

    startLoadingChanges()

    return actualChangesState.mapLatest { changesState ->
      val parsedChanges = (changesState as? ChangesState.Loaded)?.changes
      parsedChanges?.changes?.find { it.virtualFile == virtualFile }?.let {
        parsedChanges.patchesByChange[it]!!
      }
    }.mapScoped {
      it?.let { patch ->
        GitLabMergeRequestChangeViewModelImpl(project, this, currentUser, mergeRequest, patch, avatarIconsProvider,
                                              MutableStateFlow(DiscussionsViewOption.UNRESOLVED_ONLY), Side.RIGHT)
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