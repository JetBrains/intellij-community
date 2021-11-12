// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * A prototype of a storage system for the project model which stores data in typed entities. Entities are represented by interfaces
 * implementing WorkspaceEntity interface.
 */

/**
 * A base interface for entities. A entity may have properties of the following types:
 * * primitive types;
 * * String;
 * * enum;
 * * [VirtualFileUrl];
 * * [WorkspaceEntity] or [PersistentEntityId];
 * * [List] of another allowed type;
 * * [Array] of another allowed type;
 * * another data class with properties of the allowed types (references to entities must be wrapped into [EntityReference]);
 * * sealed abstract class where all implementations satisfy these requirements.
 *
 * Currently the entities are representing by classes inheriting from WorkspaceEntityBase, and need to have a separate class with `Data`
 * suffix extending WorkspaceEntityData to store the actual data.
 *
 * # Equality and identity
 *
 * Entities follow the java equality approach where by default objects are compared by identity. So, two different entities are never equal.
 * However, even requesting the same entity multiple times may return different java objects, they are still considered as equal.
 *
 * Entities from independent snapshots are never equal.
 *
 * Requesting the same entity from two different snapshots will return two different java objects.
 * However, they are equal if one snapshot is a modification of another, and this particular entity was not modified.
 *
 * This is the default behaviour of `equals` method that may be changed for any particular inheritor.
 *
 * ### Examples:
 * ```kotlin
 * val entityOne = builderOne.addEntity("data")
 * val entityTwo = builderTwo.addEntity("data")
 * entityOne != entityTwo
 * ```
 *
 * ```kotlin
 * val entityOne = snapshot.getEntity()
 * val entityTwo = snapshot.getEntity()
 * entityOne !== entityTwo
 * entityOne == entityTwo
 * ```
 *
 * ```kotlin
 * val entityA = snapshot.getEntityA()
 * val entityB = snapshot.getEntityB()
 * entityA != entityB
 * ```
 *
 * ```kotlin
 * val entityAOne = snapshot1.getEntityA()
 * val snapshot2 = snapshot1.toBuilder().modifyEntityB().toSnapshot()
 * val entityATwo = snapshot2.getEntityA()
 * entityAOne == entityATwo
 * ```
 *
 * ```kotlin
 * val entityAOne = snapshot1.getEntityA()
 * val snapshot2 = snapshot1.toBuilder().modifyEntityA().toSnapshot()
 * val entityATwo = snapshot2.getEntityA()
 * entityAOne != entityATwo
 * ```
 */
interface WorkspaceEntity {
  val entitySource: EntitySource

  /**
   * Returns `true` if this entity and [e] have the same type and the same properties. Properties of type [WorkspaceEntity] are compared by
   * internal IDs of the corresponding entities.
   */
  fun hasEqualProperties(e: WorkspaceEntity): Boolean

  fun <E : WorkspaceEntity> createReference(): EntityReference<E>
}

/**
 * Base interface for modifiable variant of [Unmodifiable] entity. The implementation can be used to [create a new entity][WorkspaceEntityStorageBuilder.addEntity]
 * or [modify an existing value][WorkspaceEntityStorageBuilder.modifyEntity].
 *
 * Currently the class must inherit from ModifiableWorkspaceEntityBase. 
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
interface EntitySource {
  val virtualFileUrl: VirtualFileUrl?
    get() = null
}

/**
 * Marker interface to represent entities which properties aren't loaded and which were added to the storage because other entities requires
 * them. Entities which sources implements this interface don't replace existing entities when [WorkspaceEntityStorageBuilder.replaceBySource]
 * is called.
 *
 * For example if we have `FacetEntity` which requires `ModuleEntity`, and need to load facet configuration from *.iml file and load the module
 * configuration from some other source, we may use this interface to mark `entitySource` for `ModuleEntity`. This way when content of *.iml
 * file is applied to the model via [WorkspaceEntityStorageBuilder.replaceBySource], it won't overwrite actual configuration
 * of `ModuleEntity`.
 */
interface DummyParentEntitySource : EntitySource

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
 * Represents a reference to an entity inside of [WorkspaceEntity].
 *
 * The reference can be obtained via [WorkspaceEntityStorage.createReference].
 *
 * The reference will return the same entity for the same storage, but the changes in storages should be tracked if the client want to
 *   use this reference between different storages. For example, if the referred entity was removed from the storage, this reference may
 *   return null, but it can also return a different (newly added) entity.
 */
