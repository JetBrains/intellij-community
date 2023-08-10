// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.workspace.storage.EntitySource
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
    originalParents: Map<ConnectionId, ParentEntityId>,
    addedChildren: List<Pair<ConnectionId, ChildEntityId>>,
    removedChildren: Set<Pair<ConnectionId, ChildEntityId>>,
    parentsMapRes: Map<ConnectionId, ParentEntityId?>,
  ) {
    modificationCount++

    addReplaceReferencesEvent(entityId, originalParents, addedChildren, removedChildren, parentsMapRes, incModificationCounter = false)
    addReplaceDataEvent(entityId, copiedData, originalEntity, incModificationCounter = false)
  }

  /**
   * This function adds replace event that represents changes in references between entities (without change of data)
   * Use [addReplaceDataEvent] to record changes in data inside the entity
   */
  private fun addReplaceReferencesEvent(
    entityId: EntityId,
    originalParents: Map<ConnectionId, ParentEntityId>,
    addedChildren: List<Pair<ConnectionId, ChildEntityId>>,
    removedChildren: Set<Pair<ConnectionId, ChildEntityId>>,
    parentsMapRes: Map<ConnectionId, ParentEntityId?>,
    incModificationCounter: Boolean = true,
  ) {
    if (incModificationCounter) modificationCount++

    val existingChange = changeLog[entityId]
    val replaceEvent = ChangeEntry.ReplaceEntity(
      null,
      ChangeEntry.ReplaceEntity.References(addedChildren, removedChildren.toList(), originalParents, parentsMapRes)
    )

    val makeReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      val addedChildrenSet = addedChildren.toSet()
      val newAddedChildren = if (replaceEntity.references != null) {
        (replaceEntity.references.newChildren.toSet() - removedChildren + (addedChildrenSet - replaceEntity.references.removedChildren.toSet())).toList()
      } else addedChildrenSet.toList()
      val newRemovedChildren = if (replaceEntity.references != null) {
        (replaceEntity.references.removedChildren.toSet() - addedChildrenSet + (removedChildren - replaceEntity.references.newChildren.toSet())).toList()
      } else removedChildren.toList()
      val newChangedParents = ((replaceEntity.references?.modifiedParents ?: emptyMap()) + parentsMapRes).toMutableMap()
      originalParents.forEach { (key, value) -> newChangedParents.remove(key, value) }
      if (newAddedChildren.isEmpty() && newRemovedChildren.isEmpty() && newChangedParents.isEmpty()) {
        if (replaceEntity.data == null) null else replaceEntity
      }
      else ChangeEntry.ReplaceEntity(
        replaceEntity.data,
        ChangeEntry.ReplaceEntity.References(newAddedChildren, newRemovedChildren, originalParents, newChangedParents)
      )
    }

    if (existingChange == null) {
      if (addedChildren.isNotEmpty() || removedChildren.isNotEmpty() || parentsMapRes.isNotEmpty()) {
        changeLog[entityId] = ChangeEntry.ReplaceEntity(
          null,
          ChangeEntry.ReplaceEntity.References(addedChildren, removedChildren.toList(), originalParents, parentsMapRes)
        )
      }
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> Unit // Keep the existing change
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event.")
        is ChangeEntry.ChangeEntitySource -> changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(replaceEvent, existingChange)
        is ChangeEntry.ReplaceEntity -> {
          val event = makeReplaceEvent(existingChange)
          if (event != null) {
            changeLog[entityId] = event
          }
          else {
            changeLog.remove(entityId)
          }
          Unit
        }
        is ChangeEntry.ReplaceAndChangeSource -> {
          val newReplaceEvent = makeReplaceEvent(existingChange.dataChange)
          if (newReplaceEvent != null) {
            changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(newReplaceEvent, existingChange.sourceChange)
          }
          else {
            changeLog[entityId] = existingChange.sourceChange
          }
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
    val replaceEvent = ChangeEntry.ReplaceEntity(ChangeEntry.ReplaceEntity.Data(originalEntity, copiedData), null)

    val makeReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      if (originalEntity.equalsIgnoringEntitySource(copiedData)) {
        if (replaceEntity.references == null) null else replaceEntity
      }
      else ChangeEntry.ReplaceEntity(ChangeEntry.ReplaceEntity.Data(originalEntity, copiedData), replaceEntity.references)
    }

    if (existingChange == null) {
      if (!originalEntity.equalsIgnoringEntitySource(copiedData)) {
        changeLog[entityId] = ChangeEntry.ReplaceEntity(ChangeEntry.ReplaceEntity.Data(originalEntity, copiedData), null)
      }
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> changeLog[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz)
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
        is ChangeEntry.ChangeEntitySource -> changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(replaceEvent, existingChange)
        is ChangeEntry.ReplaceEntity -> {
          val event = makeReplaceEvent(existingChange)
          if (event != null) {
            changeLog[entityId] = event
          }
          else {
            changeLog.remove(entityId)
          }
          Unit
        }
        is ChangeEntry.ReplaceAndChangeSource -> {
          val newReplaceEvent = makeReplaceEvent(existingChange.dataChange)
          if (newReplaceEvent != null) {
            changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(newReplaceEvent, existingChange.sourceChange)
          }
          else {
            changeLog[entityId] = existingChange.sourceChange
          }
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

  internal fun <T : WorkspaceEntity> addChangeSourceEvent(entityId: EntityId,
                                                          copiedData: WorkspaceEntityData<T>,
                                                          originalSource: EntitySource) {
    modificationCount++

    val existingChange = changeLog[entityId]
    val changeSourceEvent = ChangeEntry.ChangeEntitySource(originalSource, copiedData)
    if (existingChange == null) {
      if (copiedData.entitySource != originalSource) {
        changeLog[entityId] = changeSourceEvent
      }
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> changeLog[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz)
        is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
        is ChangeEntry.ChangeEntitySource -> {
          if (copiedData.entitySource != originalSource) {
            changeLog[entityId] = changeSourceEvent
          }
          else {
            changeLog.remove(entityId)
          }
          Unit
        }
        is ChangeEntry.ReplaceEntity -> {
          if (copiedData.entitySource != originalSource) {
            changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(existingChange, changeSourceEvent)
          }
          Unit
        }
        is ChangeEntry.ReplaceAndChangeSource -> {
          if (copiedData.entitySource != originalSource) {
            changeLog[entityId] = ChangeEntry.ReplaceAndChangeSource.from(existingChange.dataChange, changeSourceEvent)
          }
          else {
            changeLog[entityId] = existingChange.dataChange
          }
        }
      }.let { }
    }
  }

  internal fun addRemoveEvent(removedEntityId: EntityId,
                              originalData: WorkspaceEntityData<WorkspaceEntity>,
                              oldParents: Map<ConnectionId, ParentEntityId>) {
    modificationCount++

    val existingChange = changeLog[removedEntityId]
    val removeEvent = ChangeEntry.RemoveEntity(originalData, oldParents, removedEntityId)
    if (existingChange == null) {
      changeLog[removedEntityId] = removeEvent
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> changeLog.remove(removedEntityId)
        is ChangeEntry.ChangeEntitySource -> changeLog[removedEntityId] = removeEvent
        is ChangeEntry.ReplaceEntity -> changeLog[removedEntityId] = removeEvent
        is ChangeEntry.ReplaceAndChangeSource -> changeLog[removedEntityId] = removeEvent
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
    val oldParents: Map<ConnectionId, ParentEntityId>,
    val id: EntityId,
  ) : ChangeEntry()

  data class ChangeEntitySource(
    val originalSource: EntitySource,
    val newData: WorkspaceEntityData<out WorkspaceEntity>
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
      val oldParents: Map<ConnectionId, ParentEntityId>,
      val modifiedParents: Map<ConnectionId, ParentEntityId?>
    )
  }

  data class ReplaceAndChangeSource(
    val dataChange: ReplaceEntity,
    val sourceChange: ChangeEntitySource,
  ) : ChangeEntry() {
    companion object {
      fun from(dataChange: ReplaceEntity, sourceChange: ChangeEntitySource): ReplaceAndChangeSource {
        return ReplaceAndChangeSource(dataChange, sourceChange)
      }
    }
  }
}

internal fun MutableEntityStorageImpl.getOriginalEntityData(id: EntityId): WorkspaceEntityData<*> {
  return this.changeLog.changeLog[id]?.let {
    when (it) {
      is ChangeEntry.ReplaceEntity -> it.data?.oldData
      is ChangeEntry.AddEntity -> it.entityData
      is ChangeEntry.ChangeEntitySource -> it.newData
      is ChangeEntry.RemoveEntity -> it.oldData
      is ChangeEntry.ReplaceAndChangeSource -> it.dataChange.data?.oldData
    }
  }?.clone() ?: this.entityDataByIdOrDie(id).clone()
}

/**
 * This returns parents that were attached to the entity prior ANY modification of this entity.
 */
internal fun MutableEntityStorageImpl.getOriginalParents(id: ChildEntityId): Map<ConnectionId, ParentEntityId> {
  return this.changeLog.changeLog[id.id]?.let {
    when (it) {
      is ChangeEntry.ReplaceEntity -> it.references?.oldParents
      is ChangeEntry.AddEntity -> this.refs.getParentRefsOfChild(id)
      is ChangeEntry.ChangeEntitySource -> this.refs.getParentRefsOfChild(id)
      is ChangeEntry.RemoveEntity -> it.oldParents
      is ChangeEntry.ReplaceAndChangeSource -> it.dataChange.references?.oldParents
    }
  } ?: this.refs.getParentRefsOfChild(id)
}

internal fun MutableEntityStorageImpl.getOriginalSourceFromChangelog(id: EntityId): EntitySource? {
  return this.changeLog.changeLog[id]?.let {
    when (it) {
      is ChangeEntry.ChangeEntitySource -> it.originalSource
      is ChangeEntry.ReplaceAndChangeSource -> it.sourceChange.originalSource
      else -> null
    }
  }
}
