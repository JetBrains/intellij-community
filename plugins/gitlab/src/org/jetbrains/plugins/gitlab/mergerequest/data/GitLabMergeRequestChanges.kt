// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.withInitial
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.util.childScope
import git4idea.branch.GitBranchSyncStatus
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitBranchComparisonResultImpl
import git4idea.changes.GitCommitShaWithPatches
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.fetch.GitFetchSupport
import git4idea.remote.hosting.changesSignalFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.api.getMetadata
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.nio.charset.StandardCharsets

interface GitLabMergeRequestChanges {
  /**
   * List of merge request commits
   */
  val commits: List<GitLabCommit>

  /**
   * State of remote<->local repository sync (to the best of out knowledge)
   */
  val localRepositorySyncStatus: Flow<GitBranchSyncStatus?>

  /**
   * Load and parse changes diffs
   */
  suspend fun getParsedChanges(): GitBranchComparisonResult

  /**
   * Check that all merge request revisions are fetched and fetch the missing revisions
   */
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

  override val commits: List<GitLabCommit> = mergeRequestDetails.commits.asReversed()

  override val localRepositorySyncStatus: StateFlow<GitBranchSyncStatus?> = channelFlow {
    val repository = projectMapping.remote.repository
    repository.changesSignalFlow().withInitial(Unit).map { repository.currentRevision }.distinctUntilChanged().collectLatest { currentRev ->
      if (currentRev != null) {
        val state = checkSyncState(currentRev)
        send(state)
      }
      else {
        send(GitBranchSyncStatus(true, true))
      }
    }
  }.stateIn(cs, SharingStarted.Lazily, null)

  private suspend fun checkSyncState(currentRev: String): GitBranchSyncStatus? {
    val headSha = mergeRequestDetails.diffRefs?.headSha ?: return null
    if (currentRev == headSha) return GitBranchSyncStatus.SYNCED
    if (commits.mapTo(mutableSetOf()) { it.sha }.contains(currentRev)) return GitBranchSyncStatus(true, false)
    if (testCurrentBranchContains(headSha)) return GitBranchSyncStatus(false, true)
    return GitBranchSyncStatus(true, true)
  }

  private val parsedChanges = cs.async(start = CoroutineStart.LAZY) {
    loadChanges(commits)
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
        ApiPageUtil.createPagesFlowByLinkHeader(api.getMergeRequestDiffsURI(glProject, mergeRequestDetails.iid)) {
          api.rest.loadMergeRequestDiffs(it)
        }.map { it.body() }.foldToList(GitLabDiffDTO::toPatch)
      }
    }
    return GitBranchComparisonResultImpl(repository.project, repository.root, baseSha, mergeBaseSha, commitsWithPatches, headPatches)
  }

  override suspend fun ensureAllRevisionsFetched() {
    val revsToCheck = commits.map { it.sha }.toMutableList()
    mergeRequestDetails.diffRefs?.baseSha?.also {
      revsToCheck.add(it)
    }
    withContext(Dispatchers.IO) {
      if (areAllRevisionsPresent(revsToCheck)) return@withContext

      fetch(mergeRequestDetails.targetBranch)
      fetch("""merge-requests/${mergeRequestDetails.iid}/head:""")

      check(areAllRevisionsPresent(revsToCheck)) { "Failed to fetch some revisions" }
    }
  }

  private suspend fun areAllRevisionsPresent(revisions: List<String>): Boolean =
    coroutineToIndicator {
      val h = GitLineHandler(project, projectMapping.remote.repository.root, GitCommand.CAT_FILE)
      h.setSilent(true)
      h.addParameters("--batch-check=%(objecttype)")
      h.endOptions()
      h.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(revisions, StandardCharsets.UTF_8))

      !Git.getInstance().runCommand(h).getOutputOrThrow().contains("missing")
    }

  private suspend fun testCurrentBranchContains(sha: String): Boolean =
    coroutineToIndicator {
      val h = GitLineHandler(project, projectMapping.remote.repository.root, GitCommand.MERGE_BASE)
      h.setSilent(true)
      h.addParameters("--is-ancestor", sha, "HEAD")
      Git.getInstance().runCommand(h).success()
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