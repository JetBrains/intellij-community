// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.mapStatefulToStateful
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.ui.codereview.diff.viewer.showCodeReview
import com.intellij.collaboration.ui.codereview.editor.CodeReviewActiveRangesTracker
import com.intellij.collaboration.ui.codereview.editor.CodeReviewCommentableEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorInlayRangeOutlineUtils
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel.Ranged.Adjustable.AdjustmentDisabledReason
import com.intellij.collaboration.ui.codereview.editor.CodeReviewNavigableEditorViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.Hideable
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.collaboration.util.syncOrToggleAll
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.DiffNotifications
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabSettings
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNoteLocation
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffDraftNoteViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffNewDiscussionViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestDraftNoteInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorMappedComponentModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestNewDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

class GitLabMergeRequestDiffExtension : DiffExtension() {
  private val emptyDiffNotificationProvider by lazy {
    DiffNotifications.createNotificationProvider(GitLabBundle.message("merge.request.diff.empty.patch.warning"))
  }

  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return

    if (viewer !is DiffViewerBase) return

    val reviewVm = context.getUserData(GitLabMergeRequestDiffViewModel.KEY) ?: return
    val change = request.getUserData(RefComparisonChange.KEY) ?: return

    if (request.getUserData(GitLabMergeRequestDiffChangeViewModel.EMPTY_PATCH_KEY) == true) {
      DiffUtil.addNotificationIfAbsent(emptyDiffNotificationProvider, request)
    }

    project.service<InlaysController>().installInlays(reviewVm, change, viewer)
  }

  @Service(Service.Level.PROJECT)
  private class InlaysController(private val project: Project, private val cs: CoroutineScope) {
    fun installInlays(reviewVm: GitLabMergeRequestDiffViewModel, change: RefComparisonChange, viewer: DiffViewerBase) {
      val glSettings = GitLabSettings.getInstance()
      cs.launchNow {
        withContext(Dispatchers.Main) {
          reviewVm.getViewModelFor(change).collectScoped { changeVm ->
            if (changeVm == null) return@collectScoped
            GitLabStatistics.logMrDiffOpened(project, changeVm.isCumulativeChange)

            if (glSettings.isAutomaticallyMarkAsViewed) {
              changeVm.markViewed()
            }

            viewer.showCodeReview { editor, _, locationToLine, lineToLocations, lineToUnified ->
              coroutineScope {
                val cs = this
                val model = DiffEditorModel(cs, project, changeVm, locationToLine, lineToLocations) {
                  val (leftLine, rightLine) = lineToUnified(it)
                  UnifiedCodeReviewItemPosition(change, leftLine, rightLine)
                }
                val activeRangesTracker = CodeReviewActiveRangesTracker()
                editor.showCodeReview(model) { inlayModel ->
                  createRenderer(inlayModel,
                                 changeVm.avatarIconsProvider,
                                 changeVm.imageLoader,
                                 activeRangesTracker).also { inlayRenderer ->
                    launchNow {
                      CodeReviewEditorInlayRangeOutlineUtils.showInlayOutline(editor, model, inlayModel, inlayRenderer, activeRangesTracker)
                    }
                  }
                }
              }
            }
          }
        }
      }.cancelOnDispose(viewer)
    }

    private fun CoroutineScope.createRenderer(
      model: GitLabMergeRequestEditorMappedComponentModel,
      avatarIconsProvider: IconsProvider<GitLabUserDTO>,
      imageLoader: GitLabImageLoader,
      activeRangesTracker: CodeReviewActiveRangesTracker,
    ): CodeReviewComponentInlayRenderer =
      when (model) {
        is GitLabMergeRequestEditorMappedComponentModel.Discussion<*> ->
          GitLabMergeRequestDiscussionInlayRenderer(this, project, model, avatarIconsProvider, imageLoader, activeRangesTracker,
                                                    GitLabStatistics.MergeRequestNoteActionPlace.DIFF)
        is GitLabMergeRequestEditorMappedComponentModel.DraftNote<*> ->
          GitLabMergeRequestDraftNoteInlayRenderer(this, project, model, avatarIconsProvider, imageLoader, activeRangesTracker,
                                                   GitLabStatistics.MergeRequestNoteActionPlace.DIFF)
        is GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<*> ->
          GitLabMergeRequestNewDiscussionInlayRenderer(this, project, model, avatarIconsProvider, activeRangesTracker,
                                                       GitLabStatistics.MergeRequestNoteActionPlace.DIFF, model::cancel)
      }
  }
}

