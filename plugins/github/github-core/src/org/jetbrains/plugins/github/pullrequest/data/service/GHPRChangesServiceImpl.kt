// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.util.ResultUtil.processErrorAndGet
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitCommitShaWithPatches
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitCodeReviewUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.fold
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests.Repos.Commits
import org.jetbrains.plugins.github.api.GithubApiRequests.Repos.PullRequests
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.commit.GHCommitFile
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader.batchesFlow
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.time.Duration

private typealias DiffSpec = Pair<String?, String>

class GHPRChangesServiceImpl(parentCs: CoroutineScope,
                             private val project: Project,
                             private val requestExecutor: GithubApiRequestExecutor,
                             private val gitRemote: GitRemoteUrlCoordinates,
                             private val ghRepository: GHRepositoryCoordinates) : GHPRChangesService {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Default)

  private val patchesCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(5))
    .build<DiffSpec, Deferred<List<FilePatch>>> { spec ->
      val (ref1, ref2) = spec
      if (ref1 == null) {
        cs.async {
          loadCommitDiff(ref2)
        }
      }
      else {
        cs.async {
          val diff = loadDiff(ref1, ref2)
          readAllPatches(diff)
        }
      }
    }

  override suspend fun fetch(refspec: String) =
    runCatching {
      GitCodeReviewUtils.fetch(gitRemote.repository, gitRemote.remote, refspec)
    }.processErrorAndGet {
      LOG.info("Error occurred while fetching \"$refspec\"", it)
    }

  override suspend fun areAllRevisionsFetched(revisions: List<String>): Boolean =
    runCatching {
      GitCodeReviewUtils.testRevisionsExist(gitRemote.repository, revisions)
    }.processErrorAndGet {
      LOG.info("Error occurred while seeking revisions $revisions", it)
    }

  override suspend fun loadCommitsFromApi(pullRequestId: GHPRIdentifier): List<GHCommit> =
    runCatching {
      ApiPageUtil.createGQLPagesFlow {
        requestExecutor.executeSuspend(GHGQLRequests.PullRequest.commits(ghRepository, pullRequestId.number, it))
      }.fold(mutableListOf<GHCommit>()) { acc, value ->
        acc.addAll(value.nodes.map { it.commit })
        acc
      }
    }.processErrorAndGet {
      LOG.info("Error occurred while loading commits for PR ${pullRequestId.number}", it)
    }

  private suspend fun loadCommitDiff(oid: String): List<FilePatch> =
    runCatching {
      val request = GithubApiPagesLoader.Request(Commits.getDiffFiles(ghRepository, oid), Commits::getDiffFiles)
      batchesFlow(requestExecutor, request).foldToList().mapNotNull(::toPatch)
    }.processErrorAndGet {
      LOG.info("Error occurred while loading diffs for commit $oid", it)
    }

  private suspend fun loadDiff(ref1: String, ref2: String): String =
    runCatching {
      requestExecutor.executeSuspend(Commits.getDiff(ghRepository, ref1, ref2))
    }.processErrorAndGet {
      LOG.info("Error occurred while loading diffs between $ref1 and $ref2", it)
    }

  override suspend fun loadMergeBaseOid(baseRefOid: String, headRefOid: String) =
    runCatching {
      requestExecutor.executeSuspend(Commits.compare(ghRepository, baseRefOid, headRefOid)).mergeBaseCommit.sha
    }.processErrorAndGet {
      LOG.info("Error occurred while calculating merge base for $baseRefOid and $headRefOid", it)
    }

  override suspend fun loadPatch(ref1: String, ref2: String): List<FilePatch> =
    patchesCache.get(ref1 to ref2).await()

  override suspend fun createChangesProvider(id: GHPRIdentifier,
                                             baseRef: String,
                                             mergeBaseRef: String,
                                             headRef: String,
                                             commits: List<GHCommit>): GitBranchComparisonResult =
    runCatching {
      coroutineScope {
        val commitsWithPatchesReqs = commits.map { commit ->
          async {
            val patches = patchesCache.get(null to commit.oid).await()
            GitCommitShaWithPatches(commit.oid, commit.parents.map { it.oid }, patches)
          }
        }
        val request = GithubApiPagesLoader.Request(PullRequests.getDiffFiles(ghRepository, id), PullRequests::getDiffFiles)
        val prPatches = batchesFlow(requestExecutor, request).foldToList().mapNotNull(::toPatch)
        val commitsWithPatches = commitsWithPatchesReqs.awaitAll()

        GitBranchComparisonResult.create(project, gitRemote.repository.root, baseRef, mergeBaseRef, commitsWithPatches, prPatches)
      }
    }.processErrorAndGet {
      LOG.info("Error occurred while building changes from commits", it)
    }

  companion object {
    private val LOG = logger<GHPRChangesService>()

    private fun readAllPatches(diffFile: String): List<FilePatch> {
      val reader = PatchReader(diffFile, true)
      reader.parseAllPatches()
      return reader.allPatches
    }

    private fun toPatch(file: GHCommitFile): FilePatch? {
      val beforeFilePath = (file.previousFilename ?: file.filename).takeIf { file.status != GHCommitFile.Status.added }
      val afterFilePath = file.filename.takeIf { file.status != GHCommitFile.Status.removed }
      val filePatch: FilePatch? = if (file.patch == null) {
        val emptyContent = ByteArray(0)
        BinaryFilePatch(
          emptyContent.takeIf { file.status != GHCommitFile.Status.added },
          emptyContent.takeIf { file.status != GHCommitFile.Status.removed }
        )
      }
      else {
        val headerFileBefore = beforeFilePath?.let { "a/$it" } ?: "/dev/null"
        val headerFileAfter = afterFilePath?.let { "b/$it" } ?: "/dev/null"
        val header = "--- $headerFileBefore\n+++ $headerFileAfter\n"
        val patch = header + file.patch
        readAllPatches(patch).filterIsInstance<TextFilePatch>().firstOrNull()?.apply {
          val fileStatus = when (file.status) {
            GHCommitFile.Status.added -> FileStatus.ADDED
            GHCommitFile.Status.removed -> FileStatus.DELETED
            else -> FileStatus.MODIFIED
          }
          setFileStatus(fileStatus)
        }
      }?.apply {
        beforeName = beforeFilePath
        afterName = afterFilePath
      }
      return filePatch
    }
  }
}