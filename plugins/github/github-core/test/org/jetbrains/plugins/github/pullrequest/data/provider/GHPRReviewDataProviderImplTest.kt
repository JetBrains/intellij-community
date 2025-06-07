// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.MainDispatcherRule
import com.intellij.util.messages.MessageBus
import io.mockk.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val PR_ID = GHPRIdentifier("id", 0)

//TODO: test threads
@RunWith(JUnit4::class)
class GHPRReviewDataProviderImplTest {

  companion object {
    @JvmField
    @ClassRule
    internal val mainRule = MainDispatcherRule()
  }

  private val reviewService = mockk<GHPRReviewService>(relaxUnitFun = true)
  private val listener = mockk<GHPRDataOperationsListener>(relaxUnitFun = true)
  private val messageBus = mockk<MessageBus> {
    every { syncPublisher(GHPRDataOperationsListener.TOPIC) } returns listener
  }

  private fun TestScope.createProvider(): GHPRReviewDataProvider =
    GHPRReviewDataProviderImpl(backgroundScope, reviewService, mockk(), PR_ID, messageBus)

  private fun createPendingReview(id: String, comments: List<GHPullRequestReviewComment>): GHPullRequestPendingReviewDTO =
    GHPullRequestPendingReviewDTO(id, GHPullRequestReviewState.PENDING, GHPullRequestPendingReviewDTO.CommentCount(comments.size))

  @Test
  fun testCachingReviewLoad() = runTest {
    coEvery { reviewService.loadPendingReview(eq(PR_ID)) } returns createPendingReview("", listOf(mockk()))

    val prv = createProvider()
    val result = prv.loadPendingReview()
    assertEquals(prv.loadPendingReview(), result)

    coVerifyAll { reviewService.loadPendingReview(PR_ID) }
  }

  @Test
  fun testCreateAndSubmitReview() = runTest {
    coEvery { reviewService.loadPendingReview(eq(PR_ID)) } returns null
    coEvery { reviewService.createReview(eq(PR_ID), any(), any(), any(), any()) } returns createPendingReview("", listOf(mockk()))

    val prv = createProvider()
    prv.createReview(mockk(), "test")
    coVerify { reviewService.createReview(eq(PR_ID), any(), any(), any(), any()) }

    assertEquals(prv.loadPendingReview(), null)
    coVerify { reviewService.loadPendingReview(PR_ID) }
    confirmVerified(reviewService)
  }

  @Test
  fun testCreatePendingReviewImmediateUpdate() = runTest {
    val result = createPendingReview("", listOf(mockk()))
    coEvery { reviewService.createReview(eq(PR_ID), any(), any(), any(), any()) } returns result

    val prv = createProvider()
    prv.createReview(null, "test")
    coVerifyAll {
      reviewService.createReview(eq(PR_ID), any(), any(), any(), any())
      listener.onReviewsChanged()
    }

    assertEquals(result.toModel(), prv.loadPendingReview())
    confirmVerified(reviewService)
  }

  @Test
  fun testDeleteReviewUpdatedImmediately() = runTest {
    val reviewDto = createPendingReview("REVIEW", listOf(mockk()))

    coEvery { reviewService.loadPendingReview(eq(PR_ID)) } returns reviewDto

    val prv = createProvider()
    // fill cache
    prv.loadPendingReview()
    prv.deleteReview(reviewDto.id)
    assertEquals(null, prv.loadPendingReview())

    coVerifySequence {
      reviewService.loadPendingReview(eq(PR_ID))
      reviewService.deleteReview(eq(PR_ID), eq(reviewDto.id))
      listener.onReviewsChanged()
    }
  }

  @Test
  fun testUpdateReviewBody() = runTest {
    val reviewId = "REVIEW"
    val text = "test"

    coEvery { reviewService.updateReviewBody(eq(reviewId), eq(text)) } returns mockk(relaxed = true)

    val prv = createProvider()
    prv.updateReviewBody(reviewId, text)

    coVerifySequence {
      reviewService.updateReviewBody(eq(reviewId), eq(text))
      listener.onReviewUpdated(eq(reviewId), eq(text))
    }
  }

  @Test
  fun testCachingThreadsLoad() = runTest {
    val result = mockk<List<GHPullRequestReviewThread>>()
    coEvery { reviewService.loadReviewThreads(PR_ID) } returns result

    val prv = createProvider()
    assertEquals(prv.loadThreads(), result)
    assertEquals(prv.loadThreads(), result)

    coVerifyAll { reviewService.loadReviewThreads(PR_ID) }
  }
}