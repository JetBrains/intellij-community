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
import org.jetbrains.annotations.ApiStatus
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

  abstract fun input(prevData: MatchList, newSnapshot: ImmutableEntityStorage): PropagationResult<T>
  abstract fun data(): T
}

@ApiStatus.Internal
public interface Diff<T> {
  public val added: List<T>
  public val removed: List<T>
}

internal class DiffImpl<T>(override val added: List<T>, override val removed: List<T>) : Diff<T>

internal class DiffCollectorCell<T>(
  id: CellId,
  val addedData: List<T>,
  val removedData: List<T>,
) : Cell<T>(id) {
  override fun input(prevData: MatchList, newSnapshot: ImmutableEntityStorage): PropagationResult<T> {
    error("Another input should be called")
  }

  /**
   * [DiffCollectorCell] has a special processing. This cell can be used only in reactive read and
   *   we calculcate the removed data by previous snapshot.
   * It's not possible to use the [prevSnapshot] in cache because there is no such snapshot at all. Previous calculatio of the snapshot can
   *   be done at any previous snapshot.
   */
  fun input(prevData: MatchList, newSnapshot: ImmutableEntityStorage, prevSnapshot: ImmutableEntityStorage?): PropagationResult<T> {
    val newAddedData = ArrayList<T>()
    val newRemovedData = ArrayList<T>()
    prevData.removedMatches().forEach {
      if (prevSnapshot == null) error("Prev snapshot cannot be null for diff")
      newRemovedData.add(it.getData(prevSnapshot) as T)
    }
    prevData.addedMatches().forEach {
      newAddedData.add(it.getData(newSnapshot) as T)
    }
    val newCell = DiffCollectorCell(id, newAddedData, newRemovedData)
    return PropagationResult(newCell, MatchList(), emptyList())
  }

  override fun data(): T {
    error("Should not be accessed")
  }
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
    val matches = ids
      .map { value -> MatchWithEntityId(value) }
      .toList()
    val traces = ReadTraceHashSet()
    traces.add(ReadTrace.EntitiesOfType(type.java).hash)
    val matchList = MatchList().also { set -> matches.forEach { set.addedMatch(it) } }
    return PropagationResult(newCell, matchList, listOf(traces to UpdateType.DIFF))
  }

  override fun input(prevData: MatchList,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<List<T>> {
    val matchList = MatchList()
    prevData.addedMatches()
      .asSequence()
      .filter { (it as MatchWithEntityId).entityId.clazz.findWorkspaceEntity().kotlin == type }
      .forEach { matchList.addedMatch(it) }
    prevData.removedMatches()
      .asSequence()
      .filter { (it as MatchWithEntityId).entityId.clazz.findWorkspaceEntity().kotlin == type }
      .forEach { matchList.removedMatch(it) }

    val traces = ReadTraceHashSet()
    traces.add(ReadTrace.EntitiesOfType(type.java).hash)
    return PropagationResult(EntityCell(this.id, this.type), matchList,
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
  private val memory: PersistentMap<Match, Iterable<Match>>,
) : Cell<List<K>>(id) {

  private var dataCache: List<K>? = null

  override fun input(prevData: MatchList,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<List<K>> {
    val generatedMatches = MatchList()
    val traces = ArrayList<Pair<ReadTraceHashSet, UpdateType>>()
    val newMemory = memory.mutate { mutableMemory ->
      prevData.removedMatches().forEach { match ->
        val removedValue = mutableMemory.remove(match) ?: error("Nothing to remove")
        removedValue.forEach {
          generatedMatches.removedMatch(it)
        }
      }
      val target = LongArrayList()
      val tracker = ReadTracker.tracedSnapshot(newSnapshot, target)
      val res = HashMap<Match, Iterable<Match>>()
      prevData.addedMatches().forEach { match ->
        target.clear()
        val mappingTarget = match.getData(tracker)
        val mappedValues = mapping(mappingTarget as T, tracker).map { it.toMatch(match) }
        val newTraces = ReadTraceHashSet(target)

        res[match] = mappedValues

        mappedValues.forEach {
          generatedMatches.addedMatch(it)
        }
        val recalculate = UpdateType.RECALCULATE(match)
        traces += newTraces to recalculate
      }
      mutableMemory.putAll(res)
    }
    return PropagationResult(FlatMapCell(id, mapping, newMemory), generatedMatches, traces)
  }

  override fun data(): List<K> {
    // There is no synchronization as this is okay to calculate data twice
    val existingData = dataCache
    if (existingData != null) {
      return existingData
    }

    val res = memory.values.flatten().map { it.value() as K }
    this.dataCache = res
    return res
  }
}

@Suppress("UNCHECKED_CAST")
internal class MapCell<T, K>(
  id: CellId,
  val mapping: (T, ImmutableEntityStorage) -> K,
  private val memory: PersistentMap<Match, Match>,
) : Cell<List<K>>(id) {

  private var dataCache: List<K>? = null

  override fun input(prevData: MatchList,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<List<K>> {
    val generatedMatches = MatchList()
    val traces = ArrayList<Pair<ReadTraceHashSet, UpdateType>>()
    val newMemory = memory.mutate { mutableMemory ->
      prevData.removedMatches().forEach { match ->
        val removedValue = mutableMemory.remove(match) ?: error("Nothing to remove")
        removedValue.let { generatedMatches.removedMatch(it) }
      }
      val target = LongArrayList()
      val tracker = ReadTracker.tracedSnapshot(newSnapshot, target)
      val res = HashMap<Match, Match>()
      prevData.addedMatches().forEach { match ->
        target.clear()
        val mappingTarget = match.getData(tracker)
        val mappedValue = mapping(mappingTarget as T, tracker)
        val mappedMatch = mappedValue.toMatch(match)
        val newTraces = ReadTraceHashSet(target)

        res[match] = mappedMatch

        if (mappedValue != null) {
          generatedMatches.addedMatch(mappedMatch)
        }
        val recalculate = UpdateType.RECALCULATE(match)
        traces += newTraces to recalculate
      }
      mutableMemory.putAll(res)
    }
    return PropagationResult(MapCell(id, mapping, newMemory), generatedMatches, traces)
  }

  override fun data(): List<K> {
    // There is no synchronization as this is okay to calculate data twice
    val existingData = dataCache
    if (existingData != null) {
      return existingData
    }

    val res = memory.values.map { it.value() as K }
    this.dataCache = res
    return res
  }
}

@Suppress("UNCHECKED_CAST")
internal class GroupByCell<T, K, V>(
  id: CellId,
  val keySelector: (T) -> K,
  val valueTransform: (T) -> V,
  private val myMemory: PersistentMap<Match, Match>,
) : Cell<Map<K, List<V>>>(id) {

  private var mapCache: Map<K, List<V>>? = null

  override fun input(prevData: MatchList,
                     newSnapshot: ImmutableEntityStorage): PropagationResult<Map<K, List<V>>> {
    val generatedMatches = MatchList()
    val traces = ArrayList<Pair<ReadTraceHashSet, UpdateType>>()
    val newMemory = myMemory.mutate { mutableMemory ->
      prevData.removedMatches().forEach { match ->
        val removedValue = mutableMemory.remove(match) ?: error("Nothing to remove")
        generatedMatches.removedMatch(removedValue)
      }
      val target = LongArrayList()
      val tracker = ReadTracker.tracedSnapshot(newSnapshot, target)
      prevData.addedMatches().forEach { match ->
        target.clear()
        val origData = match.getData(tracker)
        val keyToValue = (keySelector(origData as T) to valueTransform(origData as T)).toMatch(match)
        val newTraces = ReadTraceHashSet(target)

        mutableMemory[match] = keyToValue

        generatedMatches.addedMatch(keyToValue)

        val recalculate = UpdateType.RECALCULATE(match)
        traces += newTraces to recalculate
      }
    }
    return PropagationResult(GroupByCell(id, keySelector, valueTransform, newMemory), generatedMatches, traces)
  }

  override fun data(): Map<K, List<V>> = buildAndGetMap()

  private fun buildAndGetMap(): Map<K, List<V>> {
    val myMapCache = mapCache
    if (myMapCache != null) return myMapCache
    val res = mutableMapOf<K, MutableList<V>>()
    myMemory.values.forEach { match ->
      val (k, v) = match.value() as Pair<K, V>
      res.getOrPut(k) { ArrayList() }.add(v)
    }
    mapCache = res
    return res
  }
}