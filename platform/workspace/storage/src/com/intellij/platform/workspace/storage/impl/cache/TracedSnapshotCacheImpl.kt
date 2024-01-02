// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceBuilderChangeLog
import com.intellij.platform.workspace.storage.impl.query.*
import com.intellij.platform.workspace.storage.impl.trace.ReadTraceIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.query.compile
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import com.intellij.platform.workspace.storage.trace.toTraces
import org.jetbrains.annotations.TestOnly

internal data class CellUpdateInfo(
  val chainId: CellChainId,
  val cellId: CellId,
  val updateType: UpdateType,
)

internal sealed interface UpdateType {
  data object DIFF : UpdateType
  data class RECALCULATE(val key: Any?, val entityId: EntityId?) : UpdateType
}

internal class PropagationResult<T>(
  val newCell: Cell<T>,
  val tokenSet: TokenSet,
  val subscriptions: List<Pair<ReadTraceHashSet, UpdateType>>,
)

internal class TracedSnapshotCacheImpl : TracedSnapshotCache {
  private val lock = Any()
  private val queryToCellChainId: MutableMap<StorageQuery<*>, CellChainId> = HashMap()

  private val chainIdToChainIndex: HashMap<CellChainId, CellChain> = HashMap()
  private val cellChainToCellIndex: HashMap<CellChainId, ReadTraceIndex<Pair<StorageQuery<*>, CellUpdateInfo>>> = HashMap()

  private val changeQueue: MutableMap<CellChainId, MutableList<Pair<WorkspaceBuilderChangeLog, Map<String, Set<EntityId>>>>> = HashMap()

  /**
   * Flag indicating that this cache is now pulled from the other snapshot. During this pull, executing cache queries is not allowed
   *   because the cache structures are not ready and have unknown state.
   * We can't call for [cached] during [pullingCache] anyway because [pullingCache] is called in a controlled manner,
   *   still this flag exists to catch bugs in implementation or after refactorings.
   */
  private var pullingCache = false

  override fun pullCache(
    newSnapshot: EntityStorageSnapshot,
    from: TracedSnapshotCache,
    changes: WorkspaceBuilderChangeLog,
    externalMappingChanges: Map<String, MutableSet<EntityId>>
  ) {
    try {
      pullingCache = true
      check(from is TracedSnapshotCacheImpl)

      // Do not perform changes in [from] cache while we copy state to the new cache
      synchronized(from.lock) {
        this.queryToCellChainId.putAll(from.queryToCellChainId)
        from.cellChainToCellIndex.forEach { (chainId, index) ->
          val newIndex = ReadTraceIndex<Pair<StorageQuery<*>, CellUpdateInfo>>()
          newIndex.pull(index)
          this.cellChainToCellIndex[chainId] = newIndex
        }
        this.chainIdToChainIndex.putAll(from.chainIdToChainIndex)
        this.changeQueue.putAll(from.changeQueue.mapValues { ArrayList(it.value) })

        val cachesToRemove = ArrayList<StorageQuery<*>>()
        this.queryToCellChainId.forEach { (query, chainId) ->
          val changesQueue = this.changeQueue.getOrPut(chainId) { ArrayList() }
          val expectedNewChangelogSize = changesQueue.sumOf { it.first.changeLog.size + it.second.size } + changes.changeLog.size + externalMappingChanges.size
          if (expectedNewChangelogSize > LOG_QUEUE_MAX_SIZE) {
            @Suppress("TestOnlyProblems")
            if (CacheResetTracker.enabled) {
              CacheResetTracker.cacheReset = true
            }
            cachesToRemove += query
          }
          else {
            changesQueue.add(changes to externalMappingChanges)
          }
        }
        cachesToRemove.forEach { removeCache(it) }
      }
    }
    finally {
      pullingCache = false
    }
  }

