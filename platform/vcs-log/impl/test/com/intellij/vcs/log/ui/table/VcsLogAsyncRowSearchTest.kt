// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.ui.table.VcsLogAsyncRowSearch.Direction
import com.intellij.vcs.log.ui.table.VcsLogAsyncRowSearch.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds

class VcsLogAsyncRowSearchTest {
  @Test
  fun `near match is found synchronously`() = withSearch(matchingRows = setOf(3)) { rows, search ->
    assertEquals(3, search.find("a", 0, Direction.FIND, cycleScrolling = false))
    assertSame(State.Idle, search.state)
    assertTrue(rows.found.isEmpty())
  }

  @Test
  fun `deep match is found in background and reported`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJob(search)

    assertEquals(listOf(80 to "a"), rows.found)
    assertSame(State.Idle, search.state)
  }

  @Test
  fun `deep match is not reported when pattern changed meanwhile`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    rows.pattern = "ab"
    awaitJob(search)

    assertTrue(rows.found.isEmpty())
    assertSame(State.Idle, search.state)
  }

  @Test
  fun `full scan without match ends in no match`() = withSearch(matchingRows = emptySet()) { rows, search ->
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJob(search)

    val state = search.state as State.NoMatch
    assertEquals("a", state.pattern)
    assertEquals(ROW_COUNT, rows.matchCalls.get())
    assertTrue(rows.found.isEmpty())
  }

  @Test
  fun `extended pattern after no match returns at once`() = withSearch(matchingRows = emptySet()) { rows, search ->
    search.find("a", 0, Direction.FIND, cycleScrolling = false)
    awaitJob(search)
    val calls = rows.matchCalls.get()

    assertNull(search.find("ab", 5, Direction.FIND, cycleScrolling = false))
    assertNull(search.find("ab", 5, Direction.NEXT, cycleScrolling = false))
    assertEquals(calls, rows.matchCalls.get())
    assertTrue(search.state is State.NoMatch)

    assertNull(search.find("b", 0, Direction.FIND, cycleScrolling = false))
    assertTrue(rows.matchCalls.get() > calls)
  }

  @Test
  fun `same key twice starts one job`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    search.find("a", 0, Direction.FIND, cycleScrolling = false)
    val job = (search.state as State.Running).job
    val calls = rows.matchCalls.get()

    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    assertSame(job, (search.state as State.Running).job)
    assertEquals(calls, rows.matchCalls.get())

    job.join()
    assertEquals(listOf(80 to "a"), rows.found)
  }

  @Test
  fun `new pattern cancels old job`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    search.find("a", 0, Direction.FIND, cycleScrolling = false)
    val oldJob = (search.state as State.Running).job

    rows.pattern = "b"
    assertNull(search.find("b", 0, Direction.FIND, cycleScrolling = false))
    val newJob = (search.state as State.Running).job

    assertTrue(oldJob.isCancelled)
    assertNotSame(oldJob, newJob)
    newJob.join()
    assertEquals(listOf(80 to "b"), rows.found)
  }

  @Test
  fun `context change stops job`() = withSearch(matchingRows = setOf(90)) { rows, search ->
    rows.onMatch = { commitId -> if (commitId == commitIdOf(40)) rows.context = 2 }
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJob(search)

    assertSame(State.Idle, search.state)
    assertTrue(rows.found.isEmpty())
  }

  @Test
  fun `context change drops no match`() = withSearch(matchingRows = emptySet()) { rows, search ->
    search.find("a", 0, Direction.FIND, cycleScrolling = false)
    awaitJob(search)
    val calls = rows.matchCalls.get()

    rows.context = 2
    assertNull(search.find("ab", 0, Direction.FIND, cycleScrolling = false))
    assertTrue(rows.matchCalls.get() > calls)
  }

  @Test
  fun `next and previous follow selection and cycle scrolling`() = withSearch(matchingRows = setOf(1, 3), budget = Long.MAX_VALUE) { _, search ->
    assertEquals(3, search.find("a", 1, Direction.NEXT, cycleScrolling = false))
    assertEquals(3, search.find("a", 3, Direction.NEXT, cycleScrolling = false))
    assertEquals(1, search.find("a", 3, Direction.NEXT, cycleScrolling = true))
    assertEquals(1, search.find("a", 3, Direction.PREVIOUS, cycleScrolling = false))
    assertEquals(1, search.find("a", 1, Direction.PREVIOUS, cycleScrolling = false))
    assertEquals(3, search.find("a", 1, Direction.PREVIOUS, cycleScrolling = true))
    assertNull(search.find("a", -1, Direction.PREVIOUS, cycleScrolling = true))
    assertEquals(1, search.find("a", -1, Direction.FIND, cycleScrolling = false))
    assertEquals(3, search.find("a", 2, Direction.FIND, cycleScrolling = false))
  }

  @Test
  fun `deep next match is found in background`() = withSearch(matchingRows = setOf(5)) { rows, search ->
    assertNull(search.find("a", 10, Direction.NEXT, cycleScrolling = true))
    awaitJob(search)

    assertEquals(listOf(5 to "a"), rows.found)
  }

  @Test
  fun `row order matches SpeedSearchBase`() {
    fun order(start: Int, direction: Direction, cycle: Boolean): List<Int> =
      VcsLogAsyncRowSearch.segments(5, start, direction, cycle).flatMap { it.toList() }

    assertEquals(listOf(2, 3, 4, 0, 1), order(2, Direction.FIND, false))
    assertEquals(listOf(0, 1, 2, 3, 4), order(-1, Direction.FIND, false))
    assertEquals(listOf(3, 4, 2), order(2, Direction.NEXT, false))
    assertEquals(listOf(3, 4, 0, 1, 2), order(2, Direction.NEXT, true))
    assertEquals(listOf(0, 1, 2, 3, 4), order(-1, Direction.NEXT, true))
    assertEquals(listOf(1, 0, 2), order(2, Direction.PREVIOUS, false))
    assertEquals(listOf(1, 0, 4, 3, 2), order(2, Direction.PREVIOUS, true))
    assertEquals(emptyList<Int>(), order(-1, Direction.PREVIOUS, true))
  }

  private fun withSearch(
    matchingRows: Set<Int>,
    budget: Long = SYNC_ROWS,
    body: suspend CoroutineScope.(FakeRows, VcsLogAsyncRowSearch<String>) -> Unit,
  ) = runBlocking {
    // the runBlocking event loop plays EDT: the test body and the EDT parts of the search share its thread
    val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
    try {
      val rows = FakeRows(matchingRows.mapTo(HashSet()) { commitIdOf(it) })
      val clock = AtomicLong()
      val search = VcsLogAsyncRowSearch(scope, rows, coroutineContext[ContinuationInterceptor]!!, Dispatchers.Default,
                                        syncBudgetNanos = budget, chunkSize = CHUNK_SIZE, nanoTime = { clock.incrementAndGet() })
      withTimeout(30.seconds) {
        body(rows, search)
      }
    }
    finally {
      scope.cancel()
    }
  }

  private suspend fun awaitJob(search: VcsLogAsyncRowSearch<String>) {
    val job: Job = (search.state as State.Running).job
    job.join()
  }

  private class FakeRows(private val matchingIds: Set<VcsLogCommitStorageIndex>) : VcsLogAsyncRowSearch.Rows<String> {
    @Volatile
    var context: Any = 1
    var pattern: String? = "a"
    @Volatile
    var onMatch: (VcsLogCommitStorageIndex) -> Unit = {}
    val matchCalls = AtomicInteger()
    val found = CopyOnWriteArrayList<Pair<Int, String>>()

    override fun rowCount(): Int = ROW_COUNT
    override fun commitIdAt(row: Int): VcsLogCommitStorageIndex = commitIdOf(row)
    override fun contextKey(): Any = context
    override fun snapshot(pattern: String): String = pattern

    override fun matches(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: String): Boolean {
      assertEquals(pattern, snapshot)
      matchCalls.incrementAndGet()
      onMatch(commitId)
      return commitId in matchingIds
    }

    override fun currentPattern(): String? = pattern

    override fun onFound(row: Int, pattern: String) {
      found.add(row to pattern)
    }
  }

  private companion object {
    const val ROW_COUNT = 100
    const val SYNC_ROWS = 10L
    const val CHUNK_SIZE = 16

    fun commitIdOf(row: Int): VcsLogCommitStorageIndex = 1000 + row
  }
}
