// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.intellij.collaboration.async.timeoutRunBlockingWithBackgroundScope
import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer
import com.intellij.collaboration.util.SequenceItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class GitLabMergeRequestsListViewModelImplTest {

  @Test
  fun `loads the first page on creation`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val page = listOf(mockDetails(), mockDetails())
    val loader = TestLoader()
    val loaderSupplier = mockLoaderSupplier(loader.sequence)
    val vm = createListVm(bg, loaderSupplier)

    vm.listDataFlow.test {
      assertEquals(emptyList<GitLabMergeRequestDetails>(), awaitItem())
      loader.emitPage(page)
      assertEquals(page, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    verify { loaderSupplier.invoke(GitLabMergeRequestsFiltersValue.EMPTY) }
  }

  @Test
  fun `request more appends the next page`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val firstPage = listOf(mockDetails())
    val secondPage = listOf(mockDetails())
    val loader = TestLoader()
    val vm = createListVm(bg, mockLoaderSupplier(loader.sequence))

    vm.listDataFlow.test {
      assertEquals(emptyList<GitLabMergeRequestDetails>(), awaitItem())
      loader.emitPage(firstPage)
      assertEquals(firstPage, awaitItem())

      vm.requestMore()
      loader.emitPage(secondPage)
      assertEquals(firstPage + secondPage, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `reports loading state while a page is being loaded`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = TestLoader()
    val vm = createListVm(bg, mockLoaderSupplier(loader.sequence))
    assertFalse(vm.loading.value) // nothing is loading before the coroutines get to run

    vm.loading.test {
      awaitUntil { it }             // load of the first page is in progress (parked on the permit)
      loader.emitPage(listOf(mockDetails()))
      awaitUntil { !it }            // the page has finished loading
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `reports the loader error`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val error = RuntimeException("loading failed")
    val loader = TestLoader()
    val vm = createListVm(bg, mockLoaderSupplier(loader.sequence))

    vm.error.test {
      assertNull(awaitItem())
      loader.emitError(error)
      assertSame(error, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(emptyList<GitLabMergeRequestDetails>(), vm.listDataFlow.value)
  }

  @Test
  fun `reloads with a new loader when the filter changes`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val defaultPage = listOf(mockDetails())
    val openedPage = listOf(mockDetails())
    val openedFilter = GitLabMergeRequestsFiltersValue(state = GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue.OPENED)
    val defaultLoader = TestLoader()
    val openedLoader = TestLoader()
    val searchState = MutableStateFlow(GitLabMergeRequestsFiltersValue.EMPTY)
    val loaderSupplier = mockk<LoaderSupplier> {
      every { this@mockk.invoke(GitLabMergeRequestsFiltersValue.EMPTY) } returns defaultLoader.sequence
      every { this@mockk.invoke(openedFilter) } returns openedLoader.sequence
    }
    val vm = createListVm(bg, loaderSupplier, mockFilterVm(searchState))

    vm.listDataFlow.test {
      assertEquals(emptyList<GitLabMergeRequestDetails>(), awaitItem())
      defaultLoader.emitPage(defaultPage)
      assertEquals(defaultPage, awaitItem())

      searchState.value = openedFilter
      assertEquals(emptyList<GitLabMergeRequestDetails>(), awaitItem()) // the list is reset for the new loader
      openedLoader.emitPage(openedPage)
      assertEquals(openedPage, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    verify { loaderSupplier.invoke(GitLabMergeRequestsFiltersValue.EMPTY) }
    verify { loaderSupplier.invoke(openedFilter) }
  }

  @Test
  fun `refresh reloads the list from the start`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val firstLoad = listOf(mockDetails())
    val secondLoad = listOf(mockDetails())
    val loader = TestLoader()
    val vm = createListVm(bg, mockLoaderSupplier(loader.sequence))

    vm.listDataFlow.test {
      assertEquals(emptyList<GitLabMergeRequestDetails>(), awaitItem())
      loader.emitPage(firstLoad)
      assertEquals(firstLoad, awaitItem())

      vm.refresh()
      assertEquals(emptyList<GitLabMergeRequestDetails>(), awaitItem()) // the list is reset while a fresh computer starts
      loader.emitPage(secondLoad)
      assertEquals(secondLoad, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun createListVm(
    cs: CoroutineScope,
    loaderSupplier: LoaderSupplier,
    filterVm: GitLabMergeRequestsFiltersViewModel = mockFilterVm(MutableStateFlow(GitLabMergeRequestsFiltersValue.EMPTY)),
  ): GitLabMergeRequestsListViewModelImpl =
    GitLabMergeRequestsListViewModelImpl(
      parentCs = cs,
      filterVm = filterVm,
      repository = "",
      avatarIconsProvider = mockk(),
      loaderSupplier = loaderSupplier,
    )

  private fun mockFilterVm(searchState: MutableStateFlow<GitLabMergeRequestsFiltersValue>): GitLabMergeRequestsFiltersViewModel =
    mockk { every { this@mockk.searchState } returns searchState }

  private fun mockLoaderSupplier(sequence: ComputableSequence<ListPart<GitLabMergeRequestDetails>>): LoaderSupplier =
    mockk { every { this@mockk.invoke(any()) } returns sequence }

  private fun mockDetails(): GitLabMergeRequestDetails = mockk()

  private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
      val item = awaitItem()
      if (predicate(item)) return item
    }
  }

  /**
   * A [ComputableSequence] whose every `computeNext` parks until the test releases the next result via [emitPage]/[emitError],
   * so the caller controls exactly when each page load completes. Results are queued, so an `emit*` may be called before or
   * after the loader asks for the next item. The permit channel is shared across computers, hence a reload consumes the next
   * queued result as well.
   */
  private class TestLoader {
    private val loads = Channel<suspend () -> List<GitLabMergeRequestDetails>>(Channel.UNLIMITED)

    val sequence = ComputableSequence {
      object : SequenceComputer<ListPart<GitLabMergeRequestDetails>> {
        override suspend fun computeNext(): SequenceComputer.ComputationOutcome<ListPart<GitLabMergeRequestDetails>> =
          SequenceComputer.ComputationOutcome.Item(SequenceItem(loads.receive().invoke(), isLast = false))
      }
    }

    suspend fun emitPage(page: List<GitLabMergeRequestDetails>) {
      loads.send { page }
    }

    suspend fun emitError(error: Throwable) {
      loads.send { throw error }
    }
  }
}

private typealias LoaderSupplier = (GitLabMergeRequestsFiltersValue) -> ComputableSequence<ListPart<GitLabMergeRequestDetails>>
