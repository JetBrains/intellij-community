// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.Side
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.findCumulativeChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.ui.comment.*
import org.jetbrains.plugins.gitlab.ui.GitLabMarkdownToHtmlConverter
import java.time.Instant.EPOCH
import java.util.*

private typealias DiscussionsFlow = StateFlow<ComputedResult<Collection<GitLabMergeRequestDiscussionViewModel>>>
private typealias DraftNotesFlow = StateFlow<ComputedResult<Collection<GitLabMergeRequestStandaloneDraftNoteViewModelBase>>>
private typealias NewDiscussionsFlow = Flow<Map<GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition, NewGitLabNoteViewModel>>

/**
 * Represents the discussions and notes in a merge request at a conceptual level.
 * The discussions and notes in this VM are explicitly NOT mapped onto an exact line
 * number yet. For doing this, we need to know where the discussion is to be displayed
 * and within what context.
 *
 * For instance, if the discussion is shown in-editor, we need to take local changes
 * into account as well as all commits between when the comment was placed and the current *local* HEAD.
 */
interface GitLabMergeRequestDiscussionsViewModels {
  val discussions: DiscussionsFlow
  val draftNotes: DraftNotesFlow
  val newDiscussions: NewDiscussionsFlow

  fun lookupNextComment(cursorLocation: UnifiedCodeReviewItemPosition, isVisible: (String) -> Boolean): String?
  fun lookupNextComment(currentThreadId: String, isVisible: (String) -> Boolean): String?
  fun lookupPreviousComment(cursorLocation: UnifiedCodeReviewItemPosition, isVisible: (String) -> Boolean): String?
  fun lookupPreviousComment(currentThreadId: String, isVisible: (String) -> Boolean): String?

  fun requestNewDiscussion(position: NewDiscussionPosition, focus: Boolean)
  fun cancelNewDiscussion(position: NewDiscussionPosition)

  class NewDiscussionPosition(val position: GitLabMergeRequestNewDiscussionPosition, val side: Side) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as NewDiscussionPosition

      return position == other.position
    }

    override fun hashCode(): Int = position.hashCode()
  }
}

fun GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition.mapToLocation(diffData: GitTextFilePatchWithHistory): DiffLineLocation? =
  position.mapToLocation(diffData, side)

