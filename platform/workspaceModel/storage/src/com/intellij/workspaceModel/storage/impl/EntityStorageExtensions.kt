// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntityStorage

// ------------------------- Updating references ------------------------

@Suppress("unused")
fun EntityStorage.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                  parent: WorkspaceEntity,
                                                  children: List<WorkspaceEntity>) {
    this as MutableEntityStorageImpl
    val parentId = (parent as WorkspaceEntityBase).id
    val childrenIds = children.map { (it as WorkspaceEntityBase).id.asChild() }
    if (!connectionId.isParentNullable) {
        val existingChildren = extractOneToManyChildrenIds(connectionId, parentId).toHashSet()
        childrenIds.forEach {
            existingChildren.remove(it.id)
        }
        existingChildren.forEach { removeEntityByEntityId(it) }
    }
    refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, childrenIds)
}


@Suppress("unused")
fun EntityStorage.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                          parentEntity: WorkspaceEntity,
                                                          childrenEntity: Sequence<WorkspaceEntity>) {
    this as MutableEntityStorageImpl
    val parentId = (parentEntity as WorkspaceEntityBase).id.asParent()
    val childrenIds = childrenEntity.map { (it as WorkspaceEntityBase).id.asChild() }
    refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, childrenIds)
}

@Suppress("unused")
fun EntityStorage.updateOneToAbstractOneChildOfParent(connectionId: ConnectionId,
                                                      parentEntity: WorkspaceEntity,
                                                      childEntity: WorkspaceEntity?) {
    this as MutableEntityStorageImpl
    val parentId = (parentEntity as WorkspaceEntityBase).id.asParent()
    val childId = (childEntity as? WorkspaceEntityBase)?.id?.asChild()
    if (childId != null) {
        refs.updateOneToAbstractOneChildOfParent(connectionId, parentId, childId)
    } else {
        refs.removeOneToAbstractOneRefByParent(connectionId, parentId)
    }
}

@Suppress("unused")
fun EntityStorage.updateOneToOneChildOfParent(connectionId: ConnectionId, parentEntity: WorkspaceEntity,
                                              childEntity: WorkspaceEntity?) {
    this as MutableEntityStorageImpl
    val parentId = (parentEntity as WorkspaceEntityBase).id
    val childId = (childEntity as? WorkspaceEntityBase)?.id?.asChild()
    val existingChildId = extractOneToOneChildIds(connectionId, parentId)
    if (!connectionId.isParentNullable && existingChildId != null && (childId == null || childId.id != existingChildId)) {
        removeEntityByEntityId(existingChildId)
    }
    if (childId != null) {
        refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, childId)
    }
    else {
        refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
    }
}

@Suppress("unused")
fun <Parent : WorkspaceEntity> EntityStorage.updateOneToManyParentOfChild(connectionId: ConnectionId,
                                                                          childEntity: WorkspaceEntity,
                                                                          parentEntity: Parent?) {
    this as MutableEntityStorageImpl
    val childId = (childEntity as WorkspaceEntityBase).id.asChild()
    val parentId = (parentEntity as? WorkspaceEntityBase)?.id?.asParent()
    if (parentId != null) {
        refs.updateOneToManyParentOfChild(connectionId, childId.id.arrayId, parentId)
    }
    else {
        refs.removeOneToManyRefsByChild(connectionId, childId.id.arrayId)
    }
}

fun <Parent : WorkspaceEntity> EntityStorage.updateOneToAbstractManyParentOfChild(connectionId: ConnectionId,
                                                                                  child: WorkspaceEntity,
                                                                                  parent: Parent?) {
    this as MutableEntityStorageImpl
    val childId = (child as WorkspaceEntityBase).id.asChild()
    val parentId = (parent as? WorkspaceEntityBase)?.id?.asParent()
    if (parentId != null) {
        refs.updateOneToAbstractManyParentOfChild(connectionId, childId, parentId)
    } else {
        refs.removeOneToAbstractManyRefsByChild(connectionId, childId)
    }
}

@Suppress("unused")
fun <Parent : WorkspaceEntity> EntityStorage.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                                         childEntity: WorkspaceEntity,
                                                                         parentEntity: Parent?) {
    this as MutableEntityStorageImpl
    val parentId = (parentEntity as? WorkspaceEntityBase)?.id?.asParent()
    val childId = (childEntity as WorkspaceEntityBase).id
    if (!connectionId.isParentNullable && parentId != null) {
        // A very important thing. If we replace a field in one-to-one connection, the previous entity is automatically removed.
        val existingChild = extractOneToOneChild<WorkspaceEntityBase>(connectionId, parentEntity)
        if (existingChild != null && existingChild != childEntity) {
            removeEntity(existingChild)
        }
    }
    if (parentId != null) {
        refs.updateOneToOneParentOfChild(connectionId, childId.arrayId, parentId.id)
    }
    else {
        refs.removeOneToOneRefByChild(connectionId, childId.arrayId)
    }
}

