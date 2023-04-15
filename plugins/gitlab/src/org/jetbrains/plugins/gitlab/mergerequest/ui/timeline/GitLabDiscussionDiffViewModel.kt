// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussionChangeMapping
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabDiscussionDiffViewModel.*

interface GitLabDiscussionDiffViewModel {
  val position: GitLabDiscussionPosition
  val mapping: Flow<GitLabMergeRequestDiscussionChangeMapping>
  val patchHunk: Flow<PatchHunkResult>

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
  mr: GitLabMergeRequest,
  override val position: GitLabDiscussionPosition
) : GitLabDiscussionDiffViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val mapping: Flow<GitLabMergeRequestDiscussionChangeMapping> = mr.changes.mapLatest {
    try {
      val allChanges = it.getParsedChanges()
      GitLabMergeRequestDiscussionChangeMapping.map(allChanges, position)
    }
    catch (e: Exception) {
      GitLabMergeRequestDiscussionChangeMapping.Error(e)
    }
  }.modelFlow(cs, LOG)


  override val patchHunk: Flow<PatchHunkResult> = channelFlow {
    mr.changes.mapLatest { it.getParsedChanges() }.catch { e ->
      send(PatchHunkResult.Error(e))
    }.combine(mapping) { allChanges, mapping ->
      when {
        mapping is GitLabMergeRequestDiscussionChangeMapping.Actual && mapping.location != null -> {
          val patch = allChanges.patchesByChange[mapping.change]?.patch ?: run {
            LOG.warn("Can't find patch for ${mapping.change}")
            return@combine PatchHunkResult.NotLoaded
          }

          val (hunk, anchor) = findHunkAndAnchor(patch, mapping.location) ?: run {
            LOG.debug("Unable to map location for position $position in patch\n$patch")
            return@combine PatchHunkResult.NotLoaded
          }
          PatchHunkResult.Loaded(hunk, anchor)
        }
        mapping is GitLabMergeRequestDiscussionChangeMapping.Error -> PatchHunkResult.Error(mapping.error)
        else -> PatchHunkResult.NotLoaded
      }
    }.collectLatest {
      send(it)
    }
  }.modelFlow(cs, LOG)
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
