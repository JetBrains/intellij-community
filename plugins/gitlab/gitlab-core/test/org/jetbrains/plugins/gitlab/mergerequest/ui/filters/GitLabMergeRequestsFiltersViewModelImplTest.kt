// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.async.ReloadablePotentiallyInfiniteListLoader
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModelImpl
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestsFiltersViewModelImplTest {

  private val mockedUser: GitLabUserDTO = mockk {
    every { username } returns "username"
    every { name } returns "name"
  }
  private val defaultFilter: GitLabMergeRequestsFiltersValue = GitLabMergeRequestsFiltersViewModelImpl.defaultQuickFilter(mockedUser).filter
  private val emptyHistoryModel: GitLabMergeRequestsFiltersHistoryModel = mockk<GitLabMergeRequestsFiltersHistoryModel>(relaxed = true) {
    every { lastFilter } returns null
  }

  @Test
  fun `check initial default filter search state`() = runTest(UnconfinedTestDispatcher()) {
    val projectData = mockk<GitLabProject> {
      every { dataReloadSignal } returns MutableSharedFlow()
      every { getLabelsBatches() } returns flow {}
      every { getMembersBatches() } returns flow {}
    }
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = backgroundScope, project = null,
                                                           historyModel = emptyHistoryModel, currentUser = mockedUser,
                                                           avatarIconsProvider = mockk(), projectData = projectData)
    val loaderSupplierMock = mockLoaderSupplier()

    // Init a list VM with mocked loader
    val vm = GitLabMergeRequestsListViewModelImpl(parentCs = backgroundScope, filterVm = filterVm, repository = "",
                                                  avatarIconsProvider = mockk(),
                                                  tokenRefreshFlow = mockk(relaxed = true),
                                                  loaderSupplier = loaderSupplierMock)
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplierMock.invoke(any(), eq(defaultFilter)) }

    // Default filter
    filterVm.searchState.value = defaultFilter
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplierMock.invoke(any(), eq(defaultFilter)) }
    confirmVerified(loaderSupplierMock)

    // Default filter
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      assignee = MergeRequestsAssigneeFilterValue(mockedUser.username, mockedUser.name)
    )
    vm.awaitLoader()

    // Change filter from Default
    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplierMock.invoke(any(), eq(GitLabMergeRequestsFiltersValue.EMPTY)) }

    filterVm.searchState.value = defaultFilter
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplierMock.invoke(any(), eq(defaultFilter)) }
  }

  @Test
  fun `check changed filter search state`() = runTest(UnconfinedTestDispatcher()) {
    val projectData = mockk<GitLabProject> {
      every { dataReloadSignal } returns MutableSharedFlow()
      every { getLabelsBatches() } returns flow {}
      every { getMembersBatches() } returns flow {}
    }
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = backgroundScope, project = null,
                                                           historyModel = emptyHistoryModel, currentUser = mockedUser,
                                                           avatarIconsProvider = mockk(), projectData = projectData)
    val loaderSupplierMock = mockLoaderSupplier()

    // Init a list VM with mocked loader
    val vm = GitLabMergeRequestsListViewModelImpl(parentCs = backgroundScope, filterVm = filterVm, repository = "",
                                                  avatarIconsProvider = mockk(),
                                                  tokenRefreshFlow = mockk(relaxed = true),
                                                  loaderSupplier = loaderSupplierMock)

    val filterValueStateMerged = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.MERGED)
    filterVm.searchState.value = filterValueStateMerged
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplierMock.invoke(any(), eq(filterValueStateMerged)) }

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplierMock.invoke(any(), eq(GitLabMergeRequestsFiltersValue.EMPTY))  }

    val filterValueStateClosed = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.CLOSED)
    filterVm.searchState.value = filterValueStateClosed
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplierMock.invoke(any(), eq(filterValueStateClosed)) }
  }

  @Test
  fun `check participant filters`() = runTest(UnconfinedTestDispatcher()) {
    val projectData = mockk<GitLabProject> {
      every { dataReloadSignal } returns MutableSharedFlow()
      every { getLabelsBatches() } returns flow {}
      every { getMembersBatches() } returns flow {}
    }
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = backgroundScope, project = null,
                                                           historyModel = emptyHistoryModel, currentUser = mockedUser,
                                                           avatarIconsProvider = mockk(), projectData = projectData)
    val loaderSupplierMock = mockLoaderSupplier()

    val vm = GitLabMergeRequestsListViewModelImpl(parentCs = backgroundScope, filterVm = filterVm, repository = "",
                                                  avatarIconsProvider = mockk(),
                                                  tokenRefreshFlow = mockk(relaxed = true),
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
  }

  @Test
  fun `check default filter`() = runTest(UnconfinedTestDispatcher()) {
    val projectData = mockk<GitLabProject> {
      every { dataReloadSignal } returns MutableSharedFlow()
      every { getLabelsBatches() } returns flow {}
      every { getMembersBatches() } returns flow {}
    }
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = backgroundScope, project = null,
                                                           historyModel = emptyHistoryModel, currentUser = mockedUser,
                                                           avatarIconsProvider = mockk(), projectData = projectData)
    assertEquals(filterVm.searchState.value, defaultFilter)
  }

  @Test
  fun `select empty state filter`() = runTest(UnconfinedTestDispatcher()) {
    val projectData = mockk<GitLabProject> {
      every { dataReloadSignal } returns MutableSharedFlow()
      every { getLabelsBatches() } returns flow {}
      every { getMembersBatches() } returns flow {}
    }
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = backgroundScope, project = null,
                                                           historyModel = emptyHistoryModel, currentUser = mockedUser,
                                                           avatarIconsProvider = mockk(), projectData = projectData)
    assertEquals(filterVm.searchState.value, defaultFilter)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.EMPTY)
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
    val projectData = mockk<GitLabProject> {
      every { dataReloadSignal } returns MutableSharedFlow()
      every { getLabelsBatches() } returns flow {}
      every { getMembersBatches() } returns flow {}
    }
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = backgroundScope, project = null,
                                                           historyModel = emptyHistoryModel, currentUser = mockedUser,
                                                           avatarIconsProvider = mockk(), projectData = projectData)
    assertEquals(filterVm.searchState.value, defaultFilter)

    filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.EMPTY)

    filterVm.searchState.value = selectedFilter
    assertEquals(filterVm.searchState.value, selectedFilter)
  }

  private suspend fun verifyFilterParticipantSelect(
    vm: GitLabMergeRequestsListViewModel,
    filterVm: GitLabMergeRequestsFiltersViewModelImpl,
    loaderSupplier: (CoroutineScope, GitLabMergeRequestsFiltersValue) -> ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>,
    filter: GitLabMergeRequestsFiltersValue
  ) {
    filterVm.searchState.value = filter
    vm.awaitLoader()
    verify(exactly = 1) { loaderSupplier.invoke(any(), eq(filter)) }
  }

  private fun mockLoaderSupplier(): (CoroutineScope, GitLabMergeRequestsFiltersValue) -> ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO> {
    val mockLoader = mockk<ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>> {
      every { isBusyFlow } returns MutableStateFlow(false)
    }
    return mockk<(CoroutineScope, GitLabMergeRequestsFiltersValue) -> ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>> {
      every { this@mockk.invoke(any(), any()) } returns mockLoader
    }
  }

  private suspend fun GitLabMergeRequestsListViewModel.awaitLoader() {
    loading.first()
  }
}