fun <Parent : WorkspaceEntity> EntityStorage.updateOneToAbstractOneParentOfChild(connectionId: ConnectionId,
                                                                                 childEntity: WorkspaceEntity, parentEntity: Parent?
) {
    this as MutableEntityStorageImpl
    val parentId = (parentEntity as? WorkspaceEntityBase)?.id?.asParent()
    val childId = (childEntity as WorkspaceEntityBase).id.asChild()
    if (!connectionId.isParentNullable && parentId != null) {
        // A very important thing. If we replace a field in one-to-one connection, the previous entity is automatically removed.
        val existingChild = extractOneToAbstractOneChild<WorkspaceEntityBase>(connectionId, parentEntity)
        if (existingChild != null && existingChild != childEntity) {
            removeEntity(existingChild)
        }
    }
    if (parentId != null) {
        refs.updateOneToAbstractOneParentOfChild(connectionId, childId, parentId)
    }
    else {
        refs.removeOneToAbstractOneRefByChild(connectionId, childId)
    }
}

// ------------------------- Extracting references references ------------------------

@Suppress("unused")
fun <Child : WorkspaceEntity> EntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
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
        error(
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

internal fun AbstractEntityStorage.extractOneToOneChildIds(connectionId: ConnectionId, parentId: EntityId): EntityId? {
    return refs.getOneToOneChild(connectionId, parentId.arrayId)?.let { createEntityId(it, connectionId.childClass) } ?: return null
}

@Suppress("unused")
fun <Child : WorkspaceEntity> EntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
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

fun <Parent : WorkspaceEntity> EntityStorage.extractOneToAbstractManyParent(
    connectionId: ConnectionId,
    child: WorkspaceEntity
): Parent? {
    return (this as AbstractEntityStorage).extractOneToAbstractManyParent(
        connectionId,
        (child as WorkspaceEntityBase).id.asChild()
    )
}

@Suppress("UNCHECKED_CAST")
internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractManyParent(
    connectionId: ConnectionId,
    child: ChildEntityId
): Parent? {
  return refs.getOneToAbstractManyParent(connectionId, child)?.let { entityDataByIdOrDie(it.id).createEntity(this) as Parent }
}

@Suppress("unused")
fun <Child : WorkspaceEntity> EntityStorage.extractOneToAbstractOneChild(connectionId: ConnectionId,
                                                                         parent: WorkspaceEntity): Child? {
  return (this as AbstractEntityStorage).extractOneToAbstractOneChild(connectionId, (parent as WorkspaceEntityBase).id.asParent())
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractOneChild(connectionId: ConnectionId,
                                                                                          parentId: ParentEntityId): Child? {
  return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { entityDataByIdOrDie(it.id).createEntity(this) as Child }
}

@Suppress("unused")
fun <Child : WorkspaceEntity> EntityStorage.extractAbstractOneToOneChild(connectionId: ConnectionId,
                                                                         parent: WorkspaceEntity): Child? {
  return (this as AbstractEntityStorage).extractAbstractOneToOneChild(connectionId, (parent as WorkspaceEntityBase).id.asParent())
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractAbstractOneToOneChild(connectionId: ConnectionId,
                                                                                          parentId: ParentEntityId): Child? {
  return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { entityDataByIdOrDie(it.id).createEntity(this) as Child }
}

@Suppress("unused")
fun <Child : WorkspaceEntity> EntityStorage.extractOneToOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): Child? {
  return (this as AbstractEntityStorage).extractOneToOneChild(connectionId, (parent as WorkspaceEntityBase).id)
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToOneChild(connectionId: ConnectionId, parentId: EntityId): Child? {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return null
  return refs.getOneToOneChild(connectionId, parentId.arrayId) {
    val childEntityData = entitiesList[it]
    if (childEntityData == null) {
      if (!brokenConsistency) {
        error("""
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
fun <Parent : WorkspaceEntity> EntityStorage.extractOneToOneParent(connectionId: ConnectionId,
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
        error("""
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

fun <Parent : WorkspaceEntity> EntityStorage.extractOneToAbstractOneParent(
    connectionId: ConnectionId,
    child: WorkspaceEntity,
): Parent? {
    return (this as AbstractEntityStorage).extractOneToAbstractOneParent(
        connectionId,
        (child as WorkspaceEntityBase).id.asChild()
    )
}

@Suppress("UNCHECKED_CAST")
internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractOneParent(
    connectionId: ConnectionId,
    childId: ChildEntityId
): Parent? {
    return refs.getOneToAbstractOneParent(connectionId, childId)
        ?.let { entityDataByIdOrDie(it.id).createEntity(this) as Parent }
}

@Suppress("unused")
fun <Parent : WorkspaceEntity> EntityStorage.extractOneToManyParent(connectionId: ConnectionId,
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
        error("""
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

