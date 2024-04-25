// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.util.MainDispatcherRule
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.*

private val PR_ID = GHPRIdentifier("id", 0)

//TODO: test threads
@RunWith(JUnit4::class)
class GHPRReviewDataProviderImplTest {

  companion object {
    @JvmField
    @ClassRule
    internal val mainRule = MainDispatcherRule()
  }

  @Rule
  @JvmField
  internal val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  internal lateinit var reviewService: GHPRReviewService

  @Mock
  internal lateinit var messageBus: MessageBus

  @Mock
  internal lateinit var listener: GHPRDataOperationsListener

  private fun TestScope.createProvider(): GHPRReviewDataProvider =
    GHPRReviewDataProviderImpl(backgroundScope, reviewService, mock(), PR_ID, messageBus)

  private fun createPendingReview(id: String, comments: List<GHPullRequestReviewComment>): GHPullRequestPendingReviewDTO =
    GHPullRequestPendingReviewDTO(id, GHPullRequestReviewState.PENDING, GraphQLNodesDTO(comments, comments.size))

  @Before
  fun setUp() {
    whenever(messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC)) doReturn listener
  }

  @Test
  fun testCachingReviewLoad() = runTest {
    whenever(reviewService.loadPendingReview(PR_ID)) doReturn createPendingReview("", listOf(mock()))

    val prv = createProvider()
    val result = prv.loadPendingReview()
    assertEquals(prv.loadPendingReview(), result)

    verify(reviewService, times(1)).loadPendingReview(PR_ID)
  }

  @Test
  fun testCreateAndSubmitReview() = runTest {
    whenever(reviewService.loadPendingReview(eq(PR_ID))) doReturn null
    whenever(reviewService.createReview(eq(PR_ID), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())) doReturn
      createPendingReview("", listOf(mock()))

    val prv = createProvider()
    prv.createReview(mock(), "test")
    verify(reviewService, times(1)).createReview(eq(PR_ID), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

    assertEquals(prv.loadPendingReview(), null)
    verify(reviewService, times(1)).loadPendingReview(PR_ID)
  }

  @Test
  fun testCreatePendingReviewImmediateUpdate() = runTest {
    val result = createPendingReview("", listOf(mock()))
    whenever(reviewService.createReview(eq(PR_ID), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())) doReturn result

    val prv = createProvider()
    prv.createReview(null, "test")
    verify(reviewService, times(1)).createReview(eq(PR_ID), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    verify(listener, times(1)).onReviewsChanged()

    assertEquals(result.toModel(), prv.loadPendingReview())
    verify(reviewService, never()).loadPendingReview(PR_ID)
  }

  @Test
  fun testDeleteReviewUpdatedImmediately() = runTest {
    val reviewDto = createPendingReview("REVIEW", listOf(mock()))

    whenever(reviewService.loadPendingReview(eq(PR_ID))) doReturn reviewDto

    val prv = createProvider()
    // fill cache
    prv.loadPendingReview()
    prv.deleteReview(reviewDto.id)
    assertEquals(null, prv.loadPendingReview())

    verify(reviewService, times(1)).loadPendingReview(eq(PR_ID))
    verify(reviewService, times(1)).deleteReview(eq(PR_ID), eq(reviewDto.id))
    verify(listener, times(1)).onReviewsChanged()
  }

  @Test
  fun testUpdateReviewBody() = runTest {
    val reviewId = "REVIEW"
    val text = "test"

    whenever(reviewService.updateReviewBody(eq(reviewId), eq(text))) doReturn mock()

    val prv = createProvider()
    prv.updateReviewBody(reviewId, text)

    verify(reviewService, times(1)).updateReviewBody(eq(reviewId), eq(text))
    verify(listener, times(1)).onReviewUpdated(eq(reviewId), eq(text))
  }

  @Test
  fun testCachingThreadsLoad() = runTest {
    val result = mock<List<GHPullRequestReviewThread>>()
    whenever(reviewService.loadReviewThreads(PR_ID)) doReturn result

    val prv = createProvider()
    assertEquals(prv.loadThreads(), result)
    assertEquals(prv.loadThreads(), result)

    verify(reviewService, times(1)).loadReviewThreads(PR_ID)
  }
}