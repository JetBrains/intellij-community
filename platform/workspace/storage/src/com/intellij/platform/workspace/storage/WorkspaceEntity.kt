// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityExtensionDelegate

/**
 * A base interface for entities. An entity may have properties of the following types:
 * * primitive types;
 * * String;
 * * enum;
 * * [VirtualFileUrl];
 * * [WorkspaceEntity] or [SymbolicEntityId];
 * * [List] of another allowed type;
 * * [Map] of another allowed types where key is NOT a WorkspaceEntity;
 * * another data class with properties of the allowed types (references to entities must be wrapped into [EntityReference]);
 * * sealed abstract class where all implementations satisfy these requirements.
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

@Abstract
interface WorkspaceEntity {
  /**
   * Returns an instance describing where this entity comes from.
   */
  val entitySource: EntitySource

  /**
   * Creates a reference encapsulating an internal ID of this entity. 
   */
  fun <E : WorkspaceEntity> createReference(): EntityReference<E>

  /**
   * Returns the interface describing the specific type of this entity.
   */
  fun getEntityInterface(): Class<out WorkspaceEntity>

  companion object {
    /**
     * Used to implement extension properties for entities: every extension property must be implemented using 
     * `by WorkspaceEntity.extension()` clause.
     */
    inline fun <reified T> extension(): WorkspaceEntityExtensionDelegate<T> {
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
  interface Builder<Unmodifiable : WorkspaceEntity> : WorkspaceEntity {
    override var entitySource: EntitySource
  }
}