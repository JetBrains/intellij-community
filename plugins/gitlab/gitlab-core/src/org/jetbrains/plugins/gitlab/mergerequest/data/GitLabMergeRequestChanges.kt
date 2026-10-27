// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapConcurrentOrdered
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.api.dto.filePath
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadCommit
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadCommitDiffs
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestCommits
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiffs
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath

private val LOG = logger<GitLabMergeRequestChanges>()
internal const val MAX_CONCURRENT_COMMITS = 5

interface GitLabMergeRequestChanges {
  /**
   * Load the list of merge request commits
   */
  suspend fun getCommits(): List<GitLabCommit>

  /**
   * Load and parse changes diffs
   */
  suspend fun getParsedChanges(): GitLabMergeRequestParsedChanges

  /**
   * Check that all merge request revisions are fetched and fetch the missing revisions
   */
  suspend fun ensureAllRevisionsFetched()
}

/**
 * The parsed changes of a merge request, and the reason the comments are off, per change.
 */
interface GitLabMergeRequestParsedChanges : GitBranchComparisonResult {
  /**
   * The reason the server sent no diff body for the file of [change].
   *
   * @return `null` when the file is commentable, and when an empty diff is correct, because only the name or the mode changed
   */
  fun getEmptyPatchReason(change: RefComparisonChange): EmptyPatchReason?

  enum class EmptyPatchReason {
    /**
     * Changed file is too large [GitLabDiffDTO.tooLarge] for this server
     */
    TOO_LARGE,

    /**
     * Server has collapsed the file diff [GitLabDiffDTO.collapsed] and we could not build the diff from the local repository
     */
    COLLAPSED_NO_LOCAL,

    /**
     * The body is empty and nothing explains it - it's either some GitLab quirk or en encoding change, and we couldn't fall back to local
     */
    UNKNOWN,
  }
}

internal class GitLabMergeRequestParsedChangesImpl(
  private val delegate: GitBranchComparisonResult,
  headPatchesWithEmptyReasons: List<PatchWithEmptyReason>,
) : GitLabMergeRequestParsedChanges, GitBranchComparisonResult by delegate {
  private val emptyHeadDiffReasons: Map<String, GitLabMergeRequestParsedChanges.EmptyPatchReason> =
    headPatchesWithEmptyReasons.mapNotNull { item ->
      item.emptyReason?.let { reason -> item.patch.filePath to reason }
    }.toMap()

  override fun getEmptyPatchReason(change: RefComparisonChange): GitLabMergeRequestParsedChanges.EmptyPatchReason? {
    if (emptyHeadDiffReasons.isEmpty()) return null
    val patch = delegate.patchesByChange[change]?.patch ?: return null
    return emptyHeadDiffReasons[patch.filePath]
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabMergeRequestParsedChangesImpl) return false

    if (delegate != other.delegate) return false
    if (emptyHeadDiffReasons != other.emptyHeadDiffReasons) return false

    return true
  }

  override fun hashCode(): Int {
    var result = delegate.hashCode()
    result = 31 * result + emptyHeadDiffReasons.hashCode()
    return result
  }
}

fun GitBranchComparisonResult.findLatestCommitWithChangesTo(gitRepository: GitRepository, filePath: FilePath): String? {
  val relativePath = VcsFileUtil.relativePath(gitRepository.root, filePath)
  return commits.lastOrNull { commit -> commit.patches.any { it.filePath == relativePath } }?.sha
}

/**
 * Finds the SHA that the viewed state of [change] uses.
 * The viewed state describes only the cumulative diff, so the result is `null` for a change of a single commit.
 */
internal fun GitBranchComparisonResult.findViewedStateSha(gitRepository: GitRepository, change: RefComparisonChange): String? {
  if (patchesByChange[change]?.isCumulative != true) return null
  return findLatestCommitWithChangesTo(gitRepository, change.filePath)
}

internal suspend fun GitLabMergeRequestChanges.loadRevisionsAndParseChanges(): GitLabMergeRequestParsedChanges =
  coroutineScope {
    launch {
      ensureAllRevisionsFetched()
    }
    getParsedChanges()
  }

internal class GitLabMergeRequestChangesImpl(
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
      return@async api.rest.loadMergeRequestCommits(projectId, mergeRequestDetails.iid)
        .map(GitLabCommit::fromRestDTO)
        .asReversed()
    }

    ApiPageUtil.createGQLPagesFlow { pagination ->
      api.graphQL.loadMergeRequestCommits(projectPath, mergeRequestDetails.iid, pagination)
    }
      .map { page -> page.nodes }
      .foldToList(GitLabCommit::fromGraphQLDTO)
      .asReversed()
  }

  override suspend fun getCommits(): List<GitLabCommit> = commits.await()

  private val parsedChanges = cs.async(start = CoroutineStart.LAZY) {
    loadChanges(commits.await())
  }

  override suspend fun getParsedChanges(): GitLabMergeRequestParsedChangesImpl = parsedChanges.await()

  private suspend fun loadChanges(commits: List<GitLabCommit>): GitLabMergeRequestParsedChangesImpl {
    val diffRefs = mergeRequestDetails.diffRefs ?: error("Missing diff refs")
    val baseSha = diffRefs.startSha
    val headSha = diffRefs.headSha
    val mergeBaseSha = diffRefs.baseSha ?: error("Missing merge base revision")

    val commitsWithPatches = withContext(Dispatchers.IO) {
      commits.mapConcurrentOrdered(MAX_CONCURRENT_COMMITS) { commit ->
        val commitWithParents = api.rest.loadCommit(projectId, commit.sha)
        val patches = api.rest.loadCommitDiffs(projectId, commit.sha).map(GitLabDiffDTO::toPatch)
        GitCommitShaWithPatches(commit.sha, commitWithParents.parentIds, patches)
      }
    }.onEach { commit ->
      commit.patches.forEach {
        if (it is TextFilePatch && it.hunks.isEmpty()) {
          LOG.warn("""Empty patch for file change [${it.beforeName} -> ${it.afterName}] in commit ${commit.sha} in MR ${mergeRequestDetails.iid} with refs ${mergeRequestDetails.diffRefs}.""")
        }
      }
    }
    val headDiffs = withContext(Dispatchers.IO) {
      if (api.getMetadata().version < GitLabVersion(15, 7)) {
        api.rest.loadMergeRequestChanges(projectId, mergeRequestDetails.iid)
      }
      else {
        api.rest.loadMergeRequestDiffs(projectId, mergeRequestDetails.iid)
      }
    }
    val location = "MR ${mergeRequestDetails.iid}"
    // repair the repairable (not too large) unexpected empty diffs with the local ones
    val localPatches = loadLocalPatches(mergeBaseSha, headSha, headDiffs.filter { it.needsLocalPatch }, location)
    val headPatches = processDiffs(headDiffs, localPatches, location)
    val comparisonResult = GitBranchComparisonResult.create(project, gitRemoteUrlCoordinates.repository.root, baseSha, mergeBaseSha,
                                                            commitsWithPatches, headPatches.map { it.patch })
    return GitLabMergeRequestParsedChangesImpl(comparisonResult, headPatches)
  }

  /**
   * Reads the patches of the [apiDiffs] from the local repository.
   * Each local patch gets the names and the status that the server reported.
   * A failure keeps the change list, so the caller degrades to a patch with no hunk.
   *
   * @return the local patch per server diff. A diff with no local patch has no entry.
   */
  private suspend fun loadLocalPatches(
    baseRev: String,
    headRev: String,
    apiDiffs: Collection<GitLabDiffDTO>,
    location: String,
  ): Map<GitLabDiffDTO, TextFilePatch> {
    if (apiDiffs.isEmpty() || baseRev == headRev) return emptyMap()

    val repository = gitRemoteUrlCoordinates.repository
    val pathspec = apiDiffs.flatMap { it.localDiffPaths }.distinct()
    val localPatches = try {
      ensureAllRevisionsFetched()
      GitCodeReviewUtils.loadPatches(repository, baseRev, headRev, pathspec)
    }
    catch (e: VcsException) {
      LOG.warn("Failed to read the diff of $baseRev..$headRev in ${repository.root.name}", e)
      return emptyMap()
    }
    catch (e: PatchSyntaxException) {
      LOG.warn("Failed to parse the diff of $baseRev..$headRev in ${repository.root.name}", e)
      return emptyMap()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to rebuild the collapsed diff of $baseRev..$headRev in ${repository.root.name} in $location from the local repository",
               e)
      return emptyMap()
    }

    val localPatchesByPath = localPatches.associateBy { patch -> patch.filePath }
    return apiDiffs.mapNotNull { diffDto ->
      localPatchesByPath[diffDto.filePath]?.let { localPatch -> diffDto to localPatch.setNamesAndStatusFrom(diffDto) }
    }.toMap()
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

  companion object {
    /**
     * Parses the server [diffs]. An empty diff takes its patch from [localPatches] when it has one there.
     * A file that still has no diff body gets the reason for it.
     *
     * @param localPatches the local patch per server diff, with the names and the status of the server
     * @param location names the diff in the log
     */
    @VisibleForTesting
    internal fun processDiffs(
      diffs: List<GitLabDiffDTO>,
      localPatches: Map<GitLabDiffDTO, TextFilePatch>,
      location: String,
    ): List<PatchWithEmptyReason> {
      return diffs.map { diffDto ->
        if (diffDto.diff.isNotEmpty()) {
          return@map PatchWithEmptyReason(diffDto.toPatch())
        }

        val localPatch = localPatches[diffDto]
        if (localPatch != null) {
          return@map PatchWithEmptyReason(localPatch)
        }

        val emptyPatchReason = when {
          diffDto.tooLarge == true -> GitLabMergeRequestParsedChanges.EmptyPatchReason.TOO_LARGE
          diffDto.collapsed == true -> GitLabMergeRequestParsedChanges.EmptyPatchReason.COLLAPSED_NO_LOCAL
          else -> GitLabMergeRequestParsedChanges.EmptyPatchReason.UNKNOWN
        }
        LOG.warn("The GitLab server sent no diff body for [${diffDto.oldPath} -> ${diffDto.newPath}] in $location. The reason is $emptyPatchReason.")
        PatchWithEmptyReason(diffDto.toPatch(), emptyPatchReason)
      }
    }
  }
}

