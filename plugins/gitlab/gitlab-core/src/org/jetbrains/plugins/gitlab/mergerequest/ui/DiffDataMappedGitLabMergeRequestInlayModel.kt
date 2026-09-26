// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.combineStates
import com.intellij.collaboration.async.flatMapLatestEach
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.util.RefComparisonChange
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.filePath

/**
 * Inlays go through several stages of mapping the original line-based location of a note on GitLab
 * to the final location in the diff.
 *
 *  1. A comment arrives with raw line-based locations from the GitLab API.
 *     These locations indicate that the comment was placed on a certain line *after a certain commit*.
 *  2. **The comment is matched with diff data - the changes that are applied by commits in the merge request.**
 *     **This data is used to map the original line number in some commit to a line number in a specific target commit.**
 *     **The result is a line number that is either before a series of commits (LEFT) or after that series of commits (RIGHT).**
 *  3. The line number is then used to display the comment inside an editor; either a diff editor or a regular editor.
 *     To show comments correctly, we need to map them to an offset within the editor, which may or may not directly correspond
 *     with the line number from 2.
 *
 * This interface represents inlays that are in the second (2) stage of mapping.
 * Diff data is expected to be known to those inlays ([diffData]).
 */
@ApiStatus.Internal
interface DiffDataMappedGitLabMergeRequestInlayModel {
  val diffData: StateFlow<DiffData?>

  data class DiffData(
    val change: RefComparisonChange,
    val diffData: GitTextFilePatchWithHistory,
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
internal inline fun <reified T : DiffDataMappedGitLabMergeRequestInlayModel> Flow<Collection<T>>.filterInFile(change: RefComparisonChange): Flow<Collection<T>> =
  flatMapLatestEach { value ->
    value.diffData.map { diffData -> if (diffData?.change == change) value else null }.distinctUntilChanged()
  }.map {
    it.filterNotNull()
  }

internal fun createDiffDataFlow(
  position: GitLabNotePosition,
  changesState: StateFlow<Map<RefComparisonChange, GitTextFilePatchWithHistory>?>,
): StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?> =
  changesState.mapState { changesOrNull ->
    val sha = position.sha
    val filePath = position.filePath

    val changes = changesOrNull ?: return@mapState null

    changes.mapNotNull { (change, diffData) ->
      if (diffData.contains(sha, filePath))
        DiffDataMappedGitLabMergeRequestInlayModel.DiffData(change, diffData)
      else null
    }.lastOrNull()
  }

internal fun createDiffDataFlow(
  positionState: StateFlow<GitLabNotePosition?>,
  changesState: StateFlow<Map<RefComparisonChange, GitTextFilePatchWithHistory>?>,
): StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?> =
  combineStates(
    positionState.mapState { it?.let { position -> position.sha to position.filePath } }, // less events
    changesState
  ) { positionOrNull, changesOrNull ->
    val (sha, filePath) = positionOrNull ?: return@combineStates null
    val changes = changesOrNull ?: return@combineStates null

    changes.mapNotNull { (change, diffData) ->
      if (diffData.contains(sha, filePath))
        DiffDataMappedGitLabMergeRequestInlayModel.DiffData(change, diffData)
      else null
    }.lastOrNull()
  }
