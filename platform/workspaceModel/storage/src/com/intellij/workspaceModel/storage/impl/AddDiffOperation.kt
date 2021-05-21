// Copyright 2000-2020 JetBrains s.r.o. Use of target source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.WorkspaceEntity
import java.io.File

internal class AddDiffOperation(val target: WorkspaceEntityStorageBuilderImpl, val diff: WorkspaceEntityStorageBuilderImpl) {

  private val replaceMap = HashBiMap.create<EntityId, EntityId>()
  private val diffLog = diff.changeLog.changeLog

  // Initial storage is required in case something will fail and we need to send a report
  private val initialStorage = target.toStorage()

  fun addDiff() {
    if (target === diff) LOG.error("Trying to apply diff to itself")

    for ((entityId, change) in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity<out WorkspaceEntity> -> {
          change as ChangeEntry.AddEntity<WorkspaceEntity>

          checkPersistentId(change.entityData, null)

          val sourceEntityId = change.entityData.createEntityId()

          // Adding new entity
          val targetEntityData: WorkspaceEntityData<WorkspaceEntity>
          val targetEntityId: EntityId
          val idFromReplaceMap = replaceMap[sourceEntityId]
          if (idFromReplaceMap != null) {
            // Okay, we need to add the entity at the particular id
            targetEntityData = target.entitiesByType.cloneAndAddAt(change.entityData, idFromReplaceMap)
            targetEntityId = idFromReplaceMap
          }
          else {
            // Add new entity to store (without references)
            targetEntityData = target.entitiesByType.cloneAndAdd(change.entityData, change.clazz)
            targetEntityId = targetEntityData.createEntityId()
            replaceMap[sourceEntityId] = targetEntityId
          }
          // Restore links to soft references
          if (targetEntityData is SoftLinkable) target.indexes.updateSoftLinksIndex(targetEntityData)

          addRestoreChildren(sourceEntityId, targetEntityId)

          // Restore parent references
          addRestoreParents(sourceEntityId, targetEntityId)

          target.indexes.updateIndices(change.entityData.createEntityId(), targetEntityData, diff)
          target.changeLog.addAddEvent(targetEntityId, targetEntityData)
        }
        is ChangeEntry.RemoveEntity -> {
          val sourceEntityId = change.id

          // This sourceEntityId is definitely not presented in replaceMap as a key, so we can just remove this entity from target
          //   with this id. But there is a case when some different entity from source builder will get this id if there was a gup before.
          //   So we should check if entity at this id was added in this transaction. If replaceMap has a value with this entity id
          //   this means that this entity was added in this transaction and there was a gup before and we should not remove anything.
          if (!replaceMap.containsValue(sourceEntityId)) {
            target.indexes.removeFromIndices(sourceEntityId)
            if (target.entityDataById(sourceEntityId) != null) {
              target.removeEntity(sourceEntityId)
            }
            target.changeLog.addRemoveEvent(sourceEntityId)
          }
        }
        is ChangeEntry.ReplaceEntity<out WorkspaceEntity> -> {
          replaceOperation(change)
        }
        is ChangeEntry.ChangeEntitySource<out WorkspaceEntity> -> {
          replaceSourceOperation(change.newData)
        }
        is ChangeEntry.ReplaceAndChangeSource<out WorkspaceEntity> -> {
          replaceOperation(change.dataChange)
          replaceSourceOperation(change.sourceChange.newData)
        }
      }
    }
    target.indexes.applyExternalMappingChanges(diff, replaceMap, target)

