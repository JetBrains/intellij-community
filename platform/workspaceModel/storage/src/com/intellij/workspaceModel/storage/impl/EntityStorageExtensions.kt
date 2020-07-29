// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntity

// ------------------------- Updating references ------------------------

internal fun <SUBT : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                                                                            parentId: EntityId,
                                                                                                            children: Sequence<SUBT>) {
  refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, children)
}


internal fun <SUBT : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                                                                    parentId: EntityId,
                                                                                                                    children: Sequence<SUBT>) {
  refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, children)
}

internal fun <T : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToAbstractOneParentOfChild(connectionId: ConnectionId,
                                                                                                             childId: EntityId,
                                                                                                             parent: T) {
  refs.updateOneToAbstractOneParentOfChild(connectionId, childId, parent)
}

internal fun <SUBT : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToOneChildOfParent(connectionId: ConnectionId,
                                                                                                        parentId: EntityId,
                                                                                                        child: SUBT?) {
  if (child != null) {
    refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, child)
  }
  else {
    refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
  }
}

internal fun <T : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToManyParentOfChild(connectionId: ConnectionId,
                                                                                                      childId: EntityId,
                                                                                                      parent: T?) {
  if (parent != null) {
    refs.updateOneToManyParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToManyRefsByChild(connectionId, childId.arrayId)
  }
}

internal fun <T : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                                                                     childId: EntityId,
                                                                                                     parent: T?) {
  if (parent != null) {
    refs.updateOneToOneParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToOneRefByChild(connectionId, childId.arrayId)
  }
}

// ------------------------- Extracting references references ------------------------

internal fun <SUBT : WorkspaceEntity> AbstractEntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                                     parentId: EntityId): Sequence<SUBT> {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return emptySequence()
  return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map { entitiesList[it]!!.createEntity(this) } as? Sequence<SUBT>
         ?: emptySequence()
}

internal fun <SUBT : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
                                                                                             parentId: EntityId): Sequence<SUBT> {
  return refs.getOneToAbstractManyChildren(connectionId, parentId)?.asSequence()?.map { pid ->
    entityDataByIdOrDie(pid).createEntity(this)
  } as? Sequence<SUBT> ?: emptySequence()
}

internal fun <SUBT : WorkspaceEntity> AbstractEntityStorage.extractAbstractOneToOneChildren(connectionId: ConnectionId,
                                                                                            parentId: EntityId): Sequence<SUBT> {
  return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { pid ->
    sequenceOf(entityDataByIdOrDie(pid).createEntity(this))
  } as? Sequence<SUBT> ?: emptySequence()
}

internal fun <T : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractOneParent(connectionId: ConnectionId,
                                                                                       childId: EntityId): T? {
  return refs.getOneToAbstractOneParent(connectionId, childId)?.let { entityDataByIdOrDie(it).createEntity(this) as T }
}

internal fun <SUBT : WorkspaceEntity> AbstractEntityStorage.extractOneToOneChild(connectionId: ConnectionId,
                                                                                 parentId: EntityId): SUBT? {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return null
  return refs.getOneToOneChild(connectionId, parentId.arrayId) { entitiesList[it]!!.createEntity(this) as? SUBT }
}

internal fun <T : WorkspaceEntity> AbstractEntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                               childId: EntityId): T? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToOneParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) as? T }
}

internal fun <T : WorkspaceEntity> AbstractEntityStorage.extractOneToManyParent(connectionId: ConnectionId, childId: EntityId): T? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToManyParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) as? T }
}

