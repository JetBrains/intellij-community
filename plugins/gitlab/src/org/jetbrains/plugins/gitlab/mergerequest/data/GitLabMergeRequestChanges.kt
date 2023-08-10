// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitBranchComparisonResultImpl
import git4idea.changes.GitCommitShaWithPatches
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.fetch.GitFetchSupport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.nio.charset.StandardCharsets

interface GitLabMergeRequestChanges {
  val commits: List<GitLabCommitDTO>

  suspend fun getParsedChanges(): GitBranchComparisonResult

  suspend fun ensureAllRevisionsFetched()
}

private val LOG = logger<GitLabMergeRequestChanges>()

class GitLabMergeRequestChangesImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val projectMapping: GitLabProjectMapping,
  private val mergeRequestDetails: GitLabMergeRequestFullDetails
) : GitLabMergeRequestChanges {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val glProject = projectMapping.repository

  override val commits: List<GitLabCommitDTO> = mergeRequestDetails.commits.asReversed()

  private val parsedChanges = cs.async(start = CoroutineStart.LAZY) {
    loadChanges(commits)
  }

  override suspend fun getParsedChanges(): GitBranchComparisonResult = parsedChanges.await()

  private suspend fun loadChanges(commits: List<GitLabCommitDTO>): GitBranchComparisonResult {
    val repository = projectMapping.remote.repository
    val baseSha = mergeRequestDetails.diffRefs.startSha
    val mergeBaseSha = mergeRequestDetails.diffRefs.baseSha ?: error("Missing merge base revision")

    val commitsWithPatches = withContext(Dispatchers.IO) {
      coroutineScope {
        commits.map { commit ->
          async {
            val commitWithParents = api.rest.loadCommit(glProject, commit.sha).body()!!
            val patches = ApiPageUtil.createPagesFlowByLinkHeader(getCommitDiffsURI(glProject, commit.sha)) {
              api.rest.loadCommitDiffs(glProject.serverPath, it)
            }.map { it.body() }.foldToList(GitLabDiffDTO::toPatch)
            GitCommitShaWithPatches(commit.sha, commitWithParents.parentIds, patches)
          }
        }.awaitAll()
      }
    }
    val headPatches = withContext(Dispatchers.IO) {
      ApiPageUtil.createPagesFlowByLinkHeader(getMergeRequestDiffsURI(glProject, mergeRequestDetails)) {
        api.rest.loadMergeRequestDiffs(glProject.serverPath, it)
      }.map { it.body() }.foldToList(GitLabDiffDTO::toPatch)
    }
    return GitBranchComparisonResultImpl(repository.project, repository.root, baseSha, mergeBaseSha, commitsWithPatches, headPatches)
  }

  override suspend fun ensureAllRevisionsFetched() {
    val revsToCheck = commits.map { it.sha }.toMutableList()
    mergeRequestDetails.diffRefs.baseSha?.also {
      revsToCheck.add(it)
    }
    withContext(Dispatchers.IO) {
      if (areAllRevisionPresent(revsToCheck)) return@withContext

      fetch(mergeRequestDetails.targetBranch)
      fetch("""merge-requests/${mergeRequestDetails.iid}/head:""")

      check(areAllRevisionPresent(revsToCheck)) { "Failed to fetch some revisions" }
    }
  }

  private suspend fun areAllRevisionPresent(revisions: List<String>): Boolean {
    return coroutineToIndicator {
      val h = GitLineHandler(project, projectMapping.remote.repository.root, GitCommand.CAT_FILE)
      h.setSilent(true)
      h.addParameters("--batch-check=%(objecttype)")
      h.endOptions()
      h.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(revisions, StandardCharsets.UTF_8))

      !Git.getInstance().runCommand(h).getOutputOrThrow().contains("missing")
    }
  }

  private suspend fun fetch(refspec: String) {
    coroutineToIndicator {
      val remote = projectMapping.remote
      GitFetchSupport.fetchSupport(project)
        .fetch(remote.repository, remote.remote, refspec).throwExceptionIfFailed()
    }
  }
}

private fun GitLabDiffDTO.toPatch(): TextFilePatch {
  val aPath = oldPath.takeIf { !newFile }?.let { "a/$it" } ?: "/dev/null"
  val bPath = newPath.takeIf { !deletedFile }?.let { "b/$it" } ?: "/dev/null"
  val header = """--- $aPath
+++ $bPath
"""

  val patchReader = PatchReader(header + diff)
  return patchReader.readTextPatches().firstOrNull() ?: throw IllegalStateException("Could not parse diff $this")
}