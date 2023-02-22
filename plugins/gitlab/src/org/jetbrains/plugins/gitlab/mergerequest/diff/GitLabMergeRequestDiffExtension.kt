// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.collaboration.ui.codereview.diff.mapValue
import com.intellij.collaboration.ui.codereview.diff.viewer.LineHoverAwareGutterMark
import com.intellij.collaboration.ui.codereview.diff.viewer.controlGutterIconsIn
import com.intellij.collaboration.ui.codereview.diff.viewer.controlInlaysIn
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffInlayComponentsFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import javax.swing.Icon

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return

    if (viewer !is DiffViewerBase) return

    val reviewVm = context.getUserData(GitLabMergeRequestDiffReviewViewModel.KEY) ?: return

    val change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) ?: return

    val changeVm = reviewVm.getViewModelFor(change)
    val inlays: Flow<List<DiffMappedValue<DiffInlayViewModel>>> =
      changeVm.flatMapLatest { vm ->
        if (vm == null) return@flatMapLatest flowOf(emptyList())

        combine(vm.discussions, vm.newDiscussions) { existing, new ->
          val result = mutableListOf<DiffMappedValue<DiffInlayViewModel>>()
          for (mappedVm in existing) {
            result.add(mappedVm.mapValue { DiffInlayViewModel.Discussion(it) })
          }
          for (mappedVm in new) {
            result.add(mappedVm.mapValue { DiffInlayViewModel.NewNote(vm, mappedVm.location, it) })
          }
          result
        }
      }

    val cs = DisposingMainScope(viewer)

    val componentFactory = { inlayCs: CoroutineScope, inlay: DiffInlayViewModel ->
      when (inlay) {
        is DiffInlayViewModel.Discussion -> {
          GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(
            project, inlayCs, reviewVm.avatarIconsProvider, inlay.vm
          )
        }
        is DiffInlayViewModel.NewNote -> {
          GitLabMergeRequestDiffInlayComponentsFactory.createNewDiscussion(
            project, inlayCs, reviewVm.avatarIconsProvider, inlay.editVm
          ) {
            inlay.cancel()
          }
        }
      }
    }

    viewer.controlInlaysIn(cs, inlays, DiffInlayViewModel::id, componentFactory)

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      changeVm.collectLatest { changeVm ->
        coroutineScope {
          if (changeVm != null) {
            viewer.controlAddCommentActionsIn(this, changeVm)
            awaitCancellation()
          }
        }
      }
    }
  }

  private fun DiffViewerBase.controlAddCommentActionsIn(cs: CoroutineScope, vm: GitLabMergeRequestDiffChangeViewModel) {
    when (this) {
      is SimpleOnesideDiffViewer -> {
        editor.controlGutterIconsIn(cs) { line ->
          AddCommentGutterIconRenderer(line, vm) {
            side to line
          }
        }
      }
      is UnifiedDiffViewer -> {
        editor.controlGutterIconsIn(cs) { line ->
          AddCommentGutterIconRenderer(line, vm) {
            val (indices, side) = transferLineFromOneside(line)
            side.select(indices).takeIf { it >= 0 }?.let { side to it }
          }
        }
      }
      is TwosideTextDiffViewer -> {
        editor1.controlGutterIconsIn(cs) { line ->
          AddCommentGutterIconRenderer(line, vm) {
            Side.LEFT to line
          }
        }
        editor2.controlGutterIconsIn(cs) { line ->
          AddCommentGutterIconRenderer(line, vm) {
            Side.RIGHT to line
          }
        }
      }
      else -> return
    }
  }
}

private sealed interface DiffInlayViewModel {
  val id: String

  class Discussion(val vm: GitLabDiscussionViewModel) : DiffInlayViewModel {
    override val id: String = vm.id
  }

  class NewNote(private val changeVm: GitLabMergeRequestDiffChangeViewModel,
                private val location: DiffLineLocation,
                val editVm: NewGitLabNoteViewModel) : DiffInlayViewModel {
    override val id: String = "NEW"

    fun cancel() {
      changeVm.cancelNewDiscussion(location)
    }
  }
}

private class AddCommentGutterIconRenderer(
  override val line: Int,
  private val vm: GitLabMergeRequestDiffChangeViewModel,
  private val lineLocator: () -> DiffLineLocation?
) : GutterIconRenderer(), LineHoverAwareGutterMark, DumbAware {

  override var isHovered: Boolean = false

  override fun getIcon(): Icon = if (isHovered) AllIcons.General.InlineAdd else EmptyIcon.ICON_16

  override fun isNavigateAction() = true

  override fun getClickAction(): AnAction =
    DumbAwareAction.create(CollaborationToolsBundle.message("review.comment.action")) {
      val location = lineLocator() ?: return@create
      vm.requestNewDiscussion(location, true)
    }

  override fun getAlignment() = Alignment.RIGHT

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AddCommentGutterIconRenderer) return false

    return line == other.line
  }

  override fun hashCode(): Int = line
}