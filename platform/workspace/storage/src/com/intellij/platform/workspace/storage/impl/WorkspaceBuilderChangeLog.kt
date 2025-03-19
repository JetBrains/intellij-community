// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.platform.diagnostic.telemetry.JPS
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import io.opentelemetry.api.metrics.Meter
import kotlinx.collections.immutable.*

internal typealias ChangeLog = MutableMap<EntityId, ChangeEntry>

internal class WorkspaceBuilderChangeLog {
  var modificationCount: Long = 0

  internal val changeLog: ChangeLog = LinkedHashMap()

  internal fun clear() {
    modificationCount++
    changeLog.clear()
  }

  internal fun join(other: WorkspaceBuilderChangeLog) {
    other.changeLog.forEach { (id, entry) ->
      when (entry) {
        is ChangeEntry.AddEntity -> {
          this.addAddEventImpl(id, entry.entityData, true)
        }
        is ChangeEntry.RemoveEntity -> {
          this.addRemoveEvent(id, entry.oldData as WorkspaceEntityData<WorkspaceEntity>)
        }
        is ChangeEntry.ReplaceEntity -> {
          if (entry.data != null) {
            this.addReplaceDataEvent(id, entry.data.newData, entry.data.oldData, false)
          }
          if (entry.references != null) {
            this.addReplaceReferencesEvent(id, entry.references.newChildren, entry.references.removedChildren,
                                           entry.references.newParents, entry.references.removedParents, false)
          }
        }
      }
    }
  }

  /**
   * This function adds replace event that represents changes in references between entities (without change of data)
   * Use [addReplaceDataEvent] to record changes in data inside the entity
   */
  private fun addReplaceReferencesEvent(
    entityId: EntityId,
    addedChildren: Set<Pair<ConnectionId, ChildEntityId>> = emptySet(),
    removedChildren: Set<Pair<ConnectionId, ChildEntityId>> = emptySet(),
    newParents: Map<ConnectionId, ParentEntityId> = emptyMap(),
    removedParents: Map<ConnectionId, ParentEntityId> = emptyMap(),
    incModificationCounter: Boolean = true,
  ) {
    if (incModificationCounter) modificationCount++

    addedChildren.forEach { (connectionId, childId) ->
      addReplaceEventForNewChild(entityId, connectionId, childId, false)
    }
    removedChildren.forEach { (connectionId, childId) ->
      addReplaceEventForRemovedChild(entityId, connectionId, childId, false)
    }
    newParents.forEach { (connectionId, parentId) ->
      addReplaceEventForNewParent(entityId, connectionId, parentId, false)
    }
    removedParents.forEach { (connectionId, parentId) ->
      addReplaceEventForRemovedParent(entityId, connectionId, parentId, false)
    }
  }

  internal fun addReplaceEventForNewParent(
    entityId: EntityId,
    newConnectionId: ConnectionId,
    newParentId: ParentEntityId,
    incModificationCounter: Boolean = true,
  ) = addReplaceEventForNewParentMs.addMeasuredTime {
    if (incModificationCounter) modificationCount++

    val existingChange = changeLog[entityId]

    val updateReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      val newAddedParents = if (replaceEntity.references != null) {
        if (!replaceEntity.references.newParents.contains(newConnectionId, newParentId)
            && !replaceEntity.references.removedParents.contains(newConnectionId, newParentId)) {
          replaceEntity.references.newParents.put(newConnectionId, newParentId)
        }
        else {
          replaceEntity.references.newParents
        }
      }
      else persistentHashMapOf(newConnectionId to newParentId)

      val newRemovedParents = if (replaceEntity.references != null) {
        replaceEntity.references.removedParents.remove(newConnectionId, newParentId)
      }
      else persistentHashMapOf()

      if (replaceEntity.references != null) {
        if (replaceEntity.references.newChildren.isEmpty() && replaceEntity.references.removedChildren.isEmpty() && replaceEntity.references.childrenOrdering.isEmpty() && newAddedParents.isEmpty() && newRemovedParents.isEmpty()) {
          if (replaceEntity.data == null) null else replaceEntity.copy(references = null)
        }
        else replaceEntity.copy(
          references = replaceEntity.references.copy(newParents = newAddedParents, removedParents = newRemovedParents))
      }
      else {
        replaceEntity.copy(references = ChangeEntry.ReplaceEntity.References(newParents = newAddedParents, removedParents = newRemovedParents))
      }
    }

