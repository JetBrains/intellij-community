// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.viewer.DiffMapped
import com.intellij.collaboration.ui.codereview.diff.viewer.controlInlaysIn
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestNewDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestReviewControlsGutterRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

class GitLabMergeRequestDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return

    if (viewer !is DiffViewerBase) return

    val reviewVm = context.getUserData(GitLabMergeRequestDiffViewModel.KEY) ?: return

    val change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) ?: return

    val dataProvider = GenericDataProvider().apply {
      putData(GitLabMergeRequestReviewViewModel.DATA_KEY, reviewVm)
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
              GitLabMergeRequestDiscussionInlayRenderer(this, project, it, reviewVm.avatarIconsProvider)
            }

            viewer.controlInlaysIn(this, changeVm.draftDiscussions, GitLabMergeRequestDiffDiscussionViewModel::id) {
              GitLabMergeRequestDiscussionInlayRenderer(this, project, it, reviewVm.avatarIconsProvider)
            }

            val newDiscussions = changeVm.newDiscussions.map {
              it.map { (location, vm) ->
                NewNoteDiffInlayViewModel(changeVm, location, vm)
              }
            }
            viewer.controlInlaysIn(this, newDiscussions, NewNoteDiffInlayViewModel::id) {
              GitLabMergeRequestNewDiscussionInlayRenderer(this, project, it.editVm, reviewVm.avatarIconsProvider, it::cancel)
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

private class NewNoteDiffInlayViewModel(private val changeVm: GitLabMergeRequestChangeViewModel,
                                        private val newLocation: DiffLineLocation,
                                        val editVm: NewGitLabNoteViewModel) : DiffMapped {
  val id: String = "NEW_AT_$newLocation"
  override val location: Flow<DiffLineLocation> = flowOf(newLocation)
  override val isVisible: Flow<Boolean> = flowOf(true)

  fun cancel() {
    changeVm.cancelNewDiscussion(newLocation)
  }
}

private fun DiffViewerBase.controlAddCommentActionsIn(cs: CoroutineScope, vm: GitLabMergeRequestChangeViewModel) {
  when (this) {
    is SimpleOnesideDiffViewer -> {
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, editor.allLinesFlow(), editor) {
        vm.requestNewDiscussion(side to it, true)
      }
    }
    is UnifiedDiffViewer -> {
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, editor.allLinesFlow(), editor) {
        val (indices, side) = transferLineFromOneside(it)
        val loc = side.select(indices).takeIf { it >= 0 }?.let { side to it } ?: return@setupIn
        vm.requestNewDiscussion(loc, true)
      }
    }
    is TwosideTextDiffViewer -> {
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, editor1.allLinesFlow(), editor1) {
        vm.requestNewDiscussion(Side.LEFT to it, true)
      }
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, editor2.allLinesFlow(), editor2) {
        vm.requestNewDiscussion(Side.RIGHT to it, true)
      }
    }
    else -> return
  }
}

private fun Editor.allLinesFlow() = MutableStateFlow(listOf(LineRange(0, document.lineCount)))