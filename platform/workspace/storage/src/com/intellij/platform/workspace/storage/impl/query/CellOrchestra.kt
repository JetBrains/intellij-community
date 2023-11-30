// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import kotlinx.collections.immutable.*
import kotlin.reflect.KClass

@JvmInline
internal value class CellOrchestra(val cells: PersistentList<Cell<*>>) {
  fun snapshotInput(snapshot: EntityStorageSnapshot): CellOrchestra {
    return cells.mutate {
      var tokens: List<Token> = emptyList()
      it.indices.forEach { index ->
        val cell = it[index]
        val snapshotInput = cell.snapshotInput
        val cellAndTokens: Pair<Cell<out Any?>, List<Token>>?
        if (snapshotInput != null) {
          cellAndTokens = snapshotInput.invoke(snapshot)
        }
        else {
          cellAndTokens = cell.input(tokens)
        }
        if (cellAndTokens != null) {
          tokens = cellAndTokens.second
          it[index] = cellAndTokens.first
        }
        else {
          return@forEach
        }
      }
    }.orchestra
  }

  fun changeInput(newSnapshot: EntityStorageSnapshot, changes: Map<Class<*>, List<EntityChange<*>>>): CellOrchestra {
    return cells.mutate {
      var tokens: List<Token> = emptyList()
      it.indices.forEach { index ->
        val cell = it[index]
        val changesInput = cell.changesInput
        val cellAndTokens: Pair<Cell<out Any?>, List<Token>>?
        // TODO: What if we have both diff and tokens input
        // TODO: Input for other cells (Not only EntityCell)
        if (changesInput != null) {
          cellAndTokens = changesInput.invoke(newSnapshot, changes)
        }
        else {
          cellAndTokens = cell.input(tokens)
        }
        if (cellAndTokens != null) {
          tokens = cellAndTokens.second
          it[index] = cellAndTokens.first
        }
        else {
          return@forEach
        }
      }
    }.orchestra
  }

  fun <T> data(): T {
    return cells.last().data() as T
  }

  private val PersistentList<Cell<*>>.orchestra
    get() = CellOrchestra(this)
}

public sealed class Cell<T> {
  public open val snapshotInput: ((EntityStorageSnapshot) -> Pair<Cell<T>, List<Token>>?)? = null
  public open val changesInput: ((EntityStorageSnapshot, Map<Class<*>, List<EntityChange<*>>>) -> Pair<Cell<T>, List<Token>>?)? = null
  public abstract fun input(prevData: List<Token>): Pair<Cell<T>, List<Token>>?
  public abstract fun data(): T
}

public sealed class CollectionCell<T> : Cell<List<T>>()
public sealed class AssociationCell<K, V> : Cell<Map<K, V>>()

public class EntitiesCell<T : WorkspaceEntity>(
  public val type: KClass<T>,
  public val storage: EntityStorageSnapshot? = null
) : CollectionCell<T>() {

  override val snapshotInput: (EntityStorageSnapshot) -> Pair<Cell<List<T>>, List<Token>>?
    get() = { snapshot ->
      val newCell = EntitiesCell(this.type, snapshot)
      newCell to newCell.data().map { value -> Token(Operation.ADDED, (value as WorkspaceEntityBase).id, value) }
    }

  override val changesInput: (EntityStorageSnapshot, Map<Class<*>, List<EntityChange<*>>>) -> Pair<Cell<List<T>>, List<Token>>?
    get() = { newSnapshot, changes ->
      val typeChanges = changes[type.java]?.filter { it is EntityChange.Added || it is EntityChange.Removed } ?: emptyList()
      val tokens = typeChanges.map { change ->
        val trackedEntity = change.newEntity?.createReference<WorkspaceEntity>()?.resolve(newSnapshot)
        val entityItself = trackedEntity ?: change.oldEntity
        val id = (entityItself!! as WorkspaceEntityBase).id
        val operation = if (change is EntityChange.Added) Operation.ADDED else Operation.REMOVED
        Token(operation, id, entityItself)
      }

      val typeModifications = changes[type.java]?.filterIsInstance<EntityChange.Replaced<*>>() ?: emptyList()
      val modifyTokens = typeModifications.map { change ->
        val trackedEntity = change.newEntity.createReference<WorkspaceEntity>().resolve(newSnapshot)!!
        val id = (trackedEntity as WorkspaceEntityBase).id
        Token(Operation.MODIFIED, id, trackedEntity)
      }

      EntitiesCell(this.type, newSnapshot) to (tokens + modifyTokens)
    }

  override fun input(prevData: List<Token>): Pair<Cell<List<T>>, List<Token>> = error("There should be no input")

  // This cell doesn't have the memory as there is a hypothesis that requesting this data from the storage is fast enough
  override fun data(): List<T> = storage?.entities(type.java)?.toList() ?: emptyList()
}

