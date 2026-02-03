// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache.Companion.LOG_QUEUE_MAX_SIZE
import com.intellij.platform.workspace.storage.impl.query.Cell
import com.intellij.platform.workspace.storage.impl.query.CellChain
import com.intellij.platform.workspace.storage.impl.query.CellId
import com.intellij.platform.workspace.storage.impl.query.Diff
import com.intellij.platform.workspace.storage.impl.query.DiffCollectorCell
import com.intellij.platform.workspace.storage.impl.query.DiffImpl
import com.intellij.platform.workspace.storage.impl.query.Match
import com.intellij.platform.workspace.storage.impl.query.MatchList
import com.intellij.platform.workspace.storage.impl.query.MatchSet
import com.intellij.platform.workspace.storage.impl.query.QueryId
import com.intellij.platform.workspace.storage.impl.trace.ReadTraceIndex
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.CollectionQuery
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.query.compile
import com.intellij.platform.workspace.storage.query.trackDiff
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import org.jetbrains.annotations.TestOnly
import java.util.Random

internal data class CellUpdateInfo(
  val chainId: QueryId,
  val cellId: CellId,
  val updateType: UpdateType,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CellUpdateInfo

    if (chainId != other.chainId) return false
    if (cellId != other.cellId) return false
    if (updateType != other.updateType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = chainId.hashCode()
    result = 31 * result + cellId.hashCode()
    result = 31 * result + updateType.hashCode()
    return result
  }
}

internal sealed interface UpdateType {
  data object DIFF : UpdateType
  data class RECALCULATE(val match: Match) : UpdateType {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as RECALCULATE

      return match == other.match
    }

    override fun hashCode(): Int {
      return match.hashCode()
    }
  }
}

internal class PropagationResult<T>(
  val newCell: Cell<T>,
  val matchList: MatchList,
  val subscriptions: List<Pair<ReadTraceHashSet, UpdateType>>,
)

internal class TracedSnapshotCacheImpl : TracedSnapshotCache {
  private val lock = Any()

  private val queryIdToChain: HashMap<QueryId, CellChain> = HashMap()
  private val queryIdToTraceIndex: HashMap<QueryId, ReadTraceIndex<CellUpdateInfo>> = HashMap()

  private val changeQueue: MutableMap<QueryId, MutableList<EntityStorageChange>> = HashMap()

  /**
   * Flag indicating that this cache is now pulled from the other snapshot. During this pull, executing cache queries is not allowed
   *   because the cache structures are not ready and have unknown state.
   * We can't call for [cached] during [pullingCache] anyway because [pullingCache] is called in a controlled manner,
   *   still this flag exists to catch bugs in implementation or after refactorings.
   */
  private var pullingCache = false
  internal var shuffleEntities: Long = -1L

  override fun pullCache(
    newSnapshot: ImmutableEntityStorage,
    from: TracedSnapshotCache,
    changes: EntityStorageChange,
  ) {
    try {
      pullingCache = true
      check(from is TracedSnapshotCacheImpl)

      // Do not perform changes in [from] cache while we copy state to the new cache
      synchronized(from.lock) {
        from.queryIdToTraceIndex.forEach { (chainId, index) ->
          val newIndex = ReadTraceIndex<CellUpdateInfo>()
          newIndex.pull(index)
          this.queryIdToTraceIndex[chainId] = newIndex
        }
        this.queryIdToChain.putAll(from.queryIdToChain)
        this.changeQueue.putAll(from.changeQueue.mapValues { ArrayList(it.value) })
        this.shuffleEntities = from.shuffleEntities

        val cachesToRemove = ArrayList<QueryId>()
        this.queryIdToChain.keys.forEach { chainId ->
          val changesQueue = this.changeQueue.getOrPut(chainId) { ArrayList() }
          val expectedNewChangelogSize = changesQueue.sumOf { it.size } + changes.size
          if (expectedNewChangelogSize > LOG_QUEUE_MAX_SIZE) {
            @Suppress("TestOnlyProblems")
            if (CacheResetTracker.enabled) {
              CacheResetTracker.cacheReset = true
            }
            cachesToRemove += chainId
          }
          else {
            changesQueue.add(changes)
          }
        }
        cachesToRemove.forEach { removeCache(it) }
      }
    }
    finally {
      pullingCache = false
    }
  }

  private fun removeCache(queryId: QueryId) {
    queryIdToChain.remove(queryId)
    queryIdToTraceIndex.remove(queryId)
    changeQueue.remove(queryId)
  }

