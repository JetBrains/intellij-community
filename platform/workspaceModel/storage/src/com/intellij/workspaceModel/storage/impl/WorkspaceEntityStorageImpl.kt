// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.exceptions.PersistentIdAlreadyExistsException
import com.intellij.workspaceModel.storage.impl.exceptions.adFailed
import com.intellij.workspaceModel.storage.impl.exceptions.rbsFailed
import com.intellij.workspaceModel.storage.impl.external.EmptyExternalEntityMapping
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal class EntityReferenceImpl<E : WorkspaceEntity>(private val id: EntityId) : EntityReference<E>() {
  override fun resolve(storage: WorkspaceEntityStorage): E {
    return (storage as AbstractEntityStorage).entityDataByIdOrDie(id).createEntity(storage) as E
  }
}

internal class WorkspaceEntityStorageImpl constructor(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: StorageIndexes
) : AbstractEntityStorage() {
  companion object {
    val EMPTY = WorkspaceEntityStorageImpl(ImmutableEntitiesBarrel.EMPTY, RefsTable(), StorageIndexes.EMPTY)
  }
}

internal class WorkspaceEntityStorageBuilderImpl(
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable,
  override val indexes: MutableStorageIndexes
) : WorkspaceEntityStorageBuilder, AbstractEntityStorage() {

  private val changeLogImpl: MutableList<ChangeEntry> = mutableListOf()

  private val changeLog: List<ChangeEntry>
    get() = changeLogImpl

  private inline fun updateChangeLog(updater: (MutableList<ChangeEntry>) -> Unit) {
    updater(changeLogImpl)
    incModificationCount()
  }

  internal fun incModificationCount() {
    modificationCount++
  }

  internal sealed class ChangeEntry {
    data class AddEntity<E : WorkspaceEntity>(
      val entityData: WorkspaceEntityData<E>,
      val clazz: Int,
      val children: Map<ConnectionId, Set<EntityId>>,
      val parents: Map<ConnectionId, EntityId>
    ) : ChangeEntry()

    data class RemoveEntity(val id: EntityId) : ChangeEntry()

    data class ReplaceEntity<E : WorkspaceEntity>(
      val newData: WorkspaceEntityData<E>,
      val newChildren: List<Pair<ConnectionId, EntityId>>,
      val removedChildren: List<Pair<ConnectionId, EntityId>>,
      val modifiedParents: Map<ConnectionId, EntityId?>
    ) : ChangeEntry()
  }

  override var modificationCount: Long = 0
    private set

  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> addEntity(clazz: Class<M>,
                                                                                 source: EntitySource,
                                                                                 initializer: M.() -> Unit): T {
    // Extract entity classes
    val unmodifiableEntityClass = ClassConversion.modifiableEntityToEntity(clazz.kotlin).java
    val entityDataClass = ClassConversion.entityToEntityData(unmodifiableEntityClass.kotlin)
    val unmodifiableEntityClassId = unmodifiableEntityClass.toClassId()

    // Construct entity data
    val pEntityData = entityDataClass.java.newInstance()
    pEntityData.entitySource = source

    // Add entity data to the structure
    entitiesByType.add(pEntityData, unmodifiableEntityClassId)

    // Wrap it with modifiable and execute initialization code
    val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
    (modifiableEntity as ModifiableWorkspaceEntityBase<*>).allowModifications {
      modifiableEntity.initializer()
    }

    // Check for persistent id uniqueness
    pEntityData.persistentId(this)?.let { persistentId ->
      if (indexes.persistentIdIndex.getIdsByEntry(persistentId) != null) {
        entitiesByType.remove(pEntityData.id, unmodifiableEntityClassId)
        throw PersistentIdAlreadyExistsException(persistentId)
      }
    }

    // Add the change to changelog
    createAddEvent(pEntityData)

    // Update indexes
    indexes.entityAdded(pEntityData, this)

    // Assert consistency
    this.assertConsistencyInStrictMode()

    return pEntityData.createEntity(this)
  }

  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    // Get entity data that will be modified
    val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>
    val backup = copiedData.clone()
    val modifiableEntity = copiedData.wrapAsModifiable(this) as M

    val beforePersistentId = if (e is WorkspaceEntityWithPersistentId) e.persistentId() else null

    val pid = e.id

    val beforeParents = this.refs.getParentRefsOfChild(pid)
    val beforeChildren = this.refs.getChildrenRefsOfParentBy(pid).flatMap { (key, value) -> value.map { key to it } }

    // Execute modification code
    (modifiableEntity as ModifiableWorkspaceEntityBase<*>).allowModifications {
      modifiableEntity.change()
    }

    // Check for persistent id uniqueness
    if (beforePersistentId != null) {
      val newPersistentId = copiedData.persistentId(this) ?: error("Persistent id expected")
      if (beforePersistentId != newPersistentId && indexes.persistentIdIndex.getIdsByEntry(newPersistentId) != null ) {
          // Restore previous value
          (entitiesByType.entities[e.id.clazz] as MutableEntityFamily<T>).set(e.id.arrayId, backup)
          throw PersistentIdAlreadyExistsException(newPersistentId)
        }
    }

    // Add an entry to changelog
    addReplaceEvent(pid, beforeChildren, beforeParents, copiedData)

    val updatedEntity = copiedData.createEntity(this)

    updatePersistentIdIndexes(updatedEntity, beforePersistentId, copiedData)

    // Assert consistency
    this.assertConsistencyInStrictMode()

    return updatedEntity
  }

  private fun <T : WorkspaceEntity> addReplaceEvent(pid: EntityId,
                                                    beforeChildren: List<Pair<ConnectionId, EntityId>>,
                                                    beforeParents: Map<ConnectionId, EntityId>,
                                                    copiedData: WorkspaceEntityData<T>) {
    val parents = this.refs.getParentRefsOfChild(pid)
    val children = this.refs.getChildrenRefsOfParentBy(pid).flatMap { (key, value) -> value.map { key to it } }

    // Collect children changes
    val addedChildren = (children.toSet() - beforeChildren.toSet()).toList()
    val removedChildren = (beforeChildren.toSet() - children.toSet()).toList()

    // Collect parent changes
    val parentsMapRes: MutableMap<ConnectionId, EntityId?> = beforeParents.toMutableMap()
    for ((connectionId, parentId) in parents) {
      val existingParent = parentsMapRes[connectionId]
      if (existingParent != null) {
        if (existingParent == parentId) {
          parentsMapRes.remove(connectionId, parentId)
        }
        else {
          parentsMapRes[connectionId] = parentId
        }
      }
      else {
        parentsMapRes[connectionId] = parentId
      }
    }
    val removedKeys = beforeParents.keys - parents.keys
    removedKeys.forEach { parentsMapRes[it] = null }

    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(copiedData, addedChildren, removedChildren, parentsMapRes)) }
  }

  private fun <T : WorkspaceEntity> updatePersistentIdIndexes(updatedEntity: WorkspaceEntity,
                                                              beforePersistentId: PersistentEntityId<*>?,
                                                              copiedData: WorkspaceEntityData<T>) {
    val pid = (updatedEntity as WorkspaceEntityBase).id
    if (updatedEntity is WorkspaceEntityWithPersistentId) {
      val newPersistentId = updatedEntity.persistentId()
      if (beforePersistentId != null && beforePersistentId != newPersistentId) {
        indexes.persistentIdIndex.index(pid, newPersistentId)
        updateComposedIds(beforePersistentId, newPersistentId)
      }
    }
    indexes.simpleUpdateSoftReferences(copiedData)
  }

  private fun updateComposedIds(beforePersistentId: PersistentEntityId<*>, newPersistentId: PersistentEntityId<*>) {
    val idsWithSoftRef = HashSet(indexes.softLinks.getValues(beforePersistentId))
    for (entityId in idsWithSoftRef) {
      val entity = this.entitiesByType.getEntityDataForModification(entityId)
      val editingBeforePersistentId = entity.persistentId(this)
      (entity as SoftLinkable).updateLink(beforePersistentId, newPersistentId)

      // Add an entry to changelog
      updateChangeLog { it.add(ChangeEntry.ReplaceEntity(entity, emptyList(), emptyList(), emptyMap())) }

      updatePersistentIdIndexes(entity.createEntity(this), editingBeforePersistentId, entity)
    }
  }

  override fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>
    copiedData.entitySource = newSource

    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(copiedData, emptyList(), emptyList(), emptyMap())) }

    indexes.entitySourceIndex.index(copiedData.createPid(), newSource)

    // Assert consistency
    this.assertConsistencyInStrictMode()

    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: WorkspaceEntity) {
    e as WorkspaceEntityBase

    val removedEntities = removeEntity(e.id, null)
    updateChangeLog {
      removedEntities.forEach { removedEntityId -> it.add(ChangeEntry.RemoveEntity(removedEntityId)) }
    }

    // Assert consistency
    this.assertConsistencyInStrictMode()
  }

  override fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E> = EntityReferenceImpl((e as WorkspaceEntityBase).id)

  private fun ArrayListMultimap<Any, WorkspaceEntityData<out WorkspaceEntity>>.find(entity: WorkspaceEntityData<out WorkspaceEntity>,
                                                                                    storage: AbstractEntityStorage): WorkspaceEntityData<out WorkspaceEntity>? {
    val possibleValues = this[entity.identificator(storage)]
    val persistentId = entity.persistentId(storage)
    return if (persistentId != null) {
      possibleValues.singleOrNull()
    }
    else {
      possibleValues.find { it == entity }
    }
  }

  /**
   *
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
  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: WorkspaceEntityStorage) {
    replaceWith as AbstractEntityStorage

    this.assertConsistencyInStrictMode()
    replaceWith.assertConsistencyInStrictMode()

    LOG.debug { "Performing replace by source" }

    // Map of entities in THIS builder with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val localMatchedEntities = ArrayListMultimap.create<Any, WorkspaceEntityData<out WorkspaceEntity>>()
    // Map of entities in replaceWith store with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val replaceWithMatchedEntities = ArrayListMultimap.create<Any, WorkspaceEntityData<out WorkspaceEntity>>()

    // Map of entities in THIS builder that have a reference to matched entity. Key is either hashCode or PersistentId
    val localUnmatchedReferencedNodes = ArrayListMultimap.create<Any, WorkspaceEntityData<out WorkspaceEntity>>()

    // Association of the PId in the local store to the PId in the remote store
    val replaceMap = HashBiMap.create<EntityId, EntityId>()

    LOG.debug { "1) Traverse all entities and store matched only" }
    this.indexes.entitySourceIndex.entries().filter { sourceFilter(it) }.forEach { entitySource ->
      this.indexes.entitySourceIndex.getIdsByEntry(entitySource)?.forEach {
        val entityData = this.entityDataByIdOrDie(it)
        localMatchedEntities.put(entityData.identificator(this), entityData)
      }
    }

    LOG.debug { "1.1) Cleanup references" }
    //   If the reference leads to the matched entity, we can safely remove this reference.
    //   If the reference leads to the unmatched entity, we should save the entity to try to restore the reference later.
    for (matchedEntityData in localMatchedEntities.values()) {
      val entityId = matchedEntityData.createPid()
      // Traverse parents of the entity
      for ((connectionId, parentId) in this.refs.getParentRefsOfChild(entityId)) {
        val parentEntity = this.entityDataByIdOrDie(parentId)
        if (sourceFilter(parentEntity.entitySource)) {
          // Remove the connection between matched entities
          this.refs.removeParentToChildRef(connectionId, parentId, entityId)
        }
        else {
          // Save the entity for restoring reference to it later
          localUnmatchedReferencedNodes.put(parentEntity.identificator(this), parentEntity)
        }
      }

      // TODO: 29.04.2020 Do we need iterate over children and parents? Maybe only parents would be enough?
      // Traverse children of the entity
      for ((connectionId, childrenIds) in this.refs.getChildrenRefsOfParentBy(entityId)) {
        for (childId in childrenIds) {
          val childEntity = this.entityDataByIdOrDie(childId)
          if (sourceFilter(childEntity.entitySource)) {
            // Remove the connection between matched entities
            this.refs.removeParentToChildRef(connectionId, entityId, childId)
          }
          else {
            // Save the entity for restoring reference to it later
            localUnmatchedReferencedNodes.put(childEntity.identificator(this), childEntity)
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
                             ?.map { replaceWith.entityDataByIdOrDie(it) } ?: continue
      for (matchedEntityData in entityDataList) {
        replaceWithMatchedEntities.put(matchedEntityData.identificator(replaceWith), matchedEntityData)

        // Find if the entity exists in local store
        val localNode = localMatchedEntities.find(matchedEntityData, replaceWith)
        val oldPid = matchedEntityData.createPid()
        if (localNode != null) {
          // This entity already exists. Store the association of pids
          replaceMap[localNode.createPid()] = oldPid
          if (localNode.hasPersistentId() && localNode != matchedEntityData) {
            // Entity exists in local store, but has changes. Generate replace operation
            val clonedEntity = matchedEntityData.clone()
            val persistentIdBefore = matchedEntityData.persistentId(replaceWith) ?: rbsFailed("PersistentId expected")
            clonedEntity.id = localNode.id
            this.entitiesByType.replaceById(clonedEntity as WorkspaceEntityData<WorkspaceEntity>, clonedEntity.createPid().clazz)
            val pid = clonedEntity.createPid()
            val parents = this.refs.getParentRefsOfChild(pid)
            val children = this.refs.getChildrenRefsOfParentBy(pid)

            updatePersistentIdIndexes(clonedEntity.createEntity(this), persistentIdBefore, clonedEntity)
            replaceWith.indexes.virtualFileIndex.getVirtualFiles(oldPid)?.forEach { this.indexes.virtualFileIndex.index(pid, listOf(it)) }
            replaceWith.indexes.entitySourceIndex.getEntryById(oldPid)?.also { this.indexes.entitySourceIndex.index(pid, it) }

            updateChangeLog { it.add(ChangeEntry.ReplaceEntity(clonedEntity, emptyList(), emptyList(), emptyMap())) }
          }
          // Remove added entity
          localMatchedEntities.remove(localNode.identificator(this), localNode)
        }
        else {
          // This is a new entity for this store. Perform add operation
          val entityClass = ClassConversion.entityDataToEntity(matchedEntityData.javaClass).toClassId()
          val newEntity = this.entitiesByType.cloneAndAdd(matchedEntityData as WorkspaceEntityData<WorkspaceEntity>, entityClass)
          val newPid = newEntity.createPid()
          replaceMap[newPid] = oldPid

          replaceWith.indexes.virtualFileIndex.getVirtualFiles(oldPid)?.forEach { this.indexes.virtualFileIndex.index(newPid, listOf(it)) }
          replaceWith.indexes.entitySourceIndex.getEntryById(oldPid)?.also { this.indexes.entitySourceIndex.index(newPid, it) }
          replaceWith.indexes.persistentIdIndex.getEntryById(oldPid)?.also { this.indexes.persistentIdIndex.index(newPid, it) }
          if (newEntity is SoftLinkable) indexes.updateSoftLinksIndex(newEntity)

          createAddEvent(newEntity)
        }
      }
    }

    LOG.debug { "3) Remove old entities" }
    //   After previous operation localMatchedEntities contain only entities that exist in local store, but don't exist in replaceWith store.
    //   Those entities should be just removed.
    for (localEntity in localMatchedEntities.values()) {
      val entityClass = ClassConversion.entityDataToEntity(localEntity.javaClass).toClassId()
      this.entitiesByType.remove(localEntity.id, entityClass)
      val entityId = localEntity.createPid()
      indexes.removeFromIndices(entityId)
      if (localEntity is SoftLinkable) indexes.removeFromSoftLinksIndex(localEntity)
      updateChangeLog { it.add(ChangeEntry.RemoveEntity(entityId)) }
    }

    LOG.debug { "4) Restore references between matched and unmatched entities" }
    //    At this moment the operation may fail because of inconsistency.
    //    E.g. after this operation we can't have non-null references without corresponding entity.
    //      This may happen if we remove the matched entity, but don't have a replacement for it.
    for (localUnmatchedEntity in localUnmatchedReferencedNodes.values()) {
      val unmatchedId = localUnmatchedEntity.createPid()
      val replaceWithUnmatchedEntity = replaceWith.entityDataById(unmatchedId)
      if (replaceWithUnmatchedEntity == null) {
        // Okay, replaceWith storage doesn't have this "unmatched" entity at all.
        // TODO: 14.04.2020 Don't forget about entities with persistence id
        for ((connectionId, parentId) in this.refs.getParentRefsOfChild(unmatchedId)) {
          val parent = this.entityDataById(parentId)

          // TODO: 29.04.2020 Review and write tests
          if (parent == null) {
            if (connectionId.canRemoveParent()) {
              this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
            }
            else rbsFailed("Cannot link old entity to the new one")
          }
        }
        for ((connectionId, childIds) in this.refs.getChildrenRefsOfParentBy(unmatchedId)) {
          for (childId in childIds) {
            val child = this.entityDataById(childId)
            if (child == null) {
              if (connectionId.canRemoveChild()) {
                this.refs.removeParentToChildRef(connectionId, unmatchedId, childId)
              }
              else rbsFailed("Cannot link old entity to the new one")
            }
          }
        }
      }
      else {
        // ----------------- Update parent references ---------------

        val removedConnections = ArrayList<Pair<ConnectionId, EntityId>>()
        // Remove parents in local store
        for ((connectionId, parentId) in this.refs.getParentRefsOfChild(unmatchedId)) {
          val parentData = this.entityDataById(parentId)
          if (parentData != null && !sourceFilter(parentData.entitySource)) continue
          this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
          removedConnections.add(connectionId to parentId)
        }

        // Transfer parents from replaceWith storage
        for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(unmatchedId)) {
          if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId).entitySource)) continue
          val localParentId = replaceMap.inverse().getValue(parentId)
          this.refs.updateParentOfChild(connectionId, unmatchedId, localParentId)
          removedConnections.remove(connectionId to parentId)
        }

        // TODO: 05.06.2020 The similar logic should exist for children references
        // Check not restored connections
        for ((connectionId, parentId) in removedConnections) {
          if (!connectionId.canRemoveParent()) rbsFailed("Cannot restore connection to $parentId")
        }

        // ----------------- Update children references -----------------------

        for ((connectionId, childrenId) in this.refs.getChildrenRefsOfParentBy(unmatchedId)) {
          for (childId in childrenId) {
            val childData = this.entityDataById(childId)
            if (childData != null && !sourceFilter(childData.entitySource)) continue
            this.refs.removeParentToChildRef(connectionId, unmatchedId, childId)
          }
        }

        for ((connectionId, childrenId) in replaceWith.refs.getChildrenRefsOfParentBy(unmatchedId)) {
          for (childId in childrenId) {
            if (!sourceFilter(replaceWith.entityDataByIdOrDie(childId).entitySource)) continue
            val localChildId = replaceMap.inverse().getValue(childId)
            this.refs.updateParentOfChild(connectionId, localChildId, unmatchedId)
          }
        }
      }
    }

    LOG.debug { "5) Restore references in matching ids" }
    for (rightMatchedNode in replaceWithMatchedEntities.values()) {
      val nodeId = rightMatchedNode.createPid()
      for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(nodeId)) {
        if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId).entitySource)) {
          // replaceWith storage has a link to unmatched entity. We should check if we can "transfer" this link to the current storage
          if (!connectionId.isParentNullable) {
            val localParent = this.entityDataById(parentId)
            if (localParent == null) rbsFailed("Cannot link entities. Child entity doesn't have a parent after operation")

            val localChildId = replaceMap.inverse().getValue(nodeId)

            this.refs.updateParentOfChild(connectionId, localChildId, parentId)
          }
          continue
        }

        val localChildId = replaceMap.inverse().getValue(nodeId)
        val localParentId = replaceMap.inverse().getValue(parentId)

        this.refs.updateParentOfChild(connectionId, localChildId, localParentId)
      }
    }

    // Assert consistency
    this.assertConsistencyInStrictMode()

    LOG.debug { "Replace by source finished" }
  }

  sealed class EntityDataChange<T : WorkspaceEntityData<out WorkspaceEntity>> {
    data class Added<T : WorkspaceEntityData<out WorkspaceEntity>>(val entity: T) : EntityDataChange<T>()
    data class Removed<T : WorkspaceEntityData<out WorkspaceEntity>>(val entity: T) : EntityDataChange<T>()
    data class Replaced<T : WorkspaceEntityData<out WorkspaceEntity>>(val oldEntity: T, val newEntity: T) : EntityDataChange<T>()
  }

  override fun collectChanges(original: WorkspaceEntityStorage): Map<Class<*>, List<EntityChange<*>>> {

    // TODO: 27.03.2020 Since we have an instance of original storage, we actually can provide a method without an argument

    val originalImpl = original as AbstractEntityStorage
    //this can be optimized to avoid creation of entity instances which are thrown away and copying the results from map to list
    // LinkedHashMap<Long, EntityChange<T>>
    val changes = LinkedHashMap<EntityId, Pair<Class<*>, EntityChange<*>>>()
    for (change in changeLog) {
      when (change) {
        is ChangeEntry.AddEntity<*> -> {
          val addedEntity = change.entityData.createEntity(this) as WorkspaceEntityBase
          changes[addedEntity.id] = addedEntity.id.clazz.findEntityClass<WorkspaceEntity>() to EntityChange.Added(addedEntity)
        }
        is ChangeEntry.RemoveEntity -> {
          val removedData = originalImpl.entityDataById(change.id)
          val oldChange = changes.remove(change.id)
          if (oldChange?.second !is EntityChange.Added && removedData != null) {
            val removedEntity = removedData.createEntity(originalImpl) as WorkspaceEntityBase
            changes[removedEntity.id] = change.id.clazz.findEntityClass<WorkspaceEntity>() to EntityChange.Removed(removedEntity)
          }
        }
        is ChangeEntry.ReplaceEntity<*> -> {
          val id = change.newData.createPid()
          val oldChange = changes.remove(id)
          if (oldChange?.second is EntityChange.Added) {
            val addedEntity = change.newData.createEntity(this) as WorkspaceEntityBase
            changes[addedEntity.id] = addedEntity.id.clazz.findEntityClass<WorkspaceEntity>() to EntityChange.Added(addedEntity)
          }
          else {
            val oldData = originalImpl.entityDataById(id)
            if (oldData != null) {
              val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
              val replaceToData = change.newData.createEntity(this) as WorkspaceEntityBase
              changes[replacedData.id] = replacedData.id.clazz.findEntityClass<WorkspaceEntity>() to EntityChange.Replaced(
                replacedData, replaceToData)
            }
          }
        }
      }
    }
    return changes.values.groupBy { it.first }.mapValues { list -> list.value.map { it.second } }
  }

  override fun resetChanges() {
    updateChangeLog { it.clear() }
  }

  override fun toStorage(): WorkspaceEntityStorageImpl {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    val storage = WorkspaceEntityStorageImpl(newEntities, newRefs, newIndexes)

    storage.assertConsistencyInStrictMode()

    return storage
  }

  override fun isEmpty(): Boolean = changeLogImpl.isEmpty()

  override fun addDiff(diff: WorkspaceEntityStorageDiffBuilder): Map<WorkspaceEntity, WorkspaceEntity> {
    val replaceMap = HashBiMap.create<EntityId, EntityId>()
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    val diffLog = builder.changeLog
    for (change in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity<out WorkspaceEntity> -> {
          change as ChangeEntry.AddEntity<WorkspaceEntity>

          val newPersistentId = change.entityData.persistentId(this)
          if (newPersistentId != null) {
            val existingIds = this.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
            if (existingIds != null && existingIds.isNotEmpty()) adFailed("PersistentId already exists: $newPersistentId")
          }

          val updatedChildren = change.children.mapValues { it.value.map { v -> replaceMap.getOrDefault(v, v) }.toSet() }
          val updatedParents = change.parents.mapValues { replaceMap.getOrDefault(it.value, it.value) }

          val entity2id = cloneEntity(change.entityData, change.clazz, replaceMap)
          updateEntityRefs(entity2id.second, updatedChildren, updatedParents)
          indexes.updateIndices(change.entityData.createPid(), entity2id.second, builder)
          updateChangeLog {
            it.add(ChangeEntry.AddEntity(entity2id.first, change.clazz, updatedChildren, updatedParents))
          }
        }
        is ChangeEntry.RemoveEntity -> {
          val outdatedId = change.id
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)
          indexes.removeFromIndices(usedPid)
          if (this.entityDataById(usedPid) != null) {
            removeEntity(usedPid, replaceMap.inverse())
          }
          updateChangeLog { it.add(ChangeEntry.RemoveEntity(usedPid)) }
        }
        is ChangeEntry.ReplaceEntity<out WorkspaceEntity> -> {
          change as ChangeEntry.ReplaceEntity<WorkspaceEntity>

          val updatedNewChildren = change.newChildren.map { (connectionId, id) -> connectionId to replaceMap.getOrDefault(id, id) }
          val updatedRemovedChildren = change.removedChildren.map { (connectionId, id) -> connectionId to replaceMap.getOrDefault(id, id) }
          val updatedModifiedParents = change.modifiedParents.mapValues { if (it.value == null) null else replaceMap.getOrDefault(it.value, it.value) }

          val outdatedId = change.newData.createPid()
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)
          val newData = change.newData.clone()
          newData.id = usedPid.arrayId

          // We don't modify entity that isn't exist in this version of storage
          if (this.entityDataById(usedPid) != null) {
            indexes.updateIndices(outdatedId, newData.createPid(), builder)
            updateChangeLog { it.add(ChangeEntry.ReplaceEntity(newData, updatedNewChildren, updatedRemovedChildren, updatedModifiedParents)) }
            replaceEntityWithRefs(newData, outdatedId.clazz, updatedNewChildren, updatedRemovedChildren, updatedModifiedParents)
          }
        }
      }
    }
    indexes.applyExternalMappingChanges(diff, replaceMap)
    val res = HashMap<WorkspaceEntity, WorkspaceEntity>()
    replaceMap.forEach { (oldId, newId) ->
      if (oldId != newId) {
        res[diff.entityDataByIdOrDie(oldId).createEntity(diff)] = this.entityDataByIdOrDie(newId).createEntity(this)
      }
    }

    // Assert consistency
    this.assertConsistencyInStrictMode()
    return res
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T> {
    val mapping = indexes.externalMappings.computeIfAbsent(identifier) { MutableExternalEntityMappingImpl<T>() } as MutableExternalEntityMappingImpl<T>
    mapping.setTypedEntityStorage(this)
    return mapping
  }

  fun removeExternalMapping(identifier: String) {
    indexes.externalMappings.remove(identifier)
  }

  // modificationCount is not incremented
  private fun removeEntity(idx: EntityId, mapToUpdate: MutableMap<EntityId, EntityId>?): Collection<EntityId> {
    val accumulator: MutableSet<EntityId> = mutableSetOf(idx)

    accumulateEntitiesToRemove(idx, accumulator)

    for (id in accumulator) {
      val entityData = entityDataById(id)
      if (entityData is SoftLinkable) indexes.removeFromSoftLinksIndex(entityData)
      entitiesByType.remove(id.arrayId, id.clazz)
      mapToUpdate?.remove(id)
    }

    // Update index
    //   Please don't join it with the previous loop
    for (id in accumulator) indexes.removeFromIndices(id)

    return accumulator
  }

  private fun WorkspaceEntityData<*>.hasPersistentId(): Boolean {
    val entity = this.createEntity(this@WorkspaceEntityStorageBuilderImpl)
    return entity is WorkspaceEntityWithPersistentId
  }

  private fun WorkspaceEntityData<*>.identificator(storage: AbstractEntityStorage): Any {
    return this.persistentId(storage) ?: this.hashCode()
  }

  private fun <T : WorkspaceEntity> createAddEvent(pEntityData: WorkspaceEntityData<T>) {
    val pid = pEntityData.createPid()
    val parents = refs.getParentRefsOfChild(pid)
    val children = refs.getChildrenRefsOfParentBy(pid)
    updateChangeLog { it.add(ChangeEntry.AddEntity(pEntityData, pid.clazz, children, parents)) }
  }

  private fun <T : WorkspaceEntity> cloneEntity(entity: WorkspaceEntityData<T>, clazz: Int,
                                                replaceMap: MutableMap<EntityId, EntityId>): Pair<WorkspaceEntityData<T>, EntityId> {
    // Add new entity to store (without references)
    val cloned = entitiesByType.cloneAndAdd(entity, clazz)
    val clonedPid = cloned.createPid()
    if (clonedPid != entity.createPid()) {
      replaceMap[entity.createPid()] = clonedPid
    }

    // Restore links to soft references
    if (cloned is SoftLinkable) indexes.updateSoftLinksIndex(cloned)

    return cloned to clonedPid
  }

  // modificationCount is not incremented
  private fun updateEntityRefs(entityId: EntityId, updatedChildren: Map<ConnectionId, Set<EntityId>>, updatedParents: Map<ConnectionId, EntityId>) {
    // Restore children references of the entity
    for ((connectionId, children) in updatedChildren) {
      val (missingChildren, existingChildren) = children.partition { this.entityDataById(it) == null }
      if (missingChildren.isNotEmpty() && !connectionId.canRemoveChild()) adFailed("Cannot restore some dependencies")
      refs.updateChildrenOfParent(connectionId, entityId, existingChildren)
    }

    // Restore parent references of the entity
    for ((connection, parent) in updatedParents) {
      if (this.entityDataById(parent) != null) {
        refs.updateParentOfChild(connection, entityId, parent)
      }
      else if (!connection.canRemoveParent()) adFailed("Cannot restore some dependencies")
    }
  }

  private fun <T : WorkspaceEntity> replaceEntityWithRefs(
    newEntity: WorkspaceEntityData<T>,
    clazz: Int,
    addedChildren: List<Pair<ConnectionId, EntityId>>,
    removedChildren: List<Pair<ConnectionId, EntityId>>,
    modifiedParents: Map<ConnectionId, EntityId?>
  ) {

    val id = newEntity.createPid()
    val existingEntityData = entityDataById(id)
    val beforePersistentId = existingEntityData?.persistentId(this)

    /// Replace entity data. id should not be changed
    entitiesByType.replaceById(newEntity, clazz)

    // Restore soft references
    updatePersistentIdIndexes(newEntity.createEntity(this), beforePersistentId, newEntity)

    // Restore connections
    val addedChildrenMap = HashMultimap.create<ConnectionId, EntityId>()
    addedChildren.forEach { addedChildrenMap.put(it.first, it.second) }

    val removedChildrenMap = HashMultimap.create<ConnectionId, EntityId>()
    removedChildren.forEach { removedChildrenMap.put(it.first, it.second) }

    //     Restore children connections
    val existingChildren = refs.getChildrenRefsOfParentBy(id)
    for ((connectionId, children) in existingChildren) {
      // Take current children....
      val mutableChildren = children.toMutableSet()

      // ...   Add missing children ...
      val addedChildrenSet = addedChildrenMap[connectionId] ?: mutableSetOf()
      for (addedChild in addedChildrenSet) {
        if (addedChild !in mutableChildren) {
          val addedEntityData = this.entityDataById(addedChild)
          if (addedEntityData == null && !connectionId.canRemoveParent()) adFailed("Cannot restore some dependencies")
          mutableChildren.add(addedChild)
        }
      }

      // ...    Remove removed children ....
      val removedChildrenSet = removedChildrenMap[connectionId] ?: mutableSetOf()
      for (removedChild in removedChildrenSet) {
        if (removedChild !in mutableChildren && StrictMode.enabled) adFailed("Trying to remove child that isn't present")
        mutableChildren.remove(removedChild)
      }

      // .... Update if something changed
      if (children != mutableChildren) {
        refs.updateChildrenOfParent(connectionId, id, mutableChildren)
      }
      addedChildrenMap.removeAll(connectionId)
      removedChildrenMap.removeAll(connectionId)
    }
    // Do we have more children to remove? This should not happen
    if (!removedChildrenMap.isEmpty && StrictMode.enabled) adFailed("Trying to remove children that aren't present")
    // Do we have more children to add? Add them
    for ((connectionId, children) in addedChildrenMap.asMap()) {
      refs.updateChildrenOfParent(connectionId, id, children)
    }

    //       Restore parent connections
    val modifiedParentsMap = modifiedParents.toMutableMap()
    val existingParents = refs.getParentRefsOfChild(id)
    for ((connectionId, existingParent) in existingParents) {
      if (connectionId in modifiedParentsMap) {
        val parent = modifiedParentsMap.getValue(connectionId)
        if (parent != null && this.entityDataById(parent) != null && parent != existingParent) {
          // This child has parent, but different one. Update parent
          refs.updateParentOfChild(connectionId, id, parent)
        }
        else if (parent == null || this.entityDataById(parent) != null) {
          // This child doesn't have a pareny anymore
          if (!connectionId.canRemoveParent()) adFailed("Cannot restore some dependencies")
          else refs.removeParentToChildRef(connectionId, existingParent, id)
        }
        modifiedParentsMap.remove(connectionId)
      }
    }
    // Any new parents? Add them
    for ((connectionId, parentId) in modifiedParentsMap) {
      if (parentId == null) continue
      if (this.entityDataById(parentId) != null) {
        refs.updateParentOfChild(connectionId, id, parentId)
      }
      else if (!connectionId.canRemoveParent()) adFailed("Cannot restore some dependencies")
    }
  }

  /**
   * Cleanup references and accumulate hard linked entities in [accumulator]
   */
  private fun accumulateEntitiesToRemove(id: EntityId, accumulator: MutableSet<EntityId>) {
    val children = refs.getChildrenRefsOfParentBy(id)
    for ((connectionId, children) in children) {
      for (child in children) {
        if (child in accumulator) continue
        accumulator.add(child)
        accumulateEntitiesToRemove(child, accumulator)
        refs.removeRefsByParent(connectionId, id)
      }
    }

    val parents = refs.getParentRefsOfChild(id)
    for ((connectionId, parent) in parents) {
      refs.removeParentToChildRef(connectionId, parent, id)
    }
  }

  companion object {

    private val LOG = logger<WorkspaceEntityStorageBuilderImpl>()

    fun create(): WorkspaceEntityStorageBuilderImpl = from(WorkspaceEntityStorageImpl.EMPTY)

    fun from(storage: WorkspaceEntityStorage): WorkspaceEntityStorageBuilderImpl {
      storage as AbstractEntityStorage
      return when (storage) {
        is WorkspaceEntityStorageImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
          val copiedRefs = MutableRefsTable.from(storage.refs)
          val copiedIndex = storage.indexes.toMutable()
          WorkspaceEntityStorageBuilderImpl(copiedBarrel, copiedRefs, copiedIndex)
        }
        is WorkspaceEntityStorageBuilderImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType.toImmutable())
          val copiedRefs = MutableRefsTable.from(storage.refs.toImmutable())
          val copiedIndexes = storage.indexes.toImmutable().toMutable()
          WorkspaceEntityStorageBuilderImpl(copiedBarrel, copiedRefs, copiedIndexes)
        }
      }
    }
  }
}

