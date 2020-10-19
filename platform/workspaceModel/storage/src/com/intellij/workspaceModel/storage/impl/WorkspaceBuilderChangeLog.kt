// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.WorkspaceEntity

internal typealias ChangeLog = MutableMap<EntityId, Pair<ChangeEntry, ChangeEntry.ChangeEntitySource<*>?>>

class WorkspaceBuilderChangeLog {
  var modificationCount: Long = 0

  internal val changeLog: ChangeLog = HashMap()

  internal fun clear() {
    modificationCount++
    changeLog.clear()
  }

  internal fun <T : WorkspaceEntity> addReplaceEvent(entityId: EntityId,
                                                     copiedData: WorkspaceEntityData<T>,
                                                     addedChildren: List<Pair<ConnectionId, EntityId>>,
                                                     removedChildren: List<Pair<ConnectionId, EntityId>>,
                                                     parentsMapRes: Map<ConnectionId, EntityId?>) {
    modificationCount++

    val existingChange = changeLog[entityId]
    val replaceEvent = ChangeEntry.ReplaceEntity(copiedData, addedChildren, removedChildren, parentsMapRes)
    if (existingChange == null) {
      changeLog[entityId] = replaceEvent to null
    }
    else {
      when (val firstChange = existingChange.first) {
        is ChangeEntry.AddEntity<*> -> changeLog[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz) to null
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
        is ChangeEntry.ChangeEntitySource<*> -> changeLog[entityId] = replaceEvent to firstChange
        is ChangeEntry.ReplaceEntity<*> -> {
          val newAddedChildren = (firstChange.newChildren.toSet() - removedChildren.toSet()).toList()
          val newRemovedChildren = (firstChange.removedChildren.toSet() - addedChildren.toSet()).toList()
          val newChangedParents = firstChange.modifiedParents + parentsMapRes
          changeLog[entityId] = ChangeEntry.ReplaceEntity(copiedData, newAddedChildren, newRemovedChildren,
                                                          newChangedParents) to existingChange.second
        }
      }
    }
  }

  internal fun <T : WorkspaceEntity> addAddEvent(pid: EntityId, pEntityData: WorkspaceEntityData<T>) {
    modificationCount++

    // XXX: This check should exist, but some tests fails with it.
    //if (targetEntityId in it) LOG.error("Trying to add entity again. ")

    changeLog[pid] = ChangeEntry.AddEntity(pEntityData, pid.clazz) to null
  }

  internal fun <T : WorkspaceEntity> addChangeSourceEvent(entityId: EntityId, copiedData: WorkspaceEntityData<T>) {
    modificationCount++

    val existingChange = changeLog[entityId]
    val changeSourceEvent = ChangeEntry.ChangeEntitySource(copiedData)
    if (existingChange == null) {
      changeLog[entityId] = changeSourceEvent to null
    }
    else {
      when (val firstChange = existingChange.first) {
        is ChangeEntry.AddEntity<*> -> changeLog[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz) to null
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
        is ChangeEntry.ChangeEntitySource<*> -> changeLog[entityId] = changeSourceEvent to null
        is ChangeEntry.ReplaceEntity<*> -> changeLog[entityId] = firstChange to changeSourceEvent
      }
    }
  }

  internal fun addRemoveEvent(removedEntityId: EntityId) {
    modificationCount++

    val existingChange = changeLog[removedEntityId]
    val removeEvent = ChangeEntry.RemoveEntity(removedEntityId)
    if (existingChange == null) {
      changeLog[removedEntityId] = removeEvent to null
    }
    else {
      when (existingChange.first) {
        is ChangeEntry.AddEntity<*> -> changeLog.remove(removedEntityId)
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to remove the entity twice. $removedEntityId")
        is ChangeEntry.ChangeEntitySource<*> -> changeLog[removedEntityId] = removeEvent to null
        is ChangeEntry.ReplaceEntity<*> -> changeLog[removedEntityId] = removeEvent to null
      }
    }
  }

  companion object {
    val LOG = logger<WorkspaceBuilderChangeLog>()
  }
}

internal sealed class ChangeEntry {
  data class AddEntity<E : WorkspaceEntity>(val entityData: WorkspaceEntityData<E>, val clazz: Int) : ChangeEntry()

  data class RemoveEntity(val id: EntityId) : ChangeEntry()

  data class ChangeEntitySource<E : WorkspaceEntity>(val newData: WorkspaceEntityData<E>) : ChangeEntry()

  data class ReplaceEntity<E : WorkspaceEntity>(
    val newData: WorkspaceEntityData<E>,
    val newChildren: List<Pair<ConnectionId, EntityId>>,
    val removedChildren: List<Pair<ConnectionId, EntityId>>,
    val modifiedParents: Map<ConnectionId, EntityId?>
  ) : ChangeEntry()
}
