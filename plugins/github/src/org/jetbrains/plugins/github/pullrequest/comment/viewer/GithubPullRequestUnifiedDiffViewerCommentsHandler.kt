// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.EditorComponentInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileCommentsThreadsMap
import org.jetbrains.plugins.github.pullrequest.data.model.GithubPullRequestFileCommentsThreadMapping

class GithubPullRequestUnifiedDiffViewerCommentsHandler(viewer: UnifiedDiffViewer,
                                                        componentFactory: GithubPullRequestEditorCommentsThreadComponentFactory)
  : GithubPullRequestDiffViewerBaseCommentsHandler<UnifiedDiffViewer>(viewer, componentFactory) {

  private val editorThreads = GithubPullRequestFileCommentsThreadsMap()

  override val viewerReady: Boolean
    get() = viewer.isContentGood

  init {
    val inlaysManager = EditorComponentInlaysManager(viewer.editor as EditorImpl)
    GithubPullRequestEditorCommentsThreadsController(editorThreads, componentFactory, inlaysManager)

    viewer.addListener(object : DiffViewerListener() {
      override fun onAfterRediff() {
        updateThreads(mappings)
      }
    })
  }

  override fun updateThreads(mappings: List<GithubPullRequestFileCommentsThreadMapping>) {
    editorThreads.update(mappings.groupBy { viewer.transferLineToOneside(it.side, it.fileLine - 1) + 1 }.filter { (line, _) -> line >= 1 })
  }
}




