// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.viewer.DiffMapped
import com.intellij.collaboration.ui.codereview.diff.viewer.LineHoverAwareGutterMark
import com.intellij.collaboration.ui.codereview.diff.viewer.controlInlaysIn
import com.intellij.collaboration.ui.codereview.editor.controlGutterIconsIn
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffInlayComponentsFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import javax.swing.Icon

class GitLabMergeRequestDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return

    if (viewer !is DiffViewerBase) return

    val reviewVm = context.getUserData(GitLabMergeRequestDiffViewModel.KEY) ?: return

    val change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) ?: return

    val dataProvider = GenericDataProvider().apply {
      putData(GitLabMergeRequestDiffViewModel.DATA_KEY, reviewVm)
    }
    context.putUserData(DiffUserDataKeys.DATA_PROVIDER, dataProvider)
    context.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS,
                        listOf(ActionManager.getInstance().getAction("GitLab.MergeRequest.Review.Submit")))

    project.service<InlaysController>().installInlays(reviewVm, change, viewer)
  }

  @Service(Service.Level.PROJECT)
  private class InlaysController(private val project: Project, private val cs: CoroutineScope) {

    fun installInlays(reviewVm: GitLabMergeRequestDiffViewModel, change: Change, viewer: DiffViewerBase) {
      cs.launchNow(Dispatchers.Main) {
        reviewVm.getViewModelFor(change).collectLatest { changeVm ->
          if (changeVm == null) return@collectLatest
          GitLabStatistics.logMrDiffOpened(project, changeVm.isCumulativeChange)

          coroutineScope {
            viewer.controlInlaysIn(this, changeVm.discussions, GitLabMergeRequestDiffDiscussionViewModel::id) {
              val inlayCs = this
              GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(
                project, inlayCs, reviewVm.avatarIconsProvider, it
              )
            }

            viewer.controlInlaysIn(this, changeVm.draftDiscussions, GitLabMergeRequestDiffDiscussionViewModel::id) {
              val inlayCs = this
              GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(
                project, inlayCs, reviewVm.avatarIconsProvider, it
              )
            }

            val newDiscussions = changeVm.newDiscussions.map {
              it.map { (location, vm) ->
                NewNoteDiffInlayViewModel(changeVm, location, vm)
              }
            }
            viewer.controlInlaysIn(this, newDiscussions, NewNoteDiffInlayViewModel::id) {
              val inlayCs = this
              GitLabMergeRequestDiffInlayComponentsFactory.createNewDiscussion(
                project, inlayCs, reviewVm.avatarIconsProvider, it.editVm, it::cancel
              )
            }

            launch {
              changeVm.discussionsViewOption.collectLatest { discussionsViewOption ->
                if (discussionsViewOption != DiscussionsViewOption.DONT_SHOW) {
                  coroutineScope {
                    viewer.controlAddCommentActionsIn(this, changeVm)
                    awaitCancellation()
                  }
                }
              }
            }

            awaitCancellation()
          }
        }
      }.cancelOnDispose(viewer)
    }
  }
}

private class NewNoteDiffInlayViewModel(private val changeVm: GitLabMergeRequestDiffChangeViewModel,
                                        private val newLocation: DiffLineLocation,
                                        val editVm: NewGitLabNoteViewModel) : DiffMapped {
  val id: String = "NEW_AT_$newLocation"
  override val location: Flow<DiffLineLocation> = flowOf(newLocation)
  override val isVisible: Flow<Boolean> = flowOf(true)

  fun cancel() {
    changeVm.cancelNewDiscussion(newLocation)
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