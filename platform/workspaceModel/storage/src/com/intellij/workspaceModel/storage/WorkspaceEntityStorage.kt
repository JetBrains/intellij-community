// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * A prototype of a storage system for the project model which stores data in typed entities. Entities are represented by interfaces
 * implementing WorkspaceEntity interface.
 */

/**
 * A base interface for entities. A entity must be an interface which extend [WorkspaceEntity] and have 'val' properties of the following types:
 * * primitive types;
 * * String;
 * * enum;
 * * [VirtualFileUrl];
 * * [WorkspaceEntity] or [PersistentEntityId];
 * * [List] of another allowed type;
 * * another data class with properties of the allowed types (references to entities must be wrapped into [EntityReference]);
 * * sealed abstract class where all implementations satisfy these requirements.
 */
interface WorkspaceEntity {
  val entitySource: EntitySource

  /**
   * Returns `true` if this entity and [e] have the same type and the same properties. Properties of type [WorkspaceEntity] are compared by
   * internal IDs of the corresponding entities.
   */
  fun hasEqualProperties(e: WorkspaceEntity): Boolean
}

/**
 * Base interface for modifiable variant of [Unmodifiable] entity. Its implementation must be an interface which overrides each 'val' property in [Unmodifiable] interface
 * with 'var' modifier. Properties of type [List] may be overridden with [MutableList]. The implementation can be used to [create a new entity][WorkspaceEntityStorageBuilder.addEntity]
 * or [modify an existing value][WorkspaceEntityStorageBuilder.modifyEntity].
 *
 * It's possible create a single interface with 'var' properties and use it for both read and write operations but you should be cautious to
 * avoid calling setters on entity instances directly (such calls will fail at runtime).
 */
interface ModifiableWorkspaceEntity<Unmodifiable : WorkspaceEntity> : WorkspaceEntity

/**
 * Declares a place from which an entity came.
 * Usually contains enough information to identify a project location.
 * An entity source must be serializable along with entities, so there are some limits to implementation.
 * It must be a data class which contains read-only properties of the following types:
 * * primitive types;
 * * String;
 * * enum;
 * * [List] of another allowed type;
 * * another data class with properties of the allowed types;
 * * sealed abstract class where all implementations satisfy these requirements.
 */
interface EntitySource

/**
 * Base interface for entities which may need to find all entities referring to them.
 */
interface ReferableWorkspaceEntity : WorkspaceEntity {
  /**
   * Returns all entities of type [R] which [propertyName] property refers to this entity. Consider using type-safe variant referrers(KProperty1) instead.
   */
  fun <R : WorkspaceEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R>
}

/**
 * Returns all entities of type [R] which [property] refers to this entity.
 */
inline fun <E : ReferableWorkspaceEntity, reified R : WorkspaceEntity> E.referrers(property: KProperty1<R, E?>): Sequence<R> {
  return referrers(R::class.java, property.name)
}

/**
 * Represents a reference to a entity inside a data class stored in [WorkspaceEntity]'s implementation. Such references are always valid (use [PersistentEntityId] for references which
 * may not be resolved), they are used to allow updating a entity without replacing all entities it refers to and also to get all referrers
 * via [WorkspaceEntityStorage.referrers].
 *
 * todo Do we really need this? May be it's enough to support references to other entities only as direct properties of [WorkspaceEntity]?
 */
abstract class EntityReference<out E : WorkspaceEntity> {
  abstract fun resolve(storage: WorkspaceEntityStorage): E
}

/**
 * A base class for typed hierarchical entity IDs. An implementation must be a data class which contains read-only properties of the following types:
 * * primitive types,
 * * String,
 * * enum,
 * * another data class with properties of the allowed types;
 * * sealed abstract class where all implementations satisfy these requirements.
 */
abstract class PersistentEntityId<out E : WorkspaceEntityWithPersistentId> {
  abstract val parentId: PersistentEntityId<*>?

  /** Text which can be shown in an error message if id cannot be resolved */
  abstract val presentableName: String

  fun resolve(storage: WorkspaceEntityStorage): E? = storage.resolve(this)
  abstract override fun toString(): String
}

interface WorkspaceEntityWithPersistentId : WorkspaceEntity {
  // TODO Make it a property
  fun persistentId(): PersistentEntityId<*>
}

interface WorkspaceEntityStorage {
  fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E>
  fun <E : WorkspaceEntity, R : WorkspaceEntity> referrers(e: E, entityClass: KClass<R>, property: KProperty1<R, EntityReference<E>>): Sequence<R>
  fun <E : WorkspaceEntityWithPersistentId, R : WorkspaceEntity> referrers(id: PersistentEntityId<E>, entityClass: Class<R>): Sequence<R>
  fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E?
  fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T>
  fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>>
}

interface WorkspaceEntityStorageBuilder : WorkspaceEntityStorage, WorkspaceEntityStorageDiffBuilder {
  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> addEntity(clazz: Class<M>,
                                                                                 source: EntitySource,
                                                                                 initializer: M.() -> Unit): T

  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T
  override fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T
  override fun removeEntity(e: WorkspaceEntity)
  fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E>
  fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: WorkspaceEntityStorage)

  /**
   * Return changes in entities recorded in this instance. [original] parameter is used to get the old instances of modified
   * and removed entities.
   */
  fun collectChanges(original: WorkspaceEntityStorage): Map<Class<*>, List<EntityChange<*>>>

  // Reset all collected changes. TODO ugly!
  // This method doesn't reset builder to it initial state, but just resets a changelog,
  //   so next call to collectChanges will return empty list
  fun resetChanges()

  fun toStorage(): WorkspaceEntityStorage

  companion object {
    fun create(): WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilderImpl.create()

    fun from(storage: WorkspaceEntityStorage): WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilderImpl.from(storage)
  }
}

sealed class EntityChange<T : WorkspaceEntity> {
  data class Added<T : WorkspaceEntity>(val entity: T) : EntityChange<T>()
  data class Removed<T : WorkspaceEntity>(val entity: T) : EntityChange<T>()
  data class Replaced<T : WorkspaceEntity>(val oldEntity: T, val newEntity: T) : EntityChange<T>()
}

interface WorkspaceEntityStorageDiffBuilder {
  fun isEmpty(): Boolean

  fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> addEntity(clazz: Class<M>, source: EntitySource, initializer: M.() -> Unit): T
  fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T
  fun removeEntity(e: WorkspaceEntity)
  fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T

  // Returns an association between an entity in diff and an entity in the current builder
  fun addDiff(diff: WorkspaceEntityStorageDiffBuilder): Map<WorkspaceEntity, WorkspaceEntity>
  fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T>
  fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T>

  val modificationCount: Long

  companion object {
    fun create(underlyingStorage: WorkspaceEntityStorage): WorkspaceEntityStorageDiffBuilder = WorkspaceEntityStorageBuilder.from(underlyingStorage)
  }
}
