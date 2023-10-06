// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.asParent
import com.intellij.util.containers.FList
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Creates a detached copy of [this@createEntityTreeCopy] and its children recursively. This is used to support copying entities from one storage to another
 * via [com.intellij.platform.workspace.storage.MutableEntityStorage.addEntity] function.
 */
@Experimental
public fun <T : WorkspaceEntity> T.createEntityTreeCopy(requireTopLevelEntity: Boolean = false): WorkspaceEntity.Builder<T> {
  //copying entity from another snapshot
  val originalSnapshot = (this as WorkspaceEntityBase).snapshot as AbstractEntityStorage
  val entityData = originalSnapshot.entityDataByIdOrDie(id)
  if (requireTopLevelEntity) {
    require(entityData.getRequiredParents().isEmpty()) { "copying is supported only for top-level entities which don't have required parents" }
  }

  val newEntity = entityData.createDetachedEntity(emptyList())
  val copied = HashSet<EntityId>()
  val deferred = HashSet<EntityId>()
  val parents = FList.emptyList<WorkspaceEntity>().prepend(newEntity)
  val parentInterfaces = FList.emptyList<Class<out WorkspaceEntity>>().prepend(entityData.getEntityInterface())
  copyChildren(id, parents, parentInterfaces, originalSnapshot, copied, deferred)
  deferred.removeAll(copied)
  require(deferred.isEmpty()) { "some elements weren't copied because their additional parents are located in a separate tree: $deferred" }
  @Suppress("UNCHECKED_CAST")
  return newEntity as WorkspaceEntity.Builder<T>
}

private fun copyChildren(oldEntityId: EntityId,
                         parents: FList<WorkspaceEntity>,
                         parentInterfaces: FList<Class<out WorkspaceEntity>>,
                         originalSnapshot: AbstractEntityStorage,
                         copied: MutableSet<EntityId>,
                         deferred: MutableSet<EntityId>) {
  for ((_, children) in originalSnapshot.refs.getChildrenRefsOfParentBy(oldEntityId.asParent())) {
    for (child in children) {
      val childData = originalSnapshot.entityDataByIdOrDie(child.id)
      if (parentInterfaces.containsAll(childData.getRequiredParents())) {
        val childCopy = childData.createDetachedEntity(parents)
        copied.add(child.id)
        copyChildren(child.id, parents.prepend(childCopy), parentInterfaces.prepend(childData.getEntityInterface()), originalSnapshot, copied, deferred)
      }
      else {
        /* it may happen that an entity has an explicit reference to its grandparent, so we need to process it later as a child of its 
           immediate parent */
        deferred.add(child.id)
      }
    }
  }
}