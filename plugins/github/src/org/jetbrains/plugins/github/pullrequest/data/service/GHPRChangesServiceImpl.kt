// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.history.GitHistoryUtils
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class GHPRChangesServiceImpl(private val progressManager: ProgressManager,
                             private val git: Git,
                             private val project: Project,
                             private val requestExecutor: GithubApiRequestExecutor,
                             private val gitRemote: GitRemoteUrlCoordinates,
                             private val ghRepository: GHRepositoryCoordinates) : GHPRChangesService {

  override fun fetch(progressIndicator: ProgressIndicator, refspec: String) =
    progressManager.submitIOTask(progressIndicator) {
      GitFetchSupport.fetchSupport(project)
        .fetch(gitRemote.repository, gitRemote.remote, refspec).throwExceptionIfFailed()
    }.logError(LOG, "Error occurred while fetching \"$refspec\"")

  override fun fetchBranch(progressIndicator: ProgressIndicator, branch: String) =
    fetch(progressIndicator, branch).thenApply {
      if (!git.getObjectType(gitRemote.repository, branch).outputAsJoinedString.equals("commit", true))
        error("Branch \"${branch}\" was not fetched from \"${gitRemote.remote.name}\"")
    }.logError(LOG, "Error occurred while fetching \"$branch\"")

  override fun loadCommitsFromApi(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) { indicator ->
      SimpleGHGQLPagesLoader(requestExecutor, { p ->
        GHGQLRequests.PullRequest.commits(ghRepository, pullRequestId.number, p)
      }).loadAll(indicator).map { it.commit }
    }.logError(LOG, "Error occurred while loading commits for PR ${pullRequestId.number}")

  override fun loadCommitDiffs(progressIndicator: ProgressIndicator, baseRefOid: String, oid: String) =
    progressManager.submitIOTask(progressIndicator) {
      val commitDiff = requestExecutor.execute(it,
                                               GithubApiRequests.Repos.Commits.getDiff(ghRepository, oid))

      val cumulativeDiff = requestExecutor.execute(it,
                                                   GithubApiRequests.Repos.Commits.getDiff(ghRepository, baseRefOid, oid))
      commitDiff to cumulativeDiff
    }.logError(LOG, "Error occurred while loading diffs for commit $oid")

  override fun loadMergeBaseOid(progressIndicator: ProgressIndicator, baseRefOid: String, headRefOid: String) =
    progressManager.submitIOTask(progressIndicator) {
      GitHistoryUtils.getMergeBase(project, gitRemote.repository.root, baseRefOid, headRefOid)?.rev
      ?: error("Could not calculate merge base for PR branch")
    }.logError(LOG, "Error occurred while calculating merge base for $baseRefOid and $headRefOid")

  override fun createChangesProvider(progressIndicator: ProgressIndicator, mergeBaseOid: String, commits: List<GHCommit>) =
    progressManager.submitIOTask(progressIndicator) {
      val commitsDiffsRequests = mutableMapOf<GHCommit, CompletableFuture<Pair<String, String>>>()
      for (commit in commits) {
        commitsDiffsRequests[commit] = loadCommitDiffs(it, mergeBaseOid, commit.oid)
      }

      CompletableFuture.allOf(*commitsDiffsRequests.values.toTypedArray()).joinCancellable()
      val commitsWithDiffs = commitsDiffsRequests.map { (commit, request) ->
        val diffs = request.joinCancellable()
        Triple(commit, diffs.first, diffs.second)
      }
      it.checkCanceled()

      GHPRChangesProviderImpl(gitRemote.repository, mergeBaseOid, commitsWithDiffs) as GHPRChangesProvider
    }.logError(LOG, "Error occurred while building changes from commits")

  companion object {
    private val LOG = logger<GHPRChangesService>()

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
        throw GithubAsyncUtil.extractError(e)
      }
    }
  }
}