// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.exceptions.ReplaceBySourceException

internal object ReplaceBySourceAsGraph {
  /**
   * Here: identificator means [hashCode] or ([PersistentEntityId] in case it exists)
   *
   * Plan of [replaceBySource]:
   *  - Traverse all entities of the current builder and save the matched (by [sourceFilter]) to map by identificator.
   *  - In the current builder, remove all references *between* matched entities. If a matched entity has a reference to an unmatched one,
   *       save the unmatched entity to map by identificator.
   *       We'll check if the reference to unmatched reference is still valid after replacing.
   *  - Traverse all matched entities in the [replaceWith] storage. Detect if the particular entity exists in current builder using identificator.
   *       Perform add / replace operation if necessary (remove operation will be later).
   *  - Remove all entities that weren't found in [replaceWith] storage.
   *  - Restore entities between matched and unmatched entities. At this point the full action may fail (e.g. if an entity in [replaceWith]
   *        has a reference to an entity that doesn't exist in current builder.
   *  - Restore references between matched entities.
   */
  internal fun replaceBySourceAsGraph(
    thisBuilder: WorkspaceEntityStorageBuilderImpl,
    replaceWith: WorkspaceEntityStorage,
    sourceFilter: (EntitySource) -> Boolean,

    // This is a super ultra dirty hack to make one test reproducible
    // This should definitely NOT be used or moved to other implementations
    reverseEntities: Boolean = false,
  ) {
    replaceWith as AbstractEntityStorage

    if (LOG.isTraceEnabled) {
      thisBuilder.assertConsistency()
      replaceWith.assertConsistency()
      LOG.trace("Before starting replaceBySource no consistency issues were found")
    }

    val initialStore = if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) thisBuilder.toStorage() else null

    LOG.debug { "Performing replace by source" }

    // Map of entities in THIS builder with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val localMatchedEntities = HashMultimap.create<Any, Pair<WorkspaceEntityData<out WorkspaceEntity>, ThisEntityId>>()
    // Map of entities in replaceWith store with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val replaceWithMatchedEntities = HashMultimap.create<Any, NotThisEntityId>()

    // Map of entities in THIS builder that have a reference to matched entity. Key is either hashCode or PersistentId
    val localUnmatchedReferencedNodes = HashMultimap.create<Any, ThisEntityId>()

    // Association of the EntityId in THIS store to the EntityId in the remote store
    val replaceMap = HashBiMap.create<ThisEntityId, NotThisEntityId>()

    LOG.debug { "1) Traverse all entities and store matched only" }
    thisBuilder.indexes.entitySourceIndex.entries().filter { sourceFilter(it) }.forEach { entitySource ->
      thisBuilder.indexes.entitySourceIndex.getIdsByEntry(entitySource)?.forEach {
        val entityData = thisBuilder.entityDataByIdOrDie(it)
        localMatchedEntities.put(entityData.identificator(), entityData to it.asThis())
      }
    }

    LOG.debug { "1.1) Cleanup references" }
    //   If the reference leads to the matched entity, we can safely remove this reference.
    //   If the reference leads to the unmatched entity, we should save the entity to try to restore the reference later.
    for ((_, entityId) in localMatchedEntities.values()) {
      // Traverse parents of the entity
      val childEntityId = entityId.id.asChild()
      for ((connectionId, parentId) in thisBuilder.refs.getParentRefsOfChild(childEntityId)) {
        val parentEntity = thisBuilder.entityDataByIdOrDie(parentId.id)
        if (sourceFilter(parentEntity.entitySource)) {
          // Remove the connection between matched entities
          thisBuilder.refs.removeParentToChildRef(connectionId, parentId, childEntityId)
        }
        else {
          // Save the entity for restoring reference to it later
          localUnmatchedReferencedNodes.put(parentEntity.identificator(), parentId.id.asThis())
        }
      }

      // TODO: 29.04.2020 Do we need iterate over children and parents? Maybe only parents would be enough?
      // Traverse children of the entity
      for ((connectionId, childrenIds) in thisBuilder.refs.getChildrenRefsOfParentBy(entityId.id.asParent())) {
        for (childId in childrenIds) {
          val childEntity = thisBuilder.entityDataByIdOrDie(childId.id)
          if (sourceFilter(childEntity.entitySource)) {
            // Remove the connection between matched entities
            thisBuilder.refs.removeParentToChildRef(connectionId, entityId.id.asParent(), childId)
          }
          else {
            // Save the entity for restoring reference to it later
            localUnmatchedReferencedNodes.put(childEntity.identificator(), childId.id.asThis())
          }
        }
      }
    }

