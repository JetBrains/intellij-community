// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModel
import com.intellij.collaboration.ui.codereview.setupCodeReviewProgressModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider

internal fun ChangesTree.showPullRequestProgress(
  parent: Disposable,
  repository: GitRepository,
  reviewData: GHPRReviewDataProvider
) {
  val model = GHPRProgressTreeModel(repository, reviewData)
  Disposer.register(parent, model)

  setupCodeReviewProgressModel(parent, model)
}

private class GHPRProgressTreeModel(
  private val repository: GitRepository,
  private val reviewData: GHPRReviewDataProvider
) : CodeReviewProgressTreeModel(),
    Disposable {

  private var unresolvedThreadsCount: Map<String, Int> = emptyMap()

  init {
    loadThreads()
    reviewData.addReviewThreadsListener(this) { loadThreads() }
  }

  private fun loadThreads() {
    reviewData.loadReviewThreads().handleOnEdt(this) { threads, _ ->
      threads ?: return@handleOnEdt // error

      unresolvedThreadsCount = threads.filter { !it.isResolved }.groupingBy { it.path }.eachCount()
      fireModelChanged()
    }
  }

  override fun dispose() = Unit

  override fun isRead(node: ChangesBrowserNode<*>): Boolean = true

  override fun getUnresolvedDiscussionsCount(node: ChangesBrowserNode<*>): Int {
    val change = node.userObject as? Change ?: return 0
    val repositoryRelativePath = VcsFileUtil.relativePath(repository.root, ChangesUtil.getFilePath(change))

    return unresolvedThreadsCount[repositoryRelativePath] ?: 0
  }
}