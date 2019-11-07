package com.intellij.workspace.api

import com.intellij.openapi.util.ModificationTracker
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * A prototype of a storage system for the project model which stores data in typed entities. Entities are represented by interfaces
 * implementing TypedEntity interface.
 */

/**
 * A base interface for entities. A entity must be an interface which extend [TypedEntity] and have 'val' properties of the following types:
 * * primitive types;
 * * String;
 * * enum;
 * * [VirtualFileUrl];
 * * [TypedEntity] or [PersistentEntityId];
 * * [List] of another allowed type;
 * * another data class with properties of the allowed types (references to entities must be wrapped into [EntityReference]);
 * * sealed abstract class where all implementations satisfy these requirements.
 */
interface TypedEntity {
  val entitySource: EntitySource

  /**
   * Returns `true` if this entity and [e] have the same type and the same properties. Properties of type [TypedEntity] are compared by
   * internal IDs of the corresponding entities.
   */
  fun hasEqualProperties(e: TypedEntity): Boolean
}

/**
 * Base interface for modifiable variant of [Unmodifiable] entity. Its implementation must be an interface which overrides each 'val' property in [Unmodifiable] interface
 * with 'var' modifier. Properties of type [List] may be overridden with [MutableList]. The implementation can be used to [create a new entity][TypedEntityStorageBuilder.addEntity]
 * or [modify an existing value][TypedEntityStorageBuilder.modifyEntity].
 *
 * It's possible create a single interface with 'var' properties and use it for both read and write operations but you should be cautious to
 * avoid calling setters on entity instances directly (such calls will fail at runtime).
 */
interface ModifiableTypedEntity<Unmodifiable : TypedEntity> : TypedEntity

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
}

/**
 * Base interface for entities which may need to find all entities referring to them.
 */
interface ReferableTypedEntity : TypedEntity {
  /**
   * Returns all entities of type [R] which [propertyName] property refers to this entity. Consider using type-safe variant referrers(KProperty1) instead.
   */
  fun <R : TypedEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R>
}

/**
 * Returns all entities of type [R] which [property] refers to this entity.
 */
inline fun <E : ReferableTypedEntity, reified R : TypedEntity> E.referrers(property: KProperty1<R, E>): Sequence<R> {
  return referrers(R::class.java, property.name)
}

/**
 * Represents a reference to a entity inside a data class stored in [TypedEntity]'s implementation. Such references are always valid (use [PersistentEntityId] for references which
 * may not be resolved), they are used to allow updating a entity without replacing all entities it refers to and also to get all referrers
 * via [TypedEntityStorage.referrers].
 *
 * todo Do we really need this? May be it's enough to support references to other entities only as direct properties of [TypedEntity]?
 */
abstract class EntityReference<out E : TypedEntity> {
  abstract fun resolve(storage: TypedEntityStorage): E
}

/**
 * A base class for typed hierarchical entity IDs. An implementation must be a data class which contains read-only properties of the following types:
 * * primitive types,
 * * String,
 * * enum,
 * * another data class with properties of the allowed types;
 * * sealed abstract class where all implementations satisfy these requirements.
 */
abstract class PersistentEntityId<out E : TypedEntityWithPersistentId>(entityClass: KClass<out E>) {
  abstract val parentId: PersistentEntityId<*>?
  /** Text which can be shown in an error message if id cannot be resolved */
  abstract val presentableName: String

  fun resolve(storage: TypedEntityStorage): E? = storage.resolve(this)
  abstract override fun toString(): String
}

interface TypedEntityWithPersistentId : TypedEntity {
  // TODO Make it a property
  fun persistentId(): PersistentEntityId<*>
}

interface TypedEntityStorage {
  fun <E : TypedEntity> entities(entityClass: KClass<E>): Sequence<E>
  fun <E : TypedEntity, R : TypedEntity> referrers(e: E, entityClass: KClass<R>, property: KProperty1<R, EntityReference<E>>): Sequence<R>
  fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E?
  fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out TypedEntity>, List<TypedEntity>>>
}

interface TypedEntityStorageBuilder : TypedEntityStorage, TypedEntityStorageDiffBuilder {
  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> addEntity(clazz: Class<M>,
                                                                         source: EntitySource,
                                                                         initializer: M.() -> Unit): T

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T
  fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T
  override fun removeEntity(e: TypedEntity)
  fun <E : TypedEntity> createReference(e: E): EntityReference<E>
  fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage)

  /**
   * Return changes in entities recorded in this instance. [original] parameter is used to get the old instances of modified
   * and removed entities.
   */
  fun collectChanges(original: TypedEntityStorage): Map<Class<*>, List<EntityChange<*>>>

  // Reset all collected changes. TODO ugly!
  fun resetChanges()

  fun toStorage(): TypedEntityStorage

  companion object {
    fun create(): TypedEntityStorageBuilder = TypedEntityStorageBuilderImpl(HashMap(), HashMap(), HashMap(), HashMap(), HashMap(), EntityMetaDataRegistry())
    fun from(storage: TypedEntityStorage): TypedEntityStorageBuilder {
      return TypedEntityStorageBuilderImpl(storage as ProxyBasedEntityStorage)
    }
  }
}

sealed class EntityChange<T : TypedEntity> {
  data class Added<T : TypedEntity>(val entity: T) : EntityChange<T>()
  data class Removed<T : TypedEntity>(val entity: T) : EntityChange<T>()
  data class Replaced<T : TypedEntity>(val oldEntity: T, val newEntity: T) : EntityChange<T>()
}

interface TypedEntityStorageDiffBuilder : ModificationTracker {
  fun isEmpty(): Boolean

  fun <M : ModifiableTypedEntity<T>, T : TypedEntity> addEntity(clazz: Class<M>, source: EntitySource, initializer: M.() -> Unit): T
  fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T
  fun removeEntity(e: TypedEntity)

  fun addDiff(diff: TypedEntityStorageDiffBuilder)

  companion object {
    fun create(underlyingStorage: TypedEntityStorage): TypedEntityStorageDiffBuilder = TypedEntityStorageBuilder.from(underlyingStorage)
  }
}