    LOG.debug { "2) Traverse entities of replaceWith store" }
    // 2) Traverse entities of the enemy
    //    and trying to detect whenever the entity already present in the local builder or not.
    //    If the entity already exists we optionally perform replace operation (or nothing),
    //    otherwise we add the entity.
    //    Local entities that don't exist in replaceWith store will be removed later.
    for (replaceWithEntitySource in replaceWith.indexes.entitySourceIndex.entries().filter { sourceFilter(it) }) {
      val entityDataList = replaceWith.indexes.entitySourceIndex
                             .getIdsByEntry(replaceWithEntitySource)
                             ?.mapTo(ArrayList()) { replaceWith.entityDataByIdOrDie(it) to it.notThis() } ?: continue
      if (reverseEntities) entityDataList.reverse()
      for ((matchedEntityData, matchedEntityId) in entityDataList) {
        replaceWithMatchedEntities.put(matchedEntityData.identificator(), matchedEntityId)

        // Find if the entity exists in local store
        val localNodeAndId = localMatchedEntities.find(matchedEntityData)

        // We should check if the issue still exists in this builder because it can be removed if it's referenced by another entity
        //   that had persistent id clash.
        val entityStillExists = localNodeAndId?.second?.let { thisBuilder.entityDataById(it.id) != null } ?: false
        if (entityStillExists && localNodeAndId != null) {
          val (localNode, localNodeEntityId) = localNodeAndId
          // This entity already exists. Store the association of EntityIdss
          replaceMap[localNodeEntityId] = matchedEntityId
          val dataDiffersByProperties = !localNode.equalsIgnoringEntitySource(matchedEntityData)
          val dataDiffersByEntitySource = localNode.entitySource != matchedEntityData.entitySource
          if (localNode.hasPersistentId(
              thisBuilder) && (dataDiffersByEntitySource || dataDiffersByProperties) && matchedEntityData.entitySource !is DummyParentEntitySource) {
            // Entity exists in local store, but has changes. Generate replace operation
            replaceOperation(thisBuilder, matchedEntityData, replaceWith, localNode, matchedEntityId, dataDiffersByProperties,
                             dataDiffersByEntitySource, localNode.entitySource)
          }

          // To make a store consistent in such case, we will clean up all refer to this entity
          if (localNode.entitySource !is DummyParentEntitySource && matchedEntityData.entitySource !is DummyParentEntitySource) {
            thisBuilder.removeEntitiesByOneToOneRef(sourceFilter, replaceWith, replaceMap, matchedEntityId, localNodeEntityId)
              .forEach { removedEntityData ->
                localUnmatchedReferencedNodes.removeAll(removedEntityData.identificator())
              }
          }

          if (localNode == matchedEntityData) {
            thisBuilder.indexes.updateExternalMappingForEntityId(matchedEntityId.id, localNodeEntityId.id, replaceWith.indexes)
          }
          // Remove added entity
          localMatchedEntities.remove(localNode.identificator(), localNodeAndId)
        }
        else {
          // This is a new entity for this store. Perform add operation

          val persistentId = matchedEntityData.persistentId()
          if (persistentId != null) {
            val existingEntityId = thisBuilder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.asThis()
            if (existingEntityId != null) {
              // Bad news, we have this persistent id already. CPP-22547
              // This may happened if local entity has entity source and remote entity has a different entity source
              // Technically we should throw an exception, but now we just remove local entity
              // Entity exists in local store, but has changes. Generate replace operation

              val localNode = thisBuilder.entityDataByIdOrDie(existingEntityId.id)

              val dataDiffersByProperties = !localNode.equalsIgnoringEntitySource(matchedEntityData)
              val dataDiffersByEntitySource = localNode.entitySource != matchedEntityData.entitySource

              replaceOperation(thisBuilder, matchedEntityData, replaceWith, localNode, matchedEntityId, dataDiffersByProperties,
                dataDiffersByEntitySource, localNode.entitySource)

              // To make a store consistent in such case, we will clean up all refer to this entity
              thisBuilder.removeEntitiesByOneToOneRef(sourceFilter, replaceWith, replaceMap, matchedEntityId, existingEntityId)
                .forEach { removedEntityData ->
                  localUnmatchedReferencedNodes.removeAll(removedEntityData.identificator())
                }

              replaceMap[existingEntityId] = matchedEntityId
              continue
            }
          }

          val entityClass = ClassConversion.entityDataToEntity(matchedEntityData.javaClass).toClassId()
          val newEntity = thisBuilder.entitiesByType.cloneAndAdd(matchedEntityData, entityClass)
          val newEntityId = matchedEntityId.id.copy(arrayId = newEntity.id).asThis()
          replaceMap[newEntityId] = matchedEntityId

          thisBuilder.indexes.virtualFileIndex.updateIndex(matchedEntityId.id, newEntityId.id, replaceWith.indexes.virtualFileIndex)
          replaceWith.indexes.entitySourceIndex.getEntryById(matchedEntityId.id)?.also {
            thisBuilder.indexes.entitySourceIndex.index(newEntityId.id, it)
          }
          replaceWith.indexes.persistentIdIndex.getEntryById(matchedEntityId.id)?.also {
            thisBuilder.indexes.persistentIdIndex.index(newEntityId.id, it)
          }
          thisBuilder.indexes.updateExternalMappingForEntityId(matchedEntityId.id, newEntityId.id, replaceWith.indexes)
          if (newEntity is SoftLinkable) thisBuilder.indexes.updateSoftLinksIndex(newEntity)

          thisBuilder.createAddEvent(newEntity)
        }
      }
    }

