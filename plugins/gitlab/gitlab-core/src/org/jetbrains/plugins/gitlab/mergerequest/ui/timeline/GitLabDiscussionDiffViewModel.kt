// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.selectedChange
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.progress.checkCanceled
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNotePositionMapping
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabDiscussionDiffViewModel.PatchHunkWithAnchor
import kotlin.coroutines.cancellation.CancellationException

interface GitLabDiscussionDiffViewModel {
  val position: GitLabNotePosition
  val patchHunk: StateFlow<ComputedResult<PatchHunkWithAnchor?>>

  val showDiffRequests: Flow<ChangesSelection.Precise>
  val showDiffHandler: StateFlow<(() -> Unit)?>

  data class PatchHunkWithAnchor(val hunk: PatchHunk, val anchor: DiffLineLocation)
}

private val LOG = logger<GitLabDiscussionDiffViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabDiscussionDiffViewModelImpl(
  parentCs: CoroutineScope,
  private val mr: GitLabMergeRequest,
  override val position: GitLabNotePosition
) : GitLabDiscussionDiffViewModel {

  private val cs = parentCs.childScope(this::class)

  override val patchHunk: StateFlow<ComputedResult<PatchHunkWithAnchor?>>

  private val _showDiffRequests = MutableSharedFlow<ChangesSelection.Precise>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  override val showDiffRequests: Flow<ChangesSelection.Precise> = _showDiffRequests.asSharedFlow()

  override val showDiffHandler: StateFlow<(() -> Unit)?>

  init {
    patchHunk = MutableStateFlow(ComputedResult.loading())
    showDiffHandler = MutableStateFlow(null)

    cs.launch {
      mr.changes.collectLatest {
        try {
          val allChanges = it.getParsedChanges()
          val mapping = GitLabMergeRequestNotePositionMapping.map(allChanges, position)

          patchHunk.value = ComputedResult.success(getPatchHunk(allChanges, mapping))
          showDiffHandler.value = when (mapping) {
            is GitLabMergeRequestNotePositionMapping.Actual -> {
              {
                _showDiffRequests.tryEmit(mapping.change)
              }
            }
            is GitLabMergeRequestNotePositionMapping.Outdated -> {
              {
                _showDiffRequests.tryEmit(mapping.change)
              }
            }
            else -> null
          }

        }
        catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
          // getParsedChanges can be canceled outside
          checkCanceled()
        }
        catch (e: Exception) {
          patchHunk.value = ComputedResult.failure(e)
          showDiffHandler.value = null
        }
      }
    }
  }

  private fun getPatchHunk(
    allChanges: GitBranchComparisonResult,
    mapping: GitLabMergeRequestNotePositionMapping,
  ): PatchHunkWithAnchor? {
    if (mapping !is GitLabMergeRequestNotePositionMapping.Actual) return null
    val location = mapping.change.location ?: return null

    val patch = allChanges.patchesByChange[mapping.change.selectedChange]?.patch ?: run {
      LOG.warn("Can't find patch for ${mapping.change.selectedChange}")
      return null
    }

    return findHunkAndAnchor(patch, location) ?: run {
      LOG.debug("Unable to map location for position $position in patch\n$patch")
      null
    }
  }
}

private fun findHunkAndAnchor(patch: TextFilePatch, location: DiffLineLocation): PatchHunkWithAnchor? {
  val (side, index) = location
  return when (side) {
    Side.LEFT -> {
      patch.hunks.find {
        index >= it.startLineBefore && index < it.endLineBefore
      }
    }
    Side.RIGHT -> {
      patch.hunks.find {
        index >= it.startLineAfter && index < it.endLineAfter
      }
    }
  }?.let { PatchHunkWithAnchor(it, location) }
}
