// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitCommitShaWithPatches
import git4idea.changes.filePath
import git4idea.remote.hosting.GitCodeReviewUtils
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabMergeRequestChanges {
  /**
   * List of merge request commits
   */
  val commits: Deferred<List<GitLabCommit>>

  /**
   * Load and parse changes diffs
   */
  suspend fun getParsedChanges(): GitBranchComparisonResult

  /**
   * Check that all merge request revisions are fetched and fetch the missing revisions
   */
  suspend fun ensureAllRevisionsFetched()
}

fun GitBranchComparisonResult.findLatestCommitWithChangesTo(gitRepository: GitRepository, filePath: FilePath): String? {
  val relativePath = VcsFileUtil.relativePath(gitRepository.root, filePath)
  return commits.lastOrNull { commit -> commit.patches.any { it.filePath == relativePath } }?.sha
}

private val LOG = logger<GitLabMergeRequestChanges>()

class GitLabMergeRequestChangesImpl(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  private val projectMapping: GitLabProjectMapping,
  private val mergeRequestDetails: GitLabMergeRequestFullDetails
) : GitLabMergeRequestChanges {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val glProject = projectMapping.repository

  override val commits: Deferred<List<GitLabCommit>> = cs.async {
    if (glMetadata != null && glMetadata.version < GitLabVersion(14, 7)) {
      val initialURI = api.getMergeRequestCommitsURI(glProject, mergeRequestDetails.iid)
      return@async ApiPageUtil.createPagesFlowByLinkHeader(initialURI) { uri -> api.rest.loadMergeRequestCommits(uri) }
        .map { it.body() ?: emptyList() }
        .foldToList(GitLabCommit.Companion::fromRestDTO)
        .asReversed()
    }

    ApiPageUtil.createGQLPagesFlow { pagination -> api.graphQL.loadMergeRequestCommits(glProject, mergeRequestDetails.iid, pagination) }
      .map { page -> page.nodes }
      .foldToList(GitLabCommit.Companion::fromGraphQLDTO)
      .asReversed()
  }

  private val parsedChanges = cs.async(start = CoroutineStart.LAZY) {
    loadChanges(commits.await())
  }

  override suspend fun getParsedChanges(): GitBranchComparisonResult = parsedChanges.await()

  private suspend fun loadChanges(commits: List<GitLabCommit>): GitBranchComparisonResult {
    val repository = projectMapping.remote.repository
    val diffRefs = mergeRequestDetails.diffRefs ?: error("Missing diff refs")
    val baseSha = diffRefs.startSha
    val mergeBaseSha = diffRefs.baseSha ?: error("Missing merge base revision")

    val commitsWithPatches = withContext(Dispatchers.IO) {
      coroutineScope {
        commits.map { commit ->
          async {
            val commitWithParents = api.rest.loadCommit(glProject, commit.sha).body()!!
            val patches = ApiPageUtil.createPagesFlowByLinkHeader(getCommitDiffsURI(glProject, commit.sha)) {
              api.rest.loadCommitDiffs(it)
            }.map { it.body() }.foldToList(GitLabDiffDTO::toPatch)
            GitCommitShaWithPatches(commit.sha, commitWithParents.parentIds, patches)
          }
        }.awaitAll()
      }
    }
    val headPatches = withContext(Dispatchers.IO) {
      if (api.getMetadata().version < GitLabVersion(15, 7)) {
        ApiPageUtil.createPagesFlowByLinkHeader(api.getMergeRequestChangesURI(glProject, mergeRequestDetails.iid)) {
          api.rest.loadMergeRequestChanges(it)
        }.map { it.body().changes }.foldToList(GitLabDiffDTO::toPatch)
      }
      else {
        // doesn't send back Link headers...
        ApiPageUtil.createPagesFlowByPagination { page ->
          api.rest.loadMergeRequestDiffs(api.getMergeRequestDiffsURI(glProject, mergeRequestDetails.iid, page))
        }.map { it.body() }.foldToList(GitLabDiffDTO::toPatch)
      }
    }
    return GitBranchComparisonResult.create(repository.project, repository.root, baseSha, mergeBaseSha, commitsWithPatches, headPatches)
  }

  override suspend fun ensureAllRevisionsFetched() {
    val revsToCheck = commits.await().map { it.sha }.toMutableList()
    mergeRequestDetails.diffRefs?.baseSha?.also {
      revsToCheck.add(it)
    }

    if (GitCodeReviewUtils.testRevisionsExist(projectMapping.gitRepository, revsToCheck)) return

    GitCodeReviewUtils.fetch(projectMapping.gitRepository, projectMapping.gitRemote, mergeRequestDetails.targetBranch)
    GitCodeReviewUtils.fetch(projectMapping.gitRepository, projectMapping.gitRemote, """merge-requests/${mergeRequestDetails.iid}/head:""")

    check(GitCodeReviewUtils.testRevisionsExist(projectMapping.gitRepository, revsToCheck)) { "Failed to fetch some revisions" }
  }
}

private fun GitLabDiffDTO.toPatch(): TextFilePatch {
  val beforeFilePath = oldPath.takeIf { !newFile }
  val afterFilePath = newPath.takeIf { !deletedFile }
  val headerFileBefore = beforeFilePath?.let { "a/$it" } ?: "/dev/null"
  val headerFileAfter = afterFilePath?.let { "b/$it" } ?: "/dev/null"
  val header = "--- $headerFileBefore\n+++ $headerFileAfter\n"

  val fileStatus = when {
    newFile -> FileStatus.ADDED
    deletedFile -> FileStatus.DELETED
    else -> FileStatus.MODIFIED
  }

  val patchReader = PatchReader(header + diff)
  return patchReader.readTextPatches().firstOrNull()?.apply {
    beforeName = beforeFilePath
    afterName = afterFilePath
    setFileStatus(fileStatus)
  } ?: throw IllegalStateException("Could not parse diff $this")
}