// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

// TODO: Take some of this to create new tests for ListLoader in collab

//import com.intellij.collaboration.api.page.SequentialListLoader
//import com.intellij.platform.util.coroutines.childScope
//import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.MutableSharedFlow
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.test.UnconfinedTestDispatcher
//import kotlinx.coroutines.test.advanceTimeBy
//import kotlinx.coroutines.test.runTest
//import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
//import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
//import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
//import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel.ListDataUpdate
//import org.junit.Test
//import org.junit.jupiter.api.Assertions
//import org.mockito.kotlin.doSuspendableAnswer
//import org.mockito.kotlin.mock
//
//@OptIn(ExperimentalCoroutinesApi::class)
//internal class GitLabMergeRequestsListViewModelImplTest {
//
//  @Test
//  fun `loading state`() = runTest(UnconfinedTestDispatcher()) {
//    val cs = childScope()
//    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
//                                                  avatarIconsProvider = mock(),
//                                                  tokenRefreshFlow = mock(),
//                                                  loaderSupplier = delayingLoader { emptyList<GitLabMergeRequestDetails>() to true })
//
//    with(vm) {
//      val updates = cs.asyncCollectLast(listDataFlow)
//
//      requestMore()
//      Assertions.assertTrue(loading.first())
//      advanceTimeBy(3000)
//      Assertions.assertInstanceOf(ListDataUpdate.NewBatch::class.java, updates.first())
//      Assertions.assertFalse(loading.first())
//    }
//    cs.cancel()
//  }
//
//  @Test
//  fun `refresh while loading`() = runTest(UnconfinedTestDispatcher()) {
//    val cs = childScope()
//    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
//                                                  avatarIconsProvider = mock(),
//                                                  tokenRefreshFlow = mock(),
//                                                  delayingLoader { emptyList<GitLabMergeRequestDetails>() to true })
//
//    with(vm) {
//      val updates = cs.asyncCollectLast(listDataFlow)
//
//      requestMore()
//      Assertions.assertTrue(loading.first())
//      refresh()
//      Assertions.assertInstanceOf(ListDataUpdate.Clear::class.java, updates.first())
//      advanceTimeBy(3000)
//      Assertions.assertFalse(loading.first())
//      Assertions.assertNull(error.first())
//      Assertions.assertInstanceOf(ListDataUpdate.NewBatch::class.java, updates.first())
//    }
//    cs.cancel()
//  }
//
//  @Test
//  fun `refresh after loading`() = runTest(UnconfinedTestDispatcher()) {
//    val cs = childScope()
//    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
//                                                  avatarIconsProvider = mock(),
//                                                  tokenRefreshFlow = mock(),
//                                                  delayingLoader { emptyList<GitLabMergeRequestDetails>() to true })
//
//    with(vm) {
//      val updates = cs.asyncCollectLast(listDataFlow)
//
//      requestMore()
//      Assertions.assertTrue(loading.first())
//      advanceTimeBy(3000)
//      Assertions.assertFalse(loading.first())
//      Assertions.assertInstanceOf(ListDataUpdate.NewBatch::class.java, updates.first())
//
//      refresh()
//      Assertions.assertTrue(loading.first())
//      Assertions.assertNull(error.first())
//      Assertions.assertInstanceOf(ListDataUpdate.Clear::class.java, updates.first())
//      advanceTimeBy(3000)
//      Assertions.assertInstanceOf(ListDataUpdate.NewBatch::class.java, updates.first())
//      Assertions.assertFalse(loading.first())
//    }
//    cs.cancel()
//  }
//
//  @Test
//  fun `request processing continues after loading cancellation`() = runTest {
//    val cs = childScope()
//    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
//                                                  avatarIconsProvider = mock(),
//                                                  tokenRefreshFlow = mock(),
//                                                  delayingLoader { throw CancellationException() })
//
//    with(vm) {
//      requestMore()
//      Assertions.assertTrue(loading.first())
//      advanceTimeBy(3000)
//      Assertions.assertFalse(loading.first())
//      Assertions.assertNull(error.first())
//      requestMore()
//      Assertions.assertTrue(loading.first())
//      Assertions.assertNull(error.first())
//    }
//    cs.cancel()
//  }
//
//  @Test
//  fun `error state`() = runTest(UnconfinedTestDispatcher()) {
//    val cs = childScope()
//    val vm = GitLabMergeRequestsListViewModelImpl(cs, filterVmMock(), repository = "",
//                                                  avatarIconsProvider = mock(),
//                                                  tokenRefreshFlow = mock(),
//                                                  delayingLoader { error("test") })
//
//    with(vm) {
//      Assertions.assertNull(error.first())
//      requestMore()
//      Assertions.assertNull(error.first())
//      advanceTimeBy(3000)
//      Assertions.assertNotNull(error.first())
//    }
//    cs.cancel()
//  }
//
//  private fun filterVmMock(): GitLabMergeRequestsFiltersViewModel = mock {
//    on(it.searchState).thenReturn(MutableStateFlow(GitLabMergeRequestsFiltersValue.EMPTY))
//  }
//}
//
//private fun delayingLoader(delay: Long = 2000,
//                           loader: suspend () -> Pair<List<GitLabMergeRequestDetails>, Boolean>)
//  : (GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestDetails> = {
//  mock {
//    onBlocking { loadNext() } doSuspendableAnswer {
//      delay(delay)
//      val (data, hasMore) = loader()
//      SequentialListLoader.ListBatch(data, hasMore)
//    }
//  }
//}
//
//private fun <T> CoroutineScope.asyncCollectLast(flow: Flow<T>): Flow<T> {
//  val result = MutableSharedFlow<T>(replay = 1)
//  childScope().async {
//    flow.collect {
//      result.emit(it)
//    }
//  }
//  return result
//}