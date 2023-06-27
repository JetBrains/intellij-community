// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestsFiltersViewModelImplTest {

  private val mockedUser: GitLabUserDTO = mock {
    on(it.username).thenReturn("username")
    on(it.name).thenReturn("name")
  }

  @Test
  fun `check initial default filter search state`() = runTest(UnconfinedTestDispatcher()) {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    val loaderSupplierMock = mockLoaderSupplier()

    // Init a list VM with mocked loader
    val vm = GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, repository = "",

                                                  avatarIconsProvider =  mock(),
                                                  tokenRefreshFlow = mock(),
                                                  loaderSupplier = loaderSupplierMock)
    vm.awaitLoader()
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.DEFAULT)
    clearInvocations(loaderSupplierMock)

    // Default filter
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.DEFAULT
    vm.awaitLoader()
    verify(loaderSupplierMock, times(0)).invoke(GitLabMergeRequestsFiltersValue.DEFAULT)

    // Default filter
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED)
    vm.awaitLoader()
    verify(loaderSupplierMock, times(0)).invoke(GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED))

    // Change filter from Default
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    vm.awaitLoader()
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.EMPTY)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.DEFAULT
    vm.awaitLoader()
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.DEFAULT)

    cs.cancel()
  }



  @Test
  fun `check changed filter search state`() = runTest(UnconfinedTestDispatcher()) {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    val loaderSupplierMock = mockLoaderSupplier()

    // Init a list VM with mocked loader
    val vm = GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, repository = "",

                                                  avatarIconsProvider =  mock(),
                                                  tokenRefreshFlow = mock(),
                                                  loaderSupplier = loaderSupplierMock)

    val filterValueStateMerged = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.MERGED)
    filterVm.searchState.value = filterValueStateMerged
    vm.awaitLoader()
    verify(loaderSupplierMock, times(1)).invoke(filterValueStateMerged)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    vm.awaitLoader()
    verify(loaderSupplierMock, times(1)).invoke(GitLabMergeRequestsFiltersValue.EMPTY)

    val filterValueStateClosed = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.CLOSED)
    filterVm.searchState.value = filterValueStateClosed
    vm.awaitLoader()
    verify(loaderSupplierMock, times(1)).invoke(filterValueStateClosed)

    cs.cancel()
  }

  @Test
  fun `check participant filters`() = runTest(UnconfinedTestDispatcher()) {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    val loaderSupplierMock = mockLoaderSupplier()

    val vm = GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, repository = "",

                                                  avatarIconsProvider =  mock(),
                                                  tokenRefreshFlow = mock(),
                                                  loaderSupplier = loaderSupplierMock)

    val user = GitLabUserDTO(id = "", username = "", name = "", avatarUrl = "", webUrl = "")
    val member = GitLabMemberDTO(id = "", user = user, accessLevel = GitLabAccessLevel.GUEST)
    verifyFilterParticipantSelect(vm, filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      author = MergeRequestsAuthorFilterValue(member.user.username, member.user.name)
    ))
    verifyFilterParticipantSelect(vm, filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.CLOSED,
      assignee = MergeRequestsAssigneeFilterValue(member.user.username, member.user.name)
    ))
    verifyFilterParticipantSelect(vm, filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      reviewer = MergeRequestsReviewerFilterValue(member.user.username, member.user.name)
    ))
    verifyFilterParticipantSelect(vm, filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.MERGED,
      author = MergeRequestsAuthorFilterValue(member.user.username, member.user.name),
      assignee = MergeRequestsAssigneeFilterValue(member.user.username, member.user.name),
      reviewer = MergeRequestsReviewerFilterValue(member.user.username, member.user.name)
    ))

    cs.cancel()
  }

  @Test
  fun `check default filter`() = runTest(UnconfinedTestDispatcher()) {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.DEFAULT)

    cs.cancel()
  }

  @Test
  fun `select empty state filter`() = runTest(UnconfinedTestDispatcher()) {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
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

  @Test
  fun `select custom state filter`() = checkSelectedFilter(
    GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      author = MergeRequestsAuthorFilterValue(username = "authorUsername", fullname = "authorFullname"),
      assignee = MergeRequestsAssigneeFilterValue(username = "assigneeUsername", fullname = "assigneeFullname"),
      reviewer = MergeRequestsReviewerFilterValue(username = "reviewerUsername", fullname = "reviewerFullname"),
    )
  )

  private fun checkSelectedFilter(selectedFilter: GitLabMergeRequestsFiltersValue) = runTest(UnconfinedTestDispatcher()) {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.DEFAULT)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.EMPTY)

    filterVm.searchState.value = selectedFilter
    assertEquals(filterVm.searchState.value, selectedFilter)

    cs.cancel()
  }

  private suspend fun verifyFilterParticipantSelect(
    vm: GitLabMergeRequestsListViewModel,
    filterVm: GitLabMergeRequestsFiltersViewModelImpl,
    loaderSupplier: (GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestDetails>,
    filter: GitLabMergeRequestsFiltersValue
  ) {
    filterVm.searchState.value = filter
    vm.awaitLoader()
    verify(loaderSupplier, times(1)).invoke(filter)
  }

  private fun mockLoaderSupplier(): (GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestDetails> {
    val mockLoader = mock<SequentialListLoader<GitLabMergeRequestDetails>>()
    return mock<(GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestDetails>> {
      whenever(it.invoke(any())).thenReturn(mockLoader)
    }
  }

  private suspend fun GitLabMergeRequestsListViewModel.awaitLoader() {
    loading.first()
  }
}