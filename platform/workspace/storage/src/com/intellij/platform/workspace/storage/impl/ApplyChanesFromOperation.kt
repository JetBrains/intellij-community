// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.exceptions.ApplyChangesFromException
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import java.util.*

@OptIn(EntityStorageInstrumentationApi::class)
internal class ApplyChanesFromOperation(val target: MutableEntityStorageImpl, val diff: MutableEntityStorageImpl) {

  internal val replaceMap = HashBiMap.create<NotThisEntityId, ThisEntityId>()
  private val diffLog = diff.changeLog.changeLog

  var shaker = -1L

  private fun ChangeLog.shake(): Collection<MutableMap.MutableEntry<EntityId, ChangeEntry>> {
    return if (shaker != -1L && this.entries.size > 1) {
      this.entries.shuffled(Random(shaker))
    } else {
      this.entries
    }
  }

  fun applyChangesFrom() {
    if (target === diff) LOG.error("Trying to apply diff to itself")

    if (LOG.isTraceEnabled) {
      target.assertConsistency()
      diff.assertConsistency()
      LOG.trace("Before starting applyChangesFrom no consistency issues were found")
    }

    for ((entityId, change) in diffLog.shake()) {
      when (change) {
        is ChangeEntry.AddEntity -> {
          LOG.trace { "applyChangesFrom: newEntity" }

          checkSymbolicId(change.entityData, null)

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

          // Keep adding "add" event before updating children and parents. Otherwise, we'll get a weird behaviour when we try to add
          //   "add" event on top of "modify" event that was generated while adding references.
          target.changeLog.addAddEvent(targetEntityId.id, targetEntityData)

          addRestoreChildren(sourceEntityId, targetEntityId)

          // Restore parent references
          addRestoreParents(sourceEntityId, targetEntityId)

          target.indexes.updateIndices(change.entityData.createEntityId(), targetEntityData, diff)
        }
        is ChangeEntry.RemoveEntity -> {
          LOG.trace { "applyChangesFrom: remove entity. ${change.id}" }
          val sourceEntityId = change.id.asThis()

          // This sourceEntityId is definitely not presented in replaceMap as a key, so we can just remove this entity from target
          //   with this id. But there is a case when some different entity from source builder will get this id if there was a gup before.
          //   So we should check if entity at this id was added in this transaction. If replaceMap has a value with this entity id
          //   this means that this entity was added in this transaction and there was a gup before and we should not remove anything.
          if (!replaceMap.containsValue(sourceEntityId)) {
            target.indexes.entityRemoved(sourceEntityId.id)
            if (target.entityDataById(sourceEntityId.id) != null) {
              // As we generate a remove event for each cascade removed entities, we can remove entities one by one
              target.removeSingleEntity(sourceEntityId.id)
            }
          }
        }
        is ChangeEntry.ReplaceEntity -> {
          LOG.trace { "applyChangesFrom: replace entity" }
          replaceOperation(entityId, change)
        }
      }
    }
    target.indexes.applyExternalMappingChanges(diff, replaceMap, target)

    // Assert consistency
    if (!target.brokenConsistency && !diff.brokenConsistency) {
      target.assertConsistencyInStrictMode("Check after add Diff")
    }
    else {
      target.brokenConsistency = true
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
            val message = "Cannot restore dependency. $connectionId, $sourceParentId.id"
            LOG.error(message, ApplyChangesFromException(message))
          }
          null
        }
      }
      if (targetParentId != null) {

        // For one-to-one connections it's necessary to remove the obsolete children to avoid "entity leaks" and the state of broken store
        if (connectionId.isOneToOne && !connectionId.isParentNullable) {
          val obsoleteChild = target.extractOneToOneChild<WorkspaceEntityBase>(connectionId, targetParentId.id)
          if (obsoleteChild != null && obsoleteChild.id != targetEntityId.id) {
            target.removeEntity(obsoleteChild)
          }
        }

        val operations = target.refs.replaceParentOfChild(connectionId, targetEntityId.id.asChild(), targetParentId.id.asParent())
        target.createReplaceEventsForUpdates(operations, connectionId)
      }
    }
  }

  /**
   * Restore children references and add events for that
   */
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
      val modifications = target.refs.replaceChildrenOfParent(connectionId, targetEntityId.id.asParent(), targetChildrenIds)
      target.createReplaceEventsForUpdates(modifications, connectionId)
    }
  }

  private fun replaceOperation(entityId: EntityId, change: ChangeEntry.ReplaceEntity) {
    val sourceEntityId = entityId.notThis()

    val targetEntityId = replaceMap.getOrDefault(sourceEntityId, sourceEntityId.id.asThis())

    // We don't modify entity that doesn't exist in target version of storage
    val existingTargetEntityData = target.entityDataById(targetEntityId.id) ?: return
    val newTargetEntityData = change.data?.newData?.clone() ?: existingTargetEntityData.clone()
    newTargetEntityData.id = targetEntityId.id.arrayId

    if (change.data != null) {
      checkSymbolicId(change.data.newData, newTargetEntityData.createEntityId())
    }

    val originalEntityData = target.getOriginalEntityData(targetEntityId.id)

    target.indexes.updateIndices(sourceEntityId.id, newTargetEntityData, diff)

    val newEntityId = newTargetEntityData.createEntityId()
    val oldSymbolicId = target.entityDataById(newEntityId)?.symbolicId()

    /// Replace entity data. id should not be changed
    target.entitiesByType.replaceById(newTargetEntityData, sourceEntityId.id.clazz)

    // Restore soft references
    target.indexes.updateSymbolicIdIndexes(target, newTargetEntityData.createEntity(target), oldSymbolicId, newTargetEntityData)

    val addedChildrenMap = HashMap<ConnectionId, MutableList<ChildEntityId>>()
    change.references?.newChildren?.forEach { addedChildrenMap.getOrPut(it.first) { ArrayList() }.add(it.second) }

    val removedChildrenMap = HashMap<ConnectionId, MutableList<ChildEntityId>>()
    change.references?.removedChildren?.forEach { removedChildrenMap.getOrPut(it.first) { ArrayList() }.add(it.second) }

    replaceRestoreChildren(sourceEntityId.id.asParent(), newEntityId.asParent(), addedChildrenMap, removedChildrenMap)

    replaceRestoreParents(change, newEntityId)

    target.changeLog.addReplaceDataEvent(sourceEntityId.id, newTargetEntityData, originalEntityData, true)
  }

  private fun replaceRestoreChildren(
    sourceEntityId: ParentEntityId,
    newEntityId: ParentEntityId,
    addedChildrenMap: MutableMap<ConnectionId, MutableList<ChildEntityId>>,
    removedChildrenMap: MutableMap<ConnectionId, MutableList<ChildEntityId>>,
  ) {
    // Children from target store with connectionIds of affected references
    val existingChildrenOfAffectedIds: MutableMap<ConnectionId, List<ChildEntityId>> = HashMap()
    addedChildrenMap.keys.forEach { connectionId ->
      val existingChildren = target.refs.getChildrenByParent(connectionId, newEntityId)
      existingChildrenOfAffectedIds[connectionId] = existingChildren
    }
    removedChildrenMap.keys.forEach { connectionId ->
      val existingChildren = target.refs.getChildrenByParent(connectionId, newEntityId)
      existingChildrenOfAffectedIds[connectionId] = existingChildren
    }

    for ((connectionId, children) in existingChildrenOfAffectedIds) {
      if (connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
        val sourceChildren = this.diff.refs.getChildrenRefsOfParentBy(sourceEntityId)[connectionId] ?: emptyList()
        val updatedChildren = sourceChildren.mapNotNull { childrenMapper(it) }
        if (updatedChildren != children) {
          val modifications = target.refs.replaceChildrenOfParent(connectionId, newEntityId, updatedChildren)
          target.createReplaceEventsForUpdates(modifications, connectionId)
        }
      }
      else {
        // Take current children....
        // We use linked hash set because we'd like to preserve the order of children, but at the same time
        //   we'd like to quickly clear the list from duplicates
        val mutableChildren = LinkedHashSet<ChildEntityId>().also { it.addAll(children) }

        val addedChildren = addedChildrenMap[connectionId] ?: emptyList()
        val updatedAddedChildren = addedChildren.mapNotNull { childrenMapper(it) }
        if (connectionId.isOneToOne && updatedAddedChildren.isNotEmpty()) {
          mutableChildren.clear()
          mutableChildren.add(updatedAddedChildren.single())
        }
        else {
          // Firstly, we remove the children references that already exist
          //  This is needed to preserve the ordering of children that exist in diff builder (practically, order in updatedAddedChildren)
          mutableChildren.removeAll(updatedAddedChildren)
          mutableChildren.addAll(updatedAddedChildren)
        }

        val removedChildren = removedChildrenMap[connectionId] ?: emptyList()
        for (removedChild in removedChildren) {
          // This method may return false if this child is already removed
          mutableChildren.remove(childrenMapper(removedChild))
        }

        // .... Update if something changed
        if (children.toSet() != mutableChildren) {
          val modifications = target.refs.replaceChildrenOfParent(connectionId, newEntityId, mutableChildren)
          target.createReplaceEventsForUpdates(modifications, connectionId)
        }
      }
      addedChildrenMap.remove(connectionId)
      removedChildrenMap.remove(connectionId)
    }

    // N.B: removedChildrenMap may contain some entities, but this means that these entities was already removed

    // Do we have more children to add? Add them
    for ((connectionId, children) in addedChildrenMap) {
      val updatedChildren = children.mapNotNull { childrenMapper(it) }

      val modifications = target.refs.replaceChildrenOfParent(connectionId, newEntityId, updatedChildren)
      target.createReplaceEventsForUpdates(modifications, connectionId)
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
      if (replaceMap.inverse()[child.id.asThis()] == null && target.entityDataById(child.id) != null) {
        child
      }
      else null
    }
  }

  private fun replaceRestoreParents(
    change: ChangeEntry.ReplaceEntity,
    newEntityId: EntityId,
  ) {
    val removedParents = change.references?.removedParents?.mapValues { it.value } ?: emptyMap()
    val newParents = change.references?.newParents?.mapValues { it.value } ?: emptyMap()
    val modifiableNewParents = newParents.toMutableMap()

    val newChildEntityId = newEntityId.asChild()
    val existingParents = target.refs.getParentRefsOfChild(newChildEntityId)
    for ((connectionId, existingParent) in existingParents) {
      if (connectionId in removedParents) {
        // target child doesn't have a parent anymore
        if (!connectionId.canRemoveParent() && connectionId !in newParents) {
          val message = "Cannot restore some dependencies; $connectionId"
          LOG.error(message, ApplyChangesFromException(message))
        }
        else {
          val modifications = target.refs.removeParentToChildRef(connectionId, existingParent, newChildEntityId)
          target.createReplaceEventsForUpdates(modifications, connectionId)
        }
      }
      if (connectionId in newParents) {
        val newParent = newParents[connectionId]!!
        if (diffLog[newParent.id] is ChangeEntry.AddEntity) {
          var possibleNewParent = replaceMap[newParent.id.notThis()]
          if (possibleNewParent == null) {
            possibleNewParent = target.entitiesByType.book(newParent.id.clazz).asThis()
            replaceMap[newParent.id.notThis()] = possibleNewParent
          }
          val modifications = target.refs.replaceParentOfChild(connectionId, newEntityId.asChild(), possibleNewParent.id.asParent())
          target.createReplaceEventsForUpdates(modifications, connectionId)
        }
        else {
          if (target.entityDataById(newParent.id) != null) {
            val modifications = target.refs.replaceParentOfChild(connectionId, newEntityId.asChild(), newParent)
            target.createReplaceEventsForUpdates(modifications, connectionId)
          }
          else {
            if (!connectionId.canRemoveParent()) {
              val message = "Cannot restore some dependencies; $connectionId"
              LOG.error(message, ApplyChangesFromException(message))
            }
            val modifications = target.refs.removeParentToChildRef(connectionId, existingParent, newChildEntityId)
            target.createReplaceEventsForUpdates(modifications, connectionId)
          }
        }
        modifiableNewParents.remove(connectionId)
      }
    }

    // Any new parents? Add them
    for ((connectionId, parentId) in modifiableNewParents) {
      if (diffLog[parentId.id] is ChangeEntry.AddEntity) {
        var possibleNewParent = replaceMap[parentId.id.notThis()]
        if (possibleNewParent == null) {
          possibleNewParent = target.entitiesByType.book(parentId.id.clazz).asThis()
          replaceMap[parentId.id.notThis()] = possibleNewParent
        }
        val modifications = target.refs.replaceParentOfChild(connectionId, newEntityId.asChild(), possibleNewParent.id.asParent())
        target.createReplaceEventsForUpdates(modifications, connectionId)
      }
      else {
        if (target.entityDataById(parentId.id) != null) {
          val modifications = target.refs.replaceParentOfChild(connectionId, newEntityId.asChild(), parentId)
          target.createReplaceEventsForUpdates(modifications, connectionId)
        }
      }
    }
  }

  private fun checkSymbolicId(entityData: WorkspaceEntityData<out WorkspaceEntity>, newEntityId: EntityId?) {
    val newSymbolicId = entityData.symbolicId()
    if (newSymbolicId != null) {
      val existingIds = target.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId)
      if (existingIds != null) {
        val existingIdCheck = if (newEntityId != null) existingIds != newEntityId else true
        if (existingIdCheck) {
          // target symbolic id exists already.
          val existingEntityData = target.entityDataByIdOrDie(existingIds)
          LOG.debug("Removing existing entity... $existingIds")
          target.removeEntity(existingEntityData.createEntity(target))
          val message = """
                                  Symbolic ID already exists. Removing old entity
                                  Symbolic ID: $newSymbolicId
                                  Existing entity data: $existingEntityData
                                  New entity data: $entityData
                                  """.trimIndent()
          LOG.error(message, ApplyChangesFromException(message))
        }
      }
    }
  }

  companion object {
    private val LOG = logger<ApplyChanesFromOperation>()
  }
}