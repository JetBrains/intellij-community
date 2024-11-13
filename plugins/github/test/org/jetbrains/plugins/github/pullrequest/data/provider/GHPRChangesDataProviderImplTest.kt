// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.FlowTestUtil.assertEmits
import com.intellij.collaboration.util.MainDispatcherRule
import git4idea.changes.GitBranchComparisonResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import kotlin.time.Duration.Companion.seconds

class GHPRChangesDataProviderImplTest {
  companion object {
    private val PR_ID = GHPRIdentifier("id", 0)
    private val EXCEPTION = object : RuntimeException("TEST_LOADING_EXCEPTION") {}

    @JvmField
    @ClassRule
    internal val mainRule = MainDispatcherRule()
  }

  private val changesService = mockk<GHPRChangesService>()

  private fun TestScope.createProvider(refs: GHPRBranchesRefs): GHPRChangesDataProvider =
    GHPRChangesDataProviderImpl(backgroundScope, changesService, { refs }, PR_ID)

  @Test
  fun testCachingChangesAndCommitsLoad() = runTest {
    val refs = GHPRBranchesRefs("b", "h")
    val mergeBaseRef = "mb"

    val result = mockk<GitBranchComparisonResult>()
    coEvery { changesService.loadCommitsFromApi(PR_ID) } returns createMockCommits(refs.headRef)
    coEvery { changesService.loadMergeBaseOid(any(), any()) } returns mergeBaseRef
    coEvery { changesService.createChangesProvider(eq(PR_ID), any(), any(), any(), any()) } returns result

    val inst = createProvider(refs)
    assertEquals(inst.loadChanges(), inst.loadChanges())
    assertEquals(inst.loadCommits(), inst.loadCommits())

    coVerify(exactly = 1) {
      changesService.loadCommitsFromApi(eq(PR_ID))
      changesService.createChangesProvider(eq(PR_ID), eq(refs.baseRef), eq(mergeBaseRef), eq(refs.headRef), any())
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testChangesFlow() = runTest(UnconfinedTestDispatcher()) {
    val refs = GHPRBranchesRefs("b", "h")
    val mergeBaseRef = "mb"

    coEvery { changesService.loadCommitsFromApi(PR_ID) } returns createMockCommits(refs.headRef)
    coEvery { changesService.loadMergeBaseOid(any(), any()) } returns mergeBaseRef

    val result = mockk<GitBranchComparisonResult>()
    var counter = 0
    coEvery { changesService.createChangesProvider(eq(PR_ID), any(), any(), any(), any()) } coAnswers {
      if (counter == 0) {
        delay(1.seconds)
        throw EXCEPTION
      }
      else {
        delay(1.seconds)
        result
      }
    }
    val inst = createProvider(refs)
    inst.changesComputationState().assertEmits(ComputedResult.loading(),
                                             ComputedResult.failure(EXCEPTION),
                                             ComputedResult.loading(),
                                             ComputedResult.success(result)) {
      advanceTimeBy(2.seconds)
      counter++
      inst.signalChangesNeedReload()
      advanceTimeBy(2.seconds)
    }
  }
}

private fun createMockCommits(headRef: String): List<GHCommit> {
  val commits = mutableListOf<GHCommit>()

  var previousCommit: GHCommit? = null
  for (i in 1..5) {
    val mockParents = GraphQLNodesDTO(
      listOf(previousCommit?.let { GHCommitHash("", it.oid, it.abbreviatedOid) } ?: GHCommitHash("", "i", "i")),
      totalCount = 1
    )

    val commit = GHCommit(
      id = "commit$i",
      oid = if (i == 5) headRef else "commit_oid_$i",
      abbreviatedOid = "abbreviated_oid_$i",
      url = "mockUrl$i",
      messageHeadline = "Mock Commit Headline $i",
      messageBody = "Mock Commit Body $i",
      author = mockk(),
      committer = mockk(),
      committedDate = Date(),
      parents = mockParents
    )

    commits.add(commit)
    previousCommit = commit
  }
  return commits
}