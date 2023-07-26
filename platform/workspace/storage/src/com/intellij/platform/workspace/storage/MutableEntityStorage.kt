// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.url.MutableVirtualFileUrlIndex
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Writeable interface to storage. 
 * Use it if you need to build a storage from scratch or modify an existing storage in a way which requires reading its state after 
 * modifications.
 * 
 * In order to modify entities inside the IDE process, use functions from [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel]
 * interface.
 * 
 * Instances of this interface are not thread safe.
 */
interface MutableEntityStorage : EntityStorage {
  /**
   * Returns `true` if there are changes recorded in this storage after its creation. Note, that this method may return `true` if these
   * changes actually don't modify the resulting set of entities, you may use [hasSameEntities] to perform more sophisticated check.
   */
  fun hasChanges(): Boolean

  /**
   * Add the given entity to the storage. All children of the entity will also be added.
   */
  infix fun <T : WorkspaceEntity> addEntity(entity: T): T

  /**
   * Modifies the given entity [e] by passing a builder interface for it to [change]. 
   * This function isn't supposed to be used directly, it's more convenient to use a specialized `modifyEntity` extension function which
   * is generated for each entity type.
   */
  fun <M : WorkspaceEntity.Builder<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T

  /**
   * Remove the entity from the storage if it's present. All child entities of the entity with non-null reference to the parent entity are
   * also removed.
   * @return `true` if the entity was removed, `false` if the entity was not in the storage
   */
  fun removeEntity(e: WorkspaceEntity): Boolean

  /**
   * Finds all entities which [entitySource][WorkspaceEntity.entitySource] satisfy the given [sourceFilter] and replaces them with the 
   * entities from [replaceWith]. 
   * This operation tries to minimize the number of changes in the storage: 
   * * if [replaceWith] contains an entity with the same properties as an existing entity, no [EntityChange] will be recorded; 
   * * if [replaceWith] contains an entity which [symbolicId][WorkspaceEntityWithSymbolicId.symbolicId] is equals to 
   * [symbolicId][WorkspaceEntityWithSymbolicId.symbolicId] of an existing entity, [EntityChange.Replaced] event will be recorded and old
   * children of the existing entity will be kept.
   * 
   * The exact list of optimizations is an implementation detail.
   */
  fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage)

  /**
   * Return changes in entities recorded in this instance. [original] parameter is used to get the old instances of modified
   * and removed entities.
   * 
   * This function isn't supported to be used by client code directly. In order to subscribe to changes in entities inside the IDE process,
   * use [WorkspaceModelTopics][com.intellij.platform.backend.workspace.WorkspaceModelTopics].
   */
  @ApiStatus.Internal
  fun collectChanges(original: EntityStorage): Map<Class<*>, List<EntityChange<*>>>

  /**
   * Merges changes from [diff] to this storage. 
   * It's supposed that [diff] was created either via [MutableEntityStorage.create] function, or via [MutableEntityStorage.from] with the 
   * same base storage as this one. 
   * Calling the function for a mutable storage with a different base storage may lead to unpredictable results.
   */
  fun addDiff(diff: MutableEntityStorage)

  /**
   * Returns `true` if this instance contains entities with the same properties as [original] storage it was created from. 
   * The difference from [hasChanges] is that this method will return `true` in cases when an entity was removed, and then a new entity
   * with the same properties was added.
   */
  fun hasSameEntities(original: EntityStorage): Boolean

  /**
   * Returns an existing or create a new mapping with the given [identifier].
   * By convention, identifier should be a dot-separated string prepended with the product name, e.g.
   * * intellij.modules.bridge
   * * intellij.facets.bridge
   * * rider.backend.id
   */
  fun <T> getMutableExternalMapping(identifier: @NonNls String): MutableExternalEntityMapping<T>
  
  @ApiStatus.Internal
  fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex

  /**
   * Returns a number which is incremented after each change in the storage.
   */
  val modificationCount: Long

  companion object {
    /**
     * Creates an empty mutable storage. It may be populated with new entities and passed to [addDiff] or [replaceBySource].  
     */
    @JvmStatic
    fun create(): MutableEntityStorage = MutableEntityStorageImpl.create()

    /**
     * Creates a mutable copy of the given [storage].
     */
    @JvmStatic
    fun from(storage: EntityStorage): MutableEntityStorage = MutableEntityStorageImpl.from(storage)
  }
}

/**
 * Describes a change in an entity. Instances of this class are obtained from [VersionedStorageChange].
 */
sealed class EntityChange<T : WorkspaceEntity> {
  /**
   * Returns the entity which was removed or replaced in the change.
   */
  abstract val oldEntity: T?

  /**
   * Returns the entity which was added or used as a replacement in the change.
   */
  abstract val newEntity: T?
  
  data class Added<T : WorkspaceEntity>(val entity: T) : EntityChange<T>() {
    override val oldEntity: T?
      get() = null
    override val newEntity: T
      get() = entity
  }
  data class Removed<T : WorkspaceEntity>(val entity: T) : EntityChange<T>() {
    override val oldEntity: T
      get() = entity
    override val newEntity: T?
      get() = null
  }
  data class Replaced<T : WorkspaceEntity>(override val oldEntity: T, override val newEntity: T) : EntityChange<T>()
}

/**
 * Marks a property as a key field for [replaceBySource][MutableEntityStorage.replaceBySource] operation.
 * Entities will be compared based on properties with this annotation.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class EqualsBy
