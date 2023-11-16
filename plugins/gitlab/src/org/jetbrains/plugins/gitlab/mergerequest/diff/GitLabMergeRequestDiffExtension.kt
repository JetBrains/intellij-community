// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.viewer.controlInlaysIn
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestNewDiscussionInlayRenderer
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestReviewControlsGutterRenderer
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
            viewer.controlInlaysIn(this, changeVm.discussions, GitLabMergeRequestDiffDiscussionViewModel::id) {
              GitLabMergeRequestDiscussionInlayRenderer(this, project, it, changeVm.avatarIconsProvider,
                                                        GitLabStatistics.MergeRequestNoteActionPlace.DIFF)
            }

            viewer.controlInlaysIn(this, changeVm.draftDiscussions, GitLabMergeRequestDiffDiscussionViewModel::id) {
              GitLabMergeRequestDiscussionInlayRenderer(this, project, it, changeVm.avatarIconsProvider,
                                                        GitLabStatistics.MergeRequestNoteActionPlace.DIFF)
            }

            viewer.controlInlaysIn(this, changeVm.newDiscussions, { "NEW_${it.originalLocation}" }) {
              GitLabMergeRequestNewDiscussionInlayRenderer(this, project, it, changeVm.avatarIconsProvider,
                                                           GitLabStatistics.MergeRequestNoteActionPlace.DIFF) {
                changeVm.cancelNewDiscussion(it.originalLocation)
              }
            }

            launch {
              changeVm.canComment.collectLatest {
                if (it) {
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

private fun DiffViewerBase.controlAddCommentActionsIn(cs: CoroutineScope, vm: GitLabMergeRequestDiffReviewViewModel) {
  when (this) {
    is SimpleOnesideDiffViewer -> {
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, MutableStateFlow(emptyList()), editor) {
        vm.requestNewDiscussion(side to it, true)
      }
    }
    is UnifiedDiffViewer -> {
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, MutableStateFlow(emptyList()), editor) {
        val (indices, side) = transferLineFromOneside(it)
        val loc = side.select(indices).takeIf { it >= 0 }?.let { side to it } ?: return@setupIn
        vm.requestNewDiscussion(loc, true)
      }
    }
    is TwosideTextDiffViewer -> {
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, MutableStateFlow(emptyList()), editor1) {
        vm.requestNewDiscussion(Side.LEFT to it, true)
      }
      GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, MutableStateFlow(emptyList()), editor2) {
        vm.requestNewDiscussion(Side.RIGHT to it, true)
      }
    }
    else -> return
  }
}