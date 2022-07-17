// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepository.GIT_REPO_CHANGE
import git4idea.repo.GitRepositoryChangeListener
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider

class GHPRSuggestedChangeHelper(
  project: Project,
  parentDisposable: Disposable,
  val repository: GitRepository,
  private val reviewDataProvider: GHPRReviewDataProvider,
  private val detailsDataProvider: GHPRDetailsDataProvider
) {
  val suggestedChangeCommitMessageDocument by lazy(LazyThreadSafetyMode.NONE) {
    EditorFactory.getInstance().createDocument("")
  }

  val isCorrectBranch by lazy(LazyThreadSafetyMode.NONE) {
    SingleValueModel(isCorrectBranchWithPullRequestSource())
  }

  init {
    project.messageBus
      .connect(parentDisposable)
      .subscribe(GIT_REPO_CHANGE, GitRepositoryChangeListener {
        val currentBranchName = it.currentBranch?.name ?: return@GitRepositoryChangeListener
        val pullRequestSourceBranchName = detailsDataProvider.loadedDetails?.headRefName ?: return@GitRepositoryChangeListener

        isCorrectBranch.value = currentBranchName == pullRequestSourceBranchName
      })
  }

  fun resolveThread(threadId: String) {
    reviewDataProvider.resolveThread(EmptyProgressIndicator(), threadId)
  }

  private fun isCorrectBranchWithPullRequestSource(): Boolean {
    val currentBranchName = GitBranchUtil.getBranchNameOrRev(repository)
    val pullRequestSourceBranchName = detailsDataProvider.loadedDetails?.headRefName ?: return false

    return currentBranchName == pullRequestSourceBranchName
  }
}