// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.collaboration.ui.codereview.diff.viewer.controlInlaysIn
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffInlayComponentsFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModel

private val LOG = logger<GitLabMergeRequestDiffExtension>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return

    if(viewer !is DiffViewerBase) return

    val connection = context.getUserData(GitLabProjectConnection.KEY) ?: return
    val reviewVm = context.getUserData(GitLabMergeRequestDiffReviewViewModel.KEY) ?: return

    val change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) ?: return

    val discussions: Flow<Collection<DiffMappedValue<GitLabDiscussionViewModel>>> =
      reviewVm.getViewModelFor(change).flatMapLatest { it?.discussions!! }.catch { LOG.warn(it) }

    val cs = DisposingMainScope(viewer)
    val avatarIconsProvider = CachingIconsProvider(
      AsyncImageIconsProvider(cs, connection.imageLoader)
    )

    val discussionComponentFactory = { inlayCs: CoroutineScope, discussionVm: GitLabDiscussionViewModel ->
      GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(
        project, inlayCs, avatarIconsProvider, discussionVm
      )
    }

    viewer.controlInlaysIn(cs, discussions, GitLabDiscussionViewModel::id, discussionComponentFactory)
  }
}