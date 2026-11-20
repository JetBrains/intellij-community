// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Speed search over the log rows that does not read the index or walk the whole history on EDT.
 *
 * [find] matches rows on EDT with in-memory data only ([Rows.matchesInMemory]), in the same order as `SpeedSearchBase`,
 * for at most [syncBudgetNanos]. At the first row without data in memory, or when the budget is spent, a job goes on from that row:
 * it prepares the search data once and tests commit ids in [backgroundContext], collecting the ids on EDT in chunks.
 * A found row is reported with [Rows.onFound] and kept as [State.Found], so the refresh after it does not search again.
 *
 * All methods and [state] are used on EDT ([uiContext]) only.
 */
internal class VcsLogAsyncRowSearch<S : Any>(
  private val scope: CoroutineScope,
  private val rows: Rows<S>,
  private val uiContext: CoroutineContext,
  private val backgroundContext: CoroutineContext = Dispatchers.IO,
  private val syncBudgetNanos: Long = DEFAULT_SYNC_BUDGET_NANOS,
  private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
  private val nanoTime: () -> Long = System::nanoTime,
) {
  /**
   * The rows to search in. Only [prepare] and [matches] are called off EDT.
   */
  interface Rows<S : Any> {
    fun getRowCount(): Int

    /** The selected row, -1 when nothing is selected. */
    fun getSelectedRow(): Int

    /** In-memory lookup, called on EDT. */
    fun getCommitIdAt(row: Int): VcsLogCommitStorageIndex?

    /** Everything besides the pattern that matching depends on (the visible pack, the columns). Results are valid while it is equal. */
    fun getContextKey(): Any

    /** Called on EDT, must not read the disk. @return null when the row data is not in memory */
    fun matchesInMemory(row: Int, pattern: String): Boolean?

    /** Data for [prepare] and [matches] taken on EDT once per search. */
    fun createSnapshot(pattern: String): S

    /** Called in the background context once per job before [matches]; may read the index. */
    suspend fun prepare(pattern: String, snapshot: S): S

    /** Called in the background context; may read the index. Must be monotonic: no match for a pattern means no match for its extensions. */
    suspend fun matches(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: S): Boolean

    /** The trimmed pattern the user sees now, or null when the search is closed. */
    fun getCurrentPattern(): String?

    fun onFound(row: Int, pattern: String)
  }

  enum class Direction { FIND, NEXT, PREVIOUS, FIRST, LAST }

  data class Key(val pattern: String, val start: Int, val direction: Direction, val cycleScrolling: Boolean, val contextKey: Any)

  sealed interface State {
    data object Idle : State
    class Running(val key: Key, val job: Job) : State
    class NoMatch(val pattern: String, val contextKey: Any) : State
    class Found(val pattern: String, val row: Int, val commitId: VcsLogCommitStorageIndex, val contextKey: Any) : State
  }

  var state: State = State.Idle
    private set

  /**
   * @param pattern trimmed search pattern
   * @param start the selected row, -1 when nothing is selected
   * @return the matching row found on EDT, or null when there is none or the search goes on in the background
   */
  fun find(pattern: String, start: Int, direction: Direction, cycleScrolling: Boolean): Int? {
    val contextKey = rows.getContextKey()
    val current = state
    if (current is State.NoMatch && current.contextKey == contextKey && pattern.startsWith(current.pattern)) return null
    if (current is State.Found && direction == Direction.FIND && current.pattern == pattern && current.contextKey == contextKey &&
        current.row == start && rows.getCommitIdAt(start) == current.commitId) {
      return start
    }

    val key = Key(pattern, start, direction, cycleScrolling, contextKey)
    if (current is State.Running && current.key == key && current.job.isActive) return null

    reset()
    if (direction == Direction.PREVIOUS && start < 0) return null

    val cursor = RowCursor(segments(rows.getRowCount(), start, direction, cycleScrolling))
    val startTime = nanoTime()
    while (true) {
      val row = cursor.next()
      if (row < 0) {
        if (key.coversAll()) state = State.NoMatch(pattern, contextKey)
        return null
      }
      val matches = rows.matchesInMemory(row, pattern)
      if (matches == null) {
        cursor.pushBack(row)
        break
      }
      if (matches) return row
      if (nanoTime() - startTime >= syncBudgetNanos) break
    }

    state = State.Running(key, launchSearch(key, cursor, rows.createSnapshot(pattern)))
    return null
  }

  /** Cancels the running search and forgets the last result. */
  fun reset() {
    (state as? State.Running)?.job?.cancel()
    state = State.Idle
  }

  private fun launchSearch(key: Key, cursor: RowCursor, snapshot: S): Job = scope.launch(uiContext) {
    val job = coroutineContext.job
    val prepared = withContext(backgroundContext) { prepareSafely(key.pattern, snapshot) }
    val chunkRows = IntArray(chunkSize)
    val chunkIds = IntArray(chunkSize)
    while (true) {
      if (!checkContext(job, key)) return@launch

      var count = 0
      var scanned = 0
      var exhausted = false
      while (scanned < chunkSize) {
        val row = cursor.next()
        if (row < 0) {
          exhausted = true
          break
        }
        scanned++
        val commitId = rows.getCommitIdAt(row) ?: continue
        chunkRows[count] = row
        chunkIds[count] = commitId
        count++
      }

      val found = if (count == 0) -1 else withContext(backgroundContext) { findInChunk(chunkIds, count, key.pattern, prepared) }
      if (found >= 0) {
        if (!checkContext(job, key)) return@launch
        if (rows.getCurrentPattern() != key.pattern) {
          finish(job, State.Idle)
        }
        // set the state first: onFound refreshes the selection, which calls find for the found row
        else if (finish(job, State.Found(key.pattern, chunkRows[found], chunkIds[found], key.contextKey))) {
          rows.onFound(chunkRows[found], key.pattern)
        }
        return@launch
      }
      if (exhausted) {
        finish(job, if (key.coversAll()) State.NoMatch(key.pattern, key.contextKey) else State.Idle)
        return@launch
      }
    }
  }

  /**
   * @return true when the context of [key] is still valid. Otherwise ends the job and, when the user still sees the same pattern,
   * starts the same search again from the current selection.
   */
  private fun checkContext(job: Job, key: Key): Boolean {
    if (rows.getContextKey() == key.contextKey) return true
    if (!finish(job, State.Idle)) return false
    if (rows.getCurrentPattern() == key.pattern) {
      val row = find(key.pattern, rows.getSelectedRow(), key.direction, key.cycleScrolling)
      if (row != null) rows.onFound(row, key.pattern)
    }
    return false
  }

  private suspend fun findInChunk(ids: IntArray, count: Int, pattern: String, snapshot: S): Int {
    val context = currentCoroutineContext()
    for (i in 0 until count) {
      context.ensureActive()
      if (matchesSafely(ids[i], pattern, snapshot)) return i
    }
    return -1
  }

  /** @return true when [job] still owned the state */
  private fun finish(job: Job, newState: State): Boolean {
    val current = state
    if (current !is State.Running || current.job !== job) return false
    state = newState
    return true
  }

  private suspend fun prepareSafely(pattern: String, snapshot: S): S {
    return try {
      rows.prepare(pattern, snapshot)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.debug("Cannot prepare speed search for $pattern", e)
      snapshot
    }
  }

  private suspend fun matchesSafely(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: S): Boolean {
    return try {
      rows.matches(commitId, pattern, snapshot)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.debug("Cannot match commit $commitId", e)
      false
    }
  }

  /** Iterates over rows of several segments, without boxing. */
  private class RowCursor(private val segments: List<IntProgression>) {
    private var segment = 0
    private var iterator: IntIterator? = null
    private var pending = -1

    /** @return the next row, or -1 when all rows are visited */
    fun next(): Int {
      if (pending >= 0) {
        val row = pending
        pending = -1
        return row
      }
      while (segment < segments.size) {
        val it = iterator ?: segments[segment].iterator().also { iterator = it }
        if (it.hasNext()) return it.nextInt()
        segment++
        iterator = null
      }
      return -1
    }

    /** Makes [next] return [row] again. */
    fun pushBack(row: Int) {
      pending = row
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogAsyncRowSearch::class.java)

    private const val DEFAULT_SYNC_BUDGET_NANOS: Long = 20_000_000
    private const val DEFAULT_CHUNK_SIZE: Int = 256

    /** The search visits all rows, so no match is final for the pattern. */
    private fun Key.coversAll(): Boolean = cycleScrolling || (direction != Direction.NEXT && direction != Direction.PREVIOUS)

    /**
     * The row order of `SpeedSearchBase.findElement/findNextElement/findPreviousElement/findFirstElement/findLastElement`,
     * without rows visited twice.
     */
    internal fun segments(rowCount: Int, start: Int, direction: Direction, cycleScrolling: Boolean): List<IntProgression> {
      val last = rowCount - 1
      return when (direction) {
        Direction.FIND -> {
          val from = start.coerceIn(0, rowCount)
          listOf(from..last, 0 until from)
        }
        Direction.NEXT -> {
          val tail = when {
            cycleScrolling -> 0..minOf(start, last)
            start in 0..last -> start..start
            else -> IntRange.EMPTY
          }
          listOf(maxOf(start + 1, 0)..last, tail)
        }
        Direction.PREVIOUS -> {
          if (start < 0) return emptyList()
          val tail = when {
            cycleScrolling -> last downTo start
            start <= last -> start..start
            else -> IntRange.EMPTY
          }
          listOf(minOf(start - 1, last) downTo 0, tail)
        }
        Direction.FIRST -> listOf(0..last)
        Direction.LAST -> listOf(last downTo 0)
      }
    }
  }
}
