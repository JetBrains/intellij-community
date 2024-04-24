package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.MainDispatcherRule
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.data.GHComment
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.*
import java.util.*

class GHPRCommentsDataProviderImplTest {
  companion object {
    private val PR_ID = GHPRIdentifier("id", 0)

    @JvmField
    @ClassRule
    internal val mainRule = MainDispatcherRule()
  }

  @Rule
  @JvmField
  internal val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  internal lateinit var commentsService: GHPRCommentService

  @Mock
  internal lateinit var messageBus: MessageBus

  @Mock
  internal lateinit var listener: GHPRDataOperationsListener

  private fun createProvider(): GHPRCommentsDataProvider =
    GHPRCommentsDataProviderImpl(commentsService, PR_ID, messageBus)

  @Before
  fun setUp() {
    whenever(messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC)) doReturn listener
  }

  @Test
  fun testAddComment() = runTest {
    val body = "test"

    val prv = createProvider()
    prv.addComment(body)
    verify(commentsService, times(1)).addComment(eq(PR_ID), eq(body))
    verify(listener, times(1)).onCommentAdded()
  }

  @Test
  fun testUpdateComment() = runTest {
    val id = "id"
    val body = "test"

    whenever(commentsService.updateComment(eq(id), any())) doReturn GHComment("", null, body, Date(), mock())

    val prv = createProvider()
    prv.updateComment(id, body)
    verify(commentsService, times(1)).updateComment(eq(id), eq(body))
    verify(listener, times(1)).onCommentUpdated(eq(id), eq(body))
  }

  @Test
  fun testDeleteComment() = runTest {
    val id = "id"
    val prv = createProvider()
    prv.deleteComment(id)
    verify(commentsService, times(1)).deleteComment(eq(id))
    verify(listener, times(1)).onCommentDeleted(eq(id))
  }
}