abstract class EntityReference<out E : WorkspaceEntity> {
  abstract fun resolve(storage: WorkspaceEntityStorage): E?
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
  /** Text which can be shown in an error message if id cannot be resolved */
  abstract val presentableName: String

  fun resolve(storage: WorkspaceEntityStorage): E? = storage.resolve(this)
  abstract override fun toString(): String
}

interface WorkspaceEntityWithPersistentId : WorkspaceEntity {
  // TODO Make it a property
  fun persistentId(): PersistentEntityId<*>
}

/**
 * Read-only interface to a storage. Use [WorkspaceEntityStorageBuilder] or [WorkspaceEntityStorageDiffBuilder] to modify it.
 */
interface WorkspaceEntityStorage {
  fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E>
  fun <E : WorkspaceEntity> entitiesAmount(entityClass: Class<E>): Int
  fun <E : WorkspaceEntity, R : WorkspaceEntity> referrers(e: E, entityClass: KClass<R>, property: KProperty1<R, EntityReference<E>>): Sequence<R>
  fun <E : WorkspaceEntityWithPersistentId, R : WorkspaceEntity> referrers(id: PersistentEntityId<E>, entityClass: Class<R>): Sequence<R>
  fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E?

  /**
   * Please select a name for your mapping in a form `<product_id>.<mapping_name>`.
   * E.g.:
   *  - intellij.modules.bridge
   *  - intellij.facets.bridge
   *  - rider.backend.id
   */
  fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T>
  fun getVirtualFileUrlIndex(): VirtualFileUrlIndex
  fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>>
  fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E>
}

/**
 * Writeable interface to a storage. Use it if you need to build a storage from scratch or modify an existing storage in a way which requires
 * reading its state after modifications. For simple modifications use [WorkspaceEntityStorageDiffBuilder] instead.
 */
interface WorkspaceEntityStorageBuilder : WorkspaceEntityStorage, WorkspaceEntityStorageDiffBuilder {
  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> addEntity(clazz: Class<M>,
                                                                                 source: EntitySource,
                                                                                 initializer: M.() -> Unit): T

  override fun <M : ModifiableWorkspaceEntity<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T
  override fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T
  override fun removeEntity(e: WorkspaceEntity)
  fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: WorkspaceEntityStorage)

  /**
   * Return changes in entities recorded in this instance. [original] parameter is used to get the old instances of modified
   * and removed entities.
   */
  fun collectChanges(original: WorkspaceEntityStorage): Map<Class<*>, List<EntityChange<*>>>

  fun toStorage(): WorkspaceEntityStorage

  companion object {
    @JvmStatic
    fun create(): WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilderImpl.create()

    @JvmStatic
    fun from(storage: WorkspaceEntityStorage): WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilderImpl.from(storage)
  }
}

fun WorkspaceEntityStorage.toBuilder(): WorkspaceEntityStorageBuilder {
  return WorkspaceEntityStorageBuilder.from(this)
}

sealed class EntityChange<T : WorkspaceEntity> {
  data class Added<T : WorkspaceEntity>(val entity: T) : EntityChange<T>()
  data class Removed<T : WorkspaceEntity>(val entity: T) : EntityChange<T>()
  data class Replaced<T : WorkspaceEntity>(val oldEntity: T, val newEntity: T) : EntityChange<T>()
}

/**
 * Write-only interface to a storage. 
 */
interface WorkspaceEntityStorageDiffBuilder {
  fun isEmpty(): Boolean

  fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> addEntity(clazz: Class<M>, source: EntitySource, initializer: M.() -> Unit): T
  fun <M : ModifiableWorkspaceEntity<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T
  fun removeEntity(e: WorkspaceEntity)
  fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T

  fun addDiff(diff: WorkspaceEntityStorageDiffBuilder)

  fun <T : WorkspaceEntity> addEntity(entity: T, source: EntitySource): T

  /**
   * Please see [WorkspaceEntityStorage.getExternalMapping] for naming conventions
   */
  fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T>

  /**
   * Please see [WorkspaceEntityStorage.getExternalMapping] for naming conventions
   */
  fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T>
  fun getVirtualFileUrlIndex(): VirtualFileUrlIndex
  fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex


  val modificationCount: Long

  companion object {
    @JvmStatic
    fun create(underlyingStorage: WorkspaceEntityStorage): WorkspaceEntityStorageDiffBuilder = WorkspaceEntityStorageBuilder.from(underlyingStorage)
  }
}
