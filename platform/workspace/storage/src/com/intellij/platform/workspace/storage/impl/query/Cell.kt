// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.impl.cache.PropagationResult
import com.intellij.platform.workspace.storage.impl.cache.UpdateType
import com.intellij.platform.workspace.storage.impl.clazz
import com.intellij.platform.workspace.storage.impl.containers.PersistentMultiOccurenceMap
import com.intellij.platform.workspace.storage.impl.findWorkspaceEntity
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import com.intellij.platform.workspace.storage.trace.ReadTracker
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
    val tokens = snapshot
      .entities(type.java)
      .map { value -> Token.WithEntityId(Operation.ADDED, value.asBase().id) } // Maybe we can get ids directly without creating an entity
      .toList()
    val traces = ReadTraceHashSet()
    traces.add(ReadTrace.EntitiesOfType(type.java).hash)
    return PropagationResult(newCell, TokenSet(tokens), listOf(traces to UpdateType.DIFF))
  }

  override fun input(prevData: TokenSet,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<List<T>> {
    val tokenSet = TokenSet()
    prevData.addedTokens()
      .filter { (it as Token.WithEntityId).entityId.clazz.findWorkspaceEntity().kotlin == type }
      .forEach { tokenSet.add(it) }
    prevData.removedTokens()
      .filter { (it as Token.WithEntityId).entityId.clazz.findWorkspaceEntity().kotlin == type }
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
  private val memory: PersistentMultiOccurenceMap<Any?, Iterable<K>>,
) : Cell<List<K>>(id) {

  private var dataCache: List<K>? = null

  override fun input(prevData: TokenSet,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<List<K>> {
    val generatedTokens = TokenSet()
    val traces = ArrayList<Pair<ReadTraceHashSet, UpdateType>>()
    val newMemory = memory.mutate { mutableMemory ->
      prevData.removedTokens().forEach { token ->
        val removedValue = mutableMemory.remove(token.key())
        removedValue?.forEach {
          generatedTokens += it.toToken(Operation.REMOVED)
        }
      }
      prevData.addedTokens().forEach { token ->
        val (newTraces, mappedValues) = ReadTracker.traceHashes(newSnapshot) {
          val mappingTarget = token.getData(it)
          mapping(mappingTarget as T, it)
        }
        mutableMemory[token.key()] = mappedValues

        mappedValues.forEach {
          generatedTokens += it.toToken(Operation.ADDED)
        }
        val recalculate = when (token) {
          is Token.WithEntityId -> UpdateType.RECALCULATE(null, token.entityId)
          is Token.WithInfo -> UpdateType.RECALCULATE(token.info, null)
        }
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

    val res = memory.values().flatten()
    this.dataCache = res
    return res
  }
}

@Suppress("UNCHECKED_CAST")
internal class GroupByCell<T, K, V>(
  id: CellId,
  val keySelector: (T) -> K,
  val valueTransform: (T) -> V,
  private val myMemory: PersistentMultiOccurenceMap<Any?, Pair<K, V>>,
) : Cell<Map<K, List<V>>>(id) {

  private var mapCache: Map<K, List<V>>? = null

  override fun input(prevData: TokenSet,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<Map<K, List<V>>> {
    val generatedTokens = TokenSet()
    val traces = ArrayList<Pair<ReadTraceHashSet, UpdateType>>()
    val newMemory = myMemory.mutate { mutableMemory ->
      prevData.removedTokens().forEach { token ->
        val removedValue = mutableMemory.remove(token.key())
        if (removedValue != null) {
          generatedTokens += removedValue.toToken(Operation.REMOVED)
        }
      }
      prevData.addedTokens().forEach { token ->
        val (newTraces, keyToValue) = ReadTracker.traceHashes(newSnapshot) {
          val origData = token.getData(it)
          val key = keySelector(origData as T)
          val value = valueTransform(origData as T)
          key to value
        }

        mutableMemory[token.key()] = keyToValue

        generatedTokens += keyToValue.toToken(Operation.ADDED)

        val recalculate = when (token) {
          is Token.WithEntityId -> UpdateType.RECALCULATE(null, token.entityId)
          is Token.WithInfo -> UpdateType.RECALCULATE(token.info, null)
        }
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
    myMemory.values().forEach { (k, v) ->
      res.getOrPut(k) { ArrayList() }.add(v)
    }
    mapCache = res
    return res
  }
}