  private fun updateCellIndex(chainId: QueryId,
                              changes: EntityStorageChange,
                              newSnapshot: ImmutableEntityStorageInstrumentation,
                              prevStorage: ImmutableEntityStorageInstrumentation?): Boolean {
    val cellIndex = queryIdToTraceIndex.getValue(chainId)
    val newTraces = changes.createTraces(newSnapshot)

    val updatedCells = HashMap<CellId, MatchSet>()
    var cellsUpdated = false
    cellIndex.get(newTraces).maybeShuffled().firstDiffThenRecalculate().forEach { updateRequest ->
      cellsUpdated = true
      val cells = queryIdToChain[updateRequest.chainId] ?: error("Unindexed cell")
      val (newChain, tracesAndModifiedCells) = cells.changeInput(newSnapshot, prevStorage, updateRequest, changes, updateRequest.cellId,
                                                                 updatedCells)
      tracesAndModifiedCells.forEach { (traces, updateRequest) ->
        cellIndex.set(traces, updateRequest)
      }
      this.queryIdToChain[newChain.id] = newChain
    }
    return cellsUpdated
  }

  private fun Collection<CellUpdateInfo>.firstDiffThenRecalculate(): List<CellUpdateInfo> {
    val (diff, recalculate) = this.partition { it.updateType == UpdateType.DIFF }
    return diff + recalculate
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> cached(query: StorageQuery<T>,
                          snapshot: ImmutableEntityStorageInstrumentation,
                          prevStorage: ImmutableEntityStorageInstrumentation?): CachedValue<T> {
    check(!pullingCache) {
      "It's not allowed to request query when the cache is pulled from other snapshot"
    }

    val lastCell = getUpdatedLastCell(query, snapshot, prevStorage)
    return CachedValue(lastCell.cacheProcessStatus, lastCell.value.data() as T)
  }


  override fun <T> diff(query: CollectionQuery<T>,
                        snapshot: ImmutableEntityStorageInstrumentation,
                        prevStorage: ImmutableEntityStorageInstrumentation?): CachedValue<Diff<T>> {
    require(query !is CollectionQuery.TrackDiff<*>)

    val queryWithDiffTracker = query.trackDiff()

    val lastCell = getUpdatedLastCell(queryWithDiffTracker, snapshot, prevStorage)
    check(lastCell.value is DiffCollectorCell<*>)

    val diff = DiffImpl(lastCell.value.addedData as List<T>, lastCell.value.removedData as List<T>)
    return CachedValue(lastCell.cacheProcessStatus, diff)
  }

  private fun <T> getUpdatedLastCell(query: StorageQuery<T>,
                                     snapshot: ImmutableEntityStorageInstrumentation,
                                     prevStorage: ImmutableEntityStorageInstrumentation?): CachedValue<Cell<*>> {
    val queryId = query.queryId

    val changes = changeQueue[queryId]
    val cellChain = queryIdToChain[queryId]
    if (cellChain != null && (changes == null || changes.size == 0)) {
      return CachedValue(CacheHit, cellChain.last())
    }

    synchronized(lock) {
      val doubleCheckChanges = changeQueue[queryId]
      val doubleCheckChain = queryIdToChain[queryId]
      if (doubleCheckChain != null && (doubleCheckChanges == null || doubleCheckChanges.size == 0)) {
        return CachedValue(CacheHitInSynchronized, doubleCheckChain.last())
      }

      if (doubleCheckChanges != null && doubleCheckChanges.size > 0) {
        val collapsedChangelog = doubleCheckChanges.collapse()
        val recalculated = updateCellIndex(queryId, collapsedChangelog, snapshot, prevStorage)
        changeQueue.remove(queryId)
        val status = if (recalculated) IncrementalUpdate else CacheHitNotAffectedByChanges
        return CachedValue(status, queryIdToChain[queryId]!!.last())
      }

      val emptyCellChain = query.compile()
      val chainWithTraces = emptyCellChain.snapshotInput(snapshot)
      val (newChain, traces) = chainWithTraces
      queryIdToTraceIndex.getOrPut(newChain.id) { ReadTraceIndex() }.also { index ->
        traces.forEach { (trace, updateRequest) ->
          index.set(trace, updateRequest)
        }
      }
      queryIdToChain[newChain.id] = newChain
      return CachedValue(Initialization, newChain.last())
    }
  }

  @TestOnly
  internal fun getChangeQueue() = changeQueue
  @TestOnly
  internal fun getQueryIdToChain() = queryIdToChain
  @TestOnly
  internal fun getQueryIdToTraceIndex() = queryIdToTraceIndex

  /**
   * Shuffle collection if the field [shuffleEntities] is not -1 (set in tests)
   */
  private fun <E> Collection<E>.maybeShuffled(): Collection<E> {
    if (shuffleEntities != -1L && this.size > 1) {
      return this.shuffled(Random(shuffleEntities))
    }
    return this
  }
}
