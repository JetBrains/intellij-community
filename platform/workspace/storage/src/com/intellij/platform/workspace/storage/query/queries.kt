// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.query

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.query.*
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

// Basic interface
public sealed interface StorageQuery<T> {
  @get:ApiStatus.Internal
  public val queryId: QueryId
}

/**
 * Queries with collections as a result of operations
 *
 * Should not be used directly, but via [entities], [map] and other functions.
 */
public sealed interface CollectionQuery<T> : StorageQuery<Collection<T>> {
  public class EachOfType<T : WorkspaceEntity> internal constructor(
    override val queryId: QueryId,
    public val type: KClass<T>
  ) : CollectionQuery<T>

  public class FlatMapTo<T, K> internal constructor(
    override val queryId: QueryId,
    public val from: CollectionQuery<T>,
    public val map: (T, ImmutableEntityStorage) -> Iterable<K>,
  ) : CollectionQuery<K>

  public class MapTo<T, K> internal constructor(
    override val queryId: QueryId,
    public val from: CollectionQuery<T>,
    public val map: (T, ImmutableEntityStorage) -> K,
  ) : CollectionQuery<K>
}

/**
 * Queries with Maps as a result of operation
 *
 * Should not be used directly, but via [groupBy] function
 */
public sealed interface AssociationQuery<K, V> : StorageQuery<Map<K, V>> {
  public class GroupBy<T, K, V>(
    override val queryId: QueryId,
    public val from: CollectionQuery<T>,
    public val keySelector: (T) -> K,
    public val valueTransformer: (T) -> V,
  ) : AssociationQuery<K, List<V>>
}

/**
 * Convert a [StorageQuery] to [CellChain] that can be executed to calculate the cache.
 */
internal fun <T> StorageQuery<T>.compile(cellCollector: MutableList<Cell<*>> = mutableListOf()): CellChain {
  when (this) {
    is CollectionQuery<*> -> {
      when (this) {
        is CollectionQuery.EachOfType<*> -> {
          cellCollector.prepend(EntityCell(CellId(), type))
        }
        is CollectionQuery.FlatMapTo<*, *> -> {
          cellCollector.prepend(FlatMapCell(CellId(), map, persistentHashMapOf()))
          this.from.compile(cellCollector)
        }
        is CollectionQuery.MapTo<*, *> -> {
          cellCollector.prepend(MapCell(CellId(), map, persistentHashMapOf()))
          this.from.compile(cellCollector)
        }
      }
    }
    is AssociationQuery<*, *> -> {
      when (this) {
        is AssociationQuery.GroupBy<*, *, *> -> {
          cellCollector.prepend(GroupByCell(CellId(), keySelector, valueTransformer, persistentHashMapOf()))
          this.from.compile(cellCollector)
        }
      }
    }
  }
  return CellChain(cellCollector.toPersistentList(), this.queryId)
}

private fun <T> MutableList<T>.prepend(data: T) {
  this.add(0, data)
}
