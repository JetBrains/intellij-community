// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestsListLoader
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.testutil.MainDispatcherRule
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestsFiltersViewModelImplTest {
  companion object {
    @JvmField
    @ClassRule
    val mainRule = MainDispatcherRule()
  }

  @Test
  fun `check initial default filter search state`() = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock())
    val loaderSupplierMock = mock<(GitLabMergeRequestsFiltersValue) -> GitLabMergeRequestsListLoader>()

    // Init a list VM with mocked loader
    GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, loaderSupplier = loaderSupplierMock)
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.DEFAULT)
    clearInvocations(loaderSupplierMock)

    // Default filter
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.DEFAULT
    verify(loaderSupplierMock, times(0)).invoke(GitLabMergeRequestsFiltersValue.DEFAULT)

    // Default filter
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED)
    verify(loaderSupplierMock, times(0)).invoke(GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED))

    // Change filter from Default
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.EMPTY)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.DEFAULT
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.DEFAULT)

    cs.cancel()
  }

  @Test
  fun `check changed filter search state`() = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock())
    val loaderSupplierMock = mock<(GitLabMergeRequestsFiltersValue) -> GitLabMergeRequestsListLoader>()

    // Init a list VM with mocked loader
    GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, loaderSupplier = loaderSupplierMock)

    val filterValueStateMerged = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.MERGED)
    filterVm.searchState.value = filterValueStateMerged
    verify(loaderSupplierMock, times(1)).invoke(filterValueStateMerged)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.EMPTY)

    val filterValueStateClosed = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.CLOSED)
    filterVm.searchState.value = filterValueStateClosed
    verify(loaderSupplierMock, times(1)).invoke(filterValueStateClosed)

    cs.cancel()
  }

  @Test
  fun `check default filter`() = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock())
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.DEFAULT)

    cs.cancel()
  }

  @Test
  fun `select empty state filter`() = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock())
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.DEFAULT)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.EMPTY)

    cs.cancel()
  }

  @Test
  fun `select opened state filter`() = checkSelectedFilter(GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED))

  @Test
  fun `select closed state filter`() = checkSelectedFilter(GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.CLOSED))

  @Test
  fun `select merged state filter`() = checkSelectedFilter(GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.MERGED))

  private fun checkSelectedFilter(selectedFilter: GitLabMergeRequestsFiltersValue) = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock())
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.DEFAULT)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.EMPTY)

    filterVm.searchState.value = selectedFilter
    assertEquals(filterVm.searchState.value, selectedFilter)

    cs.cancel()
  }
}