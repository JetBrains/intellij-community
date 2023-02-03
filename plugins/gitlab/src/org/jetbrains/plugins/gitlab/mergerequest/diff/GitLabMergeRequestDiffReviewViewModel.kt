// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import com.intellij.util.containers.CollectionFactory
import git4idea.changes.GitChangeDiffData
import git4idea.changes.GitParsedChangesBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModelImpl

interface GitLabMergeRequestDiffReviewViewModel {
  fun getDiscussions(change: Change): DiscussionsFlow

  companion object {
    val KEY: Key<GitLabMergeRequestDiffReviewViewModel> = Key.create("GitLab.Diff.Review.Discussions.ViewModel")
  }
}

private typealias DiscussionsFlow = Flow<List<DiffMappedValue<GitLabDiscussionViewModel>>>

private val LOG = logger<GitLabMergeRequestDiffReviewViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiffReviewViewModelImpl(
  parentCs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  private val projectData: GitLabProject,
  private val mrId: GitLabMergeRequestId
) : GitLabMergeRequestDiffReviewViewModel {

  private val cs = parentCs.childScope()

  private val discussionsFlowsByChange: Flow<Map<Change, DiscussionsFlow>> = createDiscussionsByChangeFlow().modelFlow(cs, LOG)

  private fun createDiscussionsByChangeFlow(): Flow<Map<Change, DiscussionsFlow>> =
    channelFlow {
      val map = createMapByChanges<DiscussionsFlow>()

      projectData.mergeRequests.getShared(mrId)
        .mapNotNull(Result<GitLabMergeRequest>::getOrNull)
        .collectLatest { mr ->
          coroutineScope {
            val vmsCs = this
            mr.changes
              .map(GitLabMergeRequestChanges::getParsedChanges)
              .collectLatest { changesBundle ->
                val newChangesSet = createSetOfChanges().apply {
                  addAll(changesBundle.changes)
                  addAll(changesBundle.changesByCommits.values.flatten())
                }
                val updated = !map.keys.retainAll(newChangesSet) || !map.keys.containsAll(newChangesSet)
                if (updated) {
                  for (change in newChangesSet) {
                    val diffData = changesBundle.findChangeDiffData(change)
                    if (diffData == null) {
                      LOG.warn("Missing diff data for change $change")
                      continue
                    }

                    map.computeIfAbsent(change) {
                      createDiscussionsFlow(mr, diffData).shareIn(vmsCs, SharingStarted.Lazily, 1)
                    }
                  }
                  send(map)
                }
              }
            awaitCancellation()
          }
        }
    }

  private fun createDiscussionsFlow(mr: GitLabMergeRequest, diffData: GitChangeDiffData): DiscussionsFlow =
    mr.userDiscussions
      .map { discussions -> discussions.mapNotNull { mapDiscussionToDiff(diffData, it) } }
      .mapCaching(
        { it.value.id },
        { cs, disc -> createMappedVm(cs, disc) },
        { value.destroy() }
      )


  override fun getDiscussions(change: Change): DiscussionsFlow =
    discussionsFlowsByChange.flatMapLatest {
      it[change] ?: flowOf(emptyList())
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
    val vm = GitLabDiscussionViewModelImpl(cs, currentUser, mappedDiscussion.value)
    return DiffMappedValue(mappedDiscussion.side, mappedDiscussion.lineIndex, vm)
  }
}

private fun createSetOfChanges(): MutableSet<Change> {
  return CollectionFactory.createCustomHashingStrategySet(GitParsedChangesBundle.REVISION_COMPARISON_HASHING_STRATEGY)
}

private fun <V> createMapByChanges(): MutableMap<Change, V> {
  return CollectionFactory.createCustomHashingStrategyMap(GitParsedChangesBundle.REVISION_COMPARISON_HASHING_STRATEGY)
}
