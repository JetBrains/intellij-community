// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.turbineScope
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitCommitShaWithPatches
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

@TestApplication
class GHPRChangesViewModelImplTest {
  private companion object {
    val projectFixture = projectFixture()
    val EXCEPTION = object : RuntimeException("TEST_LOADING_EXCEPTION") {}
    val COMMIT_COUNTS = listOf(0, 2, 5)
  }

  private val project get() = projectFixture.get()

  // Per-test mutable state, read lazily by the data provider's coAnswers. Reset in @BeforeEach.
  private lateinit var reloadSignal: MutableSharedFlow<Unit>
  private var commitCount: Int = 5
  private var fail: Boolean = false

  // Pre-built (untimed, in @BeforeEach) so nothing inside timeoutRunBlocking ever creates a mock or records a stub.
  private lateinit var dataContext: GHPRDataContext
  private lateinit var dataProvider: GHPRDataProvider
  private lateinit var commitsByCount: Map<Int, List<GHCommit>>
  private lateinit var comparisonByCount: Map<Int, GitBranchComparisonResult>

  @BeforeEach
  fun setUp() {
    // All mockk construction/stubbing happens here, outside the coroutine deadline. The first stub recording
    // triggers a one-time Kotlin-reflection warm-up that can exceed 10s on a cold, contended CI JVM; keeping it
    // out of timeoutRunBlocking prevents it from consuming the test deadline (IJPL-246847).
    reloadSignal = MutableSharedFlow(extraBufferCapacity = 1)
    commitCount = 5
    fail = false

    mockkObject(GHPRStatisticsCollector)
    every { GHPRStatisticsCollector.logDetailsCommitChosen(any()) } just Runs

    commitsByCount = COMMIT_COUNTS.associateWith { buildCommits(it) }
    comparisonByCount = COMMIT_COUNTS.associateWith { buildComparison(it) }

    dataContext = mockk(relaxed = true)
    dataProvider = mockk<GHPRDataProvider>(relaxed = true).apply {
      every { changesData.changesNeedReloadSignal } returns reloadSignal
      coEvery { changesData.loadCommits() } coAnswers { if (fail) throw EXCEPTION else commitsByCount.getValue(commitCount) }
      // Faithful to the original scenario: only loadCommits() fails, so the change list stays loaded while
      // reviewCommits goes empty. loadChanges() keeps returning the initial (5-commit) result on failure.
      coEvery { changesData.loadChanges() } coAnswers { comparisonByCount.getValue(if (fail) 5 else commitCount) }
      // Keep the change-list view model's details flow well-defined and empty (no repository access).
      every { reviewData.threadsNeedReloadSignal } returns emptyFlow()
      coEvery { reviewData.loadThreads() } returns emptyList()
      every { viewedStateData.viewedStateNeedsReloadSignal } returns emptyFlow()
      coEvery { viewedStateData.loadViewedState() } returns emptyMap()
    }
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `selected commit becomes null when commits empty on failed reload`() = timeoutRunBlocking {
    val vmScope = childScope("GHPRChangesViewModelImplTest")
    try {
      val vm = GHPRChangesViewModelImpl(vmScope, project, dataContext, dataProvider) { _, _ -> }
      turbineScope {
        val selected = vm.selectedCommit.testIn(this)
        val index = vm.selectedCommitIndex.testIn(this)

        // Wait until the change list (delegate) has loaded, otherwise selectCommit is a no-op.
        vm.changeListVm.first { !it.isInProgress }
        // Select the last commit.
        vm.selectCommit(4)
        assertEquals("oid5", selected.awaitUntil { it?.oid == "oid5" }?.oid)
        assertEquals(4, index.awaitUntil { it == 4 })

        // Reload fails -> reviewCommits emits emptyList() while a commit was still selected.
        fail = true
        reloadSignal.emit(Unit)
        assertNull(selected.awaitUntil { it == null })
        assertEquals(-1, index.awaitUntil { it == -1 })

        selected.cancelAndIgnoreRemainingEvents()
        index.cancelAndIgnoreRemainingEvents()
      }
    }
    finally {
      vmScope.cancel()
    }
  }

  @Test
  fun `selected commit becomes null when commits shrink on reload`() = timeoutRunBlocking {
    val vmScope = childScope("GHPRChangesViewModelImplTest")
    try {
      val vm = GHPRChangesViewModelImpl(vmScope, project, dataContext, dataProvider) { _, _ -> }
      turbineScope {
        val selected = vm.selectedCommit.testIn(this)
        val index = vm.selectedCommitIndex.testIn(this)

        // Wait until the change list (delegate) has loaded, otherwise selectCommit is a no-op.
        vm.changeListVm.first { !it.isInProgress }
        vm.selectCommit(4)
        assertEquals("oid5", selected.awaitUntil { it?.oid == "oid5" }?.oid)
        assertEquals(4, index.awaitUntil { it == 4 })

        // Branch force-pushed to fewer commits: the previously selected index 4 is now out of range.
        commitCount = 2
        reloadSignal.emit(Unit)
        assertNull(selected.awaitUntil { it == null })
        assertEquals(-1, index.awaitUntil { it == -1 })

        selected.cancelAndIgnoreRemainingEvents()
        index.cancelAndIgnoreRemainingEvents()
      }
    }
    finally {
      vmScope.cancel()
    }
  }

  /** Consumes items until [predicate] holds, returning the matching item. Drives real-time awaiting under [timeoutRunBlocking]. */
  private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
      val item = awaitItem()
      if (predicate(item)) return item
    }
  }

  private fun buildComparison(count: Int): GitBranchComparisonResult = mockk {
    every { changes } returns emptyList()
    every { commits } returns (1..count).map { GitCommitShaWithPatches("oid$it", emptyList(), emptyList()) }
    every { changesByCommits } returns emptyMap()
  }
}

private fun buildCommits(count: Int): List<GHCommit> = (1..count).map { i ->
  GHCommit(
    id = "commit$i",
    oid = "oid$i",
    abbreviatedOid = "oid$i",
    url = "mockUrl$i",
    messageHeadline = "Mock Commit Headline $i",
    messageBody = "Mock Commit Body $i",
    author = null,
    committer = null,
    committedDate = Date(0),
    parents = GraphQLNodesDTO(emptyList())
  )
}