    // Assert consistency
    if (!target.brokenConsistency && !diff.brokenConsistency) {
      target.assertConsistencyInStrictMode("Check after add Diff", null, initialStorage, diff)
    }
    else {
      target.brokenConsistency = true
    }
  }

  private fun replaceSourceOperation(data: WorkspaceEntityData<out WorkspaceEntity>) {
    val outdatedId = data.createEntityId()
    val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)

    // We don't modify entity that isn't exist in target version of storage
    val existingEntityData = target.entityDataById(usedPid)
    if (existingEntityData != null) {
      val newEntitySource = data.entitySource
      existingEntityData.entitySource = newEntitySource
      target.indexes.entitySourceIndex.index(usedPid, newEntitySource)
      target.changeLog.addChangeSourceEvent(usedPid, existingEntityData)
    }
  }

  private fun addRestoreParents(sourceEntityId: EntityId, targetEntityId: EntityId) {
    val allParents = diff.refs.getParentRefsOfChild(sourceEntityId)
    for ((connectionId, sourceParentId) in allParents) {
      val targetParentId = if (diffLog[sourceParentId] is ChangeEntry.AddEntity<*>) {
        replaceMap[sourceParentId] ?: run {
          // target entity isn't added to the current builder yet. Add a placeholder
          val placeholderId = target.entitiesByType.book(sourceParentId.clazz)
          replaceMap[sourceParentId] = placeholderId
          placeholderId
        }
      }
      else {
        if (target.entityDataById(sourceParentId) != null) sourceParentId
        else {
          if (!connectionId.canRemoveParent()) {
            target.addDiffAndReport("Cannot restore dependency. $connectionId, $sourceParentId", initialStorage, diff, target)
          }
          null
        }
      }
      if (targetParentId != null) {
        target.refs.updateParentOfChild(connectionId, targetEntityId, targetParentId)
      }
    }
  }

  private fun addRestoreChildren(sourceEntityId: EntityId, targetEntityId: EntityId) {
    // Restore children references
    val allSourceChildren = diff.refs.getChildrenRefsOfParentBy(sourceEntityId)
    for ((connectionId, sourceChildrenIds) in allSourceChildren) {
      val targetChildrenIds = mutableSetOf<EntityId>()
      for (sourceChildId in sourceChildrenIds) {
        if (diffLog[sourceChildId] is ChangeEntry.AddEntity<*>) {
          // target particular entity is added in the same transaction.
          val possibleTargetChildId = replaceMap[sourceChildId]
          if (possibleTargetChildId != null) {
            // Entity was already added to the structure
            targetChildrenIds += possibleTargetChildId
          }
          else {
            // target entity isn't added yet. Add a placeholder
            val placeholderId = target.entitiesByType.book(sourceChildId.clazz)
            replaceMap[sourceChildId] = placeholderId
            targetChildrenIds += placeholderId
          }
        }
        else {
          if (target.entityDataById(sourceChildId) != null) {
            targetChildrenIds += sourceChildId
          }
          else if (!connectionId.canRemoveChild()) {
            target.addDiffAndReport("Cannot restore dependency. $connectionId, $sourceChildId", initialStorage, diff, target)
          }
        }
      }
      target.refs.updateChildrenOfParent(connectionId, targetEntityId, targetChildrenIds)
    }
  }

  private fun replaceOperation(change: ChangeEntry.ReplaceEntity<out WorkspaceEntity>) {
    change as ChangeEntry.ReplaceEntity<WorkspaceEntity>

    val sourceEntityId = change.newData.createEntityId()

    val beforeChildren = target.refs.getChildrenRefsOfParentBy(sourceEntityId).flatMap { (key, value) -> value.map { key to it } }
    val beforeParents = target.refs.getParentRefsOfChild(sourceEntityId)

    val targetEntityId = replaceMap.getOrDefault(sourceEntityId, sourceEntityId)
    val newTargetEntityData = change.newData.clone()
    newTargetEntityData.id = targetEntityId.arrayId

    checkPersistentId(change.newData, newTargetEntityData.createEntityId())

    // We don't modify entity that doesn't exist in target version of storage
    val existingTargetEntityData = target.entityDataById(targetEntityId) ?: return

    // Replace entity doesn't modify entitySource
    newTargetEntityData.entitySource = existingTargetEntityData.entitySource

    target.indexes.updateIndices(sourceEntityId, newTargetEntityData, diff)

    val newEntityId = newTargetEntityData.createEntityId()
    val oldPersistentId = target.entityDataById(newEntityId)?.persistentId(target)

    /// Replace entity data. id should not be changed
    target.entitiesByType.replaceById(newTargetEntityData, sourceEntityId.clazz)

    // Restore soft references
    target.updatePersistentIdIndexes(newTargetEntityData.createEntity(target), oldPersistentId, newTargetEntityData)


    val addedChildrenMap = HashMultimap.create<ConnectionId, EntityId>()
    change.newChildren.forEach { addedChildrenMap.put(it.first, it.second) }

    val removedChildrenMap = HashMultimap.create<ConnectionId, EntityId>()
    change.removedChildren.forEach { removedChildrenMap.put(it.first, it.second) }

    replaceRestoreChildren(newEntityId, addedChildrenMap, removedChildrenMap)

    replaceRestoreParents(change, newEntityId)

    WorkspaceEntityStorageBuilderImpl.addReplaceEvent(target, sourceEntityId, beforeChildren, beforeParents, newTargetEntityData)
  }

  private fun replaceRestoreChildren(
    newEntityId: EntityId,
    addedChildrenMap: HashMultimap<ConnectionId, EntityId>,
    removedChildrenMap: HashMultimap<ConnectionId, EntityId>,
  ) {
    val existingChildren = target.refs.getChildrenRefsOfParentBy(newEntityId)

    for ((connectionId, children) in existingChildren) {
      // Take current children....
      val mutableChildren = children.toMutableSet()

      val addedChildrenSet = addedChildrenMap[connectionId] ?: emptySet()
      for (addedChild in addedChildrenSet) {
        if (diffLog[addedChild] is ChangeEntry.AddEntity<*>) {
          val possibleNewChildId = replaceMap[addedChild]
          if (possibleNewChildId != null) {
            mutableChildren += possibleNewChildId
          }
          else {
            val bookedChildId = target.entitiesByType.book(addedChild.clazz)
            replaceMap[addedChild] = bookedChildId
            mutableChildren += bookedChildId
          }
        }
        else {
          if (target.entityDataById(addedChild) != null) {
            mutableChildren += addedChild
          }
        }
      }

      val removedChildrenSet = removedChildrenMap[connectionId] ?: emptySet()
      for (removedChild in removedChildrenSet) {
        // This method may return false if this child is already removed
        mutableChildren.remove(removedChild)
      }

      // .... Update if something changed
      if (children != mutableChildren) {
        target.refs.updateChildrenOfParent(connectionId, newEntityId, mutableChildren)
      }
      addedChildrenMap.removeAll(connectionId)
      removedChildrenMap.removeAll(connectionId)
    }

    // N.B: removedChildrenMap may contain some entities, but this means that these entities was already removed

    // Do we have more children to add? Add them
    for ((connectionId, children) in addedChildrenMap.asMap()) {
      val mutableChildren = children.toMutableSet()

      for (child in children) {
        if (diffLog[child] is ChangeEntry.AddEntity<*>) {
          val possibleNewChildId = replaceMap[child]
          if (possibleNewChildId != null) {
            mutableChildren += possibleNewChildId
          }
          else {
            val bookedChildId = target.entitiesByType.book(child.clazz)
            replaceMap[child] = bookedChildId
            mutableChildren += bookedChildId
          }
        }
        else {
          if (target.entityDataById(child) != null) {
            mutableChildren += child
          }
        }
      }

      target.refs.updateChildrenOfParent(connectionId, newEntityId, mutableChildren)
    }
  }

  private fun replaceRestoreParents(
    change: ChangeEntry.ReplaceEntity<out WorkspaceEntity>,
    newEntityId: EntityId,
  ) {
    val updatedModifiedParents = change.modifiedParents.mapValues { it.value }

    val modifiedParentsMap = updatedModifiedParents.toMutableMap()
    val existingParents = target.refs.getParentRefsOfChild(newEntityId)
    for ((connectionId, existingParent) in existingParents) {
      if (connectionId in modifiedParentsMap) {
        val newParent = modifiedParentsMap.getValue(connectionId)
        if (newParent == null) {
          // target child doesn't have a parent anymore
          if (!connectionId.canRemoveParent()) target.addDiffAndReport("Cannot restore some dependencies; $connectionId",
                                                                       initialStorage,
                                                                       diff, target)
          else target.refs.removeParentToChildRef(connectionId, existingParent, newEntityId)
        }
        else {
          if (diffLog[newParent] is ChangeEntry.AddEntity<*>) {
            var possibleNewParent = replaceMap[newParent]
            if (possibleNewParent == null) {
              possibleNewParent = target.entitiesByType.book(newParent.clazz)
              replaceMap[newParent] = possibleNewParent
            }
            target.refs.updateParentOfChild(connectionId, newEntityId, possibleNewParent)
          }
          else {
            if (target.entityDataById(newParent) != null) {
              target.refs.updateParentOfChild(connectionId, newEntityId, newParent)
            }
            else {
              if (!connectionId.canRemoveParent()) target.addDiffAndReport("Cannot restore some dependencies; $connectionId",
                                                                           initialStorage,
                                                                           diff, target)
              target.refs.removeParentToChildRef(connectionId, existingParent, newEntityId)
            }
          }
        }
        modifiedParentsMap.remove(connectionId)
      }
    }

    // Any new parents? Add them
    for ((connectionId, parentId) in modifiedParentsMap) {
      if (parentId == null) continue
      if (diffLog[parentId] is ChangeEntry.AddEntity<*>) {
        var possibleNewParent = replaceMap[parentId]
        if (possibleNewParent == null) {
          possibleNewParent = target.entitiesByType.book(parentId.clazz)
          replaceMap[parentId] = possibleNewParent
        }
        target.refs.updateParentOfChild(connectionId, newEntityId, possibleNewParent)
      }
      else {
        if (target.entityDataById(parentId) != null) {
          target.refs.updateParentOfChild(connectionId, newEntityId, parentId)
        }
      }
    }
  }

  private fun checkPersistentId(entityData: WorkspaceEntityData<WorkspaceEntity>, newEntityId: EntityId?) {
    val newPersistentId = entityData.persistentId(target)
    if (newPersistentId != null) {
      val existingIds = target.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
      if (existingIds != null && existingIds.isNotEmpty()) {
        val existingIdCheck = if (newEntityId != null) existingIds.single() != newEntityId else true
        if (existingIdCheck) {
          // target persistent id exists already.
          val existingEntity = target.entityDataByIdOrDie(existingIds.single()).createEntity(target)
          target.removeEntity(existingEntity)
          target.addDiffAndReport(
            """
                        addDiff: persistent id already exists. Removing old entity
                        Persistent id: $newPersistentId
                        
                        Existing entity data: $existingEntity
                        New entity data: $entityData
                        """.trimIndent(), initialStorage, diff, target)
        }
      }
    }
  }

  // For serializing current model during the debug process
  @Suppress("unused")
  private fun serialize(path: String) {
    val folder = File(path)
    target.serializeTo(folder.resolve("Instant_Save_Target").outputStream())
    diff.serializeTo(folder.resolve("Instant_Save_Source").outputStream())
    diff.serializeDiff(folder.resolve("Instant_Save_Diff").outputStream())
  }

  companion object {
    private val LOG = logger<AddDiffOperation>()
  }
}