// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.intellij.collaboration.async.timeoutRunBlockingWithBackgroundScope
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer
import com.intellij.collaboration.util.SequenceComputer.ComputationOutcome
import com.intellij.collaboration.util.SequenceItem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRTimelineDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem as GHPRTimelineItemDTO

@TestApplication
class GHPRTimelineViewModelImplListStateTest {
  private companion object {
    val projectFixture = projectFixture()
    val PR_ID = GHPRIdentifier("id", 0)
    val EXCEPTION = object : RuntimeException("TEST_TIMELINE_LOADING_EXCEPTION") {}
  }

  private val project get() = projectFixture.get()

  @Test
  fun `loading does not start until it is requested`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computeCalls = AtomicInteger(0)
    val computerCreated = CompletableDeferred<Unit>()
    val timelineData = FakeTimelineDataProvider().apply {
      computerSupplier = {
        computerCreated.complete(Unit)
        newComputer(computeCalls, listOf<GHPRTimelineItemDTO>(commit("a")))
      }
    }
    val vm = createFixture(bg, timelineData).vm

    computerCreated.await() // the loader is created eagerly on VM construction...
    assertEquals(0, computeCalls.get()) // ...but no page is computed until it is requested
    assertFalse(vm.isLoading.value)
    assertTrue(vm.timelineItems.value.isEmpty())

