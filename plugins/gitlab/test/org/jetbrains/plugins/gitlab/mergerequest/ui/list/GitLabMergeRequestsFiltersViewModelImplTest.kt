// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.util.childScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabMergeRequestsListLoader
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.testutil.MainDispatcherRule
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

internal class GitLabMergeRequestsFiltersViewModelImplTest {
  companion object {
    @JvmField
    @ClassRule
    val mainRule = MainDispatcherRule()
  }

  private val mockedUser: GitLabUserDTO = mock {
    on(it.username).thenReturn("username")
    on(it.name).thenReturn("name")
  }

  @Test
  fun `check initial default filter search state`() = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    val loaderSupplierMock = mock<(GitLabMergeRequestsFiltersValue) -> GitLabMergeRequestsListLoader>()

    // Init a list VM with mocked loader
    GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, repository = "",
                                         avatarIconsProvider = mock(),
                                         tokenRefreshFlow = mock(),
                                         loaderSupplier = loaderSupplierMock)
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
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    val loaderSupplierMock = mock<(GitLabMergeRequestsFiltersValue) -> GitLabMergeRequestsListLoader>()

    // Init a list VM with mocked loader
    GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, repository = "",
                                         avatarIconsProvider = mock(),
                                         tokenRefreshFlow = mock(),
                                         loaderSupplier = loaderSupplierMock)

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
  fun `check participant filters`() = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    val loaderSupplierMock = mock<(GitLabMergeRequestsFiltersValue) -> GitLabMergeRequestsListLoader>()

    GitLabMergeRequestsListViewModelImpl(parentCs = cs, filterVm = filterVm, repository = "",
                                         avatarIconsProvider = mock(),
                                         tokenRefreshFlow = mock(),
                                         loaderSupplier = loaderSupplierMock)

    val user = GitLabUserDTO(id = "", username = "", name = "", avatarUrl = "", webUrl = "")
    val member = GitLabMemberDTO(id = "", user = user, accessLevel = GitLabAccessLevel.GUEST)
    verifyFilterParticipantSelect(filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      author = MergeRequestsAuthorFilterValue(member.user.username, member.user.name)
    ))
    verifyFilterParticipantSelect(filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.CLOSED,
      assignee = MergeRequestsAssigneeFilterValue(member.user.username, member.user.name)
    ))
    verifyFilterParticipantSelect(filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      reviewer = MergeRequestsReviewerFilterValue(member.user.username, member.user.name)
    ))
    verifyFilterParticipantSelect(filterVm, loaderSupplierMock, GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.MERGED,
      author = MergeRequestsAuthorFilterValue(member.user.username, member.user.name),
      assignee = MergeRequestsAssigneeFilterValue(member.user.username, member.user.name),
      reviewer = MergeRequestsReviewerFilterValue(member.user.username, member.user.name)
    ))

    cs.cancel()
  }

  @Test
  fun `check default filter`() = runTest {
    val cs = childScope()
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(), projectData = mock())
    assertEquals(filterVm.searchState.value, GitLabMergeRequestsFiltersValue.DEFAULT)

    cs.cancel()
  }

  @Test
  fun `select empty state filter`() = runTest {
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

  @Test
  fun `available participants for merge request filters`() = runTest {
    val cs = childScope()

    val guest = GitLabMemberDTO(id = "guest", mock(), accessLevel = GitLabAccessLevel.GUEST)
    val reporter = GitLabMemberDTO(id = "reporter", mock(), accessLevel = GitLabAccessLevel.REPORTER)
    val developer = GitLabMemberDTO(id = "developer", mock(), accessLevel = GitLabAccessLevel.DEVELOPER)
    val maintainer = GitLabMemberDTO(id = "maintainer", mock(), accessLevel = GitLabAccessLevel.MAINTAINER)
    val owner = GitLabMemberDTO(id = "owner", mock(), accessLevel = GitLabAccessLevel.OWNER)
    val members = listOf(guest, reporter, developer, maintainer, owner)

    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(scope = cs, historyModel = mock(), currentUser = mockedUser,
                                                           avatarIconsProvider = mock(),
                                                           projectData = mock {
                                                             on(it.getMembers()).thenReturn(members)
                                                           })
    assertEquals(filterVm.getMergeRequestMembers(), members - guest)

    cs.cancel()
  }

  private fun checkSelectedFilter(selectedFilter: GitLabMergeRequestsFiltersValue) = runTest {
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

  private fun verifyFilterParticipantSelect(
    filterVm: GitLabMergeRequestsFiltersViewModelImpl,
    loaderSupplier: (GitLabMergeRequestsFiltersValue) -> GitLabMergeRequestsListLoader,
    filter: GitLabMergeRequestsFiltersValue
  ) {
    filterVm.searchState.value = filter
    verify(loaderSupplier, times(1)).invoke(filter)
  }
}