internal class GitLabMergeRequestDiscussionsViewModelsImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val projectData: GitLabProject,
  private val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  htmlConverter: GitLabMarkdownToHtmlConverter,
) : GitLabMergeRequestDiscussionsViewModels {
  private val cs = parentCs.childScope("GitLab Merge Request Review Discussions", Dispatchers.Default)

  override val discussions: DiscussionsFlow = mergeRequest.discussions
    .map { ComputedResult.fromResult(it) }
    .transformConsecutiveSuccesses {
      mapStatefulToStateful {
        GitLabMergeRequestDiscussionViewModelBase(project, this, projectData, currentUser, it, htmlConverter)
      }
    }
    .stateInNow(cs, ComputedResult.loading())

  override val draftNotes: DraftNotesFlow = mergeRequest.draftNotes
    .map { ComputedResult.fromResult(it) }
    .transformConsecutiveSuccesses {
      mapFiltered { it.discussionId == null }
        .mapStatefulToStateful {
          GitLabMergeRequestStandaloneDraftNoteViewModelBase(project, this, it, mergeRequest, projectData, htmlConverter)
        }
    }
    .stateInNow(cs, ComputedResult.loading())

  private val _newDiscussions =
    MutableStateFlow<Map<GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition, NewGitLabNoteViewModel>>(emptyMap())
  override val newDiscussions: NewDiscussionsFlow = _newDiscussions.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val allDiscussionsOrder = run {
    val discussionsData = discussions.transformConsecutiveSuccesses {
      mapStatefulToStateful { note ->
        IntermediateDiscussionData(note.id.toString(), note.createdAt, 2, note.position)
      }
    }

    val draftNotesData = draftNotes.transformConsecutiveSuccesses {
      mapStatefulToStateful { note ->
        IntermediateDiscussionData(note.id.toString(), note.createdAt ?: Date(), 1, note.position)
      }
    }

    val newDiscussionsData = _newDiscussions
      .mapState { it.entries }
      .mapStatefulToStateful { (position, note) ->
        IntermediateDiscussionData(note.trackingId, Date(), 0, MutableStateFlow(position.position))
      }

    val allDiscussions =
      combine(discussionsData, draftNotesData, newDiscussionsData) { discussionsDataResult, draftNotesDataResult, newDiscussionsData ->
        val discussionsData = discussionsDataResult.getOrNull() ?: emptyList()
        val draftNotesData = draftNotesDataResult.getOrNull() ?: emptyList()
        discussionsData + draftNotesData + newDiscussionsData
      }

    val allChanges = mergeRequest.changes
      .map { ComputedResult.fromResult(runCatchingUser { it.getParsedChanges() }) }
      .stateInNow(cs, ComputedResult.loading())

    combine(allDiscussions, allChanges) { discussions, changesResult ->
      val allChanges = changesResult.getOrNull() ?: return@combine null

      discussions to allChanges
    }.filterNotNull().flatMapLatest { (discussions, allChanges) ->
      createSortedDiscussionPositionsAndIds(allChanges, discussions)
    }.stateInNow(cs, null)
  }

  private val discussionPositionsById = allDiscussionsOrder.mapState { allDiscussions ->
    allDiscussions?.associate { it.id to it }
  }

  override fun requestNewDiscussion(position: GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition, focus: Boolean) {
    _newDiscussions.updateAndGet { currentNewDiscussions ->
      if (!currentNewDiscussions.containsKey(position) && mergeRequest.canAddNotes) {
        val vm = GitLabNoteEditingViewModel.forNewDiffNote(cs, project, projectData, mergeRequest, currentUser, position.position).apply {
          onDoneIn(cs) {
            cancelNewDiscussion(position)
          }
        }
        currentNewDiscussions + (position to vm)
      }
      else {
        currentNewDiscussions
      }
    }.apply {
      if (focus) {
        get(position)?.requestFocus()
      }
    }
  }

  override fun cancelNewDiscussion(position: GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition) {
    _newDiscussions.update {
      val oldVm = it[position]
      val newMap = it - position
      cs.launch {
        oldVm?.destroy()
      }
      newMap
    }
  }

  suspend fun destroy() = cs.cancelAndJoinSilently()

  override fun lookupNextComment(cursorLocation: UnifiedCodeReviewItemPosition, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(cursorLocation, isNext = true, isVisible)

  override fun lookupNextComment(currentThreadId: String, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(currentThreadId, isNext = true, isVisible)

  override fun lookupPreviousComment(cursorLocation: UnifiedCodeReviewItemPosition, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(cursorLocation, isNext = false, isVisible)

  override fun lookupPreviousComment(currentThreadId: String, isVisible: (String) -> Boolean): String? =
    lookupAdjacentComment(currentThreadId, isNext = false, isVisible)

  private fun lookupAdjacentComment(cursorLocation: UnifiedCodeReviewItemPosition, isNext: Boolean, isVisible: (String) -> Boolean): String? {
    // Fetch stuff
    val threads = allDiscussionsOrder.value ?: return null
    if (threads.isEmpty()) return null

    // Search from before the first comment on the selected line
    var location: DiscussionIdAndMappedPosition? = DiscussionIdAndMappedPosition(null, Date.from(EPOCH), cursorLocation)

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
    val threads = allDiscussionsOrder.value ?: return null
    if (threads.isEmpty()) return null

    // Find the current position
    val threadPositionsById = discussionPositionsById.value ?: return null
    if (threadPositionsById.isEmpty()) return null

    var location: DiscussionIdAndMappedPosition? = threadPositionsById[currentThreadId] ?: return null

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
    private fun createSortedDiscussionPositionsAndIds(
      allChanges: GitBranchComparisonResult,
      allDiscussions: List<IntermediateDiscussionData>,
    ): Flow<TreeSet<DiscussionIdAndMappedPosition>> {
      val changeIndices = allChanges.changes.mapIndexed { idx, change -> change to idx }.toMap()
      val comparator = positionComparator(changeIndices::get)

      return if (allDiscussions.isEmpty()) flowOf(TreeSet())
      else combine(allDiscussions.map { threadData ->
        threadData.position.mapState { position ->
          val commitOid = position?.sha ?: return@mapState null
          val change = allChanges.findCumulativeChange(commitOid, position.filePath) ?: return@mapState null
          val diffData = allChanges.patchesByChange[change] ?: return@mapState null

          DiscussionIdAndMappedPosition(
            threadData.id,
            threadData.createdAt,
            UnifiedCodeReviewItemPosition(
              change,
              position.mapToLeftSideLine(diffData) ?: -1,
              position.mapToRightSideLine(diffData) ?: -1
            ))
        }
      }) { locations ->
        TreeSet(Comparator<DiscussionIdAndMappedPosition> { left, right -> comparator.compare(left.mappedPosition, right.mappedPosition) }
                  .thenComparing { left, right -> left.createdAt.compareTo(right.createdAt) })
          .apply { addAll(locations.filterNotNull()) }
      }
    }

    private fun positionComparator(changeIndexLookup: (RefComparisonChange) -> Int?): Comparator<UnifiedCodeReviewItemPosition> =
      Comparator<UnifiedCodeReviewItemPosition> { left, right ->
        val leftChangeIdx = changeIndexLookup(left.change)
        val rightChangeIdx = changeIndexLookup(right.change)

        if (leftChangeIdx == rightChangeIdx) {
          return@Comparator 0
        }

        if (leftChangeIdx == null) return@Comparator -1 // nulls first
        if (rightChangeIdx == null) return@Comparator 1

        leftChangeIdx.compareTo(rightChangeIdx)
      }.thenComparingInt { it.rightLine }.thenComparingInt { it.leftLine }
  }
}

private data class IntermediateDiscussionData(
  val id: String,
  val createdAt: Date,
  val priority: Int,
  val position: StateFlow<GitLabNotePosition?>,
)

private data class DiscussionIdAndMappedPosition(
  val id: String?,
  val createdAt: Date,
  val mappedPosition: UnifiedCodeReviewItemPosition,
)