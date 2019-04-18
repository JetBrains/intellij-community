// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.util.base.ListenerDiffViewerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change

interface GithubPullRequestDiffCommentsProvider : Disposable {
  fun installComments(viewer: ListenerDiffViewerBase, change: Change)

  companion object {
    val KEY = Key.create<GithubPullRequestDiffCommentsProvider>("Github.PullRequest.Diff.Comments")
  }
}