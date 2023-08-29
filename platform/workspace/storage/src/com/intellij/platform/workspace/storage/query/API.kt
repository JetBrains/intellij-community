// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.query

import com.intellij.platform.workspace.storage.WorkspaceEntity

public inline fun <reified T : WorkspaceEntity> entities(): EntityBoundCollectionQuery<T> = EntityBoundCollectionQuery.EachOfType(T::class)

public inline fun <reified T, K> EntityBoundCollectionQuery<T>.map(noinline mapping: (T) -> K): EntityBoundCollectionQuery<K> {
  // The map function can be represented using the flatMap.
  // This is probably less efficient, but it will reduce the amount of code to implement for now.
  // The question of performance can be reviewed later.
  return this.flatMap { setOf(mapping(it)) }
}

public inline fun <reified T, K> EntityBoundCollectionQuery<T>.flatMap(noinline mapping: (T) -> Iterable<K>): EntityBoundCollectionQuery<K> {
  return EntityBoundCollectionQuery.FlatMapTo(this, mapping)
}

public fun <T, K, V> EntityBoundCollectionQuery<T>.groupBy(
  keySelector: (T) -> K,
  valueTransformer: (T) -> V,
): EntityBoundAssociationQuery<K, List<V>> {
  return EntityBoundAssociationQuery.GroupBy(this, keySelector, valueTransformer)
}
