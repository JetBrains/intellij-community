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
import git4idea.changes.GitTextFilePatchWithHistory
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
import java.time.Instant.EPOCH
import java.util.*

internal interface GHPRThreadsViewModels {
  val canComment: Boolean

  val compactThreads: StateFlow<Collection<GHPRCompactReviewThreadViewModel>>
  val newComments: StateFlow<Map<GHPRReviewCommentPosition, GHPRReviewNewCommentEditorViewModel>>

  val threadMappingData: StateFlow<Map<String, ThreadMappingData>>

  fun lookupNextComment(cursorLocation: GHPRReviewUnifiedPosition, isVisible: (String) -> Boolean): String?
  fun lookupNextComment(currentThreadId: String, isVisible: (String) -> Boolean): String?
  fun lookupPreviousComment(cursorLocation: GHPRReviewUnifiedPosition, isVisible: (String) -> Boolean): String?
  fun lookupPreviousComment(currentThreadId: String, isVisible: (String) -> Boolean): String?

  fun requestNewComment(location: GHPRReviewCommentPosition): GHPRReviewNewCommentEditorViewModel
  fun cancelNewComment(location: GHPRReviewCommentPosition)

  data class ThreadIdAndPosition(
    val id: String?,
    val createdAt: Date,
    val positionInDiff: GHPRReviewUnifiedPosition,
  )

  data class ThreadMappingData(
    val threadData: GHPullRequestReviewThread,
    val change: RefComparisonChange?,
    val diffData: GitTextFilePatchWithHistory?,
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

  private val threadOrder: StateFlow<ComputedResult<TreeSet<ThreadIdAndPosition>>?> =
    combine(changesFetchFlow, dataProvider.reviewData.threadsComputationFlow) { allChangesResult, allThreadsResult ->
      val allChanges = allChangesResult.getOrNull() ?: return@combine ComputedResult.loading()
      val allThreads = allThreadsResult.getOrNull() ?: return@combine ComputedResult.loading()

      ComputedResult.compute {
        createSortedThreadPositionsAndIds(allChanges, allThreads)
      }
    }.stateInNow(cs, ComputedResult.loading())

  private val threadPositionsById: StateFlow<ComputedResult<Map<String, ThreadIdAndPosition>>?> =
    threadOrder.mapState { result ->
      result?.map { order ->
        order
          .mapNotNull { pos -> pos.id?.let { it to pos } }
          .toMap()
      }
    }

  private val unorderedCompactThreads: StateFlow<Collection<GHPRCompactReviewThreadViewModel>> =
    dataProvider.reviewData.threadsComputationFlow
      .transformConsecutiveSuccesses(false) {
        mapDataToModel(GHPullRequestReviewThread::id,
                       { createThread(it) },
                       { update(it) })
      }.map { it.getOrNull().orEmpty() }
      .stateIn(cs, SharingStarted.Lazily, emptyList())
  override val compactThreads: StateFlow<Collection<GHPRCompactReviewThreadViewModel>> =
    unorderedCompactThreads.combineState(threadOrder) { threads, threadOrder -> threads to threadOrder }
      .combineState(threadPositionsById) { (threads, threadOrder), threadPositionsById ->
        val threadOrder = threadOrder?.getOrNull() ?: return@combineState threads
        val threadPositionsById = threadPositionsById?.getOrNull() ?: return@combineState threads

        threads.sortedBy {
          val position = threadPositionsById[it.id] ?: return@sortedBy Int.MAX_VALUE
          val index = threadOrder.indexOf(position)
          if (index == -1) Int.MAX_VALUE else index
        }
      }

  private fun CoroutineScope.createThread(initialData: GHPullRequestReviewThread) =
    UpdateableGHPRCompactReviewThreadViewModel(project, this, dataContext, dataProvider, initialData)

  override val threadMappingData: StateFlow<Map<String, GHPRThreadsViewModels.ThreadMappingData>> =
    dataProvider.reviewData.threadsComputationFlow
      .transformConsecutiveSuccesses(false) {
        combine(this, changesFetchFlow) { threads, allChangesOrNull ->
          val allChanges = allChangesOrNull.getOrNull() ?: return@combine null

          threads.associateBy(GHPullRequestReviewThread::id) { threadData ->
            val commitOid = threadData.commit?.oid
                            ?: return@associateBy GHPRThreadsViewModels.ThreadMappingData(threadData, null, null)
            val change = allChanges.findCumulativeChange(commitOid, threadData.path)
                         ?: return@associateBy GHPRThreadsViewModels.ThreadMappingData(threadData, null, null)
            val diffData = allChanges.patchesByChange[change]
                           ?: return@associateBy GHPRThreadsViewModels.ThreadMappingData(threadData, change, null)

            GHPRThreadsViewModels.ThreadMappingData(threadData, change, diffData)
          }
        }
      }.map { it.getOrNull().orEmpty() }.stateInNow(cs, emptyMap())

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

  override fun lookupNextComment(cursorLocation: GHPRReviewUnifiedPosition, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(cursorLocation, isNext = true, isVisible)

  override fun lookupNextComment(currentThreadId: String, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(currentThreadId, isNext = true, isVisible)

  override fun lookupPreviousComment(cursorLocation: GHPRReviewUnifiedPosition, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(cursorLocation, isNext = false, isVisible)

  override fun lookupPreviousComment(currentThreadId: String, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(currentThreadId, isNext = false, isVisible)

  private fun lookupAdjacentComment(cursorLocation: GHPRReviewUnifiedPosition, isNext: Boolean, isVisible: (String) -> Boolean): String? {
    // Fetch stuff
    val threads = threadOrder.value?.getOrNull() ?: return null
    if (threads.isEmpty()) return null

    // Search from before the first comment on the selected line
    var location: ThreadIdAndPosition? = ThreadIdAndPosition(null, Date.from(EPOCH), cursorLocation)

    // Find the next or previous comment
    location = when (isNext) {
      true -> threads.ceiling(location)
      false -> threads.floor(location)
    }

    return if (location?.id == null || isVisible(location.id)) {
      return location?.id
    }
    else {
      lookupAdjacentComment(location.id, isNext, isVisible)
    }
  }

  private fun lookupAdjacentComment(currentThreadId: String, isNext: Boolean, isVisible: (String) -> Boolean): String? {
    // Fetch stuff
    val threads = threadOrder.value?.getOrNull() ?: return null
    if (threads.isEmpty()) return null

    // Find the current position
    val threadPositionsById = threadPositionsById.value?.getOrNull() ?: return null
    if (threadPositionsById.isEmpty()) return null

    var location: ThreadIdAndPosition? = threadPositionsById[currentThreadId] ?: return null

    // Find the next or previous comment
    do {
      location = when (isNext) {
        true -> threads.higher(location)
        false -> threads.lower(location)
      }
    }
    while (location?.id != null && !isVisible(location.id))

    return location?.id
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
