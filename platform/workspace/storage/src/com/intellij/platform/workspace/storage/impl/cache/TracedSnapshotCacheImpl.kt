// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache.Companion.LOG_QUEUE_MAX_SIZE
import com.intellij.platform.workspace.storage.impl.query.*
import com.intellij.platform.workspace.storage.impl.trace.ReadTraceIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.query.compile
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import org.jetbrains.annotations.TestOnly

internal data class CellUpdateInfo(
  val chainId: QueryId,
  val cellId: CellId,
  val updateType: UpdateType,
)

internal sealed interface UpdateType {
  data object DIFF : UpdateType
  data class RECALCULATE(val match: Match) : UpdateType
}

internal class PropagationResult<T>(
  val newCell: Cell<T>,
  val tokenSet: TokenSet,
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

  @OptIn(EntityStorageInstrumentationApi::class)
  private fun updateCellIndex(chainId: QueryId,
                              changes: EntityStorageChange,
                              newSnapshot: ImmutableEntityStorageInstrumentation) {
    val cellIndex = queryIdToTraceIndex.getValue(chainId)
    val newTraces = changes.createTraces(newSnapshot)

    cellIndex.get(newTraces).forEach { updateRequest ->
      val cells = queryIdToChain[updateRequest.chainId] ?: error("Unindexed cell")
      val (newChain, tracesAndModifiedCells) = cells.changeInput(newSnapshot, updateRequest, changes, updateRequest.cellId)
      tracesAndModifiedCells.forEach { (traces, updateRequest) ->
        cellIndex.set(ReadTraceHashSet(traces), updateRequest)
      }
      this.queryIdToChain[newChain.id] = newChain
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun <T> cached(query: StorageQuery<T>, snapshot: ImmutableEntityStorageInstrumentation): T {
    check(!pullingCache) {
      "It's not allowed to request query when the cache is pulled from other snapshot"
    }

    val queryId = query.queryId

    val changes = changeQueue[queryId]
    val cellChain = queryIdToChain[queryId]
    if (cellChain != null && (changes == null || changes.size == 0)) {
      return cellChain.data()
    }

    synchronized(lock) {
      val doubleCheckChanges = changeQueue[queryId]
      val doubleCheckChain = queryIdToChain[queryId]
      if (doubleCheckChain != null && (doubleCheckChanges == null || doubleCheckChanges.size == 0)) {
        return doubleCheckChain.data()
      }

      if (doubleCheckChanges != null && doubleCheckChanges.size > 0) {
        val collapsedChangelog = doubleCheckChanges.collapse()
        updateCellIndex(queryId, collapsedChangelog, snapshot)
        changeQueue.remove(queryId)
        return queryIdToChain[queryId]!!.data()
      }

      val emptyCellChain = query.compile()
      val chainWithTraces = emptyCellChain.snapshotInput(snapshot)
      val (newChain, traces) = chainWithTraces
      traces.forEach { (trace, updateRequest) ->
        queryIdToTraceIndex.getOrPut(newChain.id) { ReadTraceIndex() }.set(ReadTraceHashSet(trace), updateRequest)
      }
      queryIdToChain[newChain.id] = newChain
      return newChain.data()
    }
  }

  @TestOnly
  internal fun getChangeQueue() = changeQueue
  @TestOnly
  internal fun getQueryIdToChain() = queryIdToChain
  @TestOnly
  internal fun getQueryIdToTraceIndex() = queryIdToTraceIndex
}