internal interface GitLabReviewDiffEditorModel : CodeReviewEditorModel<GitLabMergeRequestEditorMappedComponentModel>,
                                                 CodeReviewNavigableEditorViewModel,
                                                 CodeReviewCommentableEditorModel.WithMultilineComments

private class DiffEditorModel(
  cs: CoroutineScope,
  private val project: Project,
  private val diffReviewVm: GitLabMergeRequestDiffReviewViewModel,
  private val locationToLine: (DiffLineLocation) -> Int?,
  private val lineToLocation: (Int) -> DiffLineLocation?,
  @RequiresEdt private val lineToUnified: (Int) -> UnifiedCodeReviewItemPosition,
) : GitLabReviewDiffEditorModel {
  private val discussions = diffReviewVm.discussions
    .transformConsecutiveSuccesses { mapStatefulToStateful { MappedDiscussion(it) } }
    .stateInNow(cs, ComputedResult.loading())
  private val drafts = diffReviewVm.draftDiscussions
    .transformConsecutiveSuccesses { mapStatefulToStateful { MappedDraftNote(it) } }
    .stateInNow(cs, ComputedResult.loading())
  private val newDiscussions = diffReviewVm.newDiscussions
    .mapStatefulToStateful { MappedNewDiscussion(it) }
    .stateInNow(cs, emptyList())

  override val inlays: StateFlow<Collection<GitLabMergeRequestEditorMappedComponentModel>> = combine(discussions, drafts, newDiscussions) { discussionsResult, draftsResult, new ->
    (discussionsResult.getOrNull() ?: emptyList()) + (draftsResult.getOrNull() ?: emptyList()) + new
  }.stateInNow(cs, emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    combine(
      diffReviewVm.locationsWithDiscussions,
      diffReviewVm.locationsWithNewDiscussions
    ) { locationsWithDiscussions, locationsWithNewDiscussions ->
      val linesWithDiscussions = locationsWithDiscussions.mapNotNullTo(mutableSetOf(), { locationToLine(it.first to it.second) })
      val linesWithNewDiscussions = locationsWithNewDiscussions.mapNotNullTo(mutableSetOf(), { locationToLine(it.first to it.second) })
      GutterState(linesWithDiscussions, linesWithNewDiscussions, lineToLocation)
    }.stateInNow(cs, null)

  override fun requestNewComment(lineIdx: Int) {
    val loc = lineToLocation(lineIdx)?.let {
      GitLabNoteLocation(it.first, it.second, it.first, it.second)
    } ?: return
    diffReviewVm.requestNewDiscussion(loc, true)
  }

  override fun cancelNewComment(lineIdx: Int) {
    val loc = newDiscussions.value.find { it.line.value == lineIdx }?.vm?.location?.value ?: return
    diffReviewVm.cancelNewDiscussion(loc.side to loc.lineIdx)
  }

  override fun requestNewComment(lineRange: LineRange) {
    val loc1 = lineToLocation(lineRange.start) ?: return
    val loc2 = lineToLocation(lineRange.end) ?: return
    val loc = GitLabNoteLocation(loc1.first, loc1.second, loc2.first, loc2.second)
    diffReviewVm.requestNewDiscussion(loc, true)
  }

  override fun canCreateComment(lineRange: LineRange): Boolean {
    val gutterControls = gutterControlsState.value ?: return false
    return gutterControls.isLineCommentable(lineRange.start) &&
           gutterControls.isLineCommentable(lineRange.end)
  }

  override fun toggleComments(lineIdx: Int) {
    inlays.value.asSequence().filter { it.line.value == lineIdx }.filterIsInstance<Hideable>().syncOrToggleAll()
    GitLabStatistics.logToggledComments(project)
  }

  override val canNavigate: Boolean get() = diffReviewVm.isCumulativeChange

  override fun canGotoNextComment(threadId: String): Boolean =
    diffReviewVm.nextComment(threadId, ::additionalIsVisible) != null

  override fun canGotoNextComment(line: Int): Boolean =
    diffReviewVm.nextComment(lineToUnified(line), ::additionalIsVisible) != null

  override fun canGotoPreviousComment(threadId: String): Boolean =
    diffReviewVm.previousComment(threadId, ::additionalIsVisible) != null

  override fun canGotoPreviousComment(line: Int): Boolean =
    diffReviewVm.previousComment(lineToUnified(line), ::additionalIsVisible) != null

  override fun gotoNextComment(threadId: String) {
    val next = diffReviewVm.nextComment(threadId, ::additionalIsVisible) ?: return
    diffReviewVm.showDiffAtComment(next)
  }

  override fun gotoNextComment(line: Int) {
    val next = diffReviewVm.nextComment(lineToUnified(line), ::additionalIsVisible) ?: return
    diffReviewVm.showDiffAtComment(next)
  }

  override fun gotoPreviousComment(threadId: String) {
    val previous = diffReviewVm.previousComment(threadId, ::additionalIsVisible) ?: return
    diffReviewVm.showDiffAtComment(previous)
  }

  override fun gotoPreviousComment(line: Int) {
    val previous = diffReviewVm.previousComment(lineToUnified(line), ::additionalIsVisible) ?: return
    diffReviewVm.showDiffAtComment(previous)
  }

  private fun additionalIsVisible(noteTrackingId: String): Boolean {
    val inlay = inlays.value.find { it.vm.trackingId == noteTrackingId } ?: return true // it's not hidden
    return inlay.isVisible.value
  }

  private inner class MappedDiscussion(vm: GitLabMergeRequestDiffDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.Discussion<GitLabMergeRequestDiffDiscussionViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hiddenState) { visible, hidden -> visible && !hidden }
    override val range: StateFlow<LineRange?> = vm.location.mapState { it?.toLineRange(locationToLine) }
    override val line: StateFlow<Int?> = range.mapState { it?.end }
  }

  private inner class MappedDraftNote(vm: GitLabMergeRequestDiffDraftNoteViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.DraftNote<GitLabMergeRequestDiffDraftNoteViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hiddenState) { visible, hidden -> visible && !hidden }
    override val range: StateFlow<LineRange?> = vm.location.mapState { it?.toLineRange(locationToLine) }
    override val line: StateFlow<Int?> = range.mapState { it?.end }
  }

  private inner class MappedNewDiscussion(vm: GitLabMergeRequestDiffNewDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<GitLabMergeRequestDiffNewDiscussionViewModel>(vm) {
    override val key: Any = "NEW_${vm.location.value}"
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val range: StateFlow<LineRange?> = vm.location.mapState { it?.toLineRange(locationToLine) }
    override val line: StateFlow<Int?> = range.mapState { it?.end }
    override val adjustmentDisabledReason = MutableStateFlow(
      AdjustmentDisabledReason.SINGLE_COMMIT_REVIEW.takeIf { !diffReviewVm.isCumulativeChange }
    )

    override fun adjustRange(newStart: Int?, newEnd: Int?) {
      if (newStart == null && newEnd == null) return
      val range = range.value ?: return
      val newRange = LineRange(newStart ?: range.start, newEnd ?: range.end)
      val startLoc = lineToLocation(newRange.start) ?: return
      val endLoc = lineToLocation(newRange.end) ?: return
      vm.updateLineRange(startLoc, endLoc)
      vm.requestFocus()
    }
    override fun cancel() {
      vm.location.value?.let { diffReviewVm.cancelNewDiscussion(it.side to it.lineIdx) }
    }
  }

  private data class GutterState(
    override val linesWithComments: Set<Int>,
    override val linesWithNewComments: Set<Int>,
    val lineToLocation: (Int) -> DiffLineLocation?
  ) : CodeReviewEditorGutterControlsModel.ControlsState {
    override fun isLineCommentable(lineIdx: Int): Boolean = lineToLocation(lineIdx) != null
  }
}

private fun GitLabNoteLocation.toLineRange(locationToLine: (DiffLineLocation) -> Int?): LineRange? {
  val start = locationToLine(startSide to startLineIdx) ?: return null
  val end = locationToLine(side to lineIdx) ?: return null
  return LineRange(start, end)
}