// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.instrumentation

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.asString
import org.jetbrains.annotations.ApiStatus

/**
 * Instrumentation level of the storage.
 *
 * This level of function contains the functionality that should be publicly available, but not the part of the common storage API.
 *
 * For example, entity implementations may use some advanced functionality of the storage (e.g. get entities by reference).
 */
@EntityStorageInstrumentationApi
public interface EntityStorageInstrumentation : EntityStorage {
  /**
   * Create entity using [newInstance] function.
   * In some implementations of the storage ([EntityStorageSnapshot]), the entity is cached and the new instance is created only once.
   */
  public fun <T: WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T
  public fun <T : WorkspaceEntity> resolveReference(reference: EntityReference<T>): T?

  public fun getOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): WorkspaceEntity?
  public fun getManyChildren(connectionId: ConnectionId, parent: WorkspaceEntity): Sequence<WorkspaceEntity>

  public fun getParent(connectionId: ConnectionId, child: WorkspaceEntity): WorkspaceEntity?
}

@EntityStorageInstrumentationApi
public interface EntityStorageSnapshotInstrumentation : EntityStorageSnapshot, EntityStorageInstrumentation

@EntityStorageInstrumentationApi
public interface MutableEntityStorageInstrumentation : MutableEntityStorage, EntityStorageInstrumentation {
  /**
   * Returns a number which is incremented after each change in the storage.
   *
   * The number is not precise. A single operation may cause multiple increments.
   *
   * This internal API may be removed in the future, so it should not be used to build any functionality with it.
   */
  @get:ApiStatus.Internal
  public val modificationCount: Long

  /**
   * Replaces existing children of a given parent with a new list of children.
   *
   * The old children of the parent will be removed from the storage if they have a not-null reference to the parent
   *
   *   If the reference to the parent is nullable, they'll remain in the storage but with null as parent.
   *   ^^^ This behaviour is questionable. See IDEA-307409
   *
   * If any of child already has a parent, the link to this child will be removed from the old parent and added to the new one.
   *
   * This method adds records to the changelog of the builder.
   *
   * @param connectionId The ID of the connection.
   * @param parent The parent WorkspaceEntity whose children will be replaced.
   * @param newChildren The new list of WorkspaceEntities to replace the children with.
   */
  public fun replaceChildren(connectionId: ConnectionId, parent: WorkspaceEntity, newChildren: List<WorkspaceEntity>)

  /**
   * Adds a child to the list of children of parent.
   *
   * If the parent is null, we just remove the link to this parent from child entity.
   *   This works only if the parent reference in child is nullable.
   *
   * If the connection is one-to-one it works like replacing an existing child with a new one.
   *
   * If the child already has a parent, the link from this parent to this child will be removed.
   *
   * This method adds records to the changelog of the builder.
   *
   * @param connectionId The ConnectionId identifying the connection.
   * @param parent The parent WorkspaceEntity.
   * @param child The WorkspaceEntity to be added as a child.
   */
  public fun addChild(connectionId: ConnectionId, parent: WorkspaceEntity?, child: WorkspaceEntity)
}

/**
 * A record of reference modification on two entities.
 * The reference may be added or removed. Replacement of the reference with a new one is presented as a combination of
 *   remove and add modifications.
 */
internal sealed interface Modification {
  data class Add(val parent: EntityId, val child: EntityId) : Modification {
    override fun toString(): String {
      return "Add(parent=${parent.asString()}, child=${child.asString()})"
    }
  }

  data class Remove(val parent: EntityId, val child: EntityId) : Modification {
    override fun toString(): String {
      return "Remove(parent=${parent.asString()}, child=${child.asString()})"
    }
  }
}


/**
 * Annotation mark for internal level of the entity storage API
 *
 * Interfaces or methods that are marked with this annotation are intended to be used only in specific cases.
 * The regular usage of the entity storage should not access this API.
 *
 * Examples of code where the marked classes are used:
 * - Entities implementations use instrumentation API to communicate with the storage.
 * - [EntityReference] resolves the entity using the internal API.
 *
 * The API itself can't be marked as internal as it's used from external code that is generated by entity generator.
 */
@RequiresOptIn("This is an internal entity storage API and it's usage requires an explicit opt-in")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class EntityStorageInstrumentationApi

@EntityStorageInstrumentationApi
internal val EntityStorage.instrumentation: EntityStorageInstrumentation
  get() = this as EntityStorageInstrumentation

@EntityStorageInstrumentationApi
internal val EntityStorageSnapshot.instrumentation: EntityStorageSnapshotInstrumentation
  get() = this as EntityStorageSnapshotInstrumentation

@EntityStorageInstrumentationApi
internal val MutableEntityStorage.instrumentation: MutableEntityStorageInstrumentation
  get() {
    check(this is MutableEntityStorageInstrumentation) {
      "Every implementation of MutableEntityStorage must also implement the MutableEntityStorageInstrumentation"
    }
    return this
  }
