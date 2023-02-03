// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import git4idea.changes.GitChangeDiffData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModelImpl
import kotlin.coroutines.cancellation.CancellationException

private typealias DiscussionsFlow = Flow<List<DiffMappedValue<GitLabDiscussionViewModel>>>

interface GitLabMergeRequestDiffChangeViewModel {
  val discussions: DiscussionsFlow
}

private val LOG = logger<GitLabMergeRequestDiffChangeViewModel>()

class GitLabMergeRequestDiffChangeViewModelImpl(
  parentCs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  mergeRequest: GitLabMergeRequest,
  diffData: GitChangeDiffData
) : GitLabMergeRequestDiffChangeViewModel {

  private val cs = parentCs.childScope()

  override val discussions: DiscussionsFlow = mergeRequest.userDiscussions
    .map { discussions -> discussions.mapNotNull { mapDiscussionToDiff(diffData, it) } }
    .mapCaching(
      { it.value.id },
      { cs, disc -> createMappedVm(cs, disc) },
      { value.destroy() }
    ).modelFlow(cs, LOG)


  private fun mapDiscussionToDiff(diffData: GitChangeDiffData, discussion: GitLabDiscussion): DiffMappedValue<GitLabDiscussion>? {
    val position = discussion.position?.takeIf { pos -> diffData.contains(pos.diffRefs.headSha, pos.filePath) } ?: return null

    val originalSide = if (position.newLine != null) Side.RIGHT else Side.LEFT
    val originalLine = (position.newLine ?: position.oldLine!!) - 1

    val location = when (diffData) {
      is GitChangeDiffData.Cumulative -> {
        DiffLineLocation(originalSide, originalLine)
      }
      is GitChangeDiffData.Commit -> {
        diffData.mapPosition(position.diffRefs.headSha, originalSide, originalLine) ?: return null
      }
    }
    return DiffMappedValue(location, discussion)
  }

  private fun createMappedVm(cs: CoroutineScope, mappedDiscussion: DiffMappedValue<GitLabDiscussion>)
    : DiffMappedValue<GitLabDiscussionViewModel> {
    val vm = GitLabDiscussionViewModelImpl(cs, currentUser, mappedDiscussion.value)
    val location = DiffLineLocation(mappedDiscussion.side, mappedDiscussion.lineIndex)
    return DiffMappedValue(location, vm)
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
