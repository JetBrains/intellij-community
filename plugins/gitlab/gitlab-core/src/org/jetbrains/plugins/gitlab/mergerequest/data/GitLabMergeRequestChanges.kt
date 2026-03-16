// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.childScope
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitCommitShaWithPatches
import git4idea.changes.filePath
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitCodeReviewUtils
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getCommitDiffsURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestChangesURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestCommitsURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestDiffsURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadCommit
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadCommitDiffs
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestCommits
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiffs
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath

private val LOG = logger<GitLabMergeRequestChanges>()

interface GitLabMergeRequestChanges {
  /**
   * Load the list of merge request commits
   */
  suspend fun getCommits(): List<GitLabCommit>

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

internal suspend fun GitLabMergeRequestChanges.loadRevisionsAndParseChanges(): GitBranchComparisonResult =
  coroutineScope {
    launch {
      ensureAllRevisionsFetched()
    }
    getParsedChanges()
  }

class GitLabMergeRequestChangesImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val projectId: String,
  private val projectPath: GitLabProjectPath,
  private val gitRemoteUrlCoordinates: GitRemoteUrlCoordinates,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  private val mergeRequestDetails: GitLabMergeRequestFullDetails,
) : GitLabMergeRequestChanges {

  private val cs = parentCs.childScope(this::class)

  private val commits: Deferred<List<GitLabCommit>> = cs.async {
    if (glMetadata != null && glMetadata.version < GitLabVersion(14, 7)) {
      val initialURI = api.rest.getMergeRequestCommitsURI(projectId, mergeRequestDetails.iid)
      return@async ApiPageUtil.createPagesFlowByLinkHeader(initialURI) { uri -> api.rest.loadMergeRequestCommits(uri) }
        .map { it.body() ?: emptyList() }
        .foldToList(GitLabCommit.Companion::fromRestDTO)
        .asReversed()
    }

    ApiPageUtil.createGQLPagesFlow { pagination ->
      api.graphQL.loadMergeRequestCommits(projectPath, mergeRequestDetails.iid, pagination)
    }
      .map { page -> page.nodes }
      .foldToList(GitLabCommit.Companion::fromGraphQLDTO)
      .asReversed()
  }

  override suspend fun getCommits(): List<GitLabCommit> = commits.await()

  private val parsedChanges = cs.async(start = CoroutineStart.LAZY) {
    loadChanges(commits.await())
  }

  override suspend fun getParsedChanges(): GitBranchComparisonResult = parsedChanges.await()

  private suspend fun loadChanges(commits: List<GitLabCommit>): GitBranchComparisonResult {
    val diffRefs = mergeRequestDetails.diffRefs ?: error("Missing diff refs")
    val baseSha = diffRefs.startSha
    val mergeBaseSha = diffRefs.baseSha ?: error("Missing merge base revision")

    val commitsWithPatches = withContext(Dispatchers.IO) {
      coroutineScope {
        commits.map { commit ->
          async {
            val commitWithParents = api.rest.loadCommit(projectId, commit.sha).body()!!
            val patches = ApiPageUtil.createPagesFlowByLinkHeader(api.rest.getCommitDiffsURI(projectId, commit.sha)) {
              api.rest.loadCommitDiffs(it)
            }.map { it.body() }.foldToList(GitLabDiffDTO::toPatch)
            GitCommitShaWithPatches(commit.sha, commitWithParents.parentIds, patches)
          }
        }.awaitAll()
      }
    }.apply {
      forEach { commit ->
        commit.patches.forEach {
          if (it is TextFilePatch && it.hunks.isEmpty()) {
            LOG.warn("""Empty patch for file change [${it.beforeName} -> ${it.afterName}] in commit ${commit.sha} in MR ${mergeRequestDetails.iid} with refs ${mergeRequestDetails.diffRefs}.""")
          }
        }
      }
    }
    val headPatches = withContext(Dispatchers.IO) {
      if (api.getMetadata().version < GitLabVersion(15, 7)) {
        ApiPageUtil.createPagesFlowByLinkHeader(api.rest.getMergeRequestChangesURI(projectId, mergeRequestDetails.iid)) {
          api.rest.loadMergeRequestChanges(it)
        }.map { it.body().changes }.foldToList(GitLabDiffDTO::toPatch)
      }
      else {
        // doesn't send back Link headers...
        ApiPageUtil.createPagesFlowByPagination { page ->
          api.rest.loadMergeRequestDiffs(api.rest.getMergeRequestDiffsURI(projectId, mergeRequestDetails.iid, page))
        }.map { it.body() }.foldToList(GitLabDiffDTO::toPatch)
      }
    }.apply {
      forEach {
        if (it.hunks.isEmpty()) {
          LOG.warn("""Empty patch for file change [${it.beforeName} -> ${it.afterName}] in MR ${mergeRequestDetails.iid} with refs ${mergeRequestDetails.diffRefs}.""")
        }
      }
    }
    return GitBranchComparisonResult.create(project, gitRemoteUrlCoordinates.repository.root, baseSha, mergeBaseSha, commitsWithPatches, headPatches)
  }

  override suspend fun ensureAllRevisionsFetched() {
    val revsToCheck = commits.await().map { it.sha }.toMutableList()
    mergeRequestDetails.diffRefs?.baseSha?.also {
      revsToCheck.add(it)
    }

    if (GitCodeReviewUtils.testRevisionsExist(gitRemoteUrlCoordinates.repository, revsToCheck)) return

    GitCodeReviewUtils.fetch(gitRemoteUrlCoordinates.repository, gitRemoteUrlCoordinates.remote, mergeRequestDetails.targetBranch)
    GitCodeReviewUtils.fetch(gitRemoteUrlCoordinates.repository, gitRemoteUrlCoordinates.remote,
                             """merge-requests/${mergeRequestDetails.iid}/head:""")

    check(GitCodeReviewUtils.testRevisionsExist(gitRemoteUrlCoordinates.repository, revsToCheck)) {
      "Failed to fetch some revisions"
    }
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