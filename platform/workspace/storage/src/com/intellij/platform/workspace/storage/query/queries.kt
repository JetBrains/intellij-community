// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.query

import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.reflect.KClass

// Basic interface
public sealed interface StorageQuery<T>

public sealed interface EntityBoundCollectionQuery<T> : StorageQuery<Collection<T>> {
  public class EachOfType<T : WorkspaceEntity>(public val type: KClass<T>) : EntityBoundCollectionQuery<T>
  public class FlatMapTo<T, K>(public val from: EntityBoundCollectionQuery<T>,
                               public val map: (T) -> Iterable<K>) : EntityBoundCollectionQuery<K>
}

public sealed interface EntityBoundAssociationQuery<K, V> : StorageQuery<Map<K, V>> {
  public class GroupBy<T, K, V>(
    public val from: EntityBoundCollectionQuery<T>,
    public val keySelector: (T) -> K,
    public val valueTransformer: (T) -> V,
  ) : EntityBoundAssociationQuery<K, List<V>>
}
