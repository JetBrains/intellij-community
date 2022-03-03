// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage

// ------------------------- Updating references ------------------------

@Suppress("unused")
fun WorkspaceEntityStorage.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                           parent: WorkspaceEntity,
                                                           children: Sequence<WorkspaceEntity>) {
  (this as WorkspaceEntityStorageBuilderImpl).updateOneToManyChildrenOfParent(connectionId, (parent as WorkspaceEntityBase).id,
                                                                              children.map { (it as WorkspaceEntityBase).id.asChild() })

}

internal fun WorkspaceEntityStorageBuilderImpl.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                                               parentId: EntityId,
                                                                               childrenIds: Sequence<ChildEntityId>) {
  if (!connectionId.isParentNullable) {
    val existingChildren = extractOneToManyChildrenIds(connectionId, parentId).toHashSet()
    childrenIds.forEach {
      existingChildren.remove(it.id)
    }
    existingChildren.forEach { removeEntity(it) }
  }
  refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, childrenIds)
}


@Suppress("unused")
fun WorkspaceEntityStorage.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                   parent: WorkspaceEntity,
                                                                   children: Sequence<WorkspaceEntity>) {
  (this as WorkspaceEntityStorageBuilderImpl).updateOneToAbstractManyChildrenOfParent(connectionId,
                                                                                      (parent as WorkspaceEntityBase).id.asParent(),
                                                                                      children.map { (it as WorkspaceEntityBase).id.asChild() })
}

internal fun WorkspaceEntityStorageBuilderImpl.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                                       parentId: ParentEntityId,
                                                                                       childrenIds: Sequence<ChildEntityId>) {
  refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, childrenIds)
}

@Suppress("unused")
fun WorkspaceEntityStorage.updateOneToAbstractOneChildOfParent(connectionId: ConnectionId,
                                                               parent: WorkspaceEntity,
                                                               child: WorkspaceEntity?) {
  (this as WorkspaceEntityStorageBuilderImpl).updateOneToAbstractOneChildOfParent(connectionId,
                                                                                  (parent as WorkspaceEntityBase).id.asParent(),
                                                                                  (child as? WorkspaceEntityBase)?.id?.asChild())
}

internal fun WorkspaceEntityStorageBuilderImpl.updateOneToAbstractOneChildOfParent(connectionId: ConnectionId,
                                                                                   parentId: ParentEntityId,
                                                                                   childId: ChildEntityId?) {
  if (childId != null) {
    refs.updateOneToAbstractOneChildOfParent(connectionId, parentId, childId)
  }
  else {
    refs.removeOneToAbstractOneRefByParent(connectionId, parentId)
  }
}

@Suppress("unused")
fun WorkspaceEntityStorage.updateOneToOneChildOfParent(connectionId: ConnectionId,
                                                       parent: WorkspaceEntity,
                                                       childEntity: WorkspaceEntity?) {
  (this as WorkspaceEntityStorageBuilderImpl).updateOneToOneChildOfParent(connectionId, (parent as WorkspaceEntityBase).id,
                                                                          (childEntity as? WorkspaceEntityBase)?.id?.asChild())
}

internal fun WorkspaceEntityStorageBuilderImpl.updateOneToOneChildOfParent(connectionId: ConnectionId,
                                                                           parentId: EntityId,
                                                                           childEntityId: ChildEntityId?) {
  if (childEntityId != null) {
    refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, childEntityId)
  }
  else {
    refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
  }
}

@Suppress("unused")
fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorage.updateOneToManyParentOfChild(connectionId: ConnectionId,
                                                                                                child: WorkspaceEntity,
                                                                                                parent: Parent?) {
  (this as WorkspaceEntityStorageBuilderImpl).updateOneToManyParentOfChild(connectionId, (child as WorkspaceEntityBase).id, parent)
}

internal fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToManyParentOfChild(connectionId: ConnectionId,
                                                                                                           childId: EntityId,
                                                                                                           parent: Parent?) {
  if (parent != null) {
    refs.updateOneToManyParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToManyRefsByChild(connectionId, childId.arrayId)
  }
}

@Suppress("unused")
fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorage.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                                                      child: WorkspaceEntity,
                                                                                      parent: Parent?) {
  (this as WorkspaceEntityStorageBuilderImpl).updateOneToOneParentOfChild(connectionId, (child as WorkspaceEntityBase).id, parent)
}

internal fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                                                                          childId: EntityId,
                                                                                                          parent: Parent?) {
  if (!connectionId.isParentNullable && parent != null) {
    // A very important thing. If we replace a field in one-to-one connection, the previous entity is automatically removed.
    val existingChild = extractOneToOneChild<WorkspaceEntityBase>(connectionId, parent.id)
    if (existingChild != null) {
      removeEntity(existingChild)
    }
  }
  if (parent != null) {
    refs.updateOneToOneParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToOneRefByChild(connectionId, childId.arrayId)
  }
}

// ------------------------- Extracting references references ------------------------

