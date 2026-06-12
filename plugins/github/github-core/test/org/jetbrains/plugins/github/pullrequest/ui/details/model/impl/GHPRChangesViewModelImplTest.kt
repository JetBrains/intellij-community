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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Date

@TestApplication
class GHPRChangesViewModelImplTest {
  private companion object {
    val projectFixture = projectFixture()

    val EXCEPTION = object : RuntimeException("TEST_LOADING_EXCEPTION") {}
  }

  private val project get() = projectFixture.get()

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `selected commit becomes null when commits empty on failed reload`() = timeoutRunBlocking {
    val reloadSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var fail = false
    val dataProvider = createDataProvider(reloadSignal) { if (fail) throw EXCEPTION else 5 }

    val vmScope = childScope("GHPRChangesViewModelImplTest")
    try {
      val vm = createViewModel(vmScope, dataProvider)
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
    val reloadSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var commitCount = 5
    val dataProvider = createDataProvider(reloadSignal) { commitCount }

    val vmScope = childScope("GHPRChangesViewModelImplTest")
    try {
      val vm = createViewModel(vmScope, dataProvider)
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

  private fun createViewModel(parentScope: CoroutineScope, dataProvider: GHPRDataProvider): GHPRChangesViewModelImpl {
    mockkObject(GHPRStatisticsCollector)
    every { GHPRStatisticsCollector.logDetailsCommitChosen(any()) } just Runs
    val dataContext = mockk<GHPRDataContext>(relaxed = true)
    return GHPRChangesViewModelImpl(parentScope, project, dataContext, dataProvider) { _, _ -> }
  }

  /**
   * [GHPRDataProvider] whose `loadCommits()`/`loadChanges()` return [commitCount] consistent commits, where
   * [commitCount] is read lazily on each load so the test can change it (or throw) across reloads.
   */
  private fun createDataProvider(reloadSignal: MutableSharedFlow<Unit>, commitCount: () -> Int): GHPRDataProvider =
    mockk<GHPRDataProvider>(relaxed = true).apply {
      every { changesData.changesNeedReloadSignal } returns reloadSignal
      coEvery { changesData.loadCommits() } coAnswers { createCommits(commitCount()) }
      coEvery { changesData.loadChanges() } coAnswers { createComparisonResult(commitCount()) }
      // Keep the change-list view model's details flow well-defined and empty (no repository access).
      every { reviewData.threadsNeedReloadSignal } returns emptyFlow()
      coEvery { reviewData.loadThreads() } returns emptyList()
      every { viewedStateData.viewedStateNeedsReloadSignal } returns emptyFlow()
      coEvery { viewedStateData.loadViewedState() } returns emptyMap()
    }

  private fun createComparisonResult(count: Int): GitBranchComparisonResult = mockk {
    every { changes } returns emptyList()
    every { commits } returns (1..count).map { GitCommitShaWithPatches("oid$it", emptyList(), emptyList()) }
    every { changesByCommits } returns emptyMap()
  }
}

private fun createCommits(count: Int): List<GHCommit> = (1..count).map { i ->
  GHCommit(
    id = "commit$i",
    oid = "oid$i",
    abbreviatedOid = "oid$i",
    url = "mockUrl$i",
    messageHeadline = "Mock Commit Headline $i",
    messageBody = "Mock Commit Body $i",
    author = mockk(),
    committer = mockk(),
    committedDate = Date(),
    parents = GraphQLNodesDTO(listOf(GHCommitHash("", "p$i", "p$i")))
  )
}