    if (existingChange == null) {
      changeLog[entityId] = ChangeEntry.ReplaceEntity(
        null,
        ChangeEntry.ReplaceEntity.References(newParents = persistentHashMapOf(newConnectionId to newParentId))
      )
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> Unit // Keep the existing change
        is ChangeEntry.RemoveEntity -> {
          LOG.error("Trying to update removed entity. Skip change event. Entity Id: ${entityId.asString()}, New parent: $newParentId, $newConnectionId")
        }
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

  internal fun addReplaceEventForRemovedParent(
    entityId: EntityId,
    removedConnectionId: ConnectionId,
    removedParentId: ParentEntityId,
    incModificationCounter: Boolean = true,
  ) = addReplaceEventForRemovedParentMs.addMeasuredTime {
    if (incModificationCounter) modificationCount++

    val existingChange = changeLog[entityId]

    val updateReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      val newRemovedParents = if (replaceEntity.references != null) {
        if (!replaceEntity.references.removedParents.contains(removedConnectionId, removedParentId)
            && !replaceEntity.references.newParents.contains(removedConnectionId, removedParentId)) {
          replaceEntity.references.removedParents.put(removedConnectionId, removedParentId)
        }
        else replaceEntity.references.removedParents
      }
      else persistentHashMapOf(removedConnectionId to removedParentId)

      val newAddedParents = if (replaceEntity.references != null) {
        replaceEntity.references.newParents.remove(removedConnectionId, removedParentId)
      }
      else persistentHashMapOf()

      if (replaceEntity.references != null) {
        if (replaceEntity.references.newChildren.isEmpty() && replaceEntity.references.removedChildren.isEmpty() && replaceEntity.references.childrenOrdering.isEmpty() && newAddedParents.isEmpty() && newRemovedParents.isEmpty()) {
          if (replaceEntity.data == null) null else replaceEntity.copy(references = null)
        }
        else replaceEntity.copy(
          references = replaceEntity.references.copy(newParents = newAddedParents, removedParents = newRemovedParents))
      }
      else {
        replaceEntity.copy(references = ChangeEntry.ReplaceEntity.References(newParents = newAddedParents, removedParents = newRemovedParents))
      }
    }

    if (existingChange == null) {
      changeLog[entityId] = ChangeEntry.ReplaceEntity(
        null,
        ChangeEntry.ReplaceEntity.References(removedParents = persistentHashMapOf(removedConnectionId to removedParentId))
      )
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> Unit // Keep the existing change
        is ChangeEntry.RemoveEntity -> {
          LOG.error("Trying to update removed entity Skip change event. Entity Id: ${entityId.asString()}, Removed parent: $removedParentId, $removedConnectionId")
        }
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

  internal fun addReplaceEventForNewChild(
    entityId: EntityId,
    addedChildConnectionId: ConnectionId,
    addedChildId: ChildEntityId,
    incModificationCounter: Boolean = true,
  ) = addReplaceEventForNewChildMs.addMeasuredTime {
    if (incModificationCounter) modificationCount++

    val existingChange = changeLog[entityId]

    val updateReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      val connectionToId = addedChildConnectionId to addedChildId
      val newAddedChildren = if (replaceEntity.references != null) {
        if (connectionToId !in replaceEntity.references.newChildren && connectionToId !in replaceEntity.references.removedChildren) {
          replaceEntity.references.newChildren.add(connectionToId)
        }
        else {
          replaceEntity.references.newChildren
        }
      }
      else {
        persistentHashSetOf(connectionToId)
      }
      val newRemovedChildren = if (replaceEntity.references != null) {
        replaceEntity.references.removedChildren.remove(connectionToId)
      }
      else persistentHashSetOf()

      val newOrder = if (replaceEntity.references != null) {
        replaceEntity.references.childrenOrdering.getOrElse(addedChildConnectionId) { persistentSetOf() }.add(addedChildId)
      } else {
        persistentSetOf(addedChildId)
      }

      if (replaceEntity.references != null) {
        if (newAddedChildren.isEmpty() && newRemovedChildren.isEmpty() && newOrder.isEmpty() && replaceEntity.references.newParents.isEmpty() && replaceEntity.references.removedParents.isEmpty()) {
          if (replaceEntity.data == null) null else replaceEntity.copy(references = null)
        }
        else {
          val ordering = replaceEntity.references.childrenOrdering.put(addedChildConnectionId, newOrder)
          replaceEntity.copy(
            references = replaceEntity.references.copy(newChildren = newAddedChildren, removedChildren = newRemovedChildren,
                                                       childrenOrdering = ordering))
        }
      }
      else {
        val ordering = persistentHashMapOf(addedChildConnectionId to newOrder)
        replaceEntity.copy(references = ChangeEntry.ReplaceEntity.References(newAddedChildren, newRemovedChildren, ordering))
      }
    }

    if (existingChange == null) {
      val ordering = persistentHashMapOf(addedChildConnectionId to persistentSetOf(addedChildId))
      changeLog[entityId] = ChangeEntry.ReplaceEntity(
        null,
        ChangeEntry.ReplaceEntity.References(persistentHashSetOf(addedChildConnectionId to addedChildId), persistentHashSetOf(), ordering)
      )
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> Unit // Keep the existing change
        is ChangeEntry.RemoveEntity -> {
          LOG.error("Trying to update removed entity. Skip change event. Entity Id: ${entityId.asString()}, Added child: $addedChildId, $addedChildConnectionId")
        }
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

  internal fun addReplaceEventForRemovedChild(
    entityId: EntityId,
    removedChildConnectionId: ConnectionId,
    removedChildId: ChildEntityId,
    incModificationCounter: Boolean = true,
  ) = addReplaceEventForRemovedChildMs.addMeasuredTime  {
    if (incModificationCounter) modificationCount++

    val existingChange = changeLog[entityId]

    val updateReplaceEvent = { replaceEntity: ChangeEntry.ReplaceEntity ->
      val connectionToId = removedChildConnectionId to removedChildId

      val newRemovedChildren = if (replaceEntity.references != null) {
        if (connectionToId !in replaceEntity.references.removedChildren && connectionToId !in replaceEntity.references.newChildren) {
          replaceEntity.references.removedChildren.add(connectionToId)
        }
        else {
          replaceEntity.references.removedChildren
        }
      }
      else persistentHashSetOf(connectionToId)

      val newAddedChildren = if (replaceEntity.references != null) {
        replaceEntity.references.newChildren.remove(connectionToId)
      }
      else persistentHashSetOf()

      val newOrder = if (replaceEntity.references != null) {
        replaceEntity.references.childrenOrdering.getOrElse(removedChildConnectionId) { persistentSetOf() }.remove(removedChildId)
      } else {
        persistentSetOf()
      }

      if (replaceEntity.references != null) {
        if (newAddedChildren.isEmpty() && newRemovedChildren.isEmpty() && newOrder.isEmpty() && replaceEntity.references.newParents.isEmpty() && replaceEntity.references.removedParents.isEmpty()) {
          if (replaceEntity.data == null) null else replaceEntity.copy(references = null)
        }
        else {
          val ordering = replaceEntity.references.childrenOrdering.put(removedChildConnectionId, newOrder)
          replaceEntity.copy(references = replaceEntity.references.copy(
            newChildren = newAddedChildren,
            removedChildren = newRemovedChildren,
            childrenOrdering = ordering,
          ))
        }
      }
      else {
        val ordering = persistentHashMapOf(removedChildConnectionId to newOrder)
        replaceEntity.copy(references = ChangeEntry.ReplaceEntity.References(newAddedChildren, newRemovedChildren, ordering))
      }
    }

    if (existingChange == null) {
      val ordering = persistentHashMapOf(removedChildConnectionId to persistentSetOf<ChildEntityId>())
      changeLog[entityId] = ChangeEntry.ReplaceEntity(
        null,
        ChangeEntry.ReplaceEntity.References(removedChildren = persistentHashSetOf(removedChildConnectionId to removedChildId), childrenOrdering = ordering)
      )
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> Unit // Keep the existing change
        is ChangeEntry.RemoveEntity -> {
          LOG.error("Trying to update removed entity. Skip change event. Entity Id: ${entityId.asString()}, Removed child: $removedChildId, $removedChildConnectionId")
        }
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

  private fun <K, V> Map<K, V>.contains(key: K, value: V): Boolean {
    return this[key] == value
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
        if (replaceEntity.references == null) null else replaceEntity.copy(data = null)
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
        is ChangeEntry.RemoveEntity -> {
          LOG.error("Trying to update removed entity. Skip change event. $copiedData, EntityId: ${entityId.asString()}")
        }
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

  internal fun <T : WorkspaceEntity> addAddEvent(pid: EntityId,
                                                 pEntityData: WorkspaceEntityData<T>) {
    addAddEventImpl(pid, pEntityData, false)
  }

  /**
   * Add "add" event to the changelog.
   *
   * During one change we don't add entities to the same entity id. This means, if we remove an entity and add a new one,
   *   the new entity will never get an id of removed entity.
   * Thus, if we add "add" entity and it turns out that this id already has a "remove" event, this is an indication of error.
   *
   * However, the id can be reused in different operations. In this case, the same id can get a remove and then add event and this is normal.
   *   If this is the case when multiple changelogs are combined, [allowAddingOnRemoveEvent] can be set to true to avoid error on
   *   adding an add event on top of remove event. Such a combination will turn into "replace" event. However, this replaces event won't
   *   have an information about changes in references, so this should be used only for specific cases where this information is not needed.
   */
  private fun <T : WorkspaceEntity> addAddEventImpl(pid: EntityId,
                                                 pEntityData: WorkspaceEntityData<T>,
                                                 allowAddingOnRemoveEvent: Boolean) {
    modificationCount++

    // XXX: This check should exist, but some tests fails with it.
    //if (targetEntityId in it) LOG.error("Trying to add entity again. ")

    val existingEvent = changeLog[pid]
    when (existingEvent) {
      is ChangeEntry.RemoveEntity -> {
        if (!allowAddingOnRemoveEvent) error("Trying to add entity that was removed")
        changeLog.remove(pid)
        addReplaceDataEvent(pid, pEntityData, existingEvent.oldData, false)
      }
      else -> changeLog[pid] = ChangeEntry.AddEntity(pEntityData, pid.clazz)
    }
  }

  internal fun addRemoveEvent(removedEntityId: EntityId,
                              originalData: WorkspaceEntityData<WorkspaceEntity>) {
    modificationCount++

    val existingChange = changeLog[removedEntityId]
    val removeEvent = ChangeEntry.RemoveEntity(originalData, removedEntityId)
    if (existingChange == null) {
      LOG.traceThrowable { RuntimeException("Adding remove event for id: ${removedEntityId.asString()}") }
      changeLog[removedEntityId] = removeEvent
    }
    else {
      when (existingChange) {
        is ChangeEntry.AddEntity -> changeLog.remove(removedEntityId)
        is ChangeEntry.ReplaceEntity -> {
          LOG.traceThrowable { RuntimeException("Replace 'replace' event with 'remove' event for id: ${removedEntityId.asString()}") }
          changeLog[removedEntityId] = removeEvent
        }
        is ChangeEntry.RemoveEntity ->  error("Already removed ${removedEntityId.asString()}")
      }
    }
  }

  companion object {
    val LOG = logger<WorkspaceBuilderChangeLog>()

    private val addReplaceEventForNewParentMs = MillisecondsMeasurer()
    private val addReplaceEventForNewChildMs = MillisecondsMeasurer()
    private val addReplaceEventForRemovedParentMs = MillisecondsMeasurer()
    private val addReplaceEventForRemovedChildMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val addReplaceEventForNewParentCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.changeLog.addReplaceEventForNewParent.ms").buildObserver()
      val addReplaceEventForNewChildCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.changeLog.addReplaceEventForNewChild.ms").buildObserver()
      val addReplaceEventForRemovedParentCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.changeLog.addReplaceEventForRemovedParent.ms").buildObserver()
      val addReplaceEventForRemovedChildCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.changeLog.addReplaceEventForRemovedChild.ms").buildObserver()

      meter.batchCallback(
        {
          addReplaceEventForNewParentCounter.record(addReplaceEventForNewParentMs.asMilliseconds())
          addReplaceEventForNewChildCounter.record(addReplaceEventForNewChildMs.asMilliseconds())
          addReplaceEventForRemovedParentCounter.record(addReplaceEventForRemovedParentMs.asMilliseconds())
          addReplaceEventForRemovedChildCounter.record(addReplaceEventForRemovedChildMs.asMilliseconds())
        },
        addReplaceEventForNewParentCounter,
        addReplaceEventForNewChildCounter,
        addReplaceEventForRemovedParentCounter,
        addReplaceEventForRemovedChildCounter,
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(JPS))
    }
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
      val newChildren: PersistentSet<Pair<ConnectionId, ChildEntityId>> = persistentHashSetOf(),
      val removedChildren: PersistentSet<Pair<ConnectionId, ChildEntityId>> = persistentHashSetOf(),
      val childrenOrdering: PersistentMap<ConnectionId, PersistentSet<ChildEntityId>> = persistentHashMapOf(),
      val newParents: PersistentMap<ConnectionId, ParentEntityId> = persistentHashMapOf(),
      val removedParents: PersistentMap<ConnectionId, ParentEntityId> = persistentHashMapOf(),
    ) {
      fun isEmpty(): Boolean {
        return newChildren.isEmpty()
               && removedChildren.isEmpty()
               && childrenOrdering.isEmpty()
               && newParents.isEmpty()
               && removedParents.isEmpty()
      }
    }
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
