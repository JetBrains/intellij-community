// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity

// ------------------------- Updating references ------------------------

internal fun <SUBT : PTypedEntity> PEntityStorageBuilder.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                                                         parentId: PId,
                                                                                         children: Sequence<SUBT>) {
  refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, children)
}


internal fun <SUBT : PTypedEntity> PEntityStorageBuilder.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                                                 parentId: PId,
                                                                                                 children: Sequence<SUBT>) {
  refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, children)
}

internal fun <T : PTypedEntity> PEntityStorageBuilder.updateOneToAbstractOneParentOfChild(connectionId: ConnectionId,
                                                                                          childId: PId,
                                                                                          parent: T) {
  refs.updateOneToAbstractOneParentOfChild(connectionId, childId, parent)
}

internal fun <SUBT : PTypedEntity> PEntityStorageBuilder.updateOneToOneChildOfParent(connectionId: ConnectionId,
                                                                                     parentId: PId,
                                                                                     child: SUBT?) {
  if (child != null) {
    refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, child)
  }
  else {
    refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
  }
}

internal fun <T : PTypedEntity> PEntityStorageBuilder.updateOneToManyParentOfChild(connectionId: ConnectionId,
                                                                                   childId: PId,
                                                                                   parent: T?) {
  if (parent != null) {
    refs.updateOneToManyParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToManyRefsByChild(connectionId, childId.arrayId)
  }
}

internal fun <T : PTypedEntity> PEntityStorageBuilder.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                                                  childId: PId,
                                                                                  parent: T?) {
  if (parent != null) {
    refs.updateOneToOneParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToOneRefByChild(connectionId, childId.arrayId)
  }
}

// ------------------------- Extracting references references ------------------------

internal fun <SUBT : TypedEntity> AbstractPEntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                                  parentId: PId): Sequence<SUBT> {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return emptySequence()
  return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map { entitiesList[it]!!.createEntity(this) } as? Sequence<SUBT>
         ?: emptySequence()
}

internal fun <SUBT : TypedEntity> AbstractPEntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
                                                                                          parentId: PId): Sequence<SUBT> {
  return refs.getOneToAbstractManyChildren(connectionId, parentId)?.asSequence()?.map { pid ->
    entityDataByIdOrDie(pid).createEntity(this)
  } as? Sequence<SUBT> ?: emptySequence()
}

internal fun <SUBT : TypedEntity> AbstractPEntityStorage.extractAbstractOneToOneChildren(connectionId: ConnectionId,
                                                                                         parentId: PId): Sequence<SUBT> {
  return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { pid ->
    sequenceOf(entityDataByIdOrDie(pid).createEntity(this))
  } as? Sequence<SUBT> ?: emptySequence()
}

internal fun <T : TypedEntity> AbstractPEntityStorage.extractOneToAbstractOneParent(connectionId: ConnectionId,
                                                                                    childId: PId): T? {
  return refs.getOneToAbstractOneParent(connectionId, childId)?.let { entityDataByIdOrDie(it).createEntity(this) as T }
}

internal fun <SUBT : TypedEntity> AbstractPEntityStorage.extractOneToOneChild(connectionId: ConnectionId,
                                                                              parentId: PId): SUBT? {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return null
  return refs.getOneToOneChild(connectionId, parentId.arrayId) { entitiesList[it]!!.createEntity(this) as? SUBT }
}

internal fun <T : TypedEntity> AbstractPEntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                            childId: PId): T? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToOneParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) as? T }
}

internal fun <T : TypedEntity> AbstractPEntityStorage.extractOneToManyParent(connectionId: ConnectionId, childId: PId): T? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToManyParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) as? T }
}

