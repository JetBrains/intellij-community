// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.util.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.DiffPathsInput
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.ui.comment.*
import kotlin.coroutines.cancellation.CancellationException

private typealias DiscussionsFlow = Flow<Collection<DiffMappedValue<GitLabDiscussionViewModel>>>
private typealias NewDiscussionsFlow = Flow<Collection<DiffMappedValue<NewGitLabNoteViewModel>>>

interface GitLabMergeRequestDiffChangeViewModel {
  val discussions: DiscussionsFlow
  val newDiscussions: NewDiscussionsFlow

  fun requestNewDiscussion(location: DiffLineLocation, focus: Boolean)
  fun cancelNewDiscussion(location: DiffLineLocation)
}

private val LOG = logger<GitLabMergeRequestDiffChangeViewModel>()

class GitLabMergeRequestDiffChangeViewModelImpl(
  parentCs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val diffData: GitTextFilePatchWithHistory
) : GitLabMergeRequestDiffChangeViewModel {

  private val cs = parentCs.childScope()

  override val discussions: DiscussionsFlow = mergeRequest.userDiscussions
    .mapToDiff()
    .mapCaching(
      { it.value.id },
      { cs, disc -> createMappedVm(cs, disc) },
      { value.destroy() }
    ).modelFlow(cs, LOG)


  private val _newDiscussions = MutableStateFlow<Map<DiffLineLocation, NewGitLabNoteViewModel>>(emptyMap())
  override val newDiscussions: NewDiscussionsFlow =
    _newDiscussions.map { vms ->
      vms.map { DiffMappedValue(it.toPair()) }
    }.modelFlow(cs, LOG)

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun Flow<Collection<GitLabDiscussion>>.mapToDiff(): Flow<List<DiffMappedValue<GitLabDiscussion>>> =
    mapLatest { discussions ->
      discussions.mapNotNull {
        val position = it.position.first() as? GitLabDiscussionPosition.Text ?: return@mapNotNull null
        mapDiscussionToDiff(position, it)
      }
    }

  private fun mapDiscussionToDiff(position: GitLabDiscussionPosition.Text, discussion: GitLabDiscussion)
    : DiffMappedValue<GitLabDiscussion>? {

    if ((position.filePathBefore != null && !diffData.contains(position.parentSha, position.filePathBefore)) &&
        (position.filePathAfter != null && !diffData.contains(position.sha, position.filePathAfter))) return null

    val (side, lineIndex) = position.location
    // context should be mapped to the left side
    val commitSha = side.select(position.parentSha, position.sha)!!

    val location = diffData.mapLine(commitSha, lineIndex, side) ?: return null
    return DiffMappedValue(location, discussion)
  }

  private fun createMappedVm(cs: CoroutineScope, mappedDiscussion: DiffMappedValue<GitLabDiscussion>)
    : DiffMappedValue<GitLabDiscussionViewModel> {
    val vm = GitLabDiscussionViewModelImpl(cs, currentUser, mappedDiscussion.value)
    val location = DiffLineLocation(mappedDiscussion.side, mappedDiscussion.lineIndex)
    return DiffMappedValue(location, vm)
  }

  override fun requestNewDiscussion(location: DiffLineLocation, focus: Boolean) {
    _newDiscussions.updateAndGet {
      if (!it.containsKey(location)) {
        val vm = DelegatingGitLabNoteEditingViewModel(cs, "") {
          createDiscussion(location, it)
        }.apply {
          onDoneIn(cs) {
            cancelNewDiscussion(location)
          }
        }.forNewNote(currentUser)
        it + (location to vm)
      }
      else {
        it
      }
    }.apply {
      if (focus) {
        get(location)?.requestFocus()
      }
    }
  }

  private suspend fun createDiscussion(location: DiffLineLocation, body: String) {
    val patch = diffData.patch
    val startSha = patch.beforeVersionId!!
    val headSha = patch.afterVersionId!!
    val baseSha = if (diffData.isCumulative) diffData.fileHistory.findStartCommit() else startSha

    // Due to https://gitlab.com/gitlab-org/gitlab/-/issues/325161 we need line index for both sides for context lines
    val otherSide = transferToOtherSide(patch, location)
    val lineBefore = if (location.first == Side.LEFT) location.second else otherSide
    val lineAfter = if (location.first == Side.RIGHT) location.second else otherSide

    val pathBefore = patch.beforeName
    val pathAfter = patch.afterName

    // Due to https://gitlab.com/gitlab-org/gitlab/-/issues/296829 we need base ref here
    val positionInput = GitLabDiffPositionInput(
      baseSha,
      startSha,
      lineBefore?.inc(),
      headSha,
      lineAfter?.inc(),
      DiffPathsInput(pathBefore, pathAfter)
    )

    mergeRequest.addNote(positionInput, body)
  }

  override fun cancelNewDiscussion(location: DiffLineLocation) {
    _newDiscussions.update {
      val oldVm = it[location]
      val newMap = it - location
      cs.launch {
        oldVm?.destroy()
      }
      newMap
    }
  }

  suspend fun destroy() {
    try {
      cs.coroutineContext[Job]!!.cancelAndJoin()
    }
    catch (e: CancellationException) {
      // ignore, cuz we don't want to cancel the invoker
    }
  }
}

private fun transferToOtherSide(patch: TextFilePatch, location: DiffLineLocation): Int? {
  val (side, lineIndex) = location
  var lastEndBefore = 0
  var lastEndAfter = 0
  for (hunk in patch.hunks.withoutContext()) {
    when (side) {
      Side.LEFT -> {
        if (lineIndex < hunk.start1) {
          break
        }
        if (lineIndex in hunk.start1 until hunk.end1) {
          return null
        }
      }
      Side.RIGHT -> {
        if (lineIndex < hunk.start2) {
          break
        }
        if (lineIndex in hunk.start2 until hunk.end2) {
          return null
        }
      }
    }
    lastEndBefore = hunk.end1
    lastEndAfter = hunk.end2
  }
  return when (side) {
    Side.LEFT -> lastEndAfter + (lineIndex - lastEndBefore)
    Side.RIGHT -> lastEndBefore + (lineIndex - lastEndAfter)
  }
}

private fun Collection<PatchHunk>.withoutContext(): Sequence<Range> =
  asSequence().map { PatchHunkUtil.getChangeOnlyRanges(it) }.flatten()
