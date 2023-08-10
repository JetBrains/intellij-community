// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage

/**
 * Provides a way to associate [WorkspaceEntity] with external data of type [T]. 
 * The association survives modifications of an entity, and is automatically removed when the entity is deleted.  
 * Use [MutableEntityStorage.getMutableExternalMapping] to fill the index and [EntityStorage.getExternalMapping] to access it.
 */
interface ExternalEntityMapping<T> {
  /**
   * Returns all entities associated with the given [data].
   */
  fun getEntities(data: T): List<WorkspaceEntity>

  /**
   * Returns the first entity associated with the given [data] or `null` if there are no such entities.
   * `getFirstEntity(data)` is equivalent to `getEntities(data).firstOrNull()`, but works faster.
   */
  fun getFirstEntity(data: T): WorkspaceEntity?

  /**
   * Returns data associated with the given [entity] or `null` if no data is associated.
   */
  fun getDataByEntity(entity: WorkspaceEntity): T?

  /**
   * Applies the given [action] to all associations between entities and their data.
   */
  fun forEach(action: (key: WorkspaceEntity, value: T) -> Unit)

  /**
   * Returns number of entities with associated data.
   */
  fun size(): Int
}

/**
 * Provides a way to create or modify mapping between entities and external data. 
 * Use [MutableEntityStorage.getMutableExternalMapping] to obtains an instance of this interface.
 * The implementation is not thread-safe.
 */
interface MutableExternalEntityMapping<T> : ExternalEntityMapping<T> {
  /**
   * Associates [data] with the given [entity]. 
   * If the mapping already contains an association with this [entity], the new one replaces the previous association.
   */
  fun addMapping(entity: WorkspaceEntity, data: T)

  /**
   * Associates [data] with the given [entity] and returns `true` if no data is associated with it yet. 
   * Otherwise, returns `false` and doesn't modify the mapping.
   */
  fun addIfAbsent(entity: WorkspaceEntity, data: T): Boolean

  /**
   * Returns an existing data associated with [entity] if any. 
   * If no data is associated, uses [defaultValue] to create one, associates it with [entity] and return the associated data.
   */
  fun getOrPutDataByEntity(entity: WorkspaceEntity, defaultValue: () -> T): T

  /**
   * Removes data associated with the given [entity] and returns it.
   * If no data was associated, returns `null`.
   */
  fun removeMapping(entity: WorkspaceEntity): T?
}