internal sealed class AbstractEntityStorage : WorkspaceEntityStorage {

  internal abstract val entitiesByType: EntitiesBarrel
  internal abstract val refs: AbstractRefsTable
  internal abstract val indexes: StorageIndexes

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    return entitiesByType[entityClass.toClassId()]?.all()?.map { it.createEntity(this) } as? Sequence<E> ?: emptySequence()
  }

  internal fun entityDataById(id: EntityId): WorkspaceEntityData<out WorkspaceEntity>? = entitiesByType[id.clazz]?.get(id.arrayId)

  internal fun entityDataByIdOrDie(id: EntityId): WorkspaceEntityData<out WorkspaceEntity> {
    return entitiesByType[id.clazz]?.get(id.arrayId) ?: error("Cannot find an entity by id $id")
  }

  override fun <E : WorkspaceEntity, R : WorkspaceEntity> referrers(e: E, entityClass: KClass<R>,
                                                                    property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    TODO()
    //return entities(entityClass.java).filter { property.get(it).resolve(this) == e }
  }

  override fun <E : WorkspaceEntityWithPersistentId, R : WorkspaceEntity> referrers(id: PersistentEntityId<E>, entityClass: Class<R>): Sequence<R> {
    TODO("Not yet implemented")
  }

  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val pids = indexes.persistentIdIndex.getIdsByEntry(id) ?: return null
    if (pids.isEmpty()) return null
    if (pids.size > 1) error("Cannot resolve persistent id $id. The store contains more than one associated entities")
    val pid = pids.single()
    return entityDataById(pid)?.createEntity(this) as E?
  }

  // Do not remove cast to Class<out TypedEntity>. kotlin fails without it
  @Suppress("USELESS_CAST")
  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    return indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
      indexes.entitySourceIndex
        .getIdsByEntry(source)!!.map { this.entityDataByIdOrDie(it).createEntity(this) }
        .groupBy { it.javaClass as Class<out WorkspaceEntity> }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T> {
    val index = indexes.externalMappings[identifier] as? ExternalEntityMappingImpl<T>
    if (index == null) return EmptyExternalEntityMapping as ExternalEntityMapping<T>
    index.setTypedEntityStorage(this)
    return index
  }

  internal fun assertConsistency() {
    entitiesByType.assertConsistency()
    // Rules:
    //  1) Refs should not have links without a corresponding entity
    //    1.1) For abstract containers: PId has the class of ConnectionId
    //  2) There is no child without a parent under the hard reference

    refs.oneToManyContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY)

      //  1) Refs should not have links without a corresponding entity
      map.forEachKey { childId, parentId ->
        assertResolvable(connectionId.parentClass, parentId)
        assertResolvable(connectionId.childClass, childId)
      }

      //  2) All children should have a parent if the connection has a restriction for that
      if (!connectionId.isParentNullable) {
        checkStrongConnection(map.keys, connectionId.childClass, connectionId.parentClass)
      }
    }

    refs.oneToOneContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ONE)

      //  1) Refs should not have links without a corresponding entity
      map.forEachKey { childId, parentId ->
        assertResolvable(connectionId.parentClass, parentId)
        assertResolvable(connectionId.childClass, childId)
      }

      //  2) Connections satisfy connectionId requirements
      if (!connectionId.isParentNullable) checkStrongConnection(map.keys, connectionId.childClass, connectionId.parentClass)
      if (!connectionId.isChildNullable) checkStrongConnection(map.values, connectionId.parentClass, connectionId.childClass)
    }

    refs.oneToAbstractManyContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY)

      map.forEach { (childId, parentId) ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(parentId.clazz, parentId.arrayId)
        assertResolvable(childId.clazz, childId.arrayId)

        //  1.1) For abstract containers: PId has the class of ConnectionId
        assertCorrectEntityClass(connectionId.parentClass, parentId)
        assertCorrectEntityClass(connectionId.childClass, childId)
      }

      //  2) Connections satisfy connectionId requirements
      if (!connectionId.isParentNullable) {
        checkStrongAbstractConnection(map.keys.toMutableSet(), map.keys.toMutableSet().map { it.clazz }.toSet(), connectionId.debugStr())
      }
    }

    refs.abstractOneToOneContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE)

      map.forEach { (childId, parentId) ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(parentId.clazz, parentId.arrayId)
        assertResolvable(childId.clazz, childId.arrayId)

        //  1.1) For abstract containers: PId has the class of ConnectionId
        assertCorrectEntityClass(connectionId.parentClass, parentId)
        assertCorrectEntityClass(connectionId.childClass, childId)
      }

      //  2) Connections satisfy connectionId requirements
      if (!connectionId.isParentNullable) {
        checkStrongAbstractConnection(map.keys.toMutableSet(), map.keys.toMutableSet().map { it.clazz }.toSet(), connectionId.debugStr())
      }
      if (!connectionId.isChildNullable) {
        checkStrongAbstractConnection(map.values.toMutableSet(), map.values.toMutableSet().map { it.clazz }.toSet(), connectionId.debugStr())
      }
    }

    indexes.assertConsistency(this)
  }

  private fun checkStrongConnection(connectionKeys: Set<Int>, entityFamilyClass: Int, connectionTo: Int) {
    val keys = connectionKeys.toMutableSet()
    val entityFamily = entitiesByType.entities[entityFamilyClass]
                       ?: error("Entity family ${entityFamilyClass.findWorkspaceEntity()} doesn't exist")
    entityFamily.entities.forEachIndexed { i, entity ->
      if (entity == null) return@forEachIndexed
      val removed = keys.remove(i)
      assert(removed) { "Entity $entity doesn't have a correct connection to ${connectionTo.findWorkspaceEntity()}" }
    }
    assert(keys.isEmpty()) { "Store is inconsistent" }
  }

  private fun checkStrongAbstractConnection(connectionKeys: Set<EntityId>, entityFamilyClasses: Set<Int>, debugInfo: String) {
    val keys = connectionKeys.toMutableSet()
    entityFamilyClasses.forEach { entityFamilyClass ->
      checkAllStrongConnections(entityFamilyClass, keys, debugInfo)
    }
    assert(keys.isEmpty()) { "Store is inconsistent. $debugInfo" }
  }

  private fun checkAllStrongConnections(entityFamilyClass: Int, keys: MutableSet<EntityId>, debugInfo: String) {
    val entityFamily = entitiesByType.entities[entityFamilyClass] ?: error("Entity family doesn't exist. $debugInfo")
    entityFamily.entities.forEachIndexed { i, entity ->
      if (entity == null) return@forEachIndexed
      val removed = keys.remove(entity.createPid())
      assert(removed) { "Entity $entity doesn't have a correct connection. $debugInfo" }
    }
  }

  internal fun assertConsistencyInStrictMode() {
    if (StrictMode.enabled) this.assertConsistency()
  }

  private fun assertResolvable(clazz: Int, id: Int) {
    assert(entitiesByType[clazz]?.get(id) != null) {
      "Reference to ${clazz.findEntityClass<WorkspaceEntity>()}-:-$id cannot be resolved"
    }
  }

  private fun assertCorrectEntityClass(connectionClass: Int, entityId: EntityId) {
    assert(connectionClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(entityId.clazz.findEntityClass<WorkspaceEntity>())) {
      "Entity storage with connection class ${connectionClass.findEntityClass<WorkspaceEntity>()} contains entity data of wrong type $entityId"
    }
  }
}