public class FlatMapCell<T, K>(
  public val mapping: (T) -> Iterable<K>,
  public val memory: PersistentMap<EntityId, PersistentList<K>> = persistentMapOf(),
) : CollectionCell<K>() {
  override fun input(prevData: List<Token>): Pair<Cell<List<K>>, List<Token>> {
    val generatedTokens = mutableListOf<Token>()
    val newMemory = memory.mutate { myMemory ->
      prevData.forEach { token ->
        when (token.isAdded) {
          Operation.ADDED -> {
            val mappedValues = mapping(token.info as T)
            myMemory[token.index] = myMemory.getOrDefault(token.index, persistentListOf()).mutate { list -> list.addAll(mappedValues) }
            // TODO Should we generate events for each elements or one event for the whole collection?
            mappedValues.forEach {
              generatedTokens += Token(Operation.ADDED, token.index, it as Any)
            }
          }
          Operation.REMOVED -> {
            val removed = myMemory.remove(token.index) ?: error("Value expected to exist in memory")
            removed.forEach {
              generatedTokens += Token(Operation.REMOVED, token.index, it as Any)
            }
          }
          Operation.MODIFIED -> {
            // TODO: Should we generate one MODIFIED event?
            val removed = myMemory.remove(token.index) ?: error("Value expected to exist in memory")
            removed.forEach {
              generatedTokens += Token(Operation.REMOVED, token.index, it as Any)
            }

            val mappedValues = mapping(token.info as T)
            myMemory[token.index] = myMemory.getOrDefault(token.index, persistentListOf()).mutate { list -> list.addAll(mappedValues) }
            mappedValues.map {
              generatedTokens += Token(Operation.ADDED, token.index, it as Any)
            }
          }
        }
      }
    }
    return FlatMapCell(mapping, newMemory) to generatedTokens
  }

  override fun data(): List<K> = memory.values.flatten().toList()
}

public class GroupByCell<T, K, V>(
  public val keySelector: (T) -> K,
  public val valueTransform: (T) -> V,
  public val memory: PersistentMap<EntityId, PersistentList<Pair<K, V>>> = persistentMapOf(),
) : AssociationCell<K, List<V>>() {

  private var mapCache: Map<K, List<V>>? = null

  override fun input(prevData: List<Token>): Pair<Cell<Map<K, List<V>>>, List<Token>> {
    val generatedTokens = mutableListOf<Token>()
    val newMemory = memory.mutate { myMemory ->
      prevData.forEach { token ->
        when (token.isAdded) {
          Operation.ADDED -> {
            val origData = token.info as T
            val key = keySelector(origData)
            val value = valueTransform(origData)
            myMemory[token.index] = myMemory.getOrDefault(token.index, persistentListOf()).mutate { list -> list.add(key to value) }
            generatedTokens += Token(Operation.ADDED, token.index, key to value)
          }
          Operation.REMOVED -> {
            val removedEntity = myMemory.remove(token.index) ?: error("Value expected")
            removedEntity.forEach { (key, value) ->
              generatedTokens += Token(Operation.REMOVED, token.index, key to value)
            }
          }
          Operation.MODIFIED -> {
            val removedEntity = myMemory.remove(token.index) ?: error("Value expected")
            removedEntity.forEach { (key, value) ->
              generatedTokens += Token(Operation.REMOVED, token.index, key to value)
            }

            val origData = token.info as T
            val key = keySelector(origData)
            val value = valueTransform(origData)
            myMemory[token.index] = myMemory.getOrDefault(token.index, persistentListOf()).mutate { list -> list.add(key to value) }
            generatedTokens += Token(Operation.ADDED, token.index, key to value)
          }
        }
      }
    }
    return GroupByCell(keySelector, valueTransform, newMemory) to generatedTokens
  }

  override fun data(): Map<K, List<V>> = buildAndGetMap()

  private fun buildAndGetMap(): Map<K, List<V>> {
    val myMapCache = mapCache
    if (myMapCache != null) return myMapCache
    val res = mutableMapOf<K, MutableList<V>>()
    memory.forEach { (_, items) ->
      items.forEach { (k, v) ->
        res.getOrPut(k) { ArrayList() }.add(v)
      }
    }
    mapCache = res
    return res
  }
}

public class Token(
  public val isAdded: Operation,
  public val index: EntityId,
  public val info: Any,
)

public enum class Operation {
  ADDED,
  REMOVED,
  MODIFIED,
}