// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.workspace.storage.WorkspaceEntity

internal typealias ChangeLog = MutableMap<EntityId, ChangeEntry>

internal class WorkspaceBuilderChangeLog {
  var modificationCount: Long = 0

  internal val changeLog: ChangeLog = LinkedHashMap()

  internal fun clear() {
    modificationCount++
    changeLog.clear()
  }

  internal fun addReplaceEvent(
    entityId: EntityId,
    copiedData: WorkspaceEntityData<out WorkspaceEntity>,
    originalEntity: WorkspaceEntityData<out WorkspaceEntity>,
    addedChildren: List<Pair<ConnectionId, ChildEntityId>>,
    removedChildren: Set<Pair<ConnectionId, ChildEntityId>>,
    newParents: Map<ConnectionId, ParentEntityId>,
    removedParents: Map<ConnectionId, ParentEntityId>,
  ) {
    modificationCount++

    addReplaceReferencesEvent(entityId, addedChildren, removedChildren, newParents, removedParents, incModificationCounter = false)
    addReplaceDataEvent(entityId, copiedData, originalEntity, incModificationCounter = false)
  }

  /**
   * This function adds replace event that represents changes in references between entities (without change of data)
   * Use [addReplaceDataEvent] to record changes in data inside the entity
   */
  private fun addReplaceReferencesEvent(
    entityId: EntityId,
    addedChildren: List<Pair<ConnectionId, ChildEntityId>>,
    removedChildren: Set<Pair<ConnectionId, ChildEntityId>>,
    newParents: Map<ConnectionId, ParentEntityId>,
    removedParents: Map<ConnectionId, ParentEntityId>,
    incModificationCounter: Boolean = true,
  ) {
    if (incModificationCounter) modificationCount++

    val existingChange = changeLog[entityId]

    val updateReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      val addedChildrenSet = addedChildren.toSet()
      val newAddedChildren = if (replaceEntity.references != null) {
        (replaceEntity.references.newChildren.toSet() - removedChildren + (addedChildrenSet - replaceEntity.references.removedChildren.toSet())).toList()
      }
      else addedChildrenSet.toList()
      val newRemovedChildren = if (replaceEntity.references != null) {
        (replaceEntity.references.removedChildren.toSet() - addedChildrenSet + (removedChildren - replaceEntity.references.newChildren.toSet())).toList()
      }
      else removedChildren.toList()

      val newAddedParents: Map<ConnectionId, ParentEntityId> = if (replaceEntity.references != null) {
        replaceEntity.references.newParents - removedParents.keys + (newParents.toMutableMap().also {
          replaceEntity.references.removedParents.forEach { k, v ->
            it.remove(k, v)
          }
        })
      }
      else newParents
      val newRemovedParents = if (replaceEntity.references != null) {
        replaceEntity.references.removedParents.toMutableMap().also {
          newParents.forEach { k, v -> it.remove(k, v) }
        } + (removedParents - replaceEntity.references.newParents.keys)
      }
      else removedParents

      if (newAddedChildren.isEmpty() && newRemovedChildren.isEmpty() && newAddedParents.isEmpty() && newRemovedParents.isEmpty()) {
        if (replaceEntity.data == null) null else replaceEntity.copy(references = null)
      }
      else replaceEntity
        .copy(references = ChangeEntry.ReplaceEntity.References(newAddedChildren, newRemovedChildren, newAddedParents, newRemovedParents))
    }

