// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.selectedChange
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNotePositionMapping
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabDiscussionDiffViewModel.PatchHunkResult

interface GitLabDiscussionDiffViewModel {
  val position: GitLabNotePosition
  val mapping: Flow<GitLabMergeRequestNotePositionMapping>
  val patchHunk: Flow<PatchHunkResult>

  val showDiffRequests: Flow<ChangesSelection.Precise>
  val showDiffHandler: Flow<(() -> Unit)?>

  sealed interface PatchHunkResult {
    class Loaded(val hunk: PatchHunk, val anchor: DiffLineLocation) : PatchHunkResult
    object NotLoaded : PatchHunkResult
    class Error(val error: Throwable) : PatchHunkResult
  }
}

private val LOG = logger<GitLabDiscussionDiffViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabDiscussionDiffViewModelImpl(
  parentCs: CoroutineScope,
  private val mr: GitLabMergeRequest,
  override val position: GitLabNotePosition
) : GitLabDiscussionDiffViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val mapping: Flow<GitLabMergeRequestNotePositionMapping> = mr.changes.mapLatest {
    try {
      val allChanges = it.getParsedChanges()
      GitLabMergeRequestNotePositionMapping.map(allChanges, position)
    }
    catch (e: Exception) {
      GitLabMergeRequestNotePositionMapping.Error(e)
    }
  }.modelFlow(cs, LOG)


  override val patchHunk: Flow<PatchHunkResult> = channelFlow {
    mr.changes.mapLatest { it.getParsedChanges() }.catch { e ->
      send(PatchHunkResult.Error(e))
    }.combine(mapping) { allChanges, mapping ->
      when {
        mapping is GitLabMergeRequestNotePositionMapping.Actual && mapping.change.location != null -> {
          val patch = allChanges.patchesByChange[mapping.change.selectedChange]?.patch ?: run {
            LOG.warn("Can't find patch for ${mapping.change.selectedChange}")
            return@combine PatchHunkResult.NotLoaded
          }

          val (hunk, anchor) = findHunkAndAnchor(patch, mapping.change.location!!) ?: run {
            LOG.debug("Unable to map location for position $position in patch\n$patch")
            return@combine PatchHunkResult.NotLoaded
          }
          PatchHunkResult.Loaded(hunk, anchor)
        }
        mapping is GitLabMergeRequestNotePositionMapping.Error -> PatchHunkResult.Error(mapping.error)
        else -> PatchHunkResult.NotLoaded
      }
    }.collectLatest {
      send(it)
    }
  }.modelFlow(cs, LOG)

  private val _showDiffRequests = MutableSharedFlow<ChangesSelection.Precise>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  override val showDiffRequests: Flow<ChangesSelection.Precise> = _showDiffRequests.asSharedFlow()

  override val showDiffHandler: Flow<(() -> Unit)?> = mapping.map {
    when (it) {
      is GitLabMergeRequestNotePositionMapping.Actual -> {
        { requestFullDiff(it.change) }
      }
      is GitLabMergeRequestNotePositionMapping.Outdated -> {
        { requestFullDiff(it.change) }
      }
      else -> null
    }
  }

  private fun requestFullDiff(change: ChangesSelection.Precise) {
    cs.launch {
      _showDiffRequests.emit(change)
    }
  }
}

private fun findHunkAndAnchor(patch: TextFilePatch, location: DiffLineLocation): Pair<PatchHunk, DiffLineLocation>? {
  val (side, index) = location
  return when (side) {
    Side.LEFT -> {
      patch.hunks.find {
        index >= it.startLineBefore && index < it.endLineBefore
      }?.let { it to location }
    }
    Side.RIGHT -> {
      patch.hunks.find {
        index >= it.startLineAfter && index < it.endLineAfter
      }?.let { it to location }
    }
    else -> null
  }
}
