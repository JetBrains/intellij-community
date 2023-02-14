// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.testutil.MainDispatcherRule
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestsListViewModelImplTest {
  companion object {
    @JvmField
    @ClassRule
    val mainRule = MainDispatcherRule()
  }

  @Test
  fun `loading state`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
                                                  account = mock(),
                                                  avatarIconsProvider = mock(),
                                                  accountManager = mock(),
                                                  tokenRefreshFlow = mock(),
                                                  loaderSupplier = delayingLoader { emptyList<GitLabMergeRequestDetails>() to true })

    with(vm) {
      val updates = cs.asyncCollectLast(listDataFlow)

      Assertions.assertFalse(loadingState.value)
      requestMore()
      Assertions.assertTrue(loadingState.value)
      advanceTimeBy(3000)
      Assertions.assertInstanceOf(GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch::class.java, updates.first())
      Assertions.assertTrue(canLoadMoreState.value)
      Assertions.assertFalse(loadingState.value)
    }
    cs.cancel()
  }

  @Test
  fun `refresh while loading`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
                                                  account = mock(),
                                                  avatarIconsProvider = mock(),
                                                  accountManager = mock(),
                                                  tokenRefreshFlow = mock(),
                                                  delayingLoader { emptyList<GitLabMergeRequestDetails>() to true })

    with(vm) {
      val updates = cs.asyncCollectLast(listDataFlow)

      Assertions.assertFalse(loadingState.value)
      requestMore()
      Assertions.assertTrue(loadingState.value)
      refresh()
      Assertions.assertInstanceOf(GitLabMergeRequestsListViewModel.ListDataUpdate.Clear::class.java, updates.first())
      advanceTimeBy(3000)
      Assertions.assertFalse(loadingState.value)
      Assertions.assertTrue(canLoadMoreState.value)
      Assertions.assertNull(errorState.value)
      Assertions.assertInstanceOf(GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch::class.java, updates.first())
    }
    cs.cancel()
  }

  @Test
  fun `refresh after loading`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
                                                  account = mock(),
                                                  avatarIconsProvider = mock(),
                                                  accountManager = mock(),
                                                  tokenRefreshFlow = mock(),
                                                  delayingLoader { emptyList<GitLabMergeRequestDetails>() to true })

    with(vm) {
      val updates = cs.asyncCollectLast(listDataFlow)

      Assertions.assertFalse(loadingState.value)
      requestMore()
      Assertions.assertTrue(loadingState.value)
      advanceTimeBy(3000)
      Assertions.assertFalse(loadingState.value)
      Assertions.assertInstanceOf(GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch::class.java, updates.first())

      refresh()
      Assertions.assertTrue(loadingState.value)
      Assertions.assertNull(errorState.value)
      Assertions.assertInstanceOf(GitLabMergeRequestsListViewModel.ListDataUpdate.Clear::class.java, updates.first())
      advanceTimeBy(3000)
      Assertions.assertInstanceOf(GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch::class.java, updates.first())
      Assertions.assertFalse(loadingState.value)
      Assertions.assertTrue(canLoadMoreState.value)
    }
    cs.cancel()
  }

  @Test
  fun `request processing continues after loading cancellation`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
                                                  account = mock(),
                                                  avatarIconsProvider = mock(),
                                                  accountManager = mock(),
                                                  tokenRefreshFlow = mock(),
                                                  delayingLoader { throw CancellationException() })

    with(vm) {
      requestMore()
      Assertions.assertTrue(loadingState.value)
      advanceTimeBy(3000)
      Assertions.assertFalse(loadingState.value)
      Assertions.assertNull(errorState.value)
      requestMore()
      Assertions.assertTrue(loadingState.value)
      Assertions.assertNull(errorState.value)
    }
    cs.cancel()
  }

  @Test
  fun `error state`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
                                                  account = mock(),
                                                  avatarIconsProvider = mock(),
                                                  accountManager = mock(),
                                                  tokenRefreshFlow = mock(),
                                                  delayingLoader { error("test") })

    with(vm) {
      Assertions.assertNull(errorState.value)
      requestMore()
      Assertions.assertNull(errorState.value)
      advanceTimeBy(3000)
      Assertions.assertNotNull(errorState.value)
      Assertions.assertFalse(canLoadMoreState.value)
    }
    cs.cancel()
  }

  private fun filterVmMock(): GitLabMergeRequestsFiltersViewModel = mock {
    on(it.searchState).thenReturn(MutableStateFlow(GitLabMergeRequestsFiltersValue.EMPTY))
  }
}

private fun delayingLoader(delay: Long = 2000,
                           loader: suspend () -> Pair<List<GitLabMergeRequestDetails>, Boolean>)
  : (GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestDetails> = {
  mock {
    onBlocking { loadNext() } doSuspendableAnswer {
      delay(delay)
      val (data, hasMore) = loader()
      SequentialListLoader.ListBatch(data, hasMore)
    }
  }
}

private fun <T> CoroutineScope.asyncCollectLast(flow: Flow<T>): Flow<T> {
  val result = MutableSharedFlow<T>(replay = 1)
  childScope().async(Dispatchers.Main) {
    flow.collect {
      result.emit(it)
    }
  }
  return result
}