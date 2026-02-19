// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.combineStates
import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.mapNullableScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.mapStatefulToStateful
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffProcessorViewModel
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiscussionsViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.PreLoadingCodeReviewAsyncDiffViewModelDelegate
import com.intellij.collaboration.ui.codereview.diff.model.RefComparisonChangesSorter
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.pullrequest.isVisible
import org.jetbrains.plugins.github.api.data.pullrequest.mapToRange
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.threadsComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels
import org.jetbrains.plugins.github.pullrequest.ui.comment.lineLocation
import org.jetbrains.plugins.github.pullrequest.ui.review.DelegatingGHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

@ApiStatus.Internal
interface GHPRDiffViewModel : CodeReviewDiffProcessorViewModel<GHPRDiffChangeViewModel>, CodeReviewDiscussionsViewModel {
  val reviewVm: GHPRReviewViewModel
  val isLoadingReviewData: StateFlow<Boolean>

  val iconProvider: GHAvatarIconsProvider
  val currentUser: GHUser

  fun getViewModelFor(change: RefComparisonChange): Flow<GHPRDiffReviewViewModel?>

  fun reloadReview()

  /**
   * Tries to find the next comment, given that a comment is currently focused.
   *
   * @return The ID of the next comment to move to, or `null` if no next comment could be found.
   */
  fun nextComment(focused: String): String?

  /**
   * Tries to find the next comment, given that no comment is currently focused.
   *
   * @return The ID of the next comment to move to, or `null` if no next comment could be found.
   */
  fun nextComment(cursorLocation: UnifiedCodeReviewItemPosition): String?

  /**
   * Tries to find the previous comment, given that a comment is currently focused.
   *
   * @return The ID of the previous comment to move to, or `null` if no previous comment could be found.
   */
  fun previousComment(focused: String): String?

  /**
   * Tries to find the previous comment, given that no comment is currently focused.
   *
   * @return The ID of the previous comment to move to, or `null` if no previous comment could be found.
   */
  fun previousComment(cursorLocation: UnifiedCodeReviewItemPosition): String?

  fun showDiffFor(changes: ChangesSelection)
  fun showDiffAtComment(commentId: String)

  companion object {
    val KEY: Key<GHPRDiffViewModel> = Key.create("GitHub.PullRequest.Diff.ViewModel")
    val DATA_KEY: DataKey<GHPRDiffViewModel> = DataKey.create(KEY.toString())
  }
}

