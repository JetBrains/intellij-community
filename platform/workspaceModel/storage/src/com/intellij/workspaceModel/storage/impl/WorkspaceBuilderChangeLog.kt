// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.WorkspaceEntity

internal typealias ChangeLog = MutableMap<EntityId, ChangeEntry>

class WorkspaceBuilderChangeLog {
  var modificationCount: Long = 0

  internal val changeLog: ChangeLog = LinkedHashMap()

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

    val makeReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity<*> ->
      val newAddedChildren = (replaceEntity.newChildren.toSet() - removedChildren.toSet()).toList()
      val newRemovedChildren = (replaceEntity.removedChildren.toSet() - addedChildren.toSet()).toList()
      val newChangedParents = replaceEntity.modifiedParents + parentsMapRes
      ChangeEntry.ReplaceEntity(copiedData, newAddedChildren, newRemovedChildren, newChangedParents)
    }

    if (existingChange == null) {
      changeLog[entityId] = replaceEvent
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity<*> -> changeLog[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz)
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
        is ChangeEntry.ChangeEntitySource<*> -> changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(replaceEvent, existingChange)
        is ChangeEntry.ReplaceEntity<*> -> changeLog[entityId] = makeReplaceEvent(existingChange)
        is ChangeEntry.ReplaceAndChangeSource<*> -> {
          val newReplaceEvent = makeReplaceEvent(existingChange.dataChange)
          changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(newReplaceEvent, existingChange.sourceChange)
        }
      }.let { }
    }
  }

  internal fun <T : WorkspaceEntity> addAddEvent(pid: EntityId, pEntityData: WorkspaceEntityData<T>) {
    modificationCount++

    // XXX: This check should exist, but some tests fails with it.
    //if (targetEntityId in it) LOG.error("Trying to add entity again. ")

    changeLog[pid] = ChangeEntry.AddEntity(pEntityData, pid.clazz)
  }

  internal fun <T : WorkspaceEntity> addChangeSourceEvent(entityId: EntityId, copiedData: WorkspaceEntityData<T>) {
    modificationCount++

    val existingChange = changeLog[entityId]
    val changeSourceEvent = ChangeEntry.ChangeEntitySource(copiedData)
    if (existingChange == null) {
      changeLog[entityId] = changeSourceEvent
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity<*> -> changeLog[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz)
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
        is ChangeEntry.ChangeEntitySource<*> -> changeLog[entityId] = changeSourceEvent
        is ChangeEntry.ReplaceEntity<*> -> changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(existingChange, changeSourceEvent)
        is ChangeEntry.ReplaceAndChangeSource<*> -> changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(existingChange.dataChange,
                                                                                                                  changeSourceEvent)
      }.let { }
    }
  }

  internal fun addRemoveEvent(removedEntityId: EntityId) {
    modificationCount++

    val existingChange = changeLog[removedEntityId]
    val removeEvent = ChangeEntry.RemoveEntity(removedEntityId)
    if (existingChange == null) {
      changeLog[removedEntityId] = removeEvent
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity<*> -> changeLog.remove(removedEntityId)
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to remove the entity twice. $removedEntityId")
        is ChangeEntry.ChangeEntitySource<*> -> changeLog[removedEntityId] = removeEvent
        is ChangeEntry.ReplaceEntity<*> -> changeLog[removedEntityId] = removeEvent
        is ChangeEntry.ReplaceAndChangeSource<*> -> changeLog[removedEntityId] = removeEvent
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

  data class ReplaceAndChangeSource<E : WorkspaceEntity>(
    val dataChange: ReplaceEntity<E>,
    val sourceChange: ChangeEntitySource<E>,
  ) : ChangeEntry() {
    companion object {
      fun <T : WorkspaceEntity> from(dataChange: ReplaceEntity<T>, sourceChange: ChangeEntitySource<*>): ReplaceAndChangeSource<T> {
        return ReplaceAndChangeSource(dataChange, sourceChange as ChangeEntitySource<T>)
      }
    }
  }
}
