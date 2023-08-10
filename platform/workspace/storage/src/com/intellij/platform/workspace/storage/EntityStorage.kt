// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.impl.EntityStorageSnapshotImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex
import org.jetbrains.annotations.NonNls

/**
 * A base interface for immutable [EntityStorageSnapshot] and [MutableEntityStorage].
 */
interface EntityStorage {
  /**
   * Returns a sequence containing all the entities of the given type from this storage. 
   * There are no guaranties about the order of the elements in the returned sequence.
   */
  fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E>

  /**
   * Returns number of entities of the given type in the storage.
   */
  fun <E : WorkspaceEntity> entitiesAmount(entityClass: Class<E>): Int

  /**
   * Returns a sequence containing all entities of type [entityClass] which contains a [SymbolicEntityId] property equal to the given [id].
   * There are no guaranties about the order of the elements in the returned sequence.
   */
  fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(id: SymbolicEntityId<E>, entityClass: Class<R>): Sequence<R>

  /**
   * Returns an entity which [symbolicId][WorkspaceEntityWithSymbolicId.symbolicId] property is equal to the given [id] or `null` if there 
   * is no such entity.
   */
  fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E?

  /**
   * Returns `true` if there is an entity which [symbolicId][WorkspaceEntityWithSymbolicId.symbolicId] property is equal to the given [id].
   * `contains(id)` is equivalent to `resolve(id) != null`, but works a bit faster.
   */
  operator fun <E : WorkspaceEntityWithSymbolicId> contains(id: SymbolicEntityId<E>): Boolean

  /**
   * Returns a mapping with the given [identifier] which associates entities from this storage with values of type [T]. 
   */
  fun <T> getExternalMapping(identifier: @NonNls String): ExternalEntityMapping<T>

  /**
   * Returns an index which allows to quickly find entities which refer to a particular [VirtualFileUrl][com.intellij.platform.workspace.storage.url.VirtualFileUrl]
   * in their properties.
   */
  fun getVirtualFileUrlIndex(): VirtualFileUrlIndex

  /**
   * Returns a map containing all entities from this storage which [entitySource][WorkspaceEntity.entitySource] property satisfies the
   * given [sourceFilter]. 
   */
  fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>>

  /**
   * Returns a snapshot of the current state. It won't be affected by future changes in this storage if it's mutable.
   */
  fun toSnapshot(): EntityStorageSnapshot
}

/**
 * An immutable snapshot of the storage state. 
 * It isn't affected by the further modifications of the storage.
 * 
 * Use [com.intellij.platform.backend.workspace.WorkspaceModel.currentSnapshot] to get an instance representing entities inside the IDE process. 
 */
interface EntityStorageSnapshot : EntityStorage {
  companion object {
    fun empty(): EntityStorageSnapshot = EntityStorageSnapshotImpl.EMPTY
  }
}

/**
 * Creates a mutable copy of `this` storage.
 */
fun EntityStorage.toBuilder(): MutableEntityStorage {
  return MutableEntityStorage.from(this)
}

