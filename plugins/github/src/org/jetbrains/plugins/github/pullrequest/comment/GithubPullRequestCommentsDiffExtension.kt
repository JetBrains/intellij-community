// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer

class GithubPullRequestCommentsDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val commentsProvider = context.getUserData(GithubPullRequestDiffCommentsProvider.KEY) ?: return
    val change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) ?: return
    if (viewer !is ListenerDiffViewerBase) return

    commentsProvider.install(viewer, change)
  }
}