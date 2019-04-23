// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.EventDispatcher
import git4idea.GitCommit
import git4idea.commands.Git
import git4idea.history.GitCommitRequirements
import git4idea.history.GitLogUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubCommit
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.pullrequest.comment.GithubPullRequestCommentsUtil
import org.jetbrains.plugins.github.pullrequest.data.model.GithubPullRequestFileCommentsThreadMapping
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

internal class GithubPullRequestDataProviderImpl(private val project: Project,
                                                 private val progressManager: ProgressManager,
                                                 private val git: Git,
                                                 private val requestExecutor: GithubApiRequestExecutor,
                                                 private val repository: GitRepository,
                                                 private val remote: GitRemote,
                                                 private val serverPath: GithubServerPath,
                                                 private val username: String,
                                                 private val repositoryName: String,
                                                 override val number: Long) : GithubPullRequestDataProvider {

  private val requestsChangesEventDispatcher = EventDispatcher.create(GithubPullRequestDataProvider.RequestsChangedListener::class.java)

  private var lastKnownHeadSha: String? = null

  private val detailsRequestValue = object : LazyCancellableBackgroundProcessValue<GithubPullRequestDetailedWithHtml>(progressManager) {
    override fun compute(indicator: ProgressIndicator): GithubPullRequestDetailedWithHtml {
      val details = requestExecutor.execute(indicator,
                                            GithubApiRequests.Repos.PullRequests.getHtml(serverPath, username, repositoryName, number))
      invokeAndWaitIfNeeded {
        lastKnownHeadSha?.run { if (this != details.head.sha) reloadCommits() }
        lastKnownHeadSha = details.head.sha
      }
      return details
    }
  }
  override val detailsRequest
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { detailsRequestValue.value } }

  private val branchFetchRequestValue = object : LazyCancellableBackgroundProcessValue<Unit>(progressManager) {
    override fun compute(indicator: ProgressIndicator) {
      git.fetch(repository, remote, emptyList(), "refs/pull/${number}/head:").throwOnError()
    }
  }
  override val branchFetchRequest
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { branchFetchRequestValue.value } }

  private val apiCommitsRequestValue = object : LazyCancellableBackgroundProcessValue<List<GithubCommit>>(progressManager) {
    override fun compute(indicator: ProgressIndicator) = GithubApiPagesLoader
      .loadAll(requestExecutor, indicator,
               GithubApiRequests.Repos.PullRequests.Commits.pages(serverPath, username, repositoryName, number))

  }
  override val apiCommitsRequest
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { apiCommitsRequestValue.value } }

  private val logCommitsRequestValue = object : LazyCancellableBackgroundProcessValue<List<GitCommit>>(progressManager) {
    override fun compute(indicator: ProgressIndicator): List<GitCommit> {
      branchFetchRequestValue.value.joinCancellable()
      val commitHashes = apiCommitsRequestValue.value.joinCancellable().map { it.sha }
      val gitCommits = mutableListOf<GitCommit>()
      val requirements = GitCommitRequirements(diffRenameLimit = GitCommitRequirements.DiffRenameLimit.INFINITY,
                                               includeRootChanges = false)
      GitLogUtil.readFullDetailsForHashes(project, repository.root, commitHashes, requirements) {
        gitCommits.add(it)
      }

      return gitCommits
    }
  }
  override val logCommitsRequest
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { logCommitsRequestValue.value } }

  private val diffFileRequestValue = object : LazyCancellableBackgroundProcessValue<String>(progressManager) {
    override fun compute(indicator: ProgressIndicator): String {
      return requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.getDiff(serverPath, username, repositoryName, number))
    }
  }

  private val commentsRequestValue = object : LazyCancellableBackgroundProcessValue<List<GithubPullRequestCommentWithHtml>>(
    progressManager) {
    override fun compute(indicator: ProgressIndicator): List<GithubPullRequestCommentWithHtml> {
      return GithubApiPagesLoader.loadAll(requestExecutor, indicator,
                                          GithubApiRequests.Repos.PullRequests.Comments.pages(serverPath, username, repositoryName, number))
    }
  }

  private val filesCommentsRequestValue =
    object : LazyCancellableBackgroundProcessValue<Map<Change, List<GithubPullRequestFileCommentsThreadMapping>>>(progressManager) {
      override fun compute(indicator: ProgressIndicator): Map<Change, List<GithubPullRequestFileCommentsThreadMapping>> {
        return GithubPullRequestCommentsUtil.buildThreadsAndMapLines(repository,
                                                                     logCommitsRequestValue.value.joinCancellable(),
                                                                     diffFileRequestValue.value.joinCancellable(),
                                                                     commentsRequestValue.value.joinCancellable())
      }
    }
  override val filesCommentThreadsRequest: CompletableFuture<Map<Change, List<GithubPullRequestFileCommentsThreadMapping>>>
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { filesCommentsRequestValue.value } }

  @CalledInAwt
  override fun reloadDetails() {
    detailsRequestValue.drop()
    requestsChangesEventDispatcher.multicaster.detailsRequestChanged()
  }

  @CalledInAwt
  override fun reloadCommits() {
    branchFetchRequestValue.drop()
    apiCommitsRequestValue.drop()
    logCommitsRequestValue.drop()
    requestsChangesEventDispatcher.multicaster.commitsRequestChanged()
    reloadComments()
  }

  @CalledInAwt
  override fun reloadComments() {
    diffFileRequestValue.drop()
    commentsRequestValue.drop()
    filesCommentsRequestValue.drop()
    requestsChangesEventDispatcher.multicaster.commentsRequestChanged()
  }

  @Throws(ProcessCanceledException::class)
  private fun <T> CompletableFuture<T>.joinCancellable(): T {
    try {
      return join()
    }
    catch (e: CancellationException) {
      throw ProcessCanceledException(e)
    }
    catch (e: CompletionException) {
      if (GithubAsyncUtil.isCancellation(e)) throw ProcessCanceledException(e)
      throw e.cause ?: e
    }
  }

  override fun addRequestsChangesListener(listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.addListener(listener)

  override fun addRequestsChangesListener(disposable: Disposable, listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.addListener(listener, disposable)

  override fun removeRequestsChangesListener(listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.removeListener(listener)
}