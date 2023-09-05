// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.query

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.reflect.full.isSubclassOf

public inline fun <reified T : WorkspaceEntity> entities(): CollectionQuery<T> {
  return CollectionQuery.EachOfType(T::class)
}

/**
 * This kind of query is not supported now because it will lead to [obj] leak.
 * With the current implementation, the [obj] will just remain in the caches for a long time.
 * Taking the fact that we store some disposable objects in external mappings, like Module bridges, we may end up in a situation
 *   that some disposed object is captured in memory
 *
 * Also, to support this query, we have to store the history of changes of external mappings in cache to make a lazy calculation on call.
 *   This will also lead to objects leak.
 */
public fun entitiesByExternalMapping(identifier: String, obj: Any): CollectionQuery<WorkspaceEntity> {
  TODO()
}

public fun <T> CollectionQuery<T>.filter(predicate: (T) -> Boolean): CollectionQuery<T> {
  return this.flatMap { item, _ -> if (predicate(item)) setOf(item) else emptySet() }
}

public inline fun <reified T> CollectionQuery<*>.filterIsInstance(): CollectionQuery<T> {
  return filter { it != null && it::class.isSubclassOf(T::class) }.map { it as T }
}

public fun <T> CollectionQuery<T?>.filterNotNull(): CollectionQuery<T> {
  return filter { it != null }.map { it as T }
}


public fun <T, K> CollectionQuery<T>.map(mapping: (T) -> K): CollectionQuery<K> {
  // The map function can be represented using the flatMap.
  // This is probably less efficient, but it will reduce the amount of code to implement for now.
  // The question of performance can be reviewed later.
  return this.flatMap { item, _ -> setOf(mapping(item)) }
}

public inline fun <reified T, K> CollectionQuery<T>.mapWithSnapshot(noinline mapping: (T, EntityStorageSnapshot) -> K): CollectionQuery<K> {
  // The map function can be represented using the flatMap.
  // This is probably less efficient, but it will reduce the amount of code to implement for now.
  // The question of performance can be reviewed later.
  return this.flatMap { item, snapshot -> setOf(mapping(item, snapshot)) }
}

public fun <T, K> CollectionQuery<T>.flatMap(mapping: (T, EntityStorageSnapshot) -> Iterable<K>): CollectionQuery<K> {
  return CollectionQuery.FlatMapTo(this, mapping)
}

public fun <T, K, V> CollectionQuery<T>.groupBy(
  keySelector: (T) -> K,
  valueTransformer: (T) -> V,
): AssociationQuery<K, List<V>> {
  return AssociationQuery.GroupBy(this, keySelector, valueTransformer)
}
