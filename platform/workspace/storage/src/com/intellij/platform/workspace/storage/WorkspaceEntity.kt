// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityExtensionDelegate

/**
 * A base interface for entities in [the storage](psi_element://com.intellij.platform.workspace.storage).
 * Each entity type has its own interface extending [WorkspaceEntity].
 * The platform currently provides some predefined types of entities (see [this package](psi_element://com.intellij.platform.workspace.jps.entities)),
 * but they are supposed to be used only for interoperability with code which uses the old project model API. 
 * The plugins should define and use their own types of entities if they need to store framework-specific data. 
 *
 * Instances of [WorkspaceEntity] obtained from an [ImmutableEntityStorage] are immutable, further modifications will not affect them. This
 * means that they can be used without any locks. However, references to [WorkspaceEntity] instances must not be saved in long-living data 
 * structures, because each instance holds a reference to the whole snapshot, and this will create a memory leak. 
 * If you need to refer to entities from some caches, use [EntityPointer] or [ExternalEntityMapping].
 * 
 * ## Equality and identity
 *
 * There are no guaranties about identity of [WorkspaceEntity] instances: even subsequent calls of [EntityStorage.entities] function for the
 * same storage may return different instances for the same entity.
 * 
 * [equals] function returns `true` for instances which refer to the same entity, and `false` otherwise. It doesn't compare properties of
 * entities, so two entities of the same type with identical properties won't be considered equal. 
 * However, instances of the same entity returned by different calls to [EntityStorage.entities] will be equal. Moreover, an instance of the
 * same entity from a new snapshot of the storage will be equal to the instance from the previous version if that particular entity 
 * wasn't modified.
 *
 * ### Examples:
 * ```kotlin
 * val entityOne = builder.addEntity(MyEntity("data"))
 * val entityTwo = builder.addEntity(MyEntity("data"))
 * assertNotEquals(entityOne, entityTwo) //different entities
 * ```
 *
 * ```kotlin
 * val entity = builder.addEntity(MyEntity("data"))
 * val entityFromBuilder = builder.entities(MyEntity::class.java).single()
 * assertEquals(entity, entityFromBuilder) //same entity
 * ```
 *
 * ```kotlin
 * val entityA1 = snapshot1.getEntityA()
 * val entityB1 = snapshot1.getEntityA()
 * val builder = snapshot1.toBuilder()
 * builder.modifyEntity(entityB1) { ... }
 * val snapshot2 = builder.toSnapshot()
 * val entityA2 = snapshot2.getEntityA()
 * val entityB2 = snapshot2.getEntityB()
 * assertEquals(entityA1, entityA2) //entity "A" wasn't modified
 * assertNotEquals(entityB1, entityB2) //entity "B" was modified
 * ```
 * ## Parent-child relationship between entities
 * 
 * Some types of entities may be connected by "parent-child" relationship. It's introduced by a property in the parent entity interface
 * which refers to the child entity (entities) with [@Child][com.intellij.platform.workspace.storage.annotations.Child] annotation,
 * and a property in the child entity interface which refers to the parent entity. 
 * The storage automatically maintains the consistency of this relationship during modifications: 
 * * if a parent entity is removed, all its child entities are also removed;
 * * if a child entity is removed, the corresponding property in its parent entity is updated so it no longer refers to the removed entity.
 *  
 * The property referring to child entity may have a type
 * * `@Child ChildEntity?` indicating that there are zero or one child entities of the given type, or
 * * `List<@Child ChildEntity>` indicating that there are zero, one, or more child entities of the given type.
 * 
 * If `ChildEntity` is [@Abstract][Abstract], [applyChangesFrom][MutableEntityStorage.applyChangesFrom] operation won't try to merge changes in children of
 * a parent entity, but always replace the whole list of children.
 * 
 * The property referring to the parent entity may have a type
 * * `ParentEntity` indicating that the parent is mandatory, or
 * * `ParentEntity?` indicating that the parent is optional.
 * 
 * If the parent is optional, it's possible to create a child entity without the parent entity and set reference to the parent entity to
 * `null` for an existing child entity. Also, if reference to a child entity is removed from the corresponding property in the parent entity,
 * it causes automatic removal of the child entity if it specifies that the parent is mandatory, and just sets parent reference to `null`
 * if the parent is optional.
 * 
 * Entities may be also linked by symbolic references, see [SymbolicEntityId] for details.
 * 
 * ## Defining new entity types
 * In order to define a new type of entity, you need to create a new interface extending [WorkspaceEntity], see [this article](https://youtrack.jetbrains.com/articles/IJPL-A-52)
 * for details.
 */
@Abstract
public interface WorkspaceEntity {
  /**
   * Returns an instance describing where this entity comes from.
   */
  public val entitySource: EntitySource

  /**
   * Creates a pointer encapsulating an internal ID of this entity.
   */
  public fun <E : WorkspaceEntity> createPointer(): EntityPointer<E>

  /**
   * Returns the interface describing the specific type of this entity.
   */
  public fun getEntityInterface(): Class<out WorkspaceEntity>

  public companion object {
    /**
     * Used to implement extension properties for entities: every extension property must be implemented using 
     * `by WorkspaceEntity.extension()` clause.
     */
    public inline fun <reified T> extension(): WorkspaceEntityExtensionDelegate<T> {
      return WorkspaceEntityExtensionDelegate()
    }
  }

  /**
   * Base interface for modifiable variant of [Unmodifiable] entity. The implementation can be used to [create a new entity][MutableEntityStorage.addEntity]
   * or [modify an existing value][MutableEntityStorage.modifyEntity].
   *
   * Currently, the class must inherit from ModifiableWorkspaceEntityBase.
   */
  @Abstract
  public interface Builder<Unmodifiable : WorkspaceEntity> : WorkspaceEntity {
    override var entitySource: EntitySource
  }
}