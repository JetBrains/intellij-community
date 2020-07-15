// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntity

// ------------------------- Updating references ------------------------

internal fun <Child : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                                                                            parentId: EntityId,
                                                                                                            children: Sequence<Child>) {
  refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, children)
}


internal fun <Child : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                                                                    parentId: EntityId,
                                                                                                                    children: Sequence<Child>) {
  refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, children)
}

internal fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToAbstractOneParentOfChild(connectionId: ConnectionId,
                                                                                                             childId: EntityId,
                                                                                                             parent: Parent) {
  refs.updateOneToAbstractOneParentOfChild(connectionId, childId, parent)
}

internal fun <Child : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToOneChildOfParent(connectionId: ConnectionId,
                                                                                                        parentId: EntityId,
                                                                                                        child: Child?) {
  if (child != null) {
    refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, child)
  }
  else {
    refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
  }
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

internal fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                                                                     childId: EntityId,
                                                                                                     parent: Parent?) {
  if (parent != null) {
    refs.updateOneToOneParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToOneRefByChild(connectionId, childId.arrayId)
  }
}

// ------------------------- Extracting references references ------------------------

internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                                     parentId: EntityId): Sequence<Child> {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return emptySequence()
  return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map { entitiesList[it]!!.createEntity(this) } as? Sequence<Child>
         ?: emptySequence()
}

internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
                                                                                             parentId: EntityId): Sequence<Child> {
  return refs.getOneToAbstractManyChildren(connectionId, parentId)?.asSequence()?.map { pid ->
    entityDataByIdOrDie(pid).createEntity(this)
  } as? Sequence<Child> ?: emptySequence()
}

internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractAbstractOneToOneChildren(connectionId: ConnectionId,
                                                                                            parentId: EntityId): Sequence<Child> {
  return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { pid ->
    sequenceOf(entityDataByIdOrDie(pid).createEntity(this))
  } as? Sequence<Child> ?: emptySequence()
}

internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractOneParent(connectionId: ConnectionId,
                                                                                       childId: EntityId): Parent? {
  return refs.getOneToAbstractOneParent(connectionId, childId)?.let { entityDataByIdOrDie(it).createEntity(this) as Parent }
}

internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToOneChild(connectionId: ConnectionId,
                                                                                 parentId: EntityId): Child? {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return null
  return refs.getOneToOneChild(connectionId, parentId.arrayId) { entitiesList[it]!!.createEntity(this) as? Child }
}

internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                               childId: EntityId): Parent? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToOneParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) as? Parent }
}

internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToManyParent(connectionId: ConnectionId, childId: EntityId): Parent? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToManyParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) as? Parent }
}

