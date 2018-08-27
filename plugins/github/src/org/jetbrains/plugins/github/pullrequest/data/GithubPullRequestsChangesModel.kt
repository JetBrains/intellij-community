// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.util.EventDispatcher
import git4idea.commands.Git
import git4idea.history.GitLogUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListComponent
import java.util.*

class GithubPullRequestsChangesModel(private val project: Project,
                                     private val progressManager: ProgressManager,
                                     private val requestExecutorHolder: GithubApiRequestExecutorManager.ManagedHolder,
                                     private val git: Git,
                                     private val repository: GitRepository,
                                     private val remote: GitRemote) : Disposable, GithubPullRequestsListComponent.PullRequestSelectionListener {

  private var progressIndicator = EmptyProgressIndicator()
  private val stateEventDispatcher = EventDispatcher.create(StateListener::class.java)

  override fun selectionChanged(selection: GithubSearchedIssue?) {
    showChanges(selection)
  }

  @CalledInAwt
  fun showChanges(searchedIssue: GithubSearchedIssue?) {
    progressIndicator.cancel()
    progressIndicator = EmptyProgressIndicator()

    val indicator = progressIndicator
    val requestExecutor = requestExecutorHolder.executor

    stateEventDispatcher.multicaster.loadingStarted()
    if (searchedIssue == null) stateEventDispatcher.multicaster.loadingStopped()
    else progressManager.runProcessWithProgressAsynchronously(createTask(indicator, requestExecutor, searchedIssue), indicator)
  }

  private fun createTask(indicator: ProgressIndicator,
                         requestExecutor: GithubApiRequestExecutor,
                         searchedIssue: GithubSearchedIssue) =
    object : Task.Backgroundable(project, "Loading Github Pull Request Changes", true) {
      private lateinit var changes: List<Change>

      override fun run(indicator: ProgressIndicator) {
        val links = searchedIssue.pullRequestLinks ?: throw IllegalStateException("Missing pull request links")
        val pullRequest = requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.get(links.url))

        if (!isCommitFetched(pullRequest.head.sha)) {
          git.fetch(repository, remote, emptyList(), "refs/pull/${pullRequest.number}/head:").throwOnError()
        }
        if (!isCommitFetched(pullRequest.head.sha)) throw IllegalStateException("Pull request head is not available after fetch")

        val details = GitLogUtil.collectFullDetails(project, repository.root, "${pullRequest.base.sha}..${pullRequest.head.sha}")
        changes = CommittedChangesTreeBrowser.zipChanges(details.reversed().flatMap { it.changes })
      }

      override fun onSuccess() {
        if (indicator.isCanceled) return
        stateEventDispatcher.multicaster.changesLoaded(changes)
      }

      override fun onThrowable(error: Throwable) {
        if (indicator.isCanceled) return
        stateEventDispatcher.multicaster.errorOccurred(error)
      }

      override fun onFinished() {
        if (indicator.isCanceled) return
        stateEventDispatcher.multicaster.loadingStopped()
      }
    }

  private fun isCommitFetched(commitHash: String): Boolean {
    val result = git.getObjectType(repository, commitHash)
    return result.success() && result.outputAsJoinedString == "commit"
  }

  override fun dispose() {
    progressIndicator.cancel()
  }

  fun addStateListener(listener: StateListener, disposable: Disposable) = stateEventDispatcher.addListener(listener, disposable)

  interface StateListener : EventListener {
    fun loadingStarted() {}
    fun loadingStopped() {}
    fun changesLoaded(changes: List<Change>) {}
    fun errorOccurred(error: Throwable) {}
  }
}