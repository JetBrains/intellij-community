// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.impl.query.CellOrchestra
import com.intellij.platform.workspace.storage.impl.query.compile
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageSnapshotInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTracker
import com.intellij.platform.workspace.storage.trace.toTraces
import java.util.concurrent.ConcurrentHashMap

internal class EntityStorageCacheImpl : EntityStorageCache {
  private lateinit var storage: EntityStorage
  private val readLock = Any()
  private val values: ConcurrentHashMap<StorageQuery<*>, CellOrchestra> = ConcurrentHashMap()

  override fun init(storage: EntityStorage) {
    this.storage = storage
  }

  override fun <T> cached(query: StorageQuery<T>): T {
    val existingCell = values[query]
    if (existingCell != null) {
      return existingCell.data()
    }

    synchronized(readLock) {
      val doubleCheckCell = values[query]
      if (doubleCheckCell != null) {
        return doubleCheckCell.data()
      }

      val emptyCellOrchestra = compile(query)
      val newOrchestra = emptyCellOrchestra.snapshotInput(storage.toSnapshot())
      values[query] = newOrchestra
      return newOrchestra.data()
    }
  }

  override fun isEmpty(): Boolean {
    return values.isEmpty()
  }
}

@OptIn(EntityStorageInstrumentationApi::class)
internal class TracedSnapshotCacheImpl : TracedSnapshotCache {

  private lateinit var snapshot: EntityStorageSnapshotInstrumentation

  private val readLock = Any()
  private val values: ConcurrentHashMap<StorageQuery<*>, CellOrchestra> = ConcurrentHashMap()
  private val readTraces: ConcurrentHashMap<StorageQuery<*>, Set<ReadTrace>> = ConcurrentHashMap()

  override fun pullCache(from: TracedSnapshotCache, changes: Map<Class<*>, List<EntityChange<*>>>) {
    if (from !is TracedSnapshotCacheImpl) return
    this.values.putAll(from.values)
    this.readTraces.putAll(from.readTraces)
    val newTraces = changes.toTraces()
    from.readTraces.forEach { (query, trace) ->
      if (trace.intersect(newTraces).isNotEmpty()) {
        val existingCells = this.values[query]!!

        val readTrace = mutableSetOf<ReadTrace>()
        val trackedSnapshot = ReadTracker(snapshot) { readTrace.add(it) }

        val newOrchestra = existingCells.changeInput(trackedSnapshot, changes)


        this.values[query] = newOrchestra

        // TODO: This should be improved! We need to keep the track on the actual traces.
        // Perfectly the left part of the plus should not exist.
        this.readTraces[query] = (this.readTraces[query] ?: emptySet()) + readTrace.toSet()
      }
    }
  }

  override fun initSnapshot(snapshot: EntityStorageSnapshotInstrumentation) {
    this.snapshot = snapshot
  }

  override fun <T> cached(query: StorageQuery<T>): T {
    val existingCell = values[query]
    if (existingCell != null) {
      return existingCell.data()
    }

    synchronized(readLock) {
      val doubleCheckCell = values[query]
      if (doubleCheckCell != null) {
        return doubleCheckCell.data()
      }

      val emptyCellOrchestra = compile(query)
      val readTrace = mutableSetOf<ReadTrace>()
      val trackedSnapshot = ReadTracker(snapshot) { readTrace.add(it) }
      val newOrchestra = emptyCellOrchestra.snapshotInput(trackedSnapshot)
      values[query] = newOrchestra
      readTraces[query] = readTrace.toSet()
      return newOrchestra.data()
    }
  }
}
