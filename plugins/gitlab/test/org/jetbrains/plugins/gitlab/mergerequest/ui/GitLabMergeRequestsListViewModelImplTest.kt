// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestsListViewModel.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.testutil.MainDispatcherRule
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*

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
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "", avatarIconsProvider = mock(), delayingLoader {
      emptyList<GitLabMergeRequestShortDTO>() to true
    })

    with(vm) {
      val updates = cs.asyncCollectLast(listDataFlow)

      assertFalse(loadingState.value)
      requestMore()
      assertTrue(loadingState.value)
      advanceTimeBy(3000)
      assertInstanceOf(ListDataUpdate.NewBatch::class.java, updates.first())
      assertTrue(canLoadMoreState.value)
      assertFalse(loadingState.value)
    }
    cs.cancel()
  }

  @Test
  fun `reset while loading`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "", avatarIconsProvider = mock(), delayingLoader {
      emptyList<GitLabMergeRequestShortDTO>() to true
    })

    with(vm) {
      val updates = cs.asyncCollectLast(listDataFlow)

      assertFalse(loadingState.value)
      requestMore()
      assertTrue(loadingState.value)
      reset()
      advanceTimeBy(3000)
      assertFalse(loadingState.value)
      assertTrue(canLoadMoreState.value)
      assertNull(errorState.value)
      assertInstanceOf(ListDataUpdate.Clear::class.java, updates.first())
    }
    cs.cancel()
  }

  @Test
  fun `reset after loading`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "", avatarIconsProvider = mock(), delayingLoader {
      emptyList<GitLabMergeRequestShortDTO>() to true
    })

    with(vm) {
      val updates = cs.asyncCollectLast(listDataFlow)

      assertFalse(loadingState.value)
      requestMore()
      assertTrue(loadingState.value)
      advanceTimeBy(3000)
      assertFalse(loadingState.value)
      assertInstanceOf(ListDataUpdate.NewBatch::class.java, updates.first())
      reset()
      assertTrue(canLoadMoreState.value)
      assertNull(errorState.value)
      assertInstanceOf(ListDataUpdate.Clear::class.java, updates.first())
    }
    cs.cancel()
  }

  @Test
  fun `request processing continues after loading cancellation`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "", avatarIconsProvider = mock(), delayingLoader {
      throw CancellationException()
    })

    with(vm) {
      requestMore()
      assertTrue(loadingState.value)
      advanceTimeBy(3000)
      assertFalse(loadingState.value)
      assertNull(errorState.value)
      requestMore()
      assertTrue(loadingState.value)
      assertNull(errorState.value)
    }
    cs.cancel()
  }

  @Test
  fun `error state`() = runTest {
    val cs = childScope()
    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "", avatarIconsProvider = mock(), delayingLoader {
      error("test")
    })

    with(vm) {
      assertNull(errorState.value)
      requestMore()
      assertNull(errorState.value)
      advanceTimeBy(3000)
      assertNotNull(errorState.value)
      assertFalse(canLoadMoreState.value)
    }
    cs.cancel()
  }

  private fun filterVmMock(): GitLabMergeRequestsFiltersViewModel = mock {
    on(it.searchState).thenReturn(MutableStateFlow(GitLabMergeRequestsFiltersValue.EMPTY))
  }
}

private fun delayingLoader(delay: Long = 2000,
                           loader: suspend () -> Pair<List<GitLabMergeRequestShortDTO>, Boolean>)
  : (GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestShortDTO> = {
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