internal class GHPRDiffViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  reviewVmHelper: GHPRReviewViewModelHelper,
  private val threadsVm: GHPRThreadsViewModels,
) : GHPRDiffViewModel {
  private val cs = parentCs.childScope("GitHub Pull Request Diff View Model")
  private val reviewDataProvider = dataProvider.reviewData

  override val iconProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider
  override val currentUser: GHUser = dataContext.securityService.currentUser

  override val reviewVm = DelegatingGHPRReviewViewModel(reviewVmHelper)

  private val settings = GithubPullRequestsProjectUISettings.getInstance(project)

  private val changesFetchFlow = with(dataProvider.changesData) {
    computationStateFlow(changesNeedReloadSignal.withInitial(Unit)) {
      loadChanges().also {
        ensureAllRevisionsFetched()
      }
    }
  }

  private val delegate = run {
    val changesSorter = settings.changesGroupingState
      .mapState { groupings ->
        { changes: List<RefComparisonChange> -> RefComparisonChangesSorter.Grouping(project, groupings).sort(changes) }
      }

    PreLoadingCodeReviewAsyncDiffViewModelDelegate.create(changesFetchFlow, changesSorter) { allChanges, change ->
      GHPRDiffChangeViewModelImpl(project, this, allChanges, change) as GHPRDiffChangeViewModel
    }
  }

  override val changes: StateFlow<ComputedResult<CodeReviewDiffProcessorViewModel.State<GHPRDiffChangeViewModel>>?> =
    delegate.changes.stateIn(cs, SharingStarted.Eagerly, null)

  private val changeVmsMap = mutableMapOf<RefComparisonChange, StateFlow<GHPRDiffReviewViewModelImpl?>>()

  private val threads: StateFlow<ComputedResult<List<GHPullRequestReviewThread>>> =
    reviewDataProvider.threadsComputationFlow.stateInNow(cs, ComputedResult.loading())

  private val _discussionsViewOption: MutableStateFlow<DiscussionsViewOption> = MutableStateFlow(settings.editorReviewViewOption)
  override val discussionsViewOption: StateFlow<DiscussionsViewOption> = _discussionsViewOption.asStateFlow()

  override val isLoadingReviewData: StateFlow<Boolean> = reviewVm.pendingReview.combineState(threads) { reviewResult, threadsResult ->
    reviewResult.isInProgress || threadsResult.isInProgress
  }

  private val threadMappings: StateFlow<Map<String, GHPRReviewThreadDiffViewModel.MappingData>> =
    combineStates(threads, discussionsViewOption, changes) { threadDataResult, viewOption, changesResult ->
      val changeVms = changesResult?.getOrNull()?.selectedChanges?.list ?: return@combineStates emptyMap()
      val threadData = threadDataResult.getOrNull() ?: return@combineStates emptyMap()
      mapThreadsToChanges(threadData, viewOption, changeVms)
    }.stateInNow(cs, emptyMap())

  private val mappedThreads: StateFlow<List<MappedGHPRReviewThreadDiffViewModel>> =
    threadsVm.compactThreads.mapStatefulToStateful { sharedVm ->
      MappedGHPRReviewThreadDiffViewModel(this, sharedVm, threadMappings.mapNotNull { it[sharedVm.id] })
    }.stateInNow(cs, emptyList())

  override fun reloadReview() {
    cs.launch {
      reviewDataProvider.signalPendingReviewNeedsReload()
      reviewDataProvider.signalThreadsNeedReload()
    }
  }

  override fun getViewModelFor(change: RefComparisonChange): StateFlow<GHPRDiffReviewViewModelImpl?> =
    changeVmsMap.getOrPut(change) {
      changesFetchFlow
        .mapNotNull { it.getOrNull() }
        .map { it.patchesByChange[change] }
        .mapNullableScoped { createFileReviewVm(change, it) }
        .stateIn(cs, SharingStarted.Lazily, null)
    }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    _discussionsViewOption.value = viewOption
    settings.editorReviewViewOption = viewOption
  }

  private fun CoroutineScope.createFileReviewVm(change: RefComparisonChange, diffData: GitTextFilePatchWithHistory) =
    GHPRDiffReviewViewModelImpl(project, this, dataContext, dataProvider, change, diffData, threadsVm, mappedThreads)

  suspend fun handleSelection(listener: (ListSelection<RefComparisonChange>?) -> Unit): Nothing {
    delegate.handleSelection(listener)
  }

  override fun showDiffFor(changes: ChangesSelection) {
    val scrollLocation = if (changes is ChangesSelection.Precise) changes.location else null
    delegate.showChanges(ListSelection.createAt(changes.changes, changes.selectedIdx), scrollLocation?.let(DiffViewerScrollRequest::toLine))
  }

  private fun showChange(change: RefComparisonChange, scrollRequest: DiffViewerScrollRequest?) {
    delegate.showChange(change, scrollRequest)
  }

  override fun showChange(change: GHPRDiffChangeViewModel, scrollRequest: DiffViewerScrollRequest?) {
    showChange(change.change, scrollRequest)
  }

  override fun showChange(changeIdx: Int, scrollRequest: DiffViewerScrollRequest?) {
    val changeVm = changes.value?.result?.getOrNull()?.selectedChanges?.list?.getOrNull(changeIdx) ?: return
    showChange(changeVm.change, scrollRequest)
  }

  override fun nextComment(focused: String): String? =
    threadsVm.lookupNextComment(focused, this::threadIsVisible)

  override fun nextComment(cursorLocation: UnifiedCodeReviewItemPosition): String? =
    // TODO: Find a good way to map cursorLocations here (only broken for per-commit nav)
    threadsVm.lookupNextComment(cursorLocation, this::threadIsVisible)

  override fun previousComment(focused: String): String? =
    threadsVm.lookupPreviousComment(focused, this::threadIsVisible)

  override fun previousComment(cursorLocation: UnifiedCodeReviewItemPosition): String? =
    // TODO: Find a good way to map cursorLocations here (only broken for per-commit nav)
    threadsVm.lookupPreviousComment(cursorLocation, this::threadIsVisible)

  override fun showDiffAtComment(commentId: String) {
    val mapping = threadMappings.value[commentId] ?: return
    if (mapping.change == null) return
    showChange(mapping.change, mapping.location?.lineLocation?.let(DiffViewerScrollRequest::toLine))
    mappedThreads.value.find { it.id == commentId }?.requestFocus()
  }

  private fun threadIsVisible(threadId: String): Boolean =
    threadMappings.value[threadId]?.let { it.isVisible && it.location != null && it.change != null } == true
}

private fun mapThreadsToChanges(
  threadsData: List<GHPullRequestReviewThread>,
  viewOption: DiscussionsViewOption,
  changeVms: List<GHPRDiffChangeViewModel>,
): Map<String, GHPRReviewThreadDiffViewModel.MappingData> =
  threadsData.asSequence().mapNotNull {
    val changeVm = findChangeVmForThread(changeVms, it) ?: return@mapNotNull null
    it to changeVm
  }.associate { (threadData, changeVm) ->
    val mapping = mapThreadToChange(threadData, viewOption, changeVm)
    threadData.id to mapping
  }

private fun findChangeVmForThread(
  changeVms: List<GHPRDiffChangeViewModel>,
  threadData: GHPullRequestReviewThread,
): GHPRDiffChangeViewModel? {
  val filePath = threadData.path
  val commitSha = threadData.commit?.oid ?: return null
  return changeVms.find { it.diffData?.contains(commitSha, filePath) == true }
}

private fun mapThreadToChange(
  threadData: GHPullRequestReviewThread,
  viewOption: DiscussionsViewOption,
  changeVm: GHPRDiffChangeViewModel,
): GHPRReviewThreadDiffViewModel.MappingData {
  val change = changeVm.change
  val isVisible = threadData.isVisible(viewOption)
  val diffData = changeVm.diffData ?: return GHPRReviewThreadDiffViewModel.MappingData(isVisible, change, null)
  val sideToRange = threadData.mapToRange(diffData) ?: return GHPRReviewThreadDiffViewModel.MappingData(isVisible, change, null)
  val startLineLocation = sideToRange.first
  val endLineLocation = sideToRange.second
  val location = if (sideToRange.let { startLineLocation.second == endLineLocation.second }) {
    GHPRReviewCommentLocation.SingleLine(endLineLocation.first, startLineLocation.second)
  }
  else {
    GHPRReviewCommentLocation.MultiLine(startLineLocation.first, startLineLocation.second,
                                        endLineLocation.first, endLineLocation.second)
  }
  return GHPRReviewThreadDiffViewModel.MappingData(isVisible, change, location)
}
