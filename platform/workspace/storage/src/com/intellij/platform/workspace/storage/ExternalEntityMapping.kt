// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage

/**
 * Provides a way to associate [WorkspaceEntity] with external data of type [T]. 
 * The association survives modifications of an entity, and is automatically removed when the entity is deleted.  
 * Use [MutableEntityStorage.getMutableExternalMapping] to fill the index and [EntityStorage.getExternalMapping] to access it.
 */
public interface ExternalEntityMapping<T> {
  /**
   * Returns all entities associated with the given [data].
   */
  public fun getEntities(data: T): List<WorkspaceEntity>

  /**
   * Returns the first entity associated with the given [data] or `null` if there are no such entities.
   * `getFirstEntity(data)` is equivalent to `getEntities(data).firstOrNull()`, but works faster.
   */
  public fun getFirstEntity(data: T): WorkspaceEntity?

  /**
   * Returns data associated with the given [entity] or `null` if no data is associated.
   */
  public fun getDataByEntity(entity: WorkspaceEntity): T?

  /**
   * Applies the given [action] to all associations between entities and their data.
   */
  public fun forEach(action: (key: WorkspaceEntity, value: T) -> Unit)

  /**
   * Returns number of entities with associated data.
   */
  public fun size(): Int
}

/**
 * Provides a way to create or modify mapping between entities and external data. 
 * Use [MutableEntityStorage.getMutableExternalMapping] to obtains an instance of this interface.
 * The implementation is not thread-safe.
 */
public interface MutableExternalEntityMapping<T> : ExternalEntityMapping<T> {
  /**
   * Associates [data] with the given [entity]. 
   * If the mapping already contains an association with this [entity], the new one replaces the previous association.
   */
  public fun addMapping(entity: WorkspaceEntity, data: T)

  /**
   * Associates [data] with the given [entity] and returns `true` if no data is associated with it yet. 
   * Otherwise, returns `false` and doesn't modify the mapping.
   */
  public fun addIfAbsent(entity: WorkspaceEntity, data: T): Boolean

  /**
   * Returns an existing data associated with [entity] if any. 
   * If no data is associated, uses [defaultValue] to create one, associates it with [entity] and return the associated data.
   */
  public fun getOrPutDataByEntity(entity: WorkspaceEntity, defaultValue: () -> T): T

  /**
   * Removes data associated with the given [entity] and returns it.
   * If no data was associated, returns `null`.
   */
  public fun removeMapping(entity: WorkspaceEntity): T?
}

/**
 * Unique key for the external mappings.
 *
 * Each instance of key refers to a separate instance of the external mapping. Each call for [create] creates a new instance of key
 *   even if the existing name is used.
 * In order to use the key from multiple places, it should be stored in a field.
 */
// MUST NOT be data class. Each instance is unique
public class ExternalMappingKey<T>(private val name: String) {
  override fun toString(): String {
    return "ExternalMappingKey[name=$name]"
  }

  public companion object {
    /**
     * Always creates a new instance of a key, even if the name is already used.
     *
     * Suggested, but not obligated naming convention: identifier should be a dot-separated string prepended with the product name, e.g.
     * * intellij.modules.bridge
     * * intellij.facets.bridge
     * * rider.backend.id
     */
    public fun <T> create(name: String): ExternalMappingKey<T> = ExternalMappingKey(name)
  }
}
