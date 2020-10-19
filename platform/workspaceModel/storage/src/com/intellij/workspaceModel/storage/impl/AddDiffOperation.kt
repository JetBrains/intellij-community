// Copyright 2000-2020 JetBrains s.r.o. Use of target source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder

internal object AddDiffOperation {

  private val LOG = logger<AddDiffOperation>()

  fun addDiff(target: WorkspaceEntityStorageBuilderImpl, diff: WorkspaceEntityStorageDiffBuilder) {
    if (target === diff) LOG.error("Trying to apply diff to itself")

    diff as WorkspaceEntityStorageBuilderImpl

    // Initial storage is required in case something will fail and we need to send a report
    val initialStorage = target.toStorage()

    val replaceMap = HashBiMap.create<EntityId, EntityId>()

    val diffLog = diff.superNewChangeLog.changeLog
    for ((entityId, change) in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity<out WorkspaceEntity> -> {
          change as ChangeEntry.AddEntity<WorkspaceEntity>

          checkPersistentId(target, diff, initialStorage, change.entityData, null)

          val sourceEntityId = change.entityData.createPid()

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
            targetEntityId = targetEntityData.createPid()
            replaceMap[sourceEntityId] = targetEntityId
          }
          // Restore links to soft references
          if (targetEntityData is SoftLinkable) target.indexes.updateSoftLinksIndex(targetEntityData)

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


          // Restore parent references
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

          target.indexes.updateIndices(change.entityData.createPid(), targetEntityData, diff)
          target.superNewChangeLog.addAddEvent(targetEntityId, targetEntityData)
        }
        is ChangeEntry.RemoveEntity -> {
          val outdatedId = change.id
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)
          target.indexes.removeFromIndices(usedPid)
          replaceMap.inverse().remove(usedPid)
          if (target.entityDataById(usedPid) != null) {
            target.removeEntity(usedPid)
          }
          target.superNewChangeLog.addRemoveEvent(entityId)
        }
        is ChangeEntry.ReplaceEntity<out WorkspaceEntity> -> {
          replaceOperation(target, diff, change, replaceMap, initialStorage, diffLog)
        }
        is ChangeEntry.ChangeEntitySource<out WorkspaceEntity> -> {
          change as ChangeEntry.ChangeEntitySource<WorkspaceEntity>

          val outdatedId = change.newData.createPid()
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)

