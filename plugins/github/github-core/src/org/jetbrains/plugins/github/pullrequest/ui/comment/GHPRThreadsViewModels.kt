// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.*
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.collaboration.util.map
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.findCumulativeChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.pullrequest.mapToLeftSideLine
import org.jetbrains.plugins.github.api.data.pullrequest.mapToRightSideLine
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.threadsComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels.ThreadIdAndPosition
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModelImpl
import java.util.*

internal interface GHPRThreadsViewModels {
  val canComment: Boolean

  val compactThreads: StateFlow<Collection<GHPRCompactReviewThreadViewModel>>
  val newComments: StateFlow<Map<GHPRReviewCommentPosition, GHPRReviewNewCommentEditorViewModel>>

  // Ordered by the appearance in the review (file, then position inside file)
  val threadOrder: StateFlow<ComputedResult<TreeSet<ThreadIdAndPosition>>?>
  val threadPositionsById: StateFlow<ComputedResult<Map<String, ThreadIdAndPosition>>?>

  fun requestNewComment(location: GHPRReviewCommentPosition): GHPRReviewNewCommentEditorViewModel
  fun cancelNewComment(location: GHPRReviewCommentPosition)

  data class ThreadIdAndPosition(
    val id: String?,
    val createdAt: Date,
    val positionInDiff: GHPRReviewUnifiedPosition,
  )
}

internal class GHPRThreadsViewModelsImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
) : GHPRThreadsViewModels {
  private val cs = parentCs.childScope(javaClass.name)
  override val canComment: Boolean = dataProvider.reviewData.canComment()

  private val changesFetchFlow = with(dataProvider.changesData) {
    computationStateFlow(changesNeedReloadSignal.withInitial(Unit)) {
      loadChanges().also {
        ensureAllRevisionsFetched()
      }
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  override val compactThreads: StateFlow<Collection<GHPRCompactReviewThreadViewModel>> =
    dataProvider.reviewData.threadsComputationFlow
      .transformConsecutiveSuccesses(false) {
        mapDataToModel(GHPullRequestReviewThread::id,
                       { createThread(it) },
                       { update(it) })
      }.map { it.getOrNull().orEmpty() }
      .stateIn(cs, SharingStarted.Lazily, emptyList())

  private fun CoroutineScope.createThread(initialData: GHPullRequestReviewThread) =
    UpdateableGHPRCompactReviewThreadViewModel(project, this, dataContext, dataProvider, initialData)

  override val threadOrder: StateFlow<ComputedResult<TreeSet<ThreadIdAndPosition>>?> =
    combine(changesFetchFlow, dataProvider.reviewData.threadsComputationFlow) { allChangesResult, allThreadsResult ->
      val allChanges = allChangesResult.getOrNull() ?: return@combine ComputedResult.loading()
      val allThreads = allThreadsResult.getOrNull() ?: return@combine ComputedResult.loading()

      ComputedResult.compute {
        createSortedThreadPositionsAndIds(allChanges, allThreads)
      }
    }.stateInNow(cs, ComputedResult.loading())

  override val threadPositionsById: StateFlow<ComputedResult<Map<String, ThreadIdAndPosition>>?> =
    threadOrder.mapState { result ->
      result?.map { order ->
        order
          .mapNotNull { pos -> pos.id?.let { it to pos } }
          .toMap()
      }
    }

  private val _newComments = MutableStateFlow<Map<GHPRReviewCommentPosition, GHPRReviewNewCommentEditorViewModelImpl>>(emptyMap())
  override val newComments: StateFlow<Map<GHPRReviewCommentPosition, GHPRReviewNewCommentEditorViewModel>> = _newComments.asStateFlow()

  override fun requestNewComment(location: GHPRReviewCommentPosition): GHPRReviewNewCommentEditorViewModel =
    _newComments.updateAndGet { currentNewComments ->
      if (!currentNewComments.containsKey(location)) {
        val vm = createNewCommentVm(location)
        currentNewComments + (location to vm)
      }
      else {
        currentNewComments
      }
    }[location]!!

  override fun cancelNewComment(location: GHPRReviewCommentPosition) =
    _newComments.update {
      val oldVm = it[location]
      val newMap = it - location
      oldVm?.destroy()
      newMap
    }

  private fun createNewCommentVm(position: GHPRReviewCommentPosition) =
    GHPRReviewNewCommentEditorViewModelImpl(project, cs, dataProvider,
                                            dataContext.repositoryDataService.remoteCoordinates.repository,
                                            dataContext.securityService.currentUser,
                                            dataContext.avatarIconsProvider,
                                            position) {
      cancelNewComment(position)
    }

  companion object {
    private fun positionComparator(changeIndexLookup: (RefComparisonChange) -> Int?): Comparator<GHPRReviewUnifiedPosition> =
      Comparator<GHPRReviewUnifiedPosition> { left, right ->
        val leftChangeIdx = changeIndexLookup(left.change)
        val rightChangeIdx = changeIndexLookup(right.change)

        if (leftChangeIdx == rightChangeIdx) {
          return@Comparator 0
        }

        if (leftChangeIdx == null) return@Comparator -1 // nulls first
        if (rightChangeIdx == null) return@Comparator 1

        leftChangeIdx.compareTo(rightChangeIdx)
      }.thenComparingInt { it.rightLine }.thenComparingInt { it.leftLine }

    private fun createSortedThreadPositionsAndIds(
      allChanges: GitBranchComparisonResult,
      allThreads: List<GHPullRequestReviewThread>,
    ): TreeSet<ThreadIdAndPosition> {
      val changeIndices = allChanges.changes.mapIndexed { idx, change -> change to idx }.toMap()

      val locations = allThreads.mapNotNull { threadData ->
        val commitOid = threadData.commit?.oid ?: return@mapNotNull null
        val change = allChanges.findCumulativeChange(commitOid, threadData.path) ?: return@mapNotNull null
        val diffData = allChanges.patchesByChange[change] ?: return@mapNotNull null

        ThreadIdAndPosition(
          threadData.id,
          threadData.createdAt,
          GHPRReviewUnifiedPosition(
            change,
            threadData.mapToLeftSideLine(diffData) ?: -1,
            threadData.mapToRightSideLine(diffData) ?: -1
          ))
      }

      val comparator = positionComparator(changeIndices::get)
      return TreeSet(Comparator<ThreadIdAndPosition> { left, right -> comparator.compare(left.positionInDiff, right.positionInDiff) }
                       .thenComparing { left, right -> left.createdAt.compareTo(right.createdAt) })
        .apply { addAll(locations) }
    }
  }
}
