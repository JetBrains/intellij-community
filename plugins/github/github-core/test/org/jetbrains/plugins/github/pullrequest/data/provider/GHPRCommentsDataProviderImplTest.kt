package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.MainDispatcherRule
import com.intellij.util.messages.MessageBus
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.data.GHComment
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import org.junit.ClassRule
import org.junit.Test
import java.util.*

class GHPRCommentsDataProviderImplTest {
  companion object {
    private val PR_ID = GHPRIdentifier("id", 0)

    @JvmField
    @ClassRule
    internal val mainRule = MainDispatcherRule()
  }

  private val commentsService = mockk<GHPRCommentService>(relaxed = true)
  private val listener = mockk<GHPRDataOperationsListener>(relaxUnitFun = true)
  private val messageBus = mockk<MessageBus> {
    every { syncPublisher(GHPRDataOperationsListener.TOPIC) } returns listener
  }

  private fun createProvider(): GHPRCommentsDataProvider =
    GHPRCommentsDataProviderImpl(commentsService, PR_ID, messageBus)

  @Test
  fun testAddComment() = runTest {
    val body = "test"

    val prv = createProvider()
    prv.addComment(body)
    coVerifyAll {
      commentsService.addComment(eq(PR_ID), eq(body))
      listener.onCommentAdded()
    }
  }

  @Test
  fun testUpdateComment() = runTest {
    val id = "id"
    val body = "test"

    coEvery { commentsService.updateComment(eq(id), any()) } returns GHComment("", null, body, Date(), mockk())

    val prv = createProvider()
    prv.updateComment(id, body)
    coVerifyAll {
      commentsService.updateComment(eq(id), eq(body))
      listener.onCommentUpdated(eq(id), eq(body))
    }
  }

  @Test
  fun testDeleteComment() = runTest {
    val id = "id"
    val prv = createProvider()
    prv.deleteComment(id)
    coVerifyAll {
      commentsService.deleteComment(eq(id))
      listener.onCommentDeleted(eq(id))
    }
  }
}
