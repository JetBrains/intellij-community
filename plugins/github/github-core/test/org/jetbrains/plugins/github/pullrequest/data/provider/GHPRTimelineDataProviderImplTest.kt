// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import app.cash.turbine.test
import com.intellij.collaboration.async.timeoutRunBlockingWithBackgroundScope
import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer
import com.intellij.collaboration.util.SequenceComputer.ComputationOutcome
import com.intellij.collaboration.util.SequenceItem
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.SimpleMessageBusConnection
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRTimelineService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class GHPRTimelineDataProviderImplTest {
  companion object {
    private val PR_ID = GHPRIdentifier("id", 0)
  }

  /** A [ComputableSequence] whose backing parts can change between walks; records how many computers it hands out. */
  private class FakeTimelineSequence(
    @Volatile var parts: List<List<GHPRTimelineItem>>,
  ) : ComputableSequence<ListPart<GHPRTimelineItem>> {
    var computersCreated: Int = 0
      private set

    override fun getComputer(): SequenceComputer<ListPart<GHPRTimelineItem>> {
      computersCreated++
      val snapshot = parts.mapIndexed { i, list -> SequenceItem(list, isLast = i == parts.lastIndex) }
      return object : SequenceComputer<ListPart<GHPRTimelineItem>> {
        private var index = 0
        override suspend fun computeNext(): ComputationOutcome<ListPart<GHPRTimelineItem>> =
          if (index < snapshot.size) ComputationOutcome.Item(snapshot[index++]) else ComputationOutcome.Done
      }
    }
  }

  private class Fixture(
    val provider: GHPRTimelineDataProvider,
    val listener: GHPRDataOperationsListener,
    val sequence: FakeTimelineSequence,
  )

  private fun createFixture(parentCs: CoroutineScope, vararg parts: List<GHPRTimelineItem>): Fixture {
    val sequence = FakeTimelineSequence(parts.toList())
    val service = mockk<GHPRTimelineService> {
      every { getTimelineItems(PR_ID) } returns sequence
    }
    val listenerSlot = slot<GHPRDataOperationsListener>()
    val connection = mockk<SimpleMessageBusConnection> {
      every { subscribe(GHPRDataOperationsListener.TOPIC, capture(listenerSlot)) } just Runs
    }
    val messageBus = mockk<MessageBus> {
      every { connect(any<CoroutineScope>()) } returns connection
    }

    val provider = GHPRTimelineDataProviderImpl(parentCs, service, messageBus, PR_ID)
    return Fixture(provider, listenerSlot.captured, sequence)
  }

  private fun comment(id: String, body: String): GHIssueComment =
    GHIssueComment(id, null, body, Date(0), mockk(relaxed = true), false, false, false)

  private fun review(id: String, body: String): GHPullRequestReview =
    GHPullRequestReview(id, "url", null, body, GHPullRequestReviewState.COMMENTED, Date(0), false)

  /** Fully drains the "infinite" timeline computer into a flat list of items. */
  private suspend fun SequenceComputer<ListPart<GHPRTimelineItem>>.drainItems(): List<GHPRTimelineItem> = buildList {
    while (true) {
      when (val outcome = computeNext()) {
        is ComputationOutcome.Item -> addAll(outcome.value.value)
        ComputationOutcome.Done -> break
      }
    }
  }

  @Test
  fun `getTimelineItems yields the items produced by the service`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val comment = comment("c1", "hello")
    val review = review("r1", "review")
    val fixture = createFixture(bg, listOf(comment, review))

    val items = fixture.provider.getTimelineItems().drainItems()

    assertEquals(listOf(comment, review), items)
  }

  @Test
  fun `onCommentUpdated updates the cached comment body and signals a change`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val fixture = createFixture(bg, listOf(comment("c1", "old"), review("r1", "review")))
    fixture.provider.getTimelineItems().drainItems() // materialize the cache so the in-place update has something to mutate

    fixture.provider.timelineChangeSignal.test {
      fixture.listener.onCommentUpdated("c1", "new")
      awaitItem() // the change is signalled only after the update has been applied to the cache
      cancelAndIgnoreRemainingEvents()
    }

    val items = fixture.provider.getTimelineItems().drainItems()
    assertEquals("new", items.filterIsInstance<GHIssueComment>().single().body)
    assertEquals("review", items.filterIsInstance<GHPullRequestReview>().single().body) // the review is left untouched
  }

  @Test
  fun `onReviewUpdated updates the cached review body`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val fixture = createFixture(bg, listOf(comment("c1", "comment"), review("r1", "old")))
    fixture.provider.getTimelineItems().drainItems()

    fixture.provider.timelineChangeSignal.test {
      fixture.listener.onReviewUpdated("r1", "edited")
      awaitItem()
      cancelAndIgnoreRemainingEvents()
    }

    val items = fixture.provider.getTimelineItems().drainItems()
    assertEquals("edited", items.filterIsInstance<GHPullRequestReview>().single().body)
    assertEquals("comment", items.filterIsInstance<GHIssueComment>().single().body)
  }

  @Test
  fun `onCommentDeleted resets the loader so the sequence is re-read`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val fixture = createFixture(bg, listOf(comment("c1", "a"), comment("c2", "b")))
    fixture.provider.getTimelineItems().drainItems()
    assertEquals(1, fixture.sequence.computersCreated)

    // the deletion happened on the backend: the provider drops the cache and reloads the (now shorter) timeline
    fixture.sequence.parts = listOf(listOf(comment("c2", "b")))
    fixture.listener.onCommentDeleted("c1")

    val items = fixture.provider.getTimelineItems().drainItems()
    assertEquals(listOf("b"), items.filterIsInstance<GHIssueComment>().map { it.body })
    assertEquals(2, fixture.sequence.computersCreated) // reloaded through a fresh computer
  }

  @Test
  fun `signalTimelineNeedsReload resets the loader and re-reads fresh data`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val fixture = createFixture(bg, listOf(comment("c1", "old")))
    fixture.provider.getTimelineItems().drainItems()

    fixture.sequence.parts = listOf(listOf(comment("c1", "reloaded")))

    fixture.provider.timelineChangeSignal.test {
      fixture.provider.signalTimelineNeedsReload()
      awaitItem()
      cancelAndIgnoreRemainingEvents()
    }

    val items = fixture.provider.getTimelineItems().drainItems()
    assertEquals(listOf("reloaded"), items.filterIsInstance<GHIssueComment>().map { it.body })
    assertEquals(2, fixture.sequence.computersCreated)
  }

  @Test
  fun `onCommentAdded emits an update signal`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val fixture = createFixture(bg, listOf(comment("c1", "a")))

    fixture.provider.timelineUpdateSignal.test {
      fixture.listener.onCommentAdded()
      assertEquals(Unit, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `onMetadataChanged emits an update signal`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val fixture = createFixture(bg, listOf(comment("c1", "a")))

    fixture.provider.timelineUpdateSignal.test {
      fixture.listener.onMetadataChanged()
      assertEquals(Unit, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
