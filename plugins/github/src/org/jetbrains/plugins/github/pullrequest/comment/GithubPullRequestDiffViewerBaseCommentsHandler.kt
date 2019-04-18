// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.pullrequest.comment.model.GithubPullRequestFileCommentThread
import kotlin.properties.Delegates.observable

abstract class GithubPullRequestDiffViewerBaseCommentsHandler<T : DiffViewerBase>(protected val viewer: T)
  : GithubPullRequestDiffViewerCommentsHandler, Disposable {

  init {
    @Suppress("LeakingThis")
    Disposer.register(viewer, this)
  }

  protected abstract val viewerReady: Boolean

  override var threads by observable<List<GithubPullRequestFileCommentThread>?>(null) { _, _, newValue ->
    if (newValue != null && viewerReady) showCommentThreads(newValue)
  }

  protected abstract fun showCommentThreads(commentThreads: List<GithubPullRequestFileCommentThread>)

  override fun dispose() {}
}