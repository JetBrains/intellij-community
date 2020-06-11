// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph
import com.google.common.graph.Traverser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import git4idea.fetch.GitFetchSupport
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
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
    fetch(progressIndicator, branch).logError(LOG, "Error occurred while fetching \"$branch\"")

  override fun loadCommitsFromApi(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) { indicator ->
      SimpleGHGQLPagesLoader(requestExecutor, { p ->
        GHGQLRequests.PullRequest.commits(ghRepository, pullRequestId.number, p)
      }).loadAll(indicator).map { it.commit }.let(::buildCommitsTree)
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
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.Commits.compare(ghRepository, baseRefOid, headRefOid)).mergeBaseCommit.sha
    }.logError(LOG, "Error occurred while calculating merge base for $baseRefOid and $headRefOid")

  override fun createChangesProvider(progressIndicator: ProgressIndicator, mergeBaseOid: String, commits: Pair<GHCommit, Graph<GHCommit>>) =
    progressManager.submitIOTask(progressIndicator) {
      val (lastCommit, graph) = commits
      val commitsDiffsRequests = mutableMapOf<GHCommit, CompletableFuture<Pair<String, String>>>()
      for (commit in Traverser.forGraph(graph).depthFirstPostOrder(lastCommit)) {
        commitsDiffsRequests[commit] = loadCommitDiffs(it, mergeBaseOid, commit.oid)
      }

      CompletableFuture.allOf(*commitsDiffsRequests.values.toTypedArray()).joinCancellable()
      val patchesByCommits = commitsDiffsRequests.mapValues { (_, request) ->
        val diffs = request.joinCancellable()
        val commitPatches = readAllPatches(diffs.first)
        val cumulativePatches = readAllPatches(diffs.second)
        commitPatches to cumulativePatches
      }
      it.checkCanceled()

      GHPRChangesProviderImpl(gitRemote.repository, mergeBaseOid, graph, lastCommit, patchesByCommits) as GHPRChangesProvider
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

    private fun readAllPatches(diffFile: String): List<FilePatch> {
      val reader = PatchReader(diffFile, true)
      reader.parseAllPatches()
      return reader.allPatches
    }

    private fun buildCommitsTree(commits: List<GHCommit>): Pair<GHCommit, Graph<GHCommit>> {
      val commitsBySha = mutableMapOf<String, GHCommit>()
      val parentCommits = mutableSetOf<GHCommitHash>()

      for (commit in commits) {
        commitsBySha[commit.oid] = commit
        parentCommits.addAll(commit.parents)
      }

      // Last commit is a commit which is not a parent of any other commit
      // We start searching from the last hoping for some semblance of order
      val lastCommit = commits.findLast { !parentCommits.contains(it) } ?: error("Could not determine last commit")

      fun ImmutableGraph.Builder<GHCommit>.addCommits(commit: GHCommit) {
        addNode(commit)
        for (parent in commit.parents) {
          val parentCommit = commitsBySha[parent.oid]
          if (parentCommit != null) {
            putEdge(commit, parentCommit)
            addCommits(parentCommit)
          }
        }
      }

      return lastCommit to GraphBuilder
        .directed()
        .allowsSelfLoops(false)
        .immutable<GHCommit>()
        .apply {
          addCommits(lastCommit)
        }.build()
    }
  }
}