    LOG.debug { "3) Remove old entities" }
    //   After previous operation localMatchedEntities contain only entities that exist in local store, but don't exist in replaceWith store.
    //   Those entities should be just removed.
    for ((localEntity, entityId) in localMatchedEntities.values()) {
      val entityClass = ClassConversion.entityDataToEntity(localEntity.javaClass).toClassId()
      val id = createEntityId(localEntity.id, entityClass)
      val dataToRemove = thisBuilder.entityDataById(id)
      if (dataToRemove != null) {
        val original = thisBuilder.entityDataByIdOrDie(id) as WorkspaceEntityData<WorkspaceEntity>
        val originalParents = thisBuilder.refs.getParentRefsOfChild(id.asChild())
        thisBuilder.entitiesByType.remove(localEntity.id, entityClass)
        thisBuilder.indexes.entityRemoved(entityId.id)
        if (localEntity is SoftLinkable) thisBuilder.indexes.removeFromSoftLinksIndex(localEntity)
        thisBuilder.changeLog.addRemoveEvent(entityId.id, original, originalParents)
      }
    }

    val lostChildren = HashSet<ThisEntityId>()

    LOG.debug { "4) Restore references between matched and unmatched entities" }
    //    At this moment the operation may fail because of inconsistency.
    //    E.g. after this operation we can't have non-null references without corresponding entity.
    //      This may happen if we remove the matched entity, but don't have a replacement for it.
    for (thisUnmatchedId in localUnmatchedReferencedNodes.values()) {
      val replaceWithUnmatchedEntity = replaceWith.entityDataById(thisUnmatchedId.id)
      if (replaceWithUnmatchedEntity == null || replaceWithUnmatchedEntity != thisBuilder.entityDataByIdOrDie(thisUnmatchedId.id)) {
        // Okay, replaceWith storage doesn't have this "unmatched" entity at all.
        // TODO: 14.04.2020 Don't forget about entities with persistence id
        for ((connectionId, parentId) in thisBuilder.refs.getParentRefsOfChild(thisUnmatchedId.id.asChild())) {
          val parent = thisBuilder.entityDataById(parentId.id)

          // TODO: 29.04.2020 Review and write tests
          if (parent == null) {
            if (connectionId.canRemoveParent()) {
              thisBuilder.refs.removeParentToChildRef(connectionId, parentId, thisUnmatchedId.id.asChild())
            }
            else {
              thisBuilder.refs.removeParentToChildRef(connectionId, parentId, thisUnmatchedId.id.asChild())
              lostChildren += thisUnmatchedId
            }
          }
        }
        for ((connectionId, childIds) in thisBuilder.refs.getChildrenRefsOfParentBy(thisUnmatchedId.id.asParent())) {
          for (childId in childIds) {
            val child = thisBuilder.entityDataById(childId.id)
            if (child == null) {
              thisBuilder.refs.removeParentToChildRef(connectionId, thisUnmatchedId.id.asParent(), childId)
            }
          }
        }
      }
      else {
        // ----------------- Update parent references ---------------

        val removedConnections = HashMap<ConnectionId, EntityId>()
        // Remove parents in local store
        for ((connectionId, parentId) in thisBuilder.refs.getParentRefsOfChild(thisUnmatchedId.id.asChild())) {
          val parentData = thisBuilder.entityDataById(parentId.id)
          if (parentData != null && !sourceFilter(parentData.entitySource)) continue
          thisBuilder.refs.removeParentToChildRef(connectionId, parentId, thisUnmatchedId.id.asChild())
          removedConnections[connectionId] = parentId.id
        }

        // Transfer parents from replaceWith storage
        for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(thisUnmatchedId.id.asChild())) {
          if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId.id).entitySource)) continue
          val localParentId = replaceMap.inverse().getValue(parentId.id.notThis())
          thisBuilder.refs.updateParentOfChild(connectionId, thisUnmatchedId.id.asChild(), localParentId.id.asParent())
          removedConnections.remove(connectionId)
        }

        // TODO: 05.06.2020 The similar logic should exist for children references
        // Check not restored connections
        for ((connectionId, parentId) in removedConnections) {
          if (!connectionId.canRemoveParent()) thisBuilder.rbsFailedAndReport("Cannot restore connection to $parentId; $connectionId",
                                                                              sourceFilter,
                                                                              initialStore, replaceWith)
        }

        // ----------------- Update children references -----------------------

        for ((connectionId, childrenId) in thisBuilder.refs.getChildrenRefsOfParentBy(thisUnmatchedId.id.asParent())) {
          for (childId in childrenId) {
            val childData = thisBuilder.entityDataById(childId.id)
            if (childData != null && !sourceFilter(childData.entitySource)) continue
            thisBuilder.refs.removeParentToChildRef(connectionId, thisUnmatchedId.id.asParent(), childId)
          }
        }

        for ((connectionId, childrenId) in replaceWith.refs.getChildrenRefsOfParentBy(thisUnmatchedId.id.asParent())) {
          for (childId in childrenId) {
            if (!sourceFilter(replaceWith.entityDataByIdOrDie(childId.id).entitySource)) continue
            val localChildId = replaceMap.inverse().getValue(childId.id.notThis())
            thisBuilder.refs.updateParentOfChild(connectionId, localChildId.id.asChild(), thisUnmatchedId.id.asParent())
          }
        }
      }
    }

    // Some children left without parents. We should delete these children as well.
    for (entityId in lostChildren) {
      thisBuilder.removeEntity(entityId.id)
    }

    LOG.debug { "5) Restore references in matching ids" }
    val parentsWithSortedChildren = mutableSetOf<Pair<NotThisEntityId, ConnectionId>>()
    for (nodeId in replaceWithMatchedEntities.values()) {
      for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(nodeId.id.asChild())) {
        if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId.id).entitySource)) {
          // replaceWith storage has a link to unmatched entity. We should check if we can "transfer" this link to the current storage
          if (!connectionId.isParentNullable) {
            val localParent = thisBuilder.entityDataById(parentId.id)
            if (localParent == null) thisBuilder.rbsFailedAndReport(
              "Cannot link entities. Child entity doesn't have a parent after operation; $connectionId", sourceFilter, initialStore,
              replaceWith)

            val localChildId = replaceMap.inverse().getValue(nodeId)

            if (connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
              parentsWithSortedChildren += parentId.id.notThis() to connectionId
            }
            thisBuilder.refs.updateParentOfChild(connectionId, localChildId.id.asChild(), parentId)
          }
          continue
        }

        val localChildId = replaceMap.inverse().getValue(nodeId)
        val localParentId = replaceMap.inverse().getValue(parentId.id.notThis())

        if (connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
          parentsWithSortedChildren += parentId.id.notThis() to connectionId
        }
        thisBuilder.refs.updateParentOfChild(connectionId, localChildId.id.asChild(), localParentId.id.asParent())
      }
    }

    // Try to sort children
    // At the moment we sort only one-to-abstract-many children. This behaviour can be updated or removed at all
    parentsWithSortedChildren.forEach { (notThisParentId, connectionId) ->
      if (!replaceMap.containsValue(notThisParentId)) return@forEach
      val thisParentId = replaceMap.inverse().getValue(notThisParentId)
      val children = replaceWith.refs.getOneToAbstractManyChildren(connectionId, notThisParentId.id.asParent())
                       ?.mapNotNull { replaceMap.inverse().getValue(it.id.notThis()) } ?: return@forEach
      val localChildren = thisBuilder.refs.getOneToAbstractManyChildren(connectionId, thisParentId.id.asParent())?.toMutableSet()
                          ?: return@forEach
      val savedLocalChildren = thisBuilder.refs.getOneToAbstractManyChildren(connectionId, thisParentId.id.asParent()) ?: return@forEach
      val newChildren = mutableListOf<ChildEntityId>()
      for (child in children) {
        val removed = localChildren.remove(child.id.asChild())
        if (removed) {
          newChildren += child.id.asChild()
        }
      }
      newChildren.addAll(localChildren)
      if (savedLocalChildren != newChildren) {
        thisBuilder.refs.updateChildrenOfParent(connectionId, thisParentId.id.asParent(), newChildren)
      }
    }

    // Assert consistency
    if (!thisBuilder.brokenConsistency && !replaceWith.brokenConsistency) {
      thisBuilder.assertConsistencyInStrictMode("Check after replaceBySource", sourceFilter, initialStore, replaceWith)
    }
    else {
      thisBuilder.brokenConsistency = true
    }

    LOG.debug { "Replace by source finished" }
  }

  private fun WorkspaceEntityData<*>.identificator(): Any {
    return this.persistentId() ?: this.hashCode()
  }

  private fun <T> HashMultimap<Any, Pair<WorkspaceEntityData<out WorkspaceEntity>, T>>.find(entity: WorkspaceEntityData<out WorkspaceEntity>): Pair<WorkspaceEntityData<out WorkspaceEntity>, T>? {
    val possibleValues = this[entity.identificator()]
    val persistentId = entity.persistentId()
    return if (persistentId != null) {
      possibleValues.singleOrNull()
    }
    else {
      possibleValues.find { it.first == entity }
    }
  }

  private fun WorkspaceEntityData<*>.hasPersistentId(thisBuilder: WorkspaceEntityStorageBuilderImpl): Boolean {
    val entity = this.createEntity(thisBuilder)
    return entity is WorkspaceEntityWithPersistentId
  }

  private fun replaceOperation(thisBuilder: WorkspaceEntityStorageBuilderImpl,
                               matchedEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                               replaceWith: AbstractEntityStorage,
                               localNode: WorkspaceEntityData<out WorkspaceEntity>,
                               matchedEntityId: NotThisEntityId,
                               dataDiffersByProperties: Boolean,
                               dataDiffersByEntitySource: Boolean,
                               originalEntitySource: EntitySource) {
    val clonedEntity = matchedEntityData.clone() as WorkspaceEntityData<WorkspaceEntity>
    val persistentIdBefore = matchedEntityData.persistentId() ?: error("PersistentId expected for $matchedEntityData")
    clonedEntity.id = localNode.id
    val clonedEntityId = matchedEntityId.id.copy(arrayId = clonedEntity.id)
    thisBuilder.entitiesByType.replaceById(clonedEntity, clonedEntityId.clazz)

    thisBuilder.indexes.updatePersistentIdIndexes(thisBuilder, clonedEntity.createEntity(thisBuilder), persistentIdBefore, clonedEntity)
    thisBuilder.indexes.virtualFileIndex.updateIndex(matchedEntityId.id, clonedEntityId, replaceWith.indexes.virtualFileIndex)
    replaceWith.indexes.entitySourceIndex.getEntryById(matchedEntityId.id)
      ?.also { thisBuilder.indexes.entitySourceIndex.index(clonedEntityId, it) }
    thisBuilder.indexes.updateExternalMappingForEntityId(matchedEntityId.id, clonedEntityId, replaceWith.indexes)

    if (dataDiffersByProperties) {
      thisBuilder.changeLog.addReplaceEvent(clonedEntityId,
        clonedEntity,
        localNode.clone() as WorkspaceEntityData<WorkspaceEntity>,
        thisBuilder.refs.getParentRefsOfChild(localNode.createEntityId().asChild()),
        emptyList(),
        emptySet(),
        emptyMap())
    }
    if (dataDiffersByEntitySource) {
      thisBuilder.changeLog.addChangeSourceEvent(clonedEntityId, clonedEntity, originalEntitySource)
    }
  }

  /**
   * The goal of this method is to help to keep the store in at the consistent state at replaceBySource operation. It's responsible
   * for handling 1-1 references where entity source of the record is changed and we know the persistent Id of one of the elements
   * at this relationship.How this method works: we try to find all 1-1 references where current entity is parent or child.
   * We check that such reference also exist at the replacing store. And if such reference exist at the second store we can safely
   * delete related by the reference entity because we sure that the reference will be restored and entity from the second store
   * will be added to the main.
   * One important notes of the entity remove: we make it only if the second store contains entity which matched by source filter,
   * we don't remove entity which already was replaced and we don't remove the the object itself.
   */
  private fun WorkspaceEntityStorageBuilderImpl.removeEntitiesByOneToOneRef(sourceFilter: (EntitySource) -> Boolean,
                                                                            replaceWith: AbstractEntityStorage,
                                                                            replaceMap: Map<ThisEntityId, NotThisEntityId>,
                                                                            matchedEntityId: NotThisEntityId,
                                                                            localEntityId: ThisEntityId): Set<WorkspaceEntityData<out WorkspaceEntity>> {
    val replacingChildrenOneToOneConnections = replaceWith.refs
      .getChildrenOneToOneRefsOfParentBy(matchedEntityId.id.asParent())
      .filter { !it.key.isParentNullable }
    val result = refs.getChildrenOneToOneRefsOfParentBy(localEntityId.id.asParent())
      .asSequence()
      .filter { !it.key.isParentNullable }
      .mapNotNull { (connectionId, entityId) ->
        val suggestedNewChildEntityId = replacingChildrenOneToOneConnections[connectionId] ?: return@mapNotNull null
        val suggestedNewChildEntityData = replaceWith.entityDataByIdOrDie(suggestedNewChildEntityId.id)
        if (sourceFilter(suggestedNewChildEntityData.entitySource)) {

          // This entity was already moved to the new store
          if (entityId.id.asThis() in replaceMap) return@mapNotNull null

          val childEntityData = entityDataByIdOrDie(entityId.id)
          removeEntity(entityId.id) { it != localEntityId.id && !replaceMap.containsKey(it.asThis()) }
          return@mapNotNull childEntityData
        }
        return@mapNotNull null
      }.toMutableSet()
    return result
  }

  private fun WorkspaceEntityStorageBuilderImpl.rbsFailedAndReport(message: String, sourceFilter: (EntitySource) -> Boolean,
                                                                   left: WorkspaceEntityStorage?,
                                                                   right: WorkspaceEntityStorage) {
    reportConsistencyIssue(message, ReplaceBySourceException(message), sourceFilter, left, right, this)
  }


  val LOG = logger<ReplaceBySourceAsGraph>()
}
