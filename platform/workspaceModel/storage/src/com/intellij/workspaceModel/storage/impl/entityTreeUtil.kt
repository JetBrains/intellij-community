// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.util.containers.FList
import com.intellij.workspaceModel.storage.WorkspaceEntity

/**
 * Creates a detached copy of [entity] and its children recursively. This is used to support copying entities from one storage to another
 * via [com.intellij.workspaceModel.storage.MutableEntityStorage.addEntity] function.
 */
internal fun <T : WorkspaceEntity> createEntityTreeCopy(entity: T): ModifiableWorkspaceEntityBase<T, *> {
  //copying entity from another snapshot
  val originalSnapshot = (entity as WorkspaceEntityBase).snapshot as AbstractEntityStorage
  val entityData = originalSnapshot.entityDataByIdOrDie(entity.id)
  require(entityData.getRequiredParents().isEmpty()) { "copying is supported only for top-level entities which don't have required parents" }
  
  val newEntity = entityData.createDetachedEntity(emptyList())
  val copied = HashSet<EntityId>()
  val deferred = HashSet<EntityId>()
  val parents = FList.emptyList<WorkspaceEntity>().prepend(newEntity)
  val parentInterfaces = FList.emptyList<Class<out WorkspaceEntity>>().prepend(entityData.getEntityInterface())
  copyChildren(entity.id, parents, parentInterfaces, originalSnapshot, copied, deferred)
  deferred.removeAll(copied)
  require(deferred.isEmpty()) { "some elements weren't copied because their additional parents are located in a separate tree: $deferred" }
  @Suppress("UNCHECKED_CAST")
  return newEntity as ModifiableWorkspaceEntityBase<T, *>
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