@Suppress("unused")
fun <Child : WorkspaceEntity> WorkspaceEntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                              parent: WorkspaceEntity): Sequence<Child> {
  return (this as AbstractEntityStorage).extractOneToManyChildren(connectionId, (parent as WorkspaceEntityBase).id)
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                                      parentId: EntityId): Sequence<Child> {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return emptySequence()
  return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map {
    val entityData = entitiesList[it]
    if (entityData == null) {
      if (!brokenConsistency) {
        thisLogger().error(
          """Cannot resolve entity.
          |Connection id: $connectionId
          |Unresolved array id: $it
          |All child array ids: ${refs.getOneToManyChildren(connectionId, parentId.arrayId)?.toArray()}
        """.trimMargin()
        )
      }
      null
    }
    else entityData.createEntity(this)
  }?.filterNotNull() as? Sequence<Child> ?: emptySequence()
}

internal fun AbstractEntityStorage.extractOneToManyChildrenIds(connectionId: ConnectionId, parentId: EntityId): Sequence<EntityId> {
  return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map { createEntityId(it, connectionId.childClass) } ?: emptySequence()
}

@Suppress("unused")
fun <Child : WorkspaceEntity> WorkspaceEntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
                                                                                      parent: WorkspaceEntity): Sequence<Child> {
  return (this as AbstractEntityStorage).extractOneToAbstractManyChildren(connectionId, (parent as WorkspaceEntityBase).id.asParent())
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
                                                                                              parentId: ParentEntityId): Sequence<Child> {
  return refs.getOneToAbstractManyChildren(connectionId, parentId)?.asSequence()?.map { pid ->
    entityDataByIdOrDie(pid.id).createEntity(this)
  } as? Sequence<Child> ?: emptySequence()
}

@Suppress("unused")
fun <Child : WorkspaceEntity> WorkspaceEntityStorage.extractAbstractOneToOneChild(connectionId: ConnectionId,
                                                                                  parent: WorkspaceEntity): Child? {
  return (this as AbstractEntityStorage).extractAbstractOneToOneChild(connectionId, (parent as WorkspaceEntityBase).id.asParent())
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractAbstractOneToOneChild(connectionId: ConnectionId,
                                                                                          parentId: ParentEntityId): Child? {
  return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { entityDataByIdOrDie(it.id).createEntity(this) as Child }
}

@Suppress("unused")
fun <Child : WorkspaceEntity> WorkspaceEntityStorage.extractOneToOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): Child? {
  return (this as AbstractEntityStorage).extractOneToOneChild(connectionId, (parent as WorkspaceEntityBase).id)
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToOneChild(connectionId: ConnectionId, parentId: EntityId): Child? {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return null
  return refs.getOneToOneChild(connectionId, parentId.arrayId) {
    val childEntityData = entitiesList[it]
    if (childEntityData == null) {
      if (!brokenConsistency) {
        logger<AbstractEntityStorage>().error("""
          Consistency issue. Cannot get a child in one to one connection.
          Connection id: $connectionId
          Parent id: $parentId
          Child array id: $it
        """.trimIndent())
      }
      null
    }
    else childEntityData.createEntity(this) as Child
  }
}

@Suppress("unused")
fun <Parent : WorkspaceEntity> WorkspaceEntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                            child: WorkspaceEntity): Parent? {
  return (this as AbstractEntityStorage).extractOneToOneParent(connectionId, (child as WorkspaceEntityBase).id)
}

@Suppress("UNCHECKED_CAST")
internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                                    childId: EntityId): Parent? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToOneParent(connectionId, childId.arrayId) {
    val parentEntityData = entitiesList[it]
    if (parentEntityData == null) {
      if (!brokenConsistency) {
        logger<AbstractEntityStorage>().error("""
          Consistency issue. Cannot get a parent in one to one connection.
          Connection id: $connectionId
          Child id: $childId
          Parent array id: $it
        """.trimIndent())
      }
      null
    }
    else parentEntityData.createEntity(this) as Parent
  }
}

@Suppress("unused")
fun <Parent : WorkspaceEntity> WorkspaceEntityStorage.extractOneToManyParent(connectionId: ConnectionId,
                                                                             child: WorkspaceEntity): Parent? {
  return (this as AbstractEntityStorage).extractOneToManyParent(connectionId, (child as WorkspaceEntityBase).id)
}

@Suppress("UNCHECKED_CAST")
internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToManyParent(connectionId: ConnectionId,
                                                                                     childId: EntityId): Parent? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToManyParent(connectionId, childId.arrayId) {
    val parentEntityData = entitiesList[it]
    if (parentEntityData == null) {
      if (!brokenConsistency) {
        logger<AbstractEntityStorage>().error("""
          Consistency issue. Cannot get a parent in one to many connection.
          Connection id: $connectionId
          Child id: $childId
          Parent array id: $it
        """.trimIndent())
      }
      null
    }
    else parentEntityData.createEntity(this) as Parent
  }
}

