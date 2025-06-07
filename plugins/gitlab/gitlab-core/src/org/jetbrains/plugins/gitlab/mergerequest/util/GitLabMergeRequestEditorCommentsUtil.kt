// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition

object GitLabMergeRequestEditorCommentsUtil {
  fun createDiscussionsPositionsFlow(mergeRequest: GitLabMergeRequest, discussionsViewOption: Flow<DiscussionsViewOption>)
    : Flow<Set<GitLabNotePosition>> = combine(mergeRequest.nonEmptyDiscussionsData,
                                              mergeRequest.draftNotesData,
                                              discussionsViewOption) { discussions, draftNotes, viewOption ->
    val discussionsPositions = discussions.getOrNull()?.asSequence()
      ?.mapNotNull { it.notes.firstOrNull() }
      ?.filter {
        when (viewOption) {
          DiscussionsViewOption.ALL -> true
          DiscussionsViewOption.UNRESOLVED_ONLY -> !it.resolved
          DiscussionsViewOption.DONT_SHOW -> false
        }
      }?.mapNotNull { it.position?.let(GitLabNotePosition::from) }
      .orEmpty()
    val draftNotesPositions = draftNotes.getOrNull()?.asSequence()
      ?.mapNotNull { it.position.let(GitLabNotePosition.Companion::from) }
      ?.filter {
        when (viewOption) {
          DiscussionsViewOption.ALL, DiscussionsViewOption.UNRESOLVED_ONLY -> true
          DiscussionsViewOption.DONT_SHOW -> false
        }
      }.orEmpty()
    (discussionsPositions + draftNotesPositions).toSet()
  }
}

fun Flow<Set<GitLabNotePosition>>.toLines(mapper: (GitLabNotePosition) -> Int?): Flow<Set<Int>> =
  map { it.mapNotNullTo(mutableSetOf(), mapper) }

fun Flow<Set<GitLabNotePosition>>.toLocations(mapper: (GitLabNotePosition) -> DiffLineLocation?): Flow<Set<DiffLineLocation>> =
  map { it.mapNotNullTo(mutableSetOf(), mapper) }