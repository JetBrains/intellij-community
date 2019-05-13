// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import git4idea.GitCommit
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.GithubNotifications
import java.util.*

class GithubPullRequestsStateServiceImpl internal constructor(private val project: Project,
                                                              private val progressManager: ProgressManager,
                                                              private val dataLoader: GithubPullRequestsDataLoader,
                                                              private val requestExecutor: GithubApiRequestExecutor,
                                                              private val serverPath: GithubServerPath,
                                                              private val repoPath: GithubFullPath)
  : GithubPullRequestsStateService {

  private val busySet = ContainerUtil.newConcurrentSet<Long>()
  private val busyChangeEventDispatcher = EventDispatcher.create(GithubPullRequestBusyStateListener::class.java)

  @CalledInAwt
  override fun close(pullRequest: Long) {
    if (!acquire(pullRequest)) return

    progressManager.run(object : Task.Backgroundable(project, "Closing Pull Request", true) {
      override fun run(indicator: ProgressIndicator) {
        requestExecutor.execute(indicator,
                                GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.user, repoPath.repository, pullRequest,
                                                                            state = GithubIssueState.closed))
      }

      override fun onSuccess() {
        GithubNotifications.showInfo(project, "Pull Request Closed", "Successfully closed pull request #${pullRequest}")
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Close Pull Request", error)
      }

      override fun onFinished() {
        release(pullRequest)
        dataLoader.reloadDetails(pullRequest)
      }
    })
  }

  @CalledInAwt
  override fun reopen(pullRequest: Long) {
    if (!acquire(pullRequest)) return

    progressManager.run(object : Task.Backgroundable(project, "Reopening Pull Request", true) {
      override fun run(indicator: ProgressIndicator) {
        requestExecutor.execute(indicator,
                                GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.user, repoPath.repository, pullRequest,
                                                                            state = GithubIssueState.open))
      }

      override fun onSuccess() {
        GithubNotifications.showInfo(project, "Pull Request Reopened", "Successfully reopened pull request #${pullRequest}")
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Reopen Pull Request", error)
      }

      override fun onFinished() {
        release(pullRequest)
        dataLoader.reloadDetails(pullRequest)
      }
    })
  }

  @CalledInAwt
  override fun merge(pullRequest: Long) {
    if (!acquire(pullRequest)) return

    progressManager.run(object : Task.Backgroundable(project, "Merging Pull Request", true) {
      private lateinit var details: GithubPullRequestDetailed

      override fun run(indicator: ProgressIndicator) {
        indicator.text2 = "Loading details"
        details = GithubAsyncUtil.awaitMutableFuture(indicator) { dataLoader.getDataProvider(pullRequest).detailsRequest }
        indicator.checkCanceled()

        indicator.text2 = "Acquiring commit message"
        val commitMessage = invokeAndWaitIfNeeded {
          val dialog = GithubMergeCommitMessageDialog(project,
                                                      "Merge Pull Request",
                                                      "Merge pull request #${pullRequest}",
                                                      details.title)
          if (dialog.showAndGet()) dialog.message else null
        } ?: throw ProcessCanceledException()

        indicator.text2 = "Merging"
        requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.merge(details,
                                                                                      commitMessage.first, commitMessage.second,
                                                                                      details.head.sha))
      }

      override fun onSuccess() {
        GithubNotifications.showInfo(project, "Pull Request Merged", "Successfully merged pull request #${details.number}")
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Merge Pull Request", error)
      }

      override fun onFinished() {
        release(pullRequest)
        dataLoader.reloadDetails(pullRequest)
      }
    })
  }

  @CalledInAwt
  override fun rebaseMerge(pullRequest: Long) {
    if (!acquire(pullRequest)) return

    progressManager.run(object : Task.Backgroundable(project, "Merging Pull Request", true) {
      lateinit var details: GithubPullRequestDetailed

      override fun run(indicator: ProgressIndicator) {
        indicator.text2 = "Loading details"
        details = GithubAsyncUtil.awaitMutableFuture(indicator) { dataLoader.getDataProvider(pullRequest).detailsRequest }
        indicator.checkCanceled()

        indicator.text2 = "Merging"
        requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.rebaseMerge(details, details.head.sha))
      }

      override fun onSuccess() {
        GithubNotifications.showInfo(project, "Pull Request Rebased and Merged",
                                     "Successfully rebased and merged pull request #${details.number}")
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Rebase and Merge Pull Request", error)
      }

      override fun onFinished() {
        release(pullRequest)
        dataLoader.reloadDetails(pullRequest)
      }
    })
  }

  @CalledInAwt
  override fun squashMerge(pullRequest: Long) {
    if (!acquire(pullRequest)) return

    progressManager.run(object : Task.Backgroundable(project, "Merging Pull Request", true) {
      lateinit var details: GithubPullRequestDetailed
      lateinit var commits: List<GitCommit>


      override fun run(indicator: ProgressIndicator) {
        indicator.text2 = "Loading details"
        details = GithubAsyncUtil.awaitMutableFuture(indicator) { dataLoader.getDataProvider(pullRequest).detailsRequest }
        indicator.checkCanceled()

        indicator.text2 = "Loading commits"
        commits = GithubAsyncUtil.awaitMutableFuture(indicator) { dataLoader.getDataProvider(pullRequest).logCommitsRequest }
        indicator.checkCanceled()

        indicator.text2 = "Acquiring commit message"
        val body = "* " + StringUtil.join(commits, { it.subject }, "\n\n* ")
        val commitMessage = invokeAndWaitIfNeeded {
          val dialog = GithubMergeCommitMessageDialog(project,
                                                      "Merge Pull Request",
                                                      "Merge pull request #${pullRequest}",
                                                      body)
          if (dialog.showAndGet()) dialog.message else null
        } ?: throw ProcessCanceledException()

        indicator.text2 = "Merging"
        requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.merge(details,
                                                                                      commitMessage.first, commitMessage.second,
                                                                                      details.head.sha))
      }

      override fun onSuccess() {
        GithubNotifications.showInfo(project, "Pull Request Squashed and Merged",
                                     "Successfully squashed amd merged pull request #${details.number}")
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Squash and Merge Pull Request", error)
      }

      override fun onFinished() {
        release(pullRequest)
        dataLoader.reloadDetails(pullRequest)
      }
    })
  }

  @CalledInAwt
  private fun acquire(pullRequest: Long): Boolean {
    val ok = busySet.add(pullRequest)
    if (ok) busyChangeEventDispatcher.multicaster.busyStateChanged(pullRequest)
    return ok
  }

  @CalledInAwt
  private fun release(pullRequest: Long) {
    val ok = busySet.remove(pullRequest)
    if (ok) busyChangeEventDispatcher.multicaster.busyStateChanged(pullRequest)
  }

  @CalledInAwt
  override fun isBusy(pullRequest: Long) = busySet.contains(pullRequest)

  override fun addPullRequestBusyStateListener(disposable: Disposable, listener: (Long) -> Unit) =
    busyChangeEventDispatcher.addListener(object : GithubPullRequestBusyStateListener {
      override fun busyStateChanged(pullRequest: Long) {
        listener(pullRequest)
      }
    }, disposable)


  private interface GithubPullRequestBusyStateListener : EventListener {
    fun busyStateChanged(pullRequest: Long)
  }
}