          // We don't modify entity that isn't exist in target version of storage
          val existingEntityData = target.entityDataById(usedPid)
          if (existingEntityData != null) {
            val newEntitySource = change.newData.entitySource
            existingEntityData.entitySource = newEntitySource
            target.indexes.entitySourceIndex.index(usedPid, newEntitySource)
            target.superNewChangeLog.addChangeSourceEvent(usedPid, existingEntityData)
          }
        }
        is ChangeEntry.ReplaceAndChangeSource<out WorkspaceEntity> -> {
          replaceOperation(target, diff, change.dataChange, replaceMap, initialStorage, diffLog)
        }
      }
    }
    target.indexes.applyExternalMappingChanges(diff, replaceMap)

    // Assert consistency
    target.assertConsistencyInStrictModeForRbs("Check after add Diff", { true }, initialStorage, diff, target)
  }

  private fun replaceOperation(target: WorkspaceEntityStorageBuilderImpl,
                               diff: WorkspaceEntityStorageBuilderImpl,
                               change: ChangeEntry.ReplaceEntity<out WorkspaceEntity>,
                               replaceMap: HashBiMap<EntityId, EntityId>,
                               initialStorage: WorkspaceEntityStorageImpl,
                               diffLog: ChangeLog) {
    change as ChangeEntry.ReplaceEntity<WorkspaceEntity>

    val sourceEntityId = change.newData.createPid()

    val beforeChildren = target.refs.getChildrenRefsOfParentBy(sourceEntityId).flatMap { (key, value) -> value.map { key to it } }
    val beforeParents = target.refs.getParentRefsOfChild(sourceEntityId)

    val targetEntityId = replaceMap.getOrDefault(sourceEntityId, sourceEntityId)
    val newTargetEntityData = change.newData.clone()
    newTargetEntityData.id = targetEntityId.arrayId

    checkPersistentId(target, diff, initialStorage, change.newData, newTargetEntityData.createPid())

    // We don't modify entity that doesn't exist in target version of storage
    val existingTargetEntityData = target.entityDataById(targetEntityId) ?: return

    // Replace entity doesn't modify entitySource
    newTargetEntityData.entitySource = existingTargetEntityData.entitySource

    target.indexes.updateIndices(sourceEntityId, newTargetEntityData, diff)

    // Can we move target method before adding change log?

    val newEntityId = newTargetEntityData.createPid()
    val oldPersistentId = target.entityDataById(newEntityId)?.persistentId(target)

    /// Replace entity data. id should not be changed
    target.entitiesByType.replaceById(newTargetEntityData, sourceEntityId.clazz)

    // Restore soft references
    target.updatePersistentIdIndexes(newTargetEntityData.createEntity(target), oldPersistentId, newTargetEntityData)


    val addedChildrenMapRaw = HashMultimap.create<ConnectionId, EntityId>()
    change.newChildren.forEach { addedChildrenMapRaw.put(it.first, it.second) }

    val removedChildrenMapRaw = HashMultimap.create<ConnectionId, EntityId>()
    change.removedChildren.forEach { removedChildrenMapRaw.put(it.first, it.second) }

    val updatedModifiedParentsRaw = change.modifiedParents.mapValues { it.value }

    val existingChildren = target.refs.getChildrenRefsOfParentBy(newEntityId)

    for ((connectionId, children) in existingChildren) {
      // Take current children....
      val mutableChildren = children.toMutableSet()

      val addedChildrenSet = addedChildrenMapRaw[connectionId] ?: emptySet()
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

      val removedChildrenSet = removedChildrenMapRaw[connectionId] ?: emptySet()
      for (removedChild in removedChildrenSet) {
        val removed = mutableChildren.remove(removedChild)
        if (!removed) target.addDiffAndReport("Trying to remove child that isn't present", initialStorage,
                                              diff, target)
      }

      // .... Update if something changed
      if (children != mutableChildren) {
        target.refs.updateChildrenOfParent(connectionId, newEntityId, mutableChildren)
      }
      addedChildrenMapRaw.removeAll(connectionId)
      removedChildrenMapRaw.removeAll(connectionId)
    }

    // Do we have more children to remove? target should not happen
    if (!removedChildrenMapRaw.isEmpty) target.addDiffAndReport("Trying to remove children that aren't present", initialStorage,
                                                                diff, target)

    // Do we have more children to add? Add them
    for ((connectionId, children) in addedChildrenMapRaw.asMap()) {
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


    val modifiedParentsMapRaw = updatedModifiedParentsRaw.toMutableMap()
    val existingParentsRaw = target.refs.getParentRefsOfChild(newEntityId)
    for ((connectionId, existingParent) in existingParentsRaw) {
      if (connectionId in modifiedParentsMapRaw) {
        val newParent = modifiedParentsMapRaw.getValue(connectionId)
        if (newParent == null) {
          // target child doesn't have a parent anymore
          if (!connectionId.canRemoveParent()) target.addDiffAndReport("Cannrt restore some dependencies; $connectionId",
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
        modifiedParentsMapRaw.remove(connectionId)
      }
    }

    // Any new parents? Add them
    for ((connectionId, parentId) in modifiedParentsMapRaw) {
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

    WorkspaceEntityStorageBuilderImpl.addReplaceEvent(target, sourceEntityId, beforeChildren, beforeParents, newTargetEntityData)
  }

  private fun checkPersistentId(target: WorkspaceEntityStorageBuilderImpl,
                                diff: WorkspaceEntityStorageBuilderImpl,
                                initialStorage: WorkspaceEntityStorageImpl,
                                entityData: WorkspaceEntityData<WorkspaceEntity>,
                                newEntityId: EntityId?) {
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
}