private fun GitLabDiffDTO.toPatch(): TextFilePatch {
  val beforeFilePath = oldPath.takeIf { !newFile }
  val afterFilePath = newPath.takeIf { !deletedFile }
  val headerFileBefore = beforeFilePath?.let { "a/$it" } ?: "/dev/null"
  val headerFileAfter = afterFilePath?.let { "b/$it" } ?: "/dev/null"
  val header = "--- $headerFileBefore\n+++ $headerFileAfter\n"

  val patchReader = PatchReader(header + diff)
  return patchReader.readTextPatches().firstOrNull()?.setNamesAndStatusFrom(this)
         ?: throw IllegalStateException("Could not parse diff $this")
}

/**
 * Puts the names and the status that the server reported on the patch.
 * The local repository can name a file differently, so the server always wins.
 */
private fun TextFilePatch.setNamesAndStatusFrom(dto: GitLabDiffDTO): TextFilePatch = apply {
  beforeName = dto.oldPath.takeIf { !dto.newFile }
  afterName = dto.newPath.takeIf { !dto.deletedFile }
  setFileStatus(when {
                  dto.newFile -> FileStatus.ADDED
                  dto.deletedFile -> FileStatus.DELETED
                  else -> FileStatus.MODIFIED
                })
}

/**
 * The pathspec that makes `git diff -M` report the same file as the server.
 * Git pairs the two sides of a rename only when the pathspec holds both paths.
 * With the new path only, git reports an added file, and the patch adds every line.
 */
@get:VisibleForTesting
internal val GitLabDiffDTO.localDiffPaths: List<String>
  get() = listOfNotNull(oldPath.takeIf { !newFile }, newPath.takeIf { !deletedFile }).distinct()

/**
 * True when the server sent no diff body for a file that is not too large.
 * The server keeps the diff of such a file, so the local repository can build the same diff.
 * A server older than 18.4 sends no [GitLabDiffDTO.tooLarge] flag, so its empty diff gets no local patch.
 */
@get:VisibleForTesting
internal val GitLabDiffDTO.needsLocalPatch: Boolean
  get() = diff.isEmpty() && tooLarge == false

internal data class PatchWithEmptyReason(
  val patch: TextFilePatch,
  val emptyReason: GitLabMergeRequestParsedChanges.EmptyPatchReason? = null,
)

