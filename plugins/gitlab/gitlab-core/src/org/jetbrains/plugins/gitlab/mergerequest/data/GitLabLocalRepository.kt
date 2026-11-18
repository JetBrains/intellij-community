// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * The local git repository of a GitLab project, as the merge request data needs it.
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
}
