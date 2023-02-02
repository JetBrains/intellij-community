// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.util.Side
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import git4idea.changes.GitChangeDiffData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLazyProject
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModelImpl

interface GitLabMergeRequestDiffReviewViewModel {
  fun getDiscussions(change: Change): Flow<List<DiffMappedValue<GitLabDiscussionViewModel>>>

  companion object {
    val KEY: Key<GitLabMergeRequestDiffReviewViewModel> = Key.create("GitLab.Diff.Review.Discussions.ViewModel")
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiffReviewViewModelImpl(
  private val connection: GitLabProjectConnection,
  private val mrId: GitLabMergeRequestId
) : GitLabMergeRequestDiffReviewViewModel {

  override fun getDiscussions(change: Change): Flow<List<DiffMappedValue<GitLabDiscussionViewModel>>> =
    connection.projectData.mergeRequests.getShared(mrId).map {
      it.getOrThrow()
    }.flatMapLatest { mergeRequest ->
      val changeDiffData = mergeRequest.changes.mapLatest {
        it.getParsedChanges().findChangeDiffData(change) ?: error("Missing diff data for change $change")
      }
      val diffDiscussions = mergeRequest.userDiscussions.mapLatest { discussions ->
        discussions.filter { it.position != null }
      }

      combine(changeDiffData, diffDiscussions) { diffData, discussions ->
        discussions.mapNotNull {
          mapDiscussionToDiff(diffData, it)
        }
      }.mapCaching(
        { it.value.id },
        { cs, disc -> createMappedVm(cs, disc) },
        { value.destroy() }
      ).flowOn(Dispatchers.Default)
    }

  private fun mapDiscussionToDiff(diffData: GitChangeDiffData, discussion: GitLabDiscussion): DiffMappedValue<GitLabDiscussion>? {
    val position = discussion.position?.takeIf { pos -> diffData.contains(pos.diffRefs.headSha, pos.filePath) } ?: return null

    val originalSide = if (position.newLine != null) Side.RIGHT else Side.LEFT
    val originalLine = (position.newLine ?: position.oldLine!!) - 1

    val (side, line) = when (diffData) {
      is GitChangeDiffData.Cumulative -> {
        originalSide to originalLine
      }
      is GitChangeDiffData.Commit -> {
        diffData.mapPosition(position.diffRefs.headSha, originalSide, originalLine) ?: return null
      }
    }

    return DiffMappedValue(side, line, discussion)
  }

  private fun createMappedVm(cs: CoroutineScope, mappedDiscussion: DiffMappedValue<GitLabDiscussion>)
    : DiffMappedValue<GitLabDiscussionViewModel> {
    val vm = GitLabDiscussionViewModelImpl(cs, connection.currentUser, mappedDiscussion.value)
    return DiffMappedValue(mappedDiscussion.side, mappedDiscussion.lineIndex, vm)
  }
}