internal object ClassConversion {

  private val modifiableToEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val entityToEntityDataCache = HashMap<KClass<*>, KClass<*>>()
  private val entityDataToEntityCache = HashMap<Class<*>, Class<*>>()
  private val entityDataToModifiableEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val packageCache = HashMap<KClass<*>, String>()

  fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifiableEntityToEntity(clazz: KClass<out M>): KClass<T> {
    return modifiableToEntityCache.getOrPut(clazz) {
      try {
        Class.forName(getPackage(clazz) + clazz.java.simpleName.drop(10)).kotlin
      }
      catch (e: ClassNotFoundException) {
        error("Cannot get modifiable class for $clazz")
      }
    } as KClass<T>
  }

  fun <T : WorkspaceEntity> entityToEntityData(clazz: KClass<out T>): KClass<WorkspaceEntityData<T>> {
    return entityToEntityDataCache.getOrPut(clazz) {
      (Class.forName(clazz.java.name + "Data") as Class<WorkspaceEntityData<T>>).kotlin
    } as KClass<WorkspaceEntityData<T>>
  }

  fun <M : WorkspaceEntityData<out T>, T : WorkspaceEntity> entityDataToEntity(clazz: Class<out M>): Class<T> {
    return entityDataToEntityCache.getOrPut(clazz) {
      (Class.forName(clazz.name.dropLast(4)) as Class<T>)
    } as Class<T>
  }

  fun <D : WorkspaceEntityData<T>, T : WorkspaceEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableWorkspaceEntity<T>> {
    return entityDataToModifiableEntityCache.getOrPut(clazz) {
      Class.forName(getPackage(clazz) + "Modifiable" + clazz.java.simpleName.dropLast(4)).kotlin as KClass<ModifiableWorkspaceEntity<T>>
    } as KClass<ModifiableWorkspaceEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String = packageCache.getOrPut(clazz) { clazz.java.name.dropLastWhile { it != '.' } }
}
