// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.util.childScope
import git4idea.changes.filePath
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges

interface GitLabDiscussionDiffViewModel {
  val position: GitLabDiscussionPosition.Text
  val patchHunk: Flow<PatchHunkLoadingState>

  sealed interface PatchHunkLoadingState {
    object Loading : PatchHunkLoadingState
    class Loaded(val hunk: PatchHunk, val anchor: DiffLineLocation) : PatchHunkLoadingState
    object NotAvailable : PatchHunkLoadingState
    class LoadingError(val error: Throwable) : PatchHunkLoadingState
  }
}

private val LOG = logger<GitLabDiscussionDiffViewModel>()

class GitLabDiscussionDiffViewModelImpl(
  parentCs: CoroutineScope,
  mr: GitLabMergeRequest,
  override val position: GitLabDiscussionPosition.Text
) : GitLabDiscussionDiffViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val patchHunk: Flow<GitLabDiscussionDiffViewModel.PatchHunkLoadingState> = channelFlow {
    send(GitLabDiscussionDiffViewModel.PatchHunkLoadingState.Loading)
    try {
      mr.changes.mapToHunkAndAnchor().collectLatest { hunkAndAnchor ->
        if (hunkAndAnchor == null) {
          send(GitLabDiscussionDiffViewModel.PatchHunkLoadingState.NotAvailable)
        }
        else {
          send(GitLabDiscussionDiffViewModel.PatchHunkLoadingState.Loaded(hunkAndAnchor.first, hunkAndAnchor.second))
        }
      }
    }
    catch (e: Exception) {
      send(GitLabDiscussionDiffViewModel.PatchHunkLoadingState.LoadingError(e))
    }
  }.modelFlow(cs, LOG)

  private fun Flow<GitLabMergeRequestChanges>.mapToHunkAndAnchor(): Flow<Pair<PatchHunk, DiffLineLocation>?> =
    map { changes ->
      val parsedChanges = changes.getParsedChanges()
      val patchWithHistory = parsedChanges.patchesByChange.values.find {
        it.patch.beforeVersionId == position.diffRefs.startSha
        && it.patch.afterVersionId == position.diffRefs.headSha
        && it.patch.filePath == position.filePath
      }
      if (patchWithHistory == null) {
        LOG.debug("Unable to find patch for position $position")
      }
      patchWithHistory?.patch
    }.map { patch ->
      if (patch == null) return@map null
      val location = when {
        position.oldLine != null -> {
          val index = position.oldLine - 1
          patch.hunks.find {
            index >= it.startLineBefore && index < it.endLineBefore
          }?.let { it to DiffLineLocation(Side.LEFT, index) }
        }
        position.newLine != null -> {
          val index = position.newLine - 1
          patch.hunks.find {
            index >= it.startLineAfter && index < it.endLineAfter
          }?.let { it to DiffLineLocation(Side.RIGHT, index) }
        }
        else -> null
      }
      if (location == null) {
        LOG.debug("Unable to map location for position $position in patch\n$patch")
      }
      location
    }
}
