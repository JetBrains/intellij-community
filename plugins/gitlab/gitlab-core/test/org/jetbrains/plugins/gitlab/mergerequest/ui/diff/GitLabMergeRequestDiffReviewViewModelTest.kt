// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import git4idea.changes.GitFileHistory
import git4idea.changes.GitTextFilePatchWithHistory
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNewDiscussionPositionFactory
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNoteLocation
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.createDiffDataFlow
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModelsImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class GitLabMergeRequestDiffReviewViewModelTest {
  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `a context note created on the right moves to the left`() = withReview {
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.RIGHT, 3, Side.RIGHT, 3), false)

    assertLocation(Side.LEFT to 1)
  }

  @Test
  fun `a context note created on the left stays on the left`() = withReview {
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.LEFT, 1, Side.LEFT, 1), false)

    assertLocation(Side.LEFT to 1)
  }

  @Test
  fun `a note on an added line stays on the right`() = withReview {
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.RIGHT, 1, Side.RIGHT, 1), false)

    assertLocation(Side.RIGHT to 1)
  }

  @Test
  fun `a note on a deleted line stays on the left`() = withReview {
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.LEFT, 0, Side.LEFT, 0), false)

    assertLocation(Side.LEFT to 0)
  }

  @Test
  fun `a range from an added line to a context line stays on the right`() = withReview {
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.RIGHT, 1, Side.RIGHT, 3), false)

    assertLocation(Side.RIGHT to 3)
  }

  @Test
  fun `the gutter follows the inlay when the range changes`() = withReview {
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.RIGHT, 1, Side.RIGHT, 1), false)
    assertLocation(Side.RIGHT to 1)
    val inlay = reviewVm.newDiscussions.value.single()

    inlay.updateLineRange(Side.RIGHT to 3, Side.RIGHT to 3)

    val locations = reviewVm.locationsWithNewDiscussions.first { it != setOf(Side.RIGHT to 1) }
    assertEquals(setOf(Side.LEFT to 1), locations)
    assertEquals(GitLabNoteLocation(Side.LEFT, 1, Side.LEFT, 1), inlay.location.value)
  }

  @Test
  fun `a note created on the right can be cancelled from its left inlay`() = withReview {
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.RIGHT, 3, Side.RIGHT, 3), false)
    assertLocation(Side.LEFT to 1)

    reviewVm.cancelNewDiscussion(Side.LEFT to 1)

    assertTrue(discussionsContainer.newDiscussions.value.isEmpty())
    reviewVm.locationsWithNewDiscussions.first { it.isEmpty() }
    reviewVm.newDiscussions.first { it.isEmpty() }
  }

  @Test
  fun `cancellation keeps a note at the same line in another file`() = withReview {
    val otherDiff = createDiffData("other.txt")
    val otherPosition = GitLabNewDiscussionPositionFactory.calcFor(
      otherDiff, GitLabNoteLocation(Side.RIGHT, 3, Side.RIGHT, 3)
    )
    discussionsContainer.requestNewDiscussion(NewDiscussionPosition(otherPosition, Side.RIGHT), false)
    val otherNote = discussionsContainer.newDiscussions.value.single()
    otherNote.text.value = "Keep this note"
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.LEFT, 1, Side.LEFT, 1), false)
    val inlay = reviewVm.newDiscussions.first { it.isNotEmpty() }.single()
    assertEquals(GitLabNoteLocation(Side.LEFT, 1, Side.LEFT, 1), inlay.location.value)

    reviewVm.cancelNewDiscussion(Side.LEFT to 1)

    assertEquals(listOf(otherNote), discussionsContainer.newDiscussions.value)
    assertEquals("Keep this note", otherNote.text.value)
  }

  /**
   * Cancellation button was not shown for some new comments in the case when comments are added to many files
   */
  @Test
  fun `review probe for gutter updates with a note in another file`() = withReview {
    val otherPosition = GitLabNewDiscussionPositionFactory.calcFor(
      createDiffData("other.txt"), GitLabNoteLocation(Side.RIGHT, 3, Side.RIGHT, 3)
    )
    discussionsContainer.requestNewDiscussion(NewDiscussionPosition(otherPosition, Side.RIGHT), false)
    reviewVm.requestNewDiscussion(GitLabNoteLocation(Side.RIGHT, 3, Side.RIGHT, 3), false)
    val inlay = reviewVm.newDiscussions.first { it.isNotEmpty() }.single()
    assertEquals(GitLabNoteLocation(Side.LEFT, 1, Side.LEFT, 1), inlay.location.value)

    val locations = withTimeoutOrNull(5.seconds) {
      reviewVm.locationsWithNewDiscussions.first { it.isNotEmpty() }
    }
    assertEquals(setOf(Side.LEFT to 1), locations)
  }

  private fun withReview(action: suspend ReviewFixture.() -> Unit): Unit = timeoutRunBlocking {
    try {
      ReviewFixture(this).action()
    }
    finally {
      coroutineContext.cancelChildren()
    }
  }

  private class ReviewFixture(cs: CoroutineScope) {
    private val project = mockk<Project> {
      every { getService(GitLabMergeRequestsPreferences::class.java) } returns mockk {
        every { usedAsDraftSubmitActionLastState } returns MutableStateFlow(false)
      }
    }
    private val diffData = createDiffData("current.txt")
    private val change = mockk<RefComparisonChange>()
    private val changes = MutableStateFlow<Map<RefComparisonChange, GitTextFilePatchWithHistory>?>(mapOf(change to diffData))
    private val discussionsViewOption = MutableStateFlow(DiscussionsViewOption.ALL)
    private val mergeRequest = mockk<GitLabMergeRequest> {
      every { discussions } returns flowOf(Result.success(emptyList()))
      every { draftNotes } returns flowOf(Result.success(emptyList()))
      every { changes } returns MutableSharedFlow()
      every { canAddNotes } returns true
      every { canAddPositionalDraftNotes } returns true
      every { canAddMultilinePositionalNotes } returns true
    }
    val discussionsContainer = GitLabMergeRequestDiscussionsViewModelsImpl(
      project, cs, mockk(), mockk(), mergeRequest, mockk(), mockk()
    )
    private val mappedNewDiscussions = discussionsContainer.newDiscussions.mapState { notes ->
      notes.map { note ->
        val mappedDiffData = createDiffDataFlow(note.position.mapState { it.position }, changes)
        GitLabMergeRequestDiffNewDiscussionViewModel(note, mappedDiffData, discussionsViewOption)
      }
    }
    private val diffVm = mockk<GitLabMergeRequestDiffViewModel> {
      every { discussions } returns MutableStateFlow(ComputedResult.success(emptyList()))
      every { draftDiscussions } returns MutableStateFlow(ComputedResult.success(emptyList()))
      every { newDiscussions } returns mappedNewDiscussions
    }
    val reviewVm: GitLabMergeRequestDiffReviewViewModel = GitLabMergeRequestDiffReviewViewModelImpl(
      project, cs, mergeRequest, mockk(), diffData, change, diffVm, discussionsContainer, discussionsViewOption, mockk(), mockk()
    )

    suspend fun assertLocation(expected: DiffLineLocation) {
      val locations = reviewVm.locationsWithNewDiscussions.first { it.isNotEmpty() }
      assertEquals(setOf(expected), locations)
      val inlay = reviewVm.newDiscussions.first { it.isNotEmpty() }.single()
      assertEquals(expected.first, inlay.location.value?.side)
      assertEquals(expected.second, inlay.location.value?.lineIdx)
    }
  }
}

private fun createDiffData(path: String): GitTextFilePatchWithHistory {
  val patch = TextFilePatch(Charsets.UTF_8).apply {
    beforeName = path
    afterName = path
    beforeVersionId = "base"
    afterVersionId = "head"
    addHunk(PatchHunk(0, 2, 0, 4).apply {
      addLine(PatchLine(PatchLine.Type.REMOVE, "old"))
      addLine(PatchLine(PatchLine.Type.ADD, "first"))
      addLine(PatchLine(PatchLine.Type.ADD, "second"))
      addLine(PatchLine(PatchLine.Type.ADD, "third"))
      addLine(PatchLine(PatchLine.Type.CONTEXT, "context"))
    })
  }
  val history = mockk<GitFileHistory> {
    every { findStartCommit() } returns "base"
    every { contains(any(), any()) } answers { firstArg<String>() in listOf("base", "head") && secondArg<String>() == path }
  }
  return GitTextFilePatchWithHistory(patch, true, history)
}
