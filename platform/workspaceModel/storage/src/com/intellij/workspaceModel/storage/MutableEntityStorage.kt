// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import org.jetbrains.annotations.ApiStatus

/**
 * Writeable interface to a storage. Use it if you need to build a storage from scratch or modify an existing storage in a way which requires
 * reading its state after modifications.
 */
interface MutableEntityStorage : EntityStorage {
  @Deprecated("The name may be misleading, use !hasChanges() instead", ReplaceWith("!hasChanges()"))
  fun isEmpty(): Boolean

  /**
   * Returns `true` if there are changes recorded in this storage after its creation. Note, that this method may return `true` if these
   * changes actually don't modify the resulting set of entities, you may use [hasSameEntities] to perform more sophisticated check.
   */
  fun hasChanges(): Boolean
  
  infix fun <T : WorkspaceEntity> addEntity(entity: T): T

  fun <M : WorkspaceEntity.Builder<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T

  /**
   * Remove the entity from the builder.
   * Returns true if the entity was removed, false if the entity was not in the storage
   */
  fun removeEntity(e: WorkspaceEntity): Boolean
  fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage)

  /**
   * Return changes in entities recorded in this instance. [original] parameter is used to get the old instances of modified
   * and removed entities.
   */
  fun collectChanges(original: EntityStorage): Map<Class<*>, List<EntityChange<*>>>
  fun addDiff(diff: MutableEntityStorage)

  /**
   * Returns `true` if this instance contains entities with the same properties as [original] storage it was created from. 
   * The difference from [hasChanges] is that this method will return `true` in cases when an entity was removed, and then a new entity
   * with the same properties was added.
   */
  fun hasSameEntities(original: EntityStorage): Boolean

  /**
   * Please see [EntityStorage.getExternalMapping] for naming conventions
   */
  fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T>
  fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex

  val modificationCount: Long

  @ApiStatus.Internal
  fun setUseNewRbs(value: Boolean)

  companion object {
    @JvmStatic
    fun create(): MutableEntityStorage = MutableEntityStorageImpl.create()

    @JvmStatic
    fun from(storage: EntityStorage): MutableEntityStorage = MutableEntityStorageImpl.from(storage)
  }
}

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