  private fun <T> removeCache(query: StorageQuery<T>) {
    val chainId = queryToCellChainId.remove(query) ?: return
    chainIdToChainIndex.remove(chainId)
    cellChainToCellIndex.remove(chainId)
    changeQueue.remove(chainId)
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  private fun updateCellIndex(chainId: CellChainId,
                              externalMappingChanges: HashMap<String, MutableSet<EntityId>>,
                              changes: WorkspaceBuilderChangeLog,
                              newSnapshot: ImmutableEntityStorageInstrumentation) {
    val cellIndex = cellChainToCellIndex.getValue(chainId)
    val externalMappingTraces: ReadTraceHashSet = externalMappingChanges.entries
      .filter { it.value.isNotEmpty() }
      .map { it.key }
      .mapTo(ReadTraceHashSet()) { ReadTrace.ExternalMappingAccess(it).hash }
    val newTraces = ReadTraceHashSet(changes.changeLog.toTraces(newSnapshot))
    newTraces.addAll(externalMappingTraces)

    cellIndex.get(newTraces).forEach { (query, updateRequest) ->
      val cells = chainIdToChainIndex[updateRequest.chainId] ?: error("Unindexed cell")
      val (newChain, tracesAndModifiedCells) = cells.changeInput(newSnapshot, updateRequest, changes.changeLog, externalMappingChanges,
                                                                     updateRequest.cellId)
      this.queryToCellChainId[query] = newChain.id
      tracesAndModifiedCells.forEach { (traces, updateRequest) ->
        cellIndex.set(ReadTraceHashSet(traces), query to updateRequest)
      }
      this.chainIdToChainIndex[newChain.id] = newChain
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun <T> cached(query: StorageQuery<T>, snapshot: ImmutableEntityStorageInstrumentation): T {
    check(!pullingCache) {
      "It's not allowed to request query when the cache is pulled from other snapshot"
    }

    val existingCellId = queryToCellChainId[query]
    if (existingCellId != null) {
      val changes = changeQueue[existingCellId]
      val cellChain = chainIdToChainIndex[existingCellId]
      if (cellChain != null && (changes == null || changes.size == 0)) {
        return cellChain.data()
      }
    }

    synchronized(lock) {
      val doubleCheckCellId = queryToCellChainId[query]
      if (doubleCheckCellId != null) {
        val changelog = changeQueue[doubleCheckCellId]
        val cellChain = chainIdToChainIndex[doubleCheckCellId]
        if (cellChain != null && (changelog == null || changelog.size == 0)) {
          return cellChain.data()
        }

        if (changelog != null && changelog.size > 0) {
          val accChangeLog = WorkspaceBuilderChangeLog()
          val accMappingLog = HashMap<String, MutableSet<EntityId>>()
          changelog.forEach { (changeLog, mappingChangeLog) ->
            accChangeLog.join(changeLog)
            mappingChangeLog.forEach { (key, log) ->
              val existingLog = accMappingLog.getOrPut(key) { HashSet() }
              log.forEach { affectedEntityId ->
                existingLog.add(affectedEntityId)
              }
            }
          }
          updateCellIndex(doubleCheckCellId, accMappingLog, accChangeLog, snapshot)
          changeQueue.remove(doubleCheckCellId)
          return chainIdToChainIndex[doubleCheckCellId]!!.data()
        }
      }

      val emptyCellChain = query.compile()
      val chainWithTraces = emptyCellChain.snapshotInput(snapshot)
      val (newChain, traces) = chainWithTraces
      queryToCellChainId[query] = newChain.id
      traces.forEach { (trace, updateRequest) ->
        cellChainToCellIndex.getOrPut(newChain.id) { ReadTraceIndex() }.set(ReadTraceHashSet(trace), query to updateRequest)
      }
      chainIdToChainIndex[newChain.id] = newChain
      return newChain.data()
    }
  }

  @TestOnly
  internal fun getChangeQueue() = changeQueue
  @TestOnly
  internal fun getQueryToCellChainId() = queryToCellChainId
  @TestOnly
  internal fun getChainIdToChainIndex() = queryToCellChainId
  @TestOnly
  internal fun getCellChainToCellIndex() = cellChainToCellIndex

  companion object {
    internal const val LOG_QUEUE_MAX_SIZE = 10_000
  }
}
