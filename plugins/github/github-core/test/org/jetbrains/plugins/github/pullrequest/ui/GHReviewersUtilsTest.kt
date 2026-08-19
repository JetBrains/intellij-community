// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date

class GHReviewersUtilsTest {
  private val ghost = GHUser.FAKE_GHOST

  private fun user(id: String): GHUser = GHUser(id, "login-$id", "https://example/$id", "https://example/$id/avatar", "Name $id")

  private fun review(author: GHActor?, state: GHPullRequestReviewState, createdAt: Long = 0L): GHPullRequestReview =
    GHPullRequestReview("review-$state-$createdAt", "https://example/review", author, "", state, Date(createdAt), false)

  private fun reviewsByReviewers(
    reviews: List<GHPullRequestReview>,
    reviewers: List<GHPullRequestRequestedReviewer>,
    author: GHActor? = null,
  ): Map<GHPullRequestRequestedReviewer, GHPRReviewerState> =
    GHReviewersUtils.getReviewsByReviewers(author, reviews, reviewers, ghost)

  @Test
  fun `approved review is surfaced as approved`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(listOf(review(reviewer, GHPullRequestReviewState.APPROVED)), emptyList())
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.APPROVED), result)
  }

  @Test
  fun `changes-requested review is surfaced as changes requested`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(listOf(review(reviewer, GHPullRequestReviewState.CHANGES_REQUESTED)), emptyList())
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.CHANGES_REQUESTED), result)
  }

  @Test
  fun `commented-only review is surfaced as commented`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(listOf(review(reviewer, GHPullRequestReviewState.COMMENTED)), emptyList())
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.COMMENTED), result)
  }

  @Test
  fun `dismissed review is surfaced as commented`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(listOf(review(reviewer, GHPullRequestReviewState.DISMISSED)), emptyList())
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.COMMENTED), result)
  }

  @Test
  fun `pending review is not surfaced`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(listOf(review(reviewer, GHPullRequestReviewState.PENDING)), emptyList())
    assertEquals(emptyMap<GHPullRequestRequestedReviewer, GHPRReviewerState>(), result)
  }

  @Test
  fun `requested reviewer without a review needs review`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(emptyList(), listOf(reviewer))
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.NEEDS_REVIEW), result)
  }

  @Test
  fun `reviewer who approved and is re-requested becomes re-requested`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(listOf(review(reviewer, GHPullRequestReviewState.APPROVED)), listOf(reviewer))
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.RE_REQUESTED), result)
  }

  @Test
  fun `reviewer who requested changes and is re-requested becomes re-requested`() {
    val reviewer = user("1")
    val result = reviewsByReviewers(listOf(review(reviewer, GHPullRequestReviewState.CHANGES_REQUESTED)), listOf(reviewer))
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.RE_REQUESTED), result)
  }

  @Test
  fun `latest review by creation time wins`() {
    val reviewer = user("1")
    val reviews = listOf(
      review(reviewer, GHPullRequestReviewState.COMMENTED, createdAt = 1000L),
      review(reviewer, GHPullRequestReviewState.APPROVED, createdAt = 2000L),
    )
    // Even if the newest review is listed first, it must still win.
    val shuffled = reviews.reversed()
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.APPROVED),
                 reviewsByReviewers(reviews, emptyList()))
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.APPROVED),
                 reviewsByReviewers(shuffled, emptyList()))
  }

  @Test
  fun `a comment submitted after an approval keeps the reviewer approved`() {
    val reviewer = user("1")
    val reviews = listOf(
      review(reviewer, GHPullRequestReviewState.APPROVED, createdAt = 1000L),
      review(reviewer, GHPullRequestReviewState.COMMENTED, createdAt = 2000L),
    )
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.APPROVED),
                 reviewsByReviewers(reviews, emptyList()))
  }

  @Test
  fun `a comment submitted after requested changes keeps the reviewer in changes requested`() {
    val reviewer = user("1")
    val reviews = listOf(
      review(reviewer, GHPullRequestReviewState.CHANGES_REQUESTED, createdAt = 1000L),
      review(reviewer, GHPullRequestReviewState.COMMENTED, createdAt = 2000L),
    )
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.CHANGES_REQUESTED),
                 reviewsByReviewers(reviews, emptyList()))
  }

  @Test
  fun `a dismissal newer than an approval demotes the reviewer to commented`() {
    val reviewer = user("1")
    val reviews = listOf(
      review(reviewer, GHPullRequestReviewState.APPROVED, createdAt = 1000L),
      review(reviewer, GHPullRequestReviewState.DISMISSED, createdAt = 2000L),
    )
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.COMMENTED),
                 reviewsByReviewers(reviews, emptyList()))
  }

  @Test
  fun `a dismissal newer than requested changes demotes the reviewer to commented`() {
    val reviewer = user("1")
    val reviews = listOf(
      review(reviewer, GHPullRequestReviewState.CHANGES_REQUESTED, createdAt = 1000L),
      review(reviewer, GHPullRequestReviewState.DISMISSED, createdAt = 2000L),
    )
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(reviewer to GHPRReviewerState.COMMENTED),
                 reviewsByReviewers(reviews, emptyList()))
  }

  @Test
  fun `pull request author's own review is excluded`() {
    val author = user("author")
    val result = reviewsByReviewers(listOf(review(author, GHPullRequestReviewState.APPROVED)), emptyList(), author = author)
    assertEquals(emptyMap<GHPullRequestRequestedReviewer, GHPRReviewerState>(), result)
  }

  @Test
  fun `review by an unknown author falls back to the ghost user`() {
    val result = reviewsByReviewers(listOf(review(null, GHPullRequestReviewState.APPROVED)), emptyList())
    assertEquals(mapOf<GHPullRequestRequestedReviewer, GHPRReviewerState>(ghost to GHPRReviewerState.APPROVED), result)
  }

  @Test
  fun `mixed reviewers keep every state`() {
    val approver = user("1")
    val rejecter = user("2")
    val commenter = user("3")
    val dismissed = user("4")
    val requested = user("5")
    val reRequested = user("6")

    val reviews = listOf(
      review(approver, GHPullRequestReviewState.APPROVED),
      review(rejecter, GHPullRequestReviewState.CHANGES_REQUESTED),
      review(commenter, GHPullRequestReviewState.COMMENTED),
      review(dismissed, GHPullRequestReviewState.DISMISSED),
      review(reRequested, GHPullRequestReviewState.APPROVED),
    )
    val result = reviewsByReviewers(reviews, listOf(requested, reRequested))

    assertEquals(GHPRReviewerState.APPROVED, result[approver])
    assertEquals(GHPRReviewerState.CHANGES_REQUESTED, result[rejecter])
    assertEquals(GHPRReviewerState.COMMENTED, result[commenter])
    assertEquals(GHPRReviewerState.COMMENTED, result[dismissed])
    assertEquals(GHPRReviewerState.NEEDS_REVIEW, result[requested])
    assertEquals(GHPRReviewerState.RE_REQUESTED, result[reRequested])
    assertEquals(6, result.size)
  }
}
