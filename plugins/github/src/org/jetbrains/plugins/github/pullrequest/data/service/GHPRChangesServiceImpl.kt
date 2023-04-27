// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph
import com.google.common.graph.Traverser
import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import git4idea.changes.GitCommitShaWithPatches
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitBranchComparisonResultImpl
import git4idea.fetch.GitFetchSupport
import git4idea.remote.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

private typealias DiffSpec = Pair<String?, String>

class GHPRChangesServiceImpl(private val progressManager: ProgressManager,
                             private val project: Project,
                             private val requestExecutor: GithubApiRequestExecutor,
                             private val gitRemote: GitRemoteUrlCoordinates,
                             private val ghRepository: GHRepositoryCoordinates) : GHPRChangesService, Disposable {

  private val patchesLoadingIndicator = EmptyProgressIndicator()

  private val patchesCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(5))
    .executor(ProcessIOExecutorService.INSTANCE)
    .buildAsync(AsyncCacheLoader<DiffSpec, List<FilePatch>> { key, executor ->
      val (ref1, ref2) = key
      if (ref1 == null) {
        loadCommitDiff(ProgressWrapper.wrap(patchesLoadingIndicator), ref2)
      }
      else {
        loadDiff(ProgressWrapper.wrap(patchesLoadingIndicator), ref1, ref2)
      }.thenApplyAsync({ readAllPatches(it) }, executor)
    })

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

  private fun loadCommitDiff(progressIndicator: ProgressIndicator, oid: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.Commits.getDiff(ghRepository, oid))
    }.logError(LOG, "Error occurred while loading diffs for commit $oid")

  private fun loadDiff(progressIndicator: ProgressIndicator, ref1: String, ref2: String): CompletableFuture<String> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it, GithubApiRequests.Repos.Commits.getDiff(ghRepository, ref1, ref2))
    }.logError(LOG, "Error occurred while loading diffs between $ref1 and $ref2")

  override fun loadMergeBaseOid(progressIndicator: ProgressIndicator, baseRefOid: String, headRefOid: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.Commits.compare(ghRepository, baseRefOid, headRefOid)).mergeBaseCommit.sha
    }.logError(LOG, "Error occurred while calculating merge base for $baseRefOid and $headRefOid")

  override fun loadPatch(ref1: String, ref2: String): CompletableFuture<List<FilePatch>> =
    patchesCache.get(ref1 to ref2)

  override fun createChangesProvider(progressIndicator: ProgressIndicator,
                                     baseRef: String,
                                     mergeBaseRef: String,
                                     headRef: String,
                                     commits: Pair<GHCommit, Graph<GHCommit>>): CompletableFuture<GitBranchComparisonResult> {

    return progressManager.submitIOTask(ProgressWrapper.wrap(progressIndicator)) {
      val prPatchesRequest = patchesCache.get(baseRef to headRef)

      val (lastCommit, graph) = commits
      val commitsPatchesRequests = LinkedHashMap<GHCommit, CompletableFuture<List<FilePatch>>>()
      for (commit in Traverser.forGraph(graph).depthFirstPostOrder(lastCommit)) {
        commitsPatchesRequests[commit] = patchesCache.get(null to commit.oid)
      }

      val commitsList = commitsPatchesRequests.map { (commit, request) ->
        val patches = request.joinCancellable()
        GitCommitShaWithPatches(commit.oid, commit.parents.map { it.oid }, patches)
      }
      val prPatches = prPatchesRequest.joinCancellable()
      it.checkCanceled()

      GitBranchComparisonResultImpl(project, gitRemote.repository.root, baseRef, mergeBaseRef, commitsList,
                                    prPatches) as GitBranchComparisonResult
    }.logError(LOG, "Error occurred while building changes from commits")
  }

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
        if (CompletableFutureUtil.isCancellation(e)) throw ProcessCanceledException(e)
        throw CompletableFutureUtil.extractError(e)
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

  override fun dispose() {
    patchesLoadingIndicator.cancel()
  }
}