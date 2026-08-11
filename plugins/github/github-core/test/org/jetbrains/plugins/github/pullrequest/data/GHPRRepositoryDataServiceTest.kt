// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.BatchesLoader
import git4idea.remote.GitRemoteUrlCoordinates
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHRepositoryOwnerName
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GHPRRepositoryDataServiceTest {
  private val testTeam = GHTeam("", "", "", "", "", "")
  private val requestExecutor = mockk<GithubApiRequestExecutor> {
    every { addListener(any(), any()) } returns Unit
  }
  private val remoteCoordinates = mockk<GitRemoteUrlCoordinates>(relaxed = true)
  private val repositoryCoordinates = GHRepositoryCoordinates(
    GithubServerPath.DEFAULT_SERVER,
    GHRepositoryPath("owner", "repo")
  )

  private fun TestScope.createService(
    repoOwner: GHRepositoryOwnerName,
  ): GHPRRepositoryDataServiceImpl =
    GHPRRepositoryDataServiceImpl(
      parentCs = backgroundScope,
      requestExecutor = requestExecutor,
      remoteCoordinates = remoteCoordinates,
      repositoryCoordinates = repositoryCoordinates,
      repoOwner = repoOwner,
      repositoryId = "repoId",
      defaultBranchName = null,
      isFork = false,
    )

  @Test
  fun `loadBatchedTeams for user-owned repo emits empty list without requesting teams`() = runTest {
    val service = createService(GHRepositoryOwnerName.User("owner"))
    val result = service.loadBatchedTeams().single()

    assertEquals(emptyList<GHTeam>(), result)
  }

  @Test
  fun `loadBatchedTeams for organizations doesn't short-circuit`() = runTest {
    mockkConstructor(BatchesLoader::class)
    try {
      val expectedResult = listOf(testTeam)
      every {
        anyConstructed<BatchesLoader<GHTeam>>().getBatches()
      } returns flowOf(expectedResult)

      val service = createService(GHRepositoryOwnerName.Organization("owner"))
      val result = service.loadBatchedTeams().single()

      assertEquals(expectedResult, result)
    }
    finally {
      unmockkConstructor(BatchesLoader::class)
    }
  }
}