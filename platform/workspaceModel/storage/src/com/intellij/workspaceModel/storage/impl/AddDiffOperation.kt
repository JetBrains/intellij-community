// Copyright 2000-2020 JetBrains s.r.o. Use of target source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.workspaceModel.storage.*
import java.io.File

internal class AddDiffOperation(val target: WorkspaceEntityStorageBuilderImpl, val diff: WorkspaceEntityStorageBuilderImpl) {

  private val replaceMap = HashBiMap.create<NotThisEntityId, ThisEntityId>()
  private val diffLog = diff.changeLog.changeLog

  // Initial storage is required in case something will fail and we need to send a report
  private val initialStorage = if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) target.toStorage() else null

  fun addDiff() {
    if (target === diff) LOG.error("Trying to apply diff to itself")

    if (LOG.isTraceEnabled) {
      target.assertConsistency()
      diff.assertConsistency()
      LOG.trace("Before starting addDiff no consistency issues were found")
    }

    for ((_, change) in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity -> {
          LOG.trace { "addDiff: newEntity" }

          checkPersistentId(change.entityData, null)

          val sourceEntityId = change.entityData.createEntityId().notThis()

          // Adding new entity
          val targetEntityData: WorkspaceEntityData<out WorkspaceEntity>
          val targetEntityId: ThisEntityId
          val idFromReplaceMap = replaceMap[sourceEntityId]
          if (idFromReplaceMap != null) {
            // Okay, we need to add the entity at the particular id
            targetEntityData = target.entitiesByType.cloneAndAddAt(change.entityData, idFromReplaceMap.id)
            targetEntityId = idFromReplaceMap
          }
          else {
            // Add new entity to store (without references)
            targetEntityData = target.entitiesByType.cloneAndAdd(change.entityData, change.clazz)
            targetEntityId = targetEntityData.createEntityId().asThis()
            replaceMap[sourceEntityId] = targetEntityId
          }
          // Restore links to soft references
          if (targetEntityData is SoftLinkable) target.indexes.updateSoftLinksIndex(targetEntityData)

          addRestoreChildren(sourceEntityId, targetEntityId)

          // Restore parent references
          addRestoreParents(sourceEntityId, targetEntityId)

          target.indexes.updateIndices(change.entityData.createEntityId(), targetEntityData, diff)
          target.changeLog.addAddEvent(targetEntityId.id, targetEntityData)
        }
        is ChangeEntry.RemoveEntity -> {
          LOG.trace { "addDiff: remove entity. ${change.id}" }
          val sourceEntityId = change.id.asThis()

          // This sourceEntityId is definitely not presented in replaceMap as a key, so we can just remove this entity from target
          //   with this id. But there is a case when some different entity from source builder will get this id if there was a gup before.
          //   So we should check if entity at this id was added in this transaction. If replaceMap has a value with this entity id
          //   this means that this entity was added in this transaction and there was a gup before and we should not remove anything.
          if (!replaceMap.containsValue(sourceEntityId)) {
            target.indexes.entityRemoved(sourceEntityId.id)
            if (target.entityDataById(sourceEntityId.id) != null) {
              target.removeEntity(sourceEntityId.id)
            }
          }
        }
        is ChangeEntry.ReplaceEntity -> {
          LOG.trace { "addDiff: replace entity" }
          replaceOperation(change)
        }
        is ChangeEntry.ChangeEntitySource -> {
          LOG.trace { "addDiff: change entity source" }
          replaceSourceOperation(change.newData, change.originalSource)
        }
        is ChangeEntry.ReplaceAndChangeSource -> {
          LOG.trace { "addDiff: replace and change source" }
          replaceOperation(change.dataChange)
          replaceSourceOperation(change.sourceChange.newData, change.sourceChange.originalSource)
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

  private fun replaceSourceOperation(data: WorkspaceEntityData<out WorkspaceEntity>, originalSource: EntitySource) {
    val outdatedId = data.createEntityId().notThis()
    val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId.id.asThis())

    // We don't modify entity that isn't exist in target version of storage
    val existingEntityData = target.entityDataById(usedPid.id)
    if (existingEntityData != null) {
      val newEntitySource = data.entitySource
      existingEntityData.entitySource = newEntitySource
      target.indexes.entitySourceIndex.index(usedPid.id, newEntitySource)
      target.changeLog.addChangeSourceEvent(usedPid.id, existingEntityData, originalSource)
    }
  }

  private fun addRestoreParents(sourceEntityId: NotThisEntityId, targetEntityId: ThisEntityId) {
    val allParents = diff.refs.getParentRefsOfChild(sourceEntityId.id.asChild())
    for ((connectionId, sourceParentId) in allParents) {
      val targetParentId: ThisEntityId? = if (diffLog[sourceParentId.id] is ChangeEntry.AddEntity) {
        replaceMap[sourceParentId.id.notThis()] ?: run {
          // target entity isn't added to the current builder yet. Add a placeholder
          val placeholderId = target.entitiesByType.book(sourceParentId.id.clazz).asThis()
          replaceMap[sourceParentId.id.notThis()] = placeholderId
          placeholderId
        }
      }
      else {
        if (target.entityDataById(sourceParentId.id) != null) sourceParentId.id.asThis()
        else {
          if (!connectionId.canRemoveParent()) {
            target.addDiffAndReport("Cannot restore dependency. $connectionId, $sourceParentId.id", initialStorage, diff)
          }
          null
        }
      }
      if (targetParentId != null) {

        // For one-to-one connections it's necessary to remove the obsolete children to avoid "entity leaks" and the state of broken store
        if (connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ONE || connectionId.connectionType == ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE) {
          val obsoleteChild = target.extractOneToOneChild<WorkspaceEntityBase>(connectionId, targetParentId.id)
          if (obsoleteChild != null && obsoleteChild.id != targetEntityId.id) {
            target.removeEntity(obsoleteChild)
          }
        }

        target.refs.updateParentOfChild(connectionId, targetEntityId.id.asChild(), targetParentId.id.asParent())
      }
    }
  }

  private fun addRestoreChildren(sourceEntityId: NotThisEntityId, targetEntityId: ThisEntityId) {
    // Restore children references
    val allSourceChildren = diff.refs.getChildrenRefsOfParentBy(sourceEntityId.id.asParent())
    for ((connectionId, sourceChildrenIds) in allSourceChildren) {
      val targetChildrenIds = mutableListOf<ChildEntityId>()
      for (sourceChildId in sourceChildrenIds) {
        if (diffLog[sourceChildId.id] is ChangeEntry.AddEntity) {
          // target particular entity is added in the same transaction.
          val possibleTargetChildId = replaceMap[sourceChildId.id.notThis()]
          if (possibleTargetChildId != null) {
            // Entity was already added to the structure
            targetChildrenIds += possibleTargetChildId.id.asChild()
          }
          else {
            // target entity isn't added yet. Add a placeholder
            val placeholderId = target.entitiesByType.book(sourceChildId.id.clazz).asThis()
            replaceMap[sourceChildId.id.notThis()] = placeholderId
            targetChildrenIds += placeholderId.id.asChild()
          }
        }
        else {
          if (target.entityDataById(sourceChildId.id) != null) {
            targetChildrenIds += sourceChildId
          }
        }
      }
      target.refs.updateChildrenOfParent(connectionId, targetEntityId.id.asParent(), targetChildrenIds)
    }
  }

  private fun replaceOperation(change: ChangeEntry.ReplaceEntity) {
    val sourceEntityId = change.newData.createEntityId().notThis()

    val beforeChildren = target.refs.getChildrenRefsOfParentBy(sourceEntityId.id.asParent()).flatMap { (key, value) -> value.map { key to it } }
    val beforeParents = target.refs.getParentRefsOfChild(sourceEntityId.id.asChild())

    val targetEntityId = replaceMap.getOrDefault(sourceEntityId, sourceEntityId.id.asThis())
    val newTargetEntityData = change.newData.clone()
    newTargetEntityData.id = targetEntityId.id.arrayId

    checkPersistentId(change.newData, newTargetEntityData.createEntityId())

    // We don't modify entity that doesn't exist in target version of storage
    val existingTargetEntityData = target.entityDataById(targetEntityId.id) ?: return
    val originalEntityData = target.getOriginalEntityData(targetEntityId.id)
    val originalParents = target.getOriginalParents(targetEntityId.id.asChild())

    // Replace entity doesn't modify entitySource
    newTargetEntityData.entitySource = existingTargetEntityData.entitySource

    target.indexes.updateIndices(sourceEntityId.id, newTargetEntityData, diff)

    val newEntityId = newTargetEntityData.createEntityId()
    val oldPersistentId = target.entityDataById(newEntityId)?.persistentId()

    /// Replace entity data. id should not be changed
    target.entitiesByType.replaceById(newTargetEntityData, sourceEntityId.id.clazz)

    // Restore soft references
    target.indexes.updatePersistentIdIndexes(target, newTargetEntityData.createEntity(target), oldPersistentId, newTargetEntityData)


    val addedChildrenMap = HashMultimap.create<ConnectionId, ChildEntityId>()
    change.newChildren.forEach { addedChildrenMap.put(it.first, it.second) }

    val removedChildrenMap = HashMultimap.create<ConnectionId, ChildEntityId>()
    change.removedChildren.forEach { removedChildrenMap.put(it.first, it.second) }

    replaceRestoreChildren(sourceEntityId.id.asParent(), newEntityId.asParent(), addedChildrenMap, removedChildrenMap)

    replaceRestoreParents(change, newEntityId)

    WorkspaceEntityStorageBuilderImpl.addReplaceEvent(target, sourceEntityId.id, beforeChildren, beforeParents, newTargetEntityData,
      originalEntityData, originalParents)
  }

  private fun replaceRestoreChildren(
    sourceEntityId: ParentEntityId,
    newEntityId: ParentEntityId,
    addedChildrenMap: HashMultimap<ConnectionId, ChildEntityId>,
    removedChildrenMap: HashMultimap<ConnectionId, ChildEntityId>,
  ) {
    val existingChildren = target.refs.getChildrenRefsOfParentBy(newEntityId)

    for ((connectionId, children) in existingChildren) {
      if (connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
        val sourceChildren = this.diff.refs.getChildrenRefsOfParentBy(sourceEntityId)[connectionId] ?: emptyList()
        val updatedChildren = sourceChildren.mapNotNull { childrenMapper(it) }
        if (updatedChildren != children) {
          target.refs.updateChildrenOfParent(connectionId, newEntityId, updatedChildren)
        }
      }
      else {
        // Take current children....
        val mutableChildren = children.toMutableList()

        val addedChildrenSet = addedChildrenMap[connectionId] ?: emptySet()
        val updatedAddedChildren = addedChildrenSet.mapNotNull { childrenMapper(it) }
        mutableChildren.addAll(updatedAddedChildren)

        val removedChildrenSet = removedChildrenMap[connectionId] ?: emptySet()
        for (removedChild in removedChildrenSet) {
          // This method may return false if this child is already removed
          mutableChildren.remove(removedChild)
        }

        // .... Update if something changed
        if (children != mutableChildren) {
          target.refs.updateChildrenOfParent(connectionId, newEntityId, mutableChildren)
        }
      }
      addedChildrenMap.removeAll(connectionId)
      removedChildrenMap.removeAll(connectionId)
    }

    // N.B: removedChildrenMap may contain some entities, but this means that these entities was already removed

    // Do we have more children to add? Add them
    for ((connectionId, children) in addedChildrenMap.asMap()) {
      val mutableChildren = children.toMutableList()

      val updatedChildren = children.mapNotNull { childrenMapper(it) }
      mutableChildren.addAll(updatedChildren)

      target.refs.updateChildrenOfParent(connectionId, newEntityId, mutableChildren)
    }
  }

  private fun childrenMapper(child: ChildEntityId): ChildEntityId? {
    return if (diffLog[child.id] is ChangeEntry.AddEntity) {
      val possibleNewChildId = replaceMap[child.id.notThis()]
      if (possibleNewChildId != null) {
        possibleNewChildId.id.asChild()
      }
      else {
        val bookedChildId = target.entitiesByType.book(child.id.clazz)
        replaceMap[child.id.notThis()] = bookedChildId.asThis()
        bookedChildId.asChild()
      }
    }
    else {
      if (target.entityDataById(child.id) != null) {
        child
      }
      else null
    }
  }

  private fun replaceRestoreParents(
    change: ChangeEntry.ReplaceEntity,
    newEntityId: EntityId,
  ) {
    val updatedModifiedParents = change.modifiedParents.mapValues { it.value }

    val modifiedParentsMap = updatedModifiedParents.toMutableMap()
    val newChildEntityId = newEntityId.asChild()
    val existingParents = target.refs.getParentRefsOfChild(newChildEntityId)
    for ((connectionId, existingParent) in existingParents) {
      if (connectionId in modifiedParentsMap) {
        val newParent = modifiedParentsMap.getValue(connectionId)
        if (newParent == null) {
          // target child doesn't have a parent anymore
          if (!connectionId.canRemoveParent()) target.addDiffAndReport("Cannot restore some dependencies; $connectionId",
                                                                       initialStorage, diff)
          else target.refs.removeParentToChildRef(connectionId, existingParent, newChildEntityId)
        }
        else {
          if (diffLog[newParent.id] is ChangeEntry.AddEntity) {
            var possibleNewParent = replaceMap[newParent.id.notThis()]
            if (possibleNewParent == null) {
              possibleNewParent = target.entitiesByType.book(newParent.id.clazz).asThis()
              replaceMap[newParent.id.notThis()] = possibleNewParent
            }
            target.refs.updateParentOfChild(connectionId, newEntityId.asChild(), possibleNewParent.id.asParent())
          }
          else {
            if (target.entityDataById(newParent.id) != null) {
              target.refs.updateParentOfChild(connectionId, newEntityId.asChild(), newParent)
            }
            else {
              if (!connectionId.canRemoveParent()) target.addDiffAndReport("Cannot restore some dependencies; $connectionId",
                                                                           initialStorage, diff)
              target.refs.removeParentToChildRef(connectionId, existingParent, newChildEntityId)
            }
          }
        }
        modifiedParentsMap.remove(connectionId)
      }
    }

    // Any new parents? Add them
    for ((connectionId, parentId) in modifiedParentsMap) {
      if (parentId == null) continue
      if (diffLog[parentId.id] is ChangeEntry.AddEntity) {
        var possibleNewParent = replaceMap[parentId.id.notThis()]
        if (possibleNewParent == null) {
          possibleNewParent = target.entitiesByType.book(parentId.id.clazz).asThis()
          replaceMap[parentId.id.notThis()] = possibleNewParent
        }
        target.refs.updateParentOfChild(connectionId, newEntityId.asChild(), possibleNewParent.id.asParent())
      }
      else {
        if (target.entityDataById(parentId.id) != null) {
          target.refs.updateParentOfChild(connectionId, newEntityId.asChild(), parentId)
        }
      }
    }
  }

  private fun checkPersistentId(entityData: WorkspaceEntityData<out WorkspaceEntity>, newEntityId: EntityId?) {
    val newPersistentId = entityData.persistentId()
    if (newPersistentId != null) {
      val existingIds = target.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
      if (existingIds != null) {
        val existingIdCheck = if (newEntityId != null) existingIds != newEntityId else true
        if (existingIdCheck) {
          // target persistent id exists already.
          val existingEntityData = target.entityDataByIdOrDie(existingIds)
          LOG.debug("Removing existing entity... $existingIds")
          target.removeEntity(existingEntityData.createEntity(target))
          target.addDiffAndReport(
            """
                        Persistent ID already exists. Removing old entity
                        Persistent ID: $newPersistentId
                        Existing entity data: $existingEntityData
                        New entity data: $entityData
                        """.trimIndent(), initialStorage, diff)
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