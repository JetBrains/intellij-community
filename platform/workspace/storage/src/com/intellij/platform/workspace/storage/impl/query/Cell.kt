// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.cache.PropagationResult
import com.intellij.platform.workspace.storage.impl.cache.UpdateType
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import com.intellij.platform.workspace.storage.trace.ReadTracker
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlin.reflect.KClass

/**
 * We store data for workspace model caches in cells. Each cell represents a single operation of
 *   the query and stores information related to this cell. Also, intermediate calculation results are
 *   also stored in cells.
 *   Cells can be chained in [CellChain] to represent all parts of the query.
 */
internal sealed class Cell<T>(val id: CellId) {
  open fun snapshotInput(snapshot: ImmutableEntityStorage): PropagationResult<T> {
    throw NotImplementedError()
  }

  abstract fun input(prevData: TokenSet, newSnapshot: ImmutableEntityStorage): PropagationResult<T>
  abstract fun data(): T
}

/**
 * Cell related to [entities] query. It doesn't store any intermediate calculations.
 */
internal class EntityCell<T : WorkspaceEntity>(
  id: CellId,
  val type: KClass<T>,
) : Cell<List<T>>(id) {
  override fun snapshotInput(snapshot: ImmutableEntityStorage): PropagationResult<List<T>> {
    val newCell = EntityCell(this.id, this.type)
    val snapshotImpl = snapshot as ImmutableEntityStorageImpl
    val toClassId = type.java.toClassId()
    val ids = snapshotImpl.entitiesByType[toClassId]?.entities?.asSequence()
                ?.mapIndexedNotNull { index, workspaceEntityData ->
                  if (workspaceEntityData == null) null else createEntityId(index, toClassId)
                } ?: emptySequence()
    val tokens = ids
      .map { value -> Token(Operation.ADDED, MatchWithEntityId(value)) }
      .toList()
    val traces = ReadTraceHashSet()
    traces.add(ReadTrace.EntitiesOfType(type.java).hash)
    return PropagationResult(newCell, TokenSet(tokens), listOf(traces to UpdateType.DIFF))
  }

  override fun input(prevData: TokenSet,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<List<T>> {
    val tokenSet = TokenSet()
    prevData.addedTokens()
      .filter { (it.match as MatchWithEntityId).entityId.clazz.findWorkspaceEntity().kotlin == type }
      .forEach { tokenSet.add(it) }
    prevData.removedTokens()
      .filter { (it.match as MatchWithEntityId).entityId.clazz.findWorkspaceEntity().kotlin == type }
      .forEach { tokenSet.add(it) }

    val traces = ReadTraceHashSet()
    traces.add(ReadTrace.EntitiesOfType(type.java).hash)
    return PropagationResult(EntityCell(this.id, this.type), tokenSet,
                             listOf(traces to UpdateType.DIFF))
  }

  override fun data(): List<T> {
    error("This should not be accessed")
  }
}

@Suppress("UNCHECKED_CAST")
internal class FlatMapCell<T, K>(
  id: CellId,
  val mapping: (T, ImmutableEntityStorage) -> Iterable<K>,
  private val memory: PersistentMap<Match, Iterable<K>>,
) : Cell<List<K>>(id) {

  private var dataCache: List<K>? = null

  override fun input(prevData: TokenSet,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<List<K>> {
    val generatedTokens = TokenSet()
    val traces = ArrayList<Pair<ReadTraceHashSet, UpdateType>>()
    val newMemory = memory.mutate { mutableMemory ->
      prevData.removedTokens().forEach { token ->
        val removedValue = mutableMemory.remove(token.match)
        removedValue?.forEach {
          generatedTokens += it.toToken(Operation.REMOVED, token.match)
        }
      }
      val target = LongArrayList()
      val tracker = ReadTracker.tracedSnapshot(newSnapshot, target)
      prevData.addedTokens().forEach { token ->
        target.clear()
        val mappingTarget = token.getData(tracker)
        val mappedValues = mapping(mappingTarget as T, tracker)
        val newTraces = ReadTraceHashSet(target)

        mutableMemory[token.match] = mappedValues

        mappedValues.forEach {
          generatedTokens += it.toToken(Operation.ADDED, token.match)
        }
        val recalculate = UpdateType.RECALCULATE(token.match)
        traces += newTraces to recalculate
      }
    }
    return PropagationResult(FlatMapCell(id, mapping, newMemory), generatedTokens, traces)
  }

  override fun data(): List<K> {
    // There is no synchronization as this is okay to calculate data twice
    val existingData = dataCache
    if (existingData != null) {
      return existingData
    }

    val res = memory.values.flatten()
    this.dataCache = res
    return res
  }
}

@Suppress("UNCHECKED_CAST")
internal class GroupByCell<T, K, V>(
  id: CellId,
  val keySelector: (T) -> K,
  val valueTransform: (T) -> V,
  private val myMemory: PersistentMap<Match, Pair<K, V>>,
) : Cell<Map<K, List<V>>>(id) {

  private var mapCache: Map<K, List<V>>? = null

  override fun input(prevData: TokenSet,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<Map<K, List<V>>> {
    val generatedTokens = TokenSet()
    val traces = ArrayList<Pair<ReadTraceHashSet, UpdateType>>()
    val newMemory = myMemory.mutate { mutableMemory ->
      prevData.removedTokens().forEach { token ->
        val removedValue = mutableMemory.remove(token.match)
        if (removedValue != null) {
          generatedTokens += removedValue.toToken(Operation.REMOVED, token.match)
        }
      }
      val target = LongArrayList()
      val tracker = ReadTracker.tracedSnapshot(newSnapshot, target)
      prevData.addedTokens().forEach { token ->
        target.clear()
        val origData = token.getData(tracker)
        val keyToValue = keySelector(origData as T) to valueTransform(origData as T)
        val newTraces = ReadTraceHashSet(target)

        mutableMemory[token.match] = keyToValue

        generatedTokens += keyToValue.toToken(Operation.ADDED, token.match)

        val recalculate = UpdateType.RECALCULATE(token.match)
        traces += newTraces to recalculate
      }
    }
    return PropagationResult(GroupByCell(id, keySelector, valueTransform, newMemory), generatedTokens, traces)
  }

  override fun data(): Map<K, List<V>> = buildAndGetMap()

  private fun buildAndGetMap(): Map<K, List<V>> {
    val myMapCache = mapCache
    if (myMapCache != null) return myMapCache
    val res = mutableMapOf<K, MutableList<V>>()
    myMemory.values.forEach { (k, v) ->
      res.getOrPut(k) { ArrayList() }.add(v)
    }
    mapCache = res
    return res
  }
}