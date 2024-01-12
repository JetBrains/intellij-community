// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.viewer.controlReviewIn
import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.Hideable
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffDraftNoteViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffNewDiscussionViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestDraftNoteInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorMappedComponentModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestNewDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

class GitLabMergeRequestDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return

    if (viewer !is DiffViewerBase) return

    val reviewVm = context.getUserData(GitLabMergeRequestDiffViewModel.KEY) ?: return
    val change = request.getUserData(RefComparisonChange.KEY) ?: return

    project.service<InlaysController>().installInlays(reviewVm, change, viewer)
  }

  @Service(Service.Level.PROJECT)
  private class InlaysController(private val project: Project, private val cs: CoroutineScope) {
    fun installInlays(reviewVm: GitLabMergeRequestDiffViewModel, change: RefComparisonChange, viewer: DiffViewerBase) {
      cs.launchNow(Dispatchers.Main) {
        reviewVm.getViewModelFor(change).collectLatest { changeVm ->
          if (changeVm == null) return@collectLatest
          GitLabStatistics.logMrDiffOpened(project, changeVm.isCumulativeChange)
          coroutineScope {
            viewer.controlReviewIn(this, { locationToLine, lineToLocations ->
              DiffEditorModel(this, changeVm, locationToLine, lineToLocations)
            }, { createRenderer(it, changeVm.avatarIconsProvider) })
          }
        }
      }.cancelOnDispose(viewer)
    }

    private fun CoroutineScope.createRenderer(model: GitLabMergeRequestEditorMappedComponentModel,
                                              avatarIconsProvider: IconsProvider<GitLabUserDTO>): CodeReviewComponentInlayRenderer =
      when (model) {
        is GitLabMergeRequestEditorMappedComponentModel.Discussion<*> ->
          GitLabMergeRequestDiscussionInlayRenderer(this, project, model.vm, avatarIconsProvider,
                                                    GitLabStatistics.MergeRequestNoteActionPlace.DIFF)
        is GitLabMergeRequestEditorMappedComponentModel.DraftNote<*> ->
          GitLabMergeRequestDraftNoteInlayRenderer(this, project, model.vm, avatarIconsProvider,
                                                   GitLabStatistics.MergeRequestNoteActionPlace.DIFF)
        is GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<*> ->
          GitLabMergeRequestNewDiscussionInlayRenderer(this, project, model.vm, avatarIconsProvider,
                                                       GitLabStatistics.MergeRequestNoteActionPlace.DIFF, model::cancel)
      }
  }
}

private class DiffEditorModel(
  cs: CoroutineScope,
  private val diffVm: GitLabMergeRequestDiffReviewViewModel,
  private val locationToLine: (DiffLineLocation) -> Int?,
  private val lineToLocation: (Int) -> DiffLineLocation?
) : CodeReviewEditorModel<GitLabMergeRequestEditorMappedComponentModel> {

  override val inlays: StateFlow<Collection<GitLabMergeRequestEditorMappedComponentModel>> = combine(
    diffVm.discussions.mapModelsToViewModels { MappedDiscussion(it) },
    diffVm.draftDiscussions.mapModelsToViewModels { MappedDraftNote(it) },
    diffVm.newDiscussions.mapModelsToViewModels { MappedNewDiscussion(it) }
  ) { discussions, drafts, new ->
    discussions + drafts + new
  }.stateInNow(cs, emptyList())

  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    diffVm.locationsWithDiscussions.map {
      val lines = it.mapNotNullTo(mutableSetOf(), locationToLine)
      GutterState(lines)
    }.stateInNow(cs, null)

  override fun requestNewComment(lineIdx: Int) {
    val loc = lineToLocation(lineIdx) ?: return
    diffVm.requestNewDiscussion(loc, true)
  }

  override fun toggleComments(lineIdx: Int) {
    inlays.value.forEach {
      if (it is Hideable) {
        if (it.line.value == lineIdx) {
          it.toggleHidden()
        }
      }
    }
  }

  private inner class MappedDiscussion(vm: GitLabMergeRequestDiffDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.Discussion<GitLabMergeRequestDiffDiscussionViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hidden) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.location.mapState { loc -> loc?.let { locationToLine(it) } }
  }

  private inner class MappedDraftNote(vm: GitLabMergeRequestDiffDraftNoteViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.DraftNote<GitLabMergeRequestDiffDraftNoteViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hidden) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.location.mapState { loc -> loc?.let { locationToLine(it) } }
  }

  private inner class MappedNewDiscussion(vm: GitLabMergeRequestDiffNewDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<GitLabMergeRequestDiffNewDiscussionViewModel>(vm) {
    override val key: Any = "NEW_${vm.originalLocation}"
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val line: StateFlow<Int?> = vm.location.mapState { loc -> loc?.let { locationToLine(it) } }
    override fun cancel() = diffVm.cancelNewDiscussion(vm.originalLocation)
  }

  private data class GutterState(override val linesWithComments: Set<Int>) : CodeReviewEditorGutterControlsModel.ControlsState {
    override fun isLineCommentable(lineIdx: Int): Boolean = true
  }
}