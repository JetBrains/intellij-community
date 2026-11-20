// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.ui.table.VcsLogAsyncRowSearch.Direction
import com.intellij.vcs.log.ui.table.VcsLogAsyncRowSearch.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    assertEquals(0, rows.prepareCalls.get())
  }

  @Test
  fun `deep match is found in background and reported`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJobs(search)

    assertEquals(listOf(80 to "a"), rows.found)
    val state = search.state as State.Found
    assertEquals(80, state.row)
    assertEquals(commitIdOf(80), state.commitId)
  }

  @Test
  fun `memory phase stops at first row not in memory and job starts there`() =
    withSearch(matchingRows = setOf(50), inMemoryRows = (0..4).toSet() + (6 until ROW_COUNT), budget = Long.MAX_VALUE) { rows, search ->
      assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
      assertEquals((0..4).toList(), rows.memoryRows)
      assertTrue(rows.backgroundRows.isEmpty())

      awaitJobs(search)
      assertEquals((5..50).toList(), rows.backgroundRows)
      assertEquals((0..4).toList(), rows.memoryRows)
      assertFalse(rows.backgroundThreads.contains(rows.edtThread))
      assertEquals(listOf(50 to "a"), rows.found)
    }

  @Test
  fun `search data is prepared once in background`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJobs(search)

    assertEquals(1, rows.prepareCalls.get())
    assertEquals(1, rows.prepareThreads.size)
    assertFalse(rows.prepareThreads.contains(rows.edtThread))
    assertEquals(listOf(80 to "a"), rows.found)
  }

  @Test
  fun `deep match is not reported when pattern changed meanwhile`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    rows.pattern = "ab"
    awaitJobs(search)

    assertTrue(rows.found.isEmpty())
    assertSame(State.Idle, search.state)
  }

  @Test
  fun `full scan without match ends in no match`() = withSearch(matchingRows = emptySet()) { rows, search ->
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJobs(search)

    val state = search.state as State.NoMatch
    assertEquals("a", state.pattern)
    assertEquals(ROW_COUNT, rows.calls())
    assertTrue(rows.found.isEmpty())
  }

  @Test
  fun `extended pattern after no match returns at once`() = withSearch(matchingRows = emptySet()) { rows, search ->
    search.find("a", 0, Direction.FIND, cycleScrolling = false)
    awaitJobs(search)
    val calls = rows.calls()

    assertNull(search.find("ab", 5, Direction.FIND, cycleScrolling = false))
    assertNull(search.find("ab", 5, Direction.NEXT, cycleScrolling = false))
    assertNull(search.find("ab", 5, Direction.LAST, cycleScrolling = false))
    assertEquals(calls, rows.calls())
    assertTrue(search.state is State.NoMatch)

    assertNull(search.find("b", 0, Direction.FIND, cycleScrolling = false))
    assertTrue(rows.calls() > calls)
  }

  @Test
  fun `same key twice starts one job`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    search.find("a", 0, Direction.FIND, cycleScrolling = false)
    val job = (search.state as State.Running).job
    val calls = rows.calls()

    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    assertSame(job, (search.state as State.Running).job)
    assertEquals(calls, rows.calls())

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
  fun `context change with same pattern restarts from selection`() = withSearch(matchingRows = setOf(90)) { rows, search ->
    rows.onMatch = { commitId ->
      if (commitId == commitIdOf(40) && rows.context == 1) {
        rows.matchingIds = setOf(commitIdOf(60))
        rows.selected = 30
        rows.context = 2
      }
    }
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJobs(search)

    assertEquals(listOf(60 to "a"), rows.found)
    assertEquals(2, (search.state as State.Found).contextKey)
    // the restart goes on from the selection
    assertEquals((0..9).toList() + (30..39).toList(), rows.memoryRows)
  }

  @Test
  fun `context change with same pattern restarts and reports synchronous hit`() = withSearch(matchingRows = setOf(90)) { rows, search ->
    rows.onMatch = { commitId ->
      if (commitId == commitIdOf(40) && rows.context == 1) {
        rows.matchingIds = setOf(commitIdOf(32))
        rows.selected = 30
        rows.context = 2
      }
    }
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJobs(search)

    assertEquals(listOf(32 to "a"), rows.found)
    assertSame(State.Idle, search.state)
  }

  @Test
  fun `context change with changed pattern stops job`() = withSearch(matchingRows = setOf(90)) { rows, search ->
    rows.onMatch = { commitId ->
      if (commitId == commitIdOf(40)) {
        rows.pattern = "ab"
        rows.context = 2
      }
    }
    assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
    awaitJobs(search)

    assertSame(State.Idle, search.state)
    assertTrue(rows.found.isEmpty())
  }

  @Test
  fun `context change drops no match`() = withSearch(matchingRows = emptySet()) { rows, search ->
    search.find("a", 0, Direction.FIND, cycleScrolling = false)
    awaitJobs(search)
    val calls = rows.calls()

    rows.context = 2
    assertNull(search.find("ab", 0, Direction.FIND, cycleScrolling = false))
    assertTrue(rows.calls() > calls)
  }

  @Test
  fun `background hit is kept through refresh`() =
    withSearch(matchingRows = setOf(80), inMemoryRows = (0..9).toSet()) { rows, search ->
      val refreshResults = mutableListOf<Int?>()
      rows.onFoundHook = { row, pattern ->
        rows.selected = row
        // what refreshSelection does: the found row is not in memory
        refreshResults.add(search.find(pattern, row, Direction.FIND, cycleScrolling = false))
      }
      assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
      awaitJobs(search)
      val calls = rows.calls()

      assertEquals(listOf<Int?>(80), refreshResults)
      assertEquals(listOf(80 to "a"), rows.found)
      assertTrue(search.state is State.Found)

      assertEquals(80, search.find("a", 80, Direction.FIND, cycleScrolling = false))
      assertEquals(calls, rows.calls())
      assertEquals(1, rows.found.size)

      rows.pattern = "ab"
      assertNull(search.find("ab", 80, Direction.FIND, cycleScrolling = false))
      assertTrue(search.state is State.Running)
    }

  @Test
  fun `next after background hit searches again`() =
    withSearch(matchingRows = setOf(80), inMemoryRows = (0..9).toSet()) { rows, search ->
      assertNull(search.find("a", 0, Direction.FIND, cycleScrolling = false))
      awaitJobs(search)
      assertTrue(search.state is State.Found)

      assertNull(search.find("a", 80, Direction.NEXT, cycleScrolling = false))
      assertTrue(search.state is State.Running)
    }

  @Test
  fun `first cancels pending next search`() = withSearch(matchingRows = setOf(1, 80)) { rows, search ->
    assertNull(search.find("a", 2, Direction.NEXT, cycleScrolling = false))
    val nextJob = (search.state as State.Running).job
    yield() // let the job start

    assertEquals(1, search.find("a", 2, Direction.FIRST, cycleScrolling = false))
    assertTrue(nextJob.isCancelled)
    nextJob.join()

    assertTrue(rows.found.isEmpty())
    assertSame(State.Idle, search.state)
  }

  @Test
  fun `job prepares and matches with the key pattern`() = withSearch(matchingRows = setOf(80)) { rows, search ->
    rows.pattern = "john"
    assertNull(search.find("john", 0, Direction.FIND, cycleScrolling = false))
    awaitJobs(search)

    assertEquals(listOf("john"), rows.preparePatterns)
    assertEquals(listOf(80 to "john"), rows.found)
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
    assertEquals(1, search.find("a", 2, Direction.FIRST, cycleScrolling = false))
    assertEquals(3, search.find("a", 2, Direction.LAST, cycleScrolling = false))
  }

  @Test
  fun `first and last without match end in no match`() = withSearch(matchingRows = emptySet(), budget = Long.MAX_VALUE) { rows, search ->
    assertNull(search.find("a", 5, Direction.FIRST, cycleScrolling = false))
    assertEquals(ROW_COUNT, rows.calls())
    assertTrue(search.state is State.NoMatch)

    rows.context = 2
    assertNull(search.find("a", 5, Direction.LAST, cycleScrolling = false))
    assertEquals(2 * ROW_COUNT, rows.calls())
    assertTrue(search.state is State.NoMatch)
  }

  @Test
  fun `deep last match is found in background`() = withSearch(matchingRows = setOf(5)) { rows, search ->
    assertNull(search.find("a", 50, Direction.LAST, cycleScrolling = false))
    awaitJobs(search)

    assertEquals(listOf(5 to "a"), rows.found)
  }

  @Test
  fun `deep next match is found in background`() = withSearch(matchingRows = setOf(5)) { rows, search ->
    assertNull(search.find("a", 10, Direction.NEXT, cycleScrolling = true))
    awaitJobs(search)

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
    assertEquals(listOf(0, 1, 2, 3, 4), order(2, Direction.FIRST, false))
    assertEquals(listOf(4, 3, 2, 1, 0), order(2, Direction.LAST, true))
  }

  private fun withSearch(
    matchingRows: Set<Int>,
    inMemoryRows: Set<Int>? = null,
    budget: Long = SYNC_ROWS,
    body: suspend CoroutineScope.(FakeRows, VcsLogAsyncRowSearch<String>) -> Unit,
  ) = runBlocking {
    // the runBlocking event loop plays EDT: the test body and the EDT parts of the search share its thread
    val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
    try {
      val rows = FakeRows(Thread.currentThread(), matchingRows.mapTo(HashSet()) { commitIdOf(it) }, inMemoryRows)
      val clock = AtomicLong()
      val search = VcsLogAsyncRowSearch(scope, rows, coroutineContext[ContinuationInterceptor]!!, Dispatchers.IO,
                                        syncBudgetNanos = budget, chunkSize = CHUNK_SIZE, nanoTime = { clock.incrementAndGet() })
      withTimeout(30.seconds) {
        body(rows, search)
      }
    }
    finally {
      scope.cancel()
    }
  }

  /** Waits for the running job and the jobs it restarts. */
  private suspend fun awaitJobs(search: VcsLogAsyncRowSearch<String>) {
    assertTrue(search.state is State.Running)
    while (true) {
      val state = search.state as? State.Running ?: return
      state.job.join()
    }
  }

  private class FakeRows(
    val edtThread: Thread,
    @Volatile var matchingIds: Set<VcsLogCommitStorageIndex>,
    private val inMemoryRows: Set<Int>?,
  ) : VcsLogAsyncRowSearch.Rows<String> {
    @Volatile
    var context: Any = 1
    var pattern: String? = "a"
    var selected: Int = 0
    @Volatile
    var onMatch: (VcsLogCommitStorageIndex) -> Unit = {}
    var onFoundHook: (Int, String) -> Unit = { _, _ -> }
    val memoryRows = mutableListOf<Int>()
    val backgroundRows = CopyOnWriteArrayList<Int>()
    val backgroundThreads = CopyOnWriteArrayList<Thread>()
    val prepareThreads = CopyOnWriteArrayList<Thread>()
    val prepareCalls = AtomicInteger()
    val preparePatterns = CopyOnWriteArrayList<String>()
    val found = CopyOnWriteArrayList<Pair<Int, String>>()

    fun calls(): Int = memoryRows.size + backgroundRows.size

    override fun getRowCount(): Int = ROW_COUNT
    override fun getSelectedRow(): Int = selected
    override fun getCommitIdAt(row: Int): VcsLogCommitStorageIndex = commitIdOf(row)
    override fun getContextKey(): Any = context

    override fun matchesInMemory(row: Int, pattern: String): Boolean? {
      assertSame(edtThread, Thread.currentThread())
      if (inMemoryRows != null && row !in inMemoryRows) return null
      memoryRows.add(row)
      return commitIdOf(row) in matchingIds
    }

    override fun createSnapshot(pattern: String): String = pattern

    override suspend fun prepare(pattern: String, snapshot: String): String {
      prepareCalls.incrementAndGet()
      prepareThreads.add(Thread.currentThread())
      preparePatterns.add(pattern)
      return "$snapshot!"
    }

    override suspend fun matches(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: String): Boolean {
      if (snapshot != "$pattern!") return false
      backgroundRows.add(commitId - commitIdOf(0))
      backgroundThreads.add(Thread.currentThread())
      onMatch(commitId)
      return commitId in matchingIds
    }

    override fun getCurrentPattern(): String? = pattern

    override fun onFound(row: Int, pattern: String) {
      found.add(row to pattern)
      onFoundHook(row, pattern)
    }
  }

  private companion object {
    const val ROW_COUNT = 100
    const val SYNC_ROWS = 10L
    const val CHUNK_SIZE = 16

    fun commitIdOf(row: Int): VcsLogCommitStorageIndex = 1000 + row
  }
}
