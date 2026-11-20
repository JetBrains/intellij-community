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
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * Speed search over the log rows that does not walk the whole history on EDT.
 *
 * [find] scans rows on EDT for at most [syncBudgetNanos], in the same order as `SpeedSearchBase`.
 * When the budget is spent, a job goes on from the stop position: it collects commit ids on EDT in chunks
 * and tests them in [backgroundContext]. A found row is reported with [Rows.onFound].
 *
 * All methods and [state] are used on EDT ([uiContext]) only.
 */
@ApiStatus.Internal
internal class VcsLogAsyncRowSearch<S : Any>(
  private val scope: CoroutineScope,
  private val rows: Rows<S>,
  private val uiContext: CoroutineContext,
  private val backgroundContext: CoroutineContext = Dispatchers.Default,
  private val syncBudgetNanos: Long = DEFAULT_SYNC_BUDGET_NANOS,
  private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
  private val nanoTime: () -> Long = System::nanoTime,
) {
  /**
   * The rows to search in. Only [matches] is called off EDT.
   */
  interface Rows<S : Any> {
    fun rowCount(): Int

    /** In-memory lookup, called on EDT. */
    fun commitIdAt(row: Int): VcsLogCommitStorageIndex?

    /** Everything besides the pattern that matching depends on (the visible pack, the columns). Results are valid while it is equal. */
    fun contextKey(): Any

    /** Data for [matches] taken on EDT once per search. */
    fun snapshot(pattern: String): S

    /** Called in the background context; may read the index. Must be monotonic: no match for a pattern means no match for its extensions. */
    fun matches(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: S): Boolean

    /** The trimmed pattern the user sees now, or null when the search is closed. */
    fun currentPattern(): String?

    fun onFound(row: Int, pattern: String)
  }

  enum class Direction { FIND, NEXT, PREVIOUS }

  data class Key(val pattern: String, val start: Int, val direction: Direction, val contextKey: Any)

  sealed interface State {
    data object Idle : State
    class Running(val key: Key, val job: Job) : State
    class NoMatch(val pattern: String, val contextKey: Any) : State
  }

  var state: State = State.Idle
    private set

  /**
   * @param pattern trimmed search pattern
   * @param start the selected row, -1 when nothing is selected
   * @return the matching row found within the time budget, or null when there is none or the search goes on in the background
   */
  fun find(pattern: String, start: Int, direction: Direction, cycleScrolling: Boolean): Int? {
    val contextKey = rows.contextKey()
    val current = state
    if (current is State.NoMatch && current.contextKey == contextKey && pattern.startsWith(current.pattern)) return null

    val key = Key(pattern, start, direction, contextKey)
    if (current is State.Running && current.key == key && current.job.isActive) return null

    reset()
    if (direction == Direction.PREVIOUS && start < 0) return null

    val cursor = RowCursor(segments(rows.rowCount(), start, direction, cycleScrolling))
    val coversAll = direction == Direction.FIND || cycleScrolling
    val snapshot = rows.snapshot(pattern)
    val startTime = nanoTime()
    while (true) {
      val row = cursor.next()
      if (row < 0) {
        if (coversAll) state = State.NoMatch(pattern, contextKey)
        return null
      }
      val commitId = rows.commitIdAt(row)
      if (commitId != null && matchesSafely(commitId, pattern, snapshot)) return row
      if (nanoTime() - startTime >= syncBudgetNanos) break
    }

    state = State.Running(key, launchSearch(key, cursor, snapshot, coversAll))
    return null
  }

  /** Cancels the running search and forgets the last no-match result. */
  fun reset() {
    (state as? State.Running)?.job?.cancel()
    state = State.Idle
  }

  private fun launchSearch(key: Key, cursor: RowCursor, snapshot: S, coversAll: Boolean): Job = scope.launch(uiContext) {
    val job = coroutineContext.job
    val chunkRows = IntArray(chunkSize)
    val chunkIds = IntArray(chunkSize)
    while (true) {
      if (rows.contextKey() != key.contextKey) {
        finish(job, State.Idle)
        return@launch
      }

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
        val commitId = rows.commitIdAt(row) ?: continue
        chunkRows[count] = row
        chunkIds[count] = commitId
        count++
      }

      val found = if (count == 0) -1 else withContext(backgroundContext) { findInChunk(chunkIds, count, key.pattern, snapshot) }
      if (found >= 0) {
        finish(job, State.Idle)
        if (rows.contextKey() == key.contextKey && rows.currentPattern() == key.pattern) {
          rows.onFound(chunkRows[found], key.pattern)
        }
        return@launch
      }
      if (exhausted) {
        finish(job, if (coversAll) State.NoMatch(key.pattern, key.contextKey) else State.Idle)
        return@launch
      }
    }
  }

  private suspend fun findInChunk(ids: IntArray, count: Int, pattern: String, snapshot: S): Int {
    val context = currentCoroutineContext()
    for (i in 0 until count) {
      context.ensureActive()
      if (matchesSafely(ids[i], pattern, snapshot)) return i
    }
    return -1
  }

  private fun finish(job: Job, newState: State) {
    val current = state
    if (current is State.Running && current.job === job) state = newState
  }

  private fun matchesSafely(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: S): Boolean {
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

    /** @return the next row, or -1 when all rows are visited */
    fun next(): Int {
      while (segment < segments.size) {
        val it = iterator ?: segments[segment].iterator().also { iterator = it }
        if (it.hasNext()) return it.nextInt()
        segment++
        iterator = null
      }
      return -1
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogAsyncRowSearch::class.java)

    const val DEFAULT_SYNC_BUDGET_NANOS: Long = 20_000_000
    const val DEFAULT_CHUNK_SIZE: Int = 256

    /**
     * The row order of `SpeedSearchBase.findElement/findNextElement/findPreviousElement`, without rows visited twice.
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
      }
    }
  }
}