    // once requested, it does load
    vm.timelineItems.test {
      assertTrue(awaitItem().isEmpty()) // the initial empty value
      vm.requestMore()
      assertEquals(listOf("a"), awaitUntil { it.isNotEmpty() }.commitIds())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a timeline update signal makes the view model poll for more items`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computeCounter = AtomicInteger()
    val computer = newComputer(computeCounter, listOf(commit("a")), listOf(commit("b")))
    val timelineData = FakeTimelineDataProvider().apply {
      computerSupplier = { computer }
    }
    val vm = createFixture(bg, timelineData).vm

    vm.isLoading.test {
      timelineData.timelineUpdateSignal.emit(Unit)
      awaitUntil { it }
      awaitUntil { !it }
      assertEquals(1, computeCounter.get())

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `timelineItems reflects the items loaded from the timeline data provider`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val timelineData = FakeTimelineDataProvider().apply {
      computerSupplier = { newComputer(arrayOf(commit("a"), commit("b")).toList<GHPRTimelineItemDTO>()) }
    }
    val vm = createFixture(bg, timelineData).vm

    vm.timelineItems.test {
      vm.requestMore()
      val items = awaitUntil { it.isNotEmpty() }

      // the two consecutive commits are grouped into a single Commits item by the merging model
      assertEquals(1, items.size)
      val item = items.single()
      assertTrue(item is GHPRTimelineItem.Commits, "expected a Commits item but was $item")
      assertEquals(listOf("a", "b"), (item as GHPRTimelineItem.Commits).commits.map { it.id })
      cancelAndIgnoreRemainingEvents()
    }
    assertFalse(vm.isLoading.value)
    assertNull(vm.loadingError.value)
  }

  @Test
  fun `loadingError reflects a failure of the timeline computer`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val timelineData = FakeTimelineDataProvider().apply {
      computerSupplier = { throwingComputer(EXCEPTION) }
    }
    val vm = createFixture(bg, timelineData).vm

    vm.loadingError.test {
      vm.requestMore()
      val error = awaitUntil { it != null }
      assertEquals(EXCEPTION, error)
      cancelAndIgnoreRemainingEvents()
    }
    assertTrue(vm.timelineItems.value.isEmpty())
  }

  @Test
  fun `a timeline change signal recreates the loader and re-reads the sequence`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val timelineData = FakeTimelineDataProvider().apply {
      computerSupplier = { newComputer(arrayOf(commit("a")).toList<GHPRTimelineItemDTO>()) }
    }
    val vm = createFixture(bg, timelineData).vm

    vm.timelineItems.test {
      vm.requestMore()
      awaitUntil { it.isNotEmpty() } // the first page is loaded

      // the whole sequence changed on the backend
      timelineData.computerSupplier = { newComputer(arrayOf(commit("b")).toList<GHPRTimelineItemDTO>()) }
      vm.updateAll()

      val reloaded = awaitUntil { it.commitIds() == listOf("b") }
      assertEquals(1, reloaded.size)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(2, timelineData.getTimelineItemsCallCount) // a fresh computer was requested for the reload
  }

  @Test
  fun `reloading a fully loaded timeline does not surface a smaller partial list`() =
    timeoutRunBlockingWithBackgroundScope { bg ->
      // a two-page timeline, so the list is genuinely built up incrementally page by page
      val timelineData = FakeTimelineDataProvider().apply {
        computerSupplier = { newComputer(listOf(commit("a")), listOf(commit("b"))) }
      }
      val vm = createFixture(bg, timelineData).vm

      vm.timelineItems.test {
        // the first load is incremental: page 1, then the complete list
        vm.requestMore()
        awaitUntil { it.isNotEmpty() }.commitIds()
        vm.requestMore()
        awaitUntil { it.commitIds() == listOf("a", "b") }.commitIds()

        // the list is fully loaded; a reload that fetches updated data must not shrink back to a partial page
        timelineData.computerSupplier = { newComputer(listOf(commit("x")), listOf(commit("y"))) }
        vm.updateAll()

        // record everything emitted until the reload has fully completed; the `vm.requestMore()` is only a safety net
        // in case a partial page is (incorrectly) surfaced and needs driving further
        val duringReload = buildList {
          while (true) {
            val ids = awaitItem().commitIds()
            add(ids)
            if (ids == listOf("x", "y")) break
            vm.requestMore()
          }
        }
        assertEquals(listOf(listOf("x", "y")), duringReload, "reload surfaced partial pages: $duringReload")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `updateAll reloads details, timeline and reviews`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val timelineData = FakeTimelineDataProvider()
    val fixture = createFixture(bg, timelineData)

    fixture.vm.updateAll()

    timelineData.reloadRequested.await()
    coVerify(timeout = 5_000) {
      fixture.detailsData.signalDetailsNeedReload()
      fixture.detailsData.signalMergeabilityNeedsReload()
      fixture.reviewData.signalThreadsNeedReload()
    }
  }

  private class Fixture(
    val vm: GHPRTimelineViewModelImpl,
    val detailsData: GHPRDetailsDataProvider,
    val reviewData: GHPRReviewDataProvider,
  )

  private fun createFixture(parentScope: CoroutineScope, timelineData: GHPRTimelineDataProvider): Fixture {
    val dataContext = mockk<GHPRDataContext>(relaxed = true)
    val detailsData = mockk<GHPRDetailsDataProvider>(relaxed = true) {
      every { detailsNeedReloadSignal } returns emptyFlow()
      // keep the (eagerly created) details view model in the loading state so it never maps real details
      coEvery { loadDetails() } coAnswers { awaitCancellation() }
    }
    val reviewData = mockk<GHPRReviewDataProvider>(relaxed = true)
    val dataProvider = mockk<GHPRDataProvider>(relaxed = true)
    every { dataProvider.id } returns PR_ID
    every { dataProvider.timelineData } returns timelineData
    every { dataProvider.detailsData } returns detailsData
    every { dataProvider.reviewData } returns reviewData

    val viewModelWithTextCompletion = mockk<GHViewModelWithTextCompletion>(relaxed = true)
    val vm = GHPRTimelineViewModelImpl(project, parentScope, dataContext, dataProvider, viewModelWithTextCompletion)
    return Fixture(vm, detailsData, reviewData)
  }

  /** Consumes items until [predicate] holds, returning the matching item. */
  private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
      val item = awaitItem()
      if (predicate(item)) return item
    }
  }

  private fun List<GHPRTimelineItem>.commitIds(): List<String> =
    (singleOrNull() as? GHPRTimelineItem.Commits)?.commits?.map { it.id }.orEmpty()

  private fun commit(id: String): GHPullRequestCommitShort = GHPullRequestCommitShort(id, mockk(relaxed = true), "url-$id")

  private fun throwingComputer(error: Throwable): SequenceComputer<ListPart<GHPRTimelineItemDTO>> =
    object : SequenceComputer<ListPart<GHPRTimelineItemDTO>> {
      override suspend fun computeNext(): ComputationOutcome<ListPart<GHPRTimelineItemDTO>> = throw error
    }

  private fun newComputer(vararg pages: List<GHPRTimelineItemDTO>): SequenceComputer<ListPart<GHPRTimelineItemDTO>> =
    newComputer(AtomicInteger(), *pages)

  /** A computer that emits [pages] one [ListPart] at a time, flagging the last one, then finishes. */
  private fun newComputer(
    counter: AtomicInteger,
    vararg pages: List<GHPRTimelineItemDTO>,
  ): SequenceComputer<ListPart<GHPRTimelineItemDTO>> =
    object : SequenceComputer<ListPart<GHPRTimelineItemDTO>> {
      private var index = 0
      override suspend fun computeNext(): ComputationOutcome<ListPart<GHPRTimelineItemDTO>> {
        counter.incrementAndGet()
        if (index >= pages.size) return ComputationOutcome.Done
        val part = SequenceItem(pages[index], isLast = index == pages.lastIndex)
        index++
        return ComputationOutcome.Item(part)
      }
    }

  private class FakeTimelineDataProvider : GHPRTimelineDataProvider {
    @Volatile
    var computerSupplier: () -> SequenceComputer<ListPart<GHPRTimelineItemDTO>> = { emptyComputer() }
    var getTimelineItemsCallCount: Int = 0
      private set
    val reloadRequested = CompletableDeferred<Unit>()

    override val timelineChangeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val timelineUpdateSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun getTimelineItems(): SequenceComputer<ListPart<GHPRTimelineItemDTO>> {
      getTimelineItemsCallCount++
      return computerSupplier()
    }

    override fun signalTimelineNeedsReload() {
      timelineChangeSignal.tryEmit(Unit)
      reloadRequested.complete(Unit)
    }

    private fun emptyComputer(): SequenceComputer<ListPart<GHPRTimelineItemDTO>> =
      object : SequenceComputer<ListPart<GHPRTimelineItemDTO>> {
        override suspend fun computeNext(): ComputationOutcome<ListPart<GHPRTimelineItemDTO>> = ComputationOutcome.Done
      }
  }
}
