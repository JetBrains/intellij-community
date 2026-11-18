// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * The local git repository of a GitLab project, as the merge request data and the review in editors need it.
 */
@ApiStatus.Internal
interface GitLabLocalRepository {
  /**
   * Emits when the local repository changes.
   */
  val changes: Flow<Unit>

  /**
   * Builds the path of a changed file from a path relative to the repository root.
   */
  fun filePath(relativePath: String): FilePath

  /**
   * Builds the revision of a change from a commit SHA.
   */
  fun revisionNumber(sha: String): ShortVcsRevisionNumber

  /**
   * Whether the local branch of the merge request has the head that the server knows, or `null` when this is unknown.
   */
  fun isInSync(details: GitLabMergeRequestFullDetails): Boolean?

  /**
   * Fetches the [revisions] of the merge request, when the repository does not have them.
   */
  suspend fun ensureRevisionsFetched(details: GitLabMergeRequestFullDetails, revisions: List<String>)

  /**
   * Reads the patches between [baseRev] and [headRev] from the repository.
   *
   * @param paths limits the patches to these paths, relative to the repository root
   * @throws com.intellij.openapi.vcs.VcsException when git fails
   * @throws com.intellij.openapi.diff.impl.patch.PatchSyntaxException when the output of git is not a patch
   */
  suspend fun loadPatches(baseRev: String, headRev: String, paths: List<String>): List<TextFilePatch>

  /**
   * Whether the local branch misses some of the [commits] of the merge request, re-emitted when the repository changes.
   * The flow emits `null` while this is unknown.
   */
  fun hasIncomingCommits(commits: List<String>): Flow<ComputedResult<Boolean>?>

  /**
   * Fetches the source branch of the merge request and checks it out.
   */
  suspend fun checkoutBranch(details: GitLabMergeRequestFullDetails)

  /**
   * The path of [file] in the repository, or `null` when [file] is not a file of the repository.
   */
  suspend fun findFilePath(file: VirtualFile): FilePath?

  /**
   * The content of the file after [change], with the line separators converted to `\n`.
   */
  suspend fun loadContentAfter(change: RefComparisonChange): String

  /**
   * Whether the project is a linked git worktree. The user can delete this worktree after a review submit.
   */
  suspend fun isLinkedWorktree(): Boolean

  /**
   * Starts to delete the linked git worktree of the project. The deletion continues after the project closes.
   */
  suspend fun deleteWorktree()
}
