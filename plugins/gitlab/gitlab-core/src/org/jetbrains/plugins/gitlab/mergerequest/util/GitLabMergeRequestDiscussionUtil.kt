// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.async.flatMapLatestEach
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDraftNote
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNote
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNoteLocation
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition

internal object GitLabMergeRequestDiscussionUtil {
  fun createAllDiscussionsResolvedFlow(mergeRequest: GitLabMergeRequest): Flow<Boolean> {
    return mergeRequest.discussions.map { it.getOrNull().orEmpty() }.flatMapLatestEach {
      combine(it.resolvable, it.resolved) { resolvable, resolved -> !resolvable || resolved }
    }.map { resolvedStates ->
      resolvedStates.all {
        it
      }
    }
  }

  fun createDiscussionsPositionsFlow(
    mergeRequest: GitLabMergeRequest,
    discussionsViewOption: Flow<DiscussionsViewOption>,
  ): Flow<Set<GitLabNotePosition>> {
    val loadedDiscussions = mergeRequest.discussions.map { it.getOrNull().orEmpty() }
    val loadedDraftNotes = mergeRequest.draftNotes.map { it.getOrNull().orEmpty() }

    return createDiscussionsPositionsFlow(loadedDiscussions, loadedDraftNotes, discussionsViewOption)
  }

  private fun createDiscussionsPositionsFlow(
    loadedDiscussions: Flow<Collection<GitLabMergeRequestDiscussion>>,
    loadedDraftNotes: Flow<Collection<GitLabMergeRequestDraftNote>>,
    discussionsViewOption: Flow<DiscussionsViewOption>,
  ): Flow<Set<GitLabNotePosition>> {
    val firstNotes = loadedDiscussions.flatMapLatestEach {
      it.firstNote
    }

    return combine(firstNotes, loadedDraftNotes) { discussionsNotes, draftsNotes ->
      discussionsNotes.filterNotNull() + draftsNotes
    }.flatMapLatestEach {
      it.getPositionWhenVisible(discussionsViewOption)
    }.map {
      it.filterNotNull().toSet()
    }
  }

  private val GitLabMergeRequestDiscussion.firstNote: Flow<GitLabMergeRequestNote?> get() = notes.map { it.firstOrNull() }

  private fun GitLabMergeRequestNote.getPositionWhenVisible(discussionsViewOption: Flow<DiscussionsViewOption>): Flow<GitLabNotePosition?> =
    combine(discussionsViewOption, resolved, position) { viewOption, resolved, position ->
      when (viewOption) {
        DiscussionsViewOption.ALL -> position
        DiscussionsViewOption.UNRESOLVED_ONLY -> if (!resolved) position else null
        DiscussionsViewOption.DONT_SHOW -> null
      }
    }
}

fun Flow<Set<GitLabNotePosition>>.toLines(mapper: (GitLabNotePosition) -> Int?): Flow<Set<Int>> =
  map { it.mapNotNullTo(mutableSetOf(), mapper) }

fun Flow<Set<GitLabNotePosition>>.toLocations(mapper: (GitLabNotePosition) -> GitLabNoteLocation?): Flow<Set<GitLabNoteLocation>> =
  map { it.mapNotNullTo(mutableSetOf(), mapper) }