    if (existingChange == null) {
      if (addedChildren.isNotEmpty() || removedChildren.isNotEmpty() || newParents.isNotEmpty() || removedParents.isNotEmpty()) {
        changeLog[entityId] = ChangeEntry.ReplaceEntity(
          null,
          ChangeEntry.ReplaceEntity.References(addedChildren, removedChildren.toList(), newParents, removedParents)
        )
      }
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> Unit // Keep the existing change
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event.")
        is ChangeEntry.ReplaceEntity -> {
          val event = updateReplaceEvent(existingChange)
          if (event != null) {
            changeLog[entityId] = event
          }
          else {
            changeLog.remove(entityId)
          }
          Unit
        }
      }
    }
  }

  /**
   * This function adds replace event that represents changes in data only (without changes in references between entities)
   * Use [addReplaceReferencesEvent] to record changes in related entities
   */
  internal fun addReplaceDataEvent(
    entityId: EntityId,
    copiedData: WorkspaceEntityData<out WorkspaceEntity>,
    originalEntity: WorkspaceEntityData<out WorkspaceEntity>,
    incModificationCounter: Boolean = true,
  ) {
    if (incModificationCounter) modificationCount++

    val existingChange = changeLog[entityId]

    val updateReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      if (originalEntity == copiedData) {
        if (replaceEntity.references == null) null else replaceEntity
      }
      else ChangeEntry.ReplaceEntity(ChangeEntry.ReplaceEntity.Data(originalEntity, copiedData), replaceEntity.references)
    }

    if (existingChange == null) {
      if (originalEntity != copiedData) {
        changeLog[entityId] = ChangeEntry.ReplaceEntity(ChangeEntry.ReplaceEntity.Data(originalEntity, copiedData), null)
      }
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> changeLog[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz)
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
        is ChangeEntry.ReplaceEntity -> {
          val event = updateReplaceEvent(existingChange)
          if (event != null) {
            changeLog[entityId] = event
          }
          else {
            changeLog.remove(entityId)
          }
          Unit
        }
      }
    }
  }

  internal fun <T : WorkspaceEntity> addAddEvent(pid: EntityId, pEntityData: WorkspaceEntityData<T>) {
    modificationCount++

    // XXX: This check should exist, but some tests fails with it.
    //if (targetEntityId in it) LOG.error("Trying to add entity again. ")

    changeLog[pid] = ChangeEntry.AddEntity(pEntityData, pid.clazz)
  }

  internal fun addRemoveEvent(removedEntityId: EntityId,
                              originalData: WorkspaceEntityData<WorkspaceEntity>) {
    modificationCount++

    val existingChange = changeLog[removedEntityId]
    val removeEvent = ChangeEntry.RemoveEntity(originalData, removedEntityId)
    if (existingChange == null) {
      changeLog[removedEntityId] = removeEvent
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> changeLog.remove(removedEntityId)
        is ChangeEntry.ReplaceEntity -> changeLog[removedEntityId] = removeEvent
        is ChangeEntry.RemoveEntity -> Unit
      }
    }
  }

  companion object {
    val LOG = logger<WorkspaceBuilderChangeLog>()
  }
}

internal sealed class ChangeEntry {
  data class AddEntity(val entityData: WorkspaceEntityData<out WorkspaceEntity>, val clazz: Int) : ChangeEntry()

  data class RemoveEntity(
    val oldData: WorkspaceEntityData<out WorkspaceEntity>,
    val id: EntityId,
  ) : ChangeEntry()

  /**
   * Fields about children or parents contain information only about changes on this particular entity. This means, that if some entity
   *   is removed, information about its removal is NOT added to the parent using this mechanism
   *
   * The information is split into two parts: changes in the entity itself (updated fields) and changes between references
   */
  data class ReplaceEntity(
    val data: Data?,
    val references: References?,
  ) : ChangeEntry() {
    data class Data(
      val oldData: WorkspaceEntityData<out WorkspaceEntity>,
      val newData: WorkspaceEntityData<out WorkspaceEntity>,
    )

    data class References(
      val newChildren: List<Pair<ConnectionId, ChildEntityId>>,
      val removedChildren: List<Pair<ConnectionId, ChildEntityId>>,
      val newParents: Map<ConnectionId, ParentEntityId>,
      val removedParents: Map<ConnectionId, ParentEntityId>,
    )
  }
}

internal fun MutableEntityStorageImpl.getOriginalEntityData(id: EntityId): WorkspaceEntityData<*> {
  return this.changeLog.changeLog[id]?.let {
    when (it) {
      is ChangeEntry.ReplaceEntity -> it.data?.oldData
      is ChangeEntry.AddEntity -> it.entityData
      is ChangeEntry.RemoveEntity -> it.oldData
    }
  }?.clone() ?: this.entityDataByIdOrDie(id).clone()
}
