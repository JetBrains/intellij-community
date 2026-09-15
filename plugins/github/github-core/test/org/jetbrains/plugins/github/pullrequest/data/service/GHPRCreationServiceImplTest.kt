// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import git4idea.GitRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.graphql.query.GHGQLSearchQueryResponse
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestBranchMatch
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubConfusingException
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GHPRCreationServiceImplTest {
  private val repositoryDataService = mockk<GHPRRepositoryDataService>(relaxed = true) {
    every { repositoryMapping } returns GHGitRepositoryMapping(repositoryCoordinates, mockk<GitRemoteUrlCoordinates>(relaxed = true))
  }
  private val requestExecutor = mockk<GithubApiRequestExecutor>()
  private val headBranch = mockk<GitRemoteBranch> {
    every { nameForRemoteOperations } returns "feature-branch"
  }

  private val service = GHPRCreationServiceImpl(requestExecutor, repositoryDataService)

  @BeforeEach
  fun setUp() {
    mockkStatic("org.jetbrains.plugins.github.api.GithubApiRequestExecutorKt")
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
    unmockkStatic("org.jetbrains.plugins.github.api.GithubApiRequestExecutorKt")
  }

  @Test
  fun `test matches are returned when the search succeeds`() = runTest {
    val match = GHPullRequestBranchMatch("id", 1, "title", "feature-branch")
    coEvery { requestExecutor.execute<GHGQLSearchQueryResponse<GHPullRequestBranchMatch>>(any()) } returns
      GHGQLSearchQueryResponse(GHGQLSearchQueryResponse.SearchConnection(GraphQLCursorPageInfoDTO(null, false, null, false), listOf(match)))

    val result = service.findOpenPullRequestsByHeadBranches(listOf(headBranch))

    assertEquals(listOf(match), result)
  }

  @Test
  fun `test an empty list is returned when the server does not support the search syntax`() = runTest {
    coEvery { requestExecutor.execute<GHGQLSearchQueryResponse<GHPullRequestBranchMatch>>(any()) } throws
      GithubConfusingException("Argument \"type\" has an invalid value")

    val result = service.findOpenPullRequestsByHeadBranches(listOf(headBranch))

    assertEquals(emptyList<GHPullRequestBranchMatch>(), result)
  }

  @Test
  fun `test other exceptions are not swallowed`() = runTest {
    coEvery { requestExecutor.execute<GHGQLSearchQueryResponse<GHPullRequestBranchMatch>>(any()) } throws
      GithubAuthenticationException("Bad credentials")

    try {
      service.findOpenPullRequestsByHeadBranches(listOf(headBranch))
      fail("Expected a GithubAuthenticationException to be thrown")
    }
    catch (_: GithubAuthenticationException) {
      // expected
    }
  }
}
