// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffProcessorViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.PreLoadingCodeReviewAsyncDiffViewModelDelegate
import com.intellij.collaboration.ui.codereview.diff.model.RefComparisonChangesSorter
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.ListSelection
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.data.loadRevisionsAndParseChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.createDiffDataFlow
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

private typealias DiscussionsFlow = StateFlow<ComputedResult<Collection<GitLabMergeRequestDiffDiscussionViewModel>>>
private typealias DraftDiscussionsFlow = StateFlow<ComputedResult<Collection<GitLabMergeRequestDiffDraftNoteViewModel>>>
private typealias NewDiscussionsFlow = StateFlow<Collection<GitLabMergeRequestDiffNewDiscussionViewModel>>

/**
 * A viewmodel for the merge request diff window capable of showing different file diffs
 */
@ApiStatus.Internal
interface GitLabMergeRequestDiffViewModel : GitLabMergeRequestReviewViewModel, CodeReviewDiffProcessorViewModel<GitLabMergeRequestDiffChangeViewModel> {
  val discussions: DiscussionsFlow
  val draftDiscussions: DraftDiscussionsFlow

  fun getViewModelFor(change: RefComparisonChange): Flow<GitLabMergeRequestDiffReviewViewModel?>

  fun showDiffAtComment(commentId: String)

  fun findNextComment(currentThreadId: String, additionalIsVisible: (String) -> Boolean): String?
  fun findNextComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String?
  fun findPreviousComment(currentThreadId: String, additionalIsVisible: (String) -> Boolean): String?
  fun findPreviousComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String?

  companion object {
    val KEY: Key<GitLabMergeRequestDiffViewModel> = Key.create("GitLab.MergeRequest.Diff.ViewModel")
  }
}

private val LOG = logger<GitLabMergeRequestDiffProcessorViewModelImpl>()

internal class GitLabMergeRequestDiffProcessorViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val discussionsContainer: GitLabMergeRequestDiscussionsViewModels,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val imageLoader: GitLabImageLoader,
) : GitLabMergeRequestDiffViewModel, GitLabMergeRequestReviewViewModelBase(
  parentCs.childScope("GitLab Merge Request Diff Review VM"),
  currentUser, mergeRequest,
  project.service<GitLabMergeRequestsPreferences>().diffReviewViewOption
) {
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  private val changesFetchFlow = computationStateFlow(
    mergeRequest.changes,
    GitLabMergeRequestChanges::loadRevisionsAndParseChanges
  )

  private val delegate: PreLoadingCodeReviewAsyncDiffViewModelDelegate<RefComparisonChange, GitLabMergeRequestDiffChangeViewModel> = run {
    val changesSorter = project.service<GitLabMergeRequestsPreferences>().changesGroupingState
      .map {
        { changes: List<RefComparisonChange> ->
          RefComparisonChangesSorter.Grouping(project, it).sort(changes)
        }
      }

    PreLoadingCodeReviewAsyncDiffViewModelDelegate.create(changesFetchFlow, changesSorter) { allChanges, change ->
      GitLabMergeRequestDiffChangeViewModelImpl(this, project, allChanges, change)
    }
  }

  override val changes: StateFlow<ComputedResult<CodeReviewDiffProcessorViewModel.State<GitLabMergeRequestDiffChangeViewModel>>?> =
    delegate.changes.stateIn(cs, SharingStarted.Lazily, null)

  private val selectedChanges: StateFlow<Map<RefComparisonChange, GitTextFilePatchWithHistory>> =
    changes.mapState { changeVmsResult ->
      val changeVms = changeVmsResult?.getOrNull() ?: return@mapState emptyMap()

      changeVms.selectedChanges.list.mapNotNull { changeVm ->
        val change = changeVm.change
        val diffData = changeVm.diffData ?: return@mapNotNull null

        change to diffData
      }.toMap()
    }

  override val discussions: DiscussionsFlow =
    discussionsContainer.discussions.transformConsecutiveSuccesses {
      map { list ->
        list.map { vm ->
          val diffDataFlow = createDiffDataFlow(vm.position, selectedChanges)
          GitLabMergeRequestDiffDiscussionViewModel(vm, diffDataFlow, discussionsViewOption)
        }
      }
    }.stateInNow(cs, ComputedResult.loading())
  override val draftDiscussions: DraftDiscussionsFlow =
    discussionsContainer.draftNotes.transformConsecutiveSuccesses {
      map { list ->
        list.map { vm ->
          val diffDataFlow = createDiffDataFlow(vm.position, selectedChanges)
          GitLabMergeRequestDiffDraftNoteViewModel(vm, diffDataFlow, discussionsViewOption)
        }
      }
    }.stateInNow(cs, ComputedResult.loading())

  private val noteByTrackingId: StateFlow<Map<String, DiffDataMappedGitLabMergeRequestDiffInlayViewModel>> =
    combine(discussions, draftDiscussions) { discussionsResult, draftNotesResult ->
      val discussions = discussionsResult.getOrNull() ?: emptyList()
      val draftNotes = draftNotesResult.getOrNull() ?: emptyList()

      (discussions + draftNotes).associateBy { it.trackingId }
    }.stateInNow(cs, emptyMap())

  override fun showChange(change: GitLabMergeRequestDiffChangeViewModel, scrollRequest: DiffViewerScrollRequest?) =
    delegate.showChange(change.change, scrollRequest)

  override fun showChange(changeIdx: Int, scrollRequest: DiffViewerScrollRequest?) {
    val changeVm = changes.value?.result?.getOrNull()?.selectedChanges?.list?.getOrNull(changeIdx) ?: return
    showChange(changeVm, scrollRequest)
  }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    super.setDiscussionsViewOption(viewOption)
    preferences.diffReviewViewOption = viewOption
  }

  fun showChanges(changes: ListSelection<RefComparisonChange>, scrollLocation: DiffLineLocation? = null) =
    delegate.showChanges(changes, scrollLocation?.let(DiffViewerScrollRequest::toLine))

  override fun showDiffAtComment(commentId: String) {
    val mappedVm = noteByTrackingId.value[commentId] ?: return
    val location = mappedVm.location.value

    val change = mappedVm.diffData.value?.change ?: return

    delegate.showChange(change, location?.let(DiffViewerScrollRequest::toLine))
    mappedVm.requestFocus()
  }

  suspend fun handleSelection(listener: (ListSelection<RefComparisonChange>?) -> Unit): Nothing = delegate.handleSelection(listener)

  private val changeVmsMap = mutableMapOf<RefComparisonChange, StateFlow<GitLabMergeRequestDiffReviewViewModelImpl?>>()

  override fun getViewModelFor(change: RefComparisonChange): Flow<GitLabMergeRequestDiffReviewViewModel?> =
    changeVmsMap.getOrPut(change) {
      changesFetchFlow
        .mapNotNull { it.getOrNull() }
        .mapScoped { changes ->
          val patchWithHistory = changes.patchesByChange[change]
          if (patchWithHistory == null) {
            LOG.warn("Could not find patch for change $change")
            return@mapScoped null
          }
          if (patchWithHistory.patch.hunks.isEmpty()) {
            LOG.warn("Empty patch for change $change")
            return@mapScoped null
          }
          createChangeVm(change, patchWithHistory)
        }.stateIn(cs, SharingStarted.WhileSubscribed(5.minutes, ZERO), null)
    }

  private fun CoroutineScope.createChangeVm(
    change: RefComparisonChange,
    diffData: GitTextFilePatchWithHistory,
  ) =
    GitLabMergeRequestDiffReviewViewModelImpl(
      project, this,
      mergeRequest, diffData, change,
      this@GitLabMergeRequestDiffProcessorViewModelImpl,
      discussionsContainer, discussionsViewOption, avatarIconsProvider,
      imageLoader
    )

  override fun findNextComment(currentThreadId: String, additionalIsVisible: (String) -> Boolean): String? =
    discussionsContainer.lookupNextComment(currentThreadId) { isVisible(it) && additionalIsVisible(it) }

  override fun findNextComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String? =
    discussionsContainer.lookupNextComment(cursorLocation) { isVisible(it) && additionalIsVisible(it) }

  override fun findPreviousComment(currentThreadId: String, additionalIsVisible: (String) -> Boolean): String? =
    discussionsContainer.lookupPreviousComment(currentThreadId) { isVisible(it) && additionalIsVisible(it) }

  override fun findPreviousComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String? =
    discussionsContainer.lookupPreviousComment(cursorLocation) { isVisible(it) && additionalIsVisible(it) }

  private fun isVisible(noteTrackingId: String): Boolean {
    val note = noteByTrackingId.value[noteTrackingId] ?: return false
    return note.isVisible.value && note.location.value != null
  }
}
