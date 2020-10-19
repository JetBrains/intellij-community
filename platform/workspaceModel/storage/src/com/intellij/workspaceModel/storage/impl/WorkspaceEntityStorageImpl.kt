// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ObjectUtils
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.exceptions.*
import com.intellij.workspaceModel.storage.impl.external.EmptyExternalEntityMapping
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.concurrent.ConcurrentHashMap
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

  // This cache should not be transferred to other versions of storage
  private val persistentIdCache = ConcurrentHashMap<PersistentEntityId<*>, WorkspaceEntity>()

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val entity = persistentIdCache.getOrPut(id) { super.resolve(id) ?: NULl_ENTITY }
    return if (entity !== NULl_ENTITY) entity as E else null
  }

  companion object {
    private val NULl_ENTITY = ObjectUtils.sentinel("null entity", WorkspaceEntity::class.java)
    val EMPTY = WorkspaceEntityStorageImpl(ImmutableEntitiesBarrel.EMPTY, RefsTable(), StorageIndexes.EMPTY)
  }
}

internal class WorkspaceEntityStorageBuilderImpl(
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable,
  override val indexes: MutableStorageIndexes
) : WorkspaceEntityStorageBuilder, AbstractEntityStorage() {

  private val newChangeLog: LinkedHashMap<EntityId, Pair<ChangeEntry, ChangeEntry.ChangeEntitySource<*>?>> = LinkedHashMap()

  internal val changeLogImpl: MutableList<ChangeEntry> = mutableListOf()

  private val changeLog: List<ChangeEntry>
    get() = changeLogImpl

  private inline fun newUpdateChangeLog(updater: (MutableMap<EntityId, Pair<ChangeEntry, ChangeEntry.ChangeEntitySource<*>?>>) -> Unit) {
    updater(newChangeLog)
    incModificationCount()
  }

  private inline fun updateChangeLog(updater: (MutableList<ChangeEntry>) -> Unit) {
    updater(changeLogImpl)
    //incModificationCount()
  }

  internal fun incModificationCount() {
    modificationCount++
  }

  internal sealed class ChangeEntry {
    data class AddEntity<E : WorkspaceEntity>(
      val entityData: WorkspaceEntityData<E>,
      val clazz: Int,
    ) : ChangeEntry()

    data class RemoveEntity(val id: EntityId) : ChangeEntry()

    data class ChangeEntitySource<E : WorkspaceEntity>(val newData: WorkspaceEntityData<E>) : ChangeEntry()

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
    val pEntityData = entityDataClass.java.getDeclaredConstructor().newInstance()
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
      val ids = indexes.persistentIdIndex.getIdsByEntry(persistentId)
      if (ids != null && ids.isNotEmpty()) {
        // Oh oh. This persistent id exists already
        // Fallback strategy: remove existing entity with all it's references
        val existingEntity = entityDataByIdOrDie(ids.single()).createEntity(this)
        removeEntity(existingEntity)
        LOG.error("""
          addEntity: persistent id already exists. Replacing entity with the new one.
          Persistent id: $persistentId
          
          Existing entity data: $existingEntity
          New entity data: $pEntityData
        """.trimIndent(), PersistentIdAlreadyExistsException(persistentId))
      }
    }

    // Add the change to changelog
    createAddEvent(pEntityData)

    // Update indexes
    indexes.entityAdded(pEntityData, this)

    return pEntityData.createEntity(this)
  }

  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    // Get entity data that will be modified
    val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>
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
      val newPersistentId = copiedData.persistentId(this)
      if (newPersistentId != null) {
        val ids = indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
        if (beforePersistentId != newPersistentId && ids != null && ids.isNotEmpty()) {
          // Oh oh. This persistent id exists already.
          // Remove an existing entity and replace it with the new one.

          val existingEntity = entityDataByIdOrDie(ids.single()).createEntity(this)
          removeEntity(existingEntity)
          LOG.error("""
            modifyEntity: persistent id already exists. Replacing entity with the new one.
            Old entity: $existingEntity
            Persistent id: $copiedData
          """.trimIndent(), PersistentIdAlreadyExistsException(newPersistentId))
        }
      }
      else {
        LOG.error("Persistent id expected for entity: $copiedData")
      }
    }

    // Add an entry to changelog
    addReplaceEvent(pid, beforeChildren, beforeParents, copiedData)

    val updatedEntity = copiedData.createEntity(this)

    updatePersistentIdIndexes(updatedEntity, beforePersistentId, copiedData)

    return updatedEntity
  }

  private fun <T : WorkspaceEntity> addReplaceEvent(pid: EntityId,
                                                    beforeChildren: List<Pair<ConnectionId, EntityId>>,
                                                    beforeParents: Map<ConnectionId, EntityId>,
                                                    copiedData: WorkspaceEntityData<T>) {
    val parents = this.refs.getParentRefsOfChild(pid)
    val unmappedChildren = this.refs.getChildrenRefsOfParentBy(pid)
    val children = unmappedChildren.flatMap { (key, value) -> value.map { key to it } }

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

    newUpdateChangeLog {
      val existingChange = it[pid]
      val replaceEvent = ChangeEntry.ReplaceEntity(copiedData, addedChildren, removedChildren, parentsMapRes)
      if (existingChange == null) {
        it[pid] = replaceEvent to null
      }
      else {
        when (val firstChange = existingChange.first) {
          is ChangeEntry.AddEntity<*> -> it[pid] = ChangeEntry.AddEntity(copiedData, pid.clazz) to null
          is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
          is ChangeEntry.ChangeEntitySource<*> -> it[pid] = replaceEvent to firstChange
          is ChangeEntry.ReplaceEntity<*> -> {
            val newAddedChildren = (firstChange.newChildren.toSet() - removedChildren.toSet()).toList()
            val newRemovedChildren = (firstChange.removedChildren.toSet() - addedChildren.toSet()).toList()
            val newChangedParents = firstChange.modifiedParents + parentsMapRes
            it[pid] = ChangeEntry.ReplaceEntity(copiedData, newAddedChildren, newRemovedChildren, newChangedParents) to existingChange.second
          }
        }
      }
    }
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
    val idsWithSoftRef = HashSet(indexes.softLinks.getIdsByEntry(beforePersistentId))
    for (entityId in idsWithSoftRef) {
      val entity = this.entitiesByType.getEntityDataForModification(entityId)
      val editingBeforePersistentId = entity.persistentId(this)
      (entity as SoftLinkable).updateLink(beforePersistentId, newPersistentId)

      // Add an entry to changelog
      updateChangeLog { it.add(ChangeEntry.ReplaceEntity(entity, emptyList(), emptyList(), emptyMap())) }

      newUpdateChangeLog {
        val existingChange = it[entityId]
        val replaceEvent = ChangeEntry.ReplaceEntity(entity, emptyList(), emptyList(), emptyMap())
        if (existingChange == null) {
          it[entityId] = replaceEvent to null
        }
        else {
          when (val firstChange = existingChange.first) {
            is ChangeEntry.AddEntity<*> -> {
              it[entityId] = ChangeEntry.AddEntity(entity, entityId.clazz) to null
            }
            is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $entity")
            is ChangeEntry.ChangeEntitySource<*> -> it[entityId] = replaceEvent to firstChange
            is ChangeEntry.ReplaceEntity<*> -> { /* Keep the old event */ }
          }
        }
      }

      updatePersistentIdIndexes(entity.createEntity(this), editingBeforePersistentId, entity)
    }
  }

  override fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>
    copiedData.entitySource = newSource

    val entityId = copiedData.createPid()
    updateChangeLog { it.add(ChangeEntry.ChangeEntitySource(copiedData)) }

    newUpdateChangeLog {
      val existingChange = it[entityId]
      val changeSourceEvent = ChangeEntry.ChangeEntitySource(copiedData)
      if (existingChange == null) {
        it[entityId] = changeSourceEvent to null
      }
      else {
        when (val firstChange = existingChange.first) {
          is ChangeEntry.AddEntity<*> -> {
            it[entityId] = ChangeEntry.AddEntity(copiedData, entityId.clazz) to null
          }
          is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $copiedData")
          is ChangeEntry.ChangeEntitySource<*> -> it[entityId] = changeSourceEvent to null
          is ChangeEntry.ReplaceEntity<*> -> it[entityId] = firstChange to changeSourceEvent
        }
      }
    }

    indexes.entitySourceIndex.index(entityId, newSource)

    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: WorkspaceEntity) {
    e as WorkspaceEntityBase
    val removedEntities = removeEntity(e.id)
    updateChangeLog {
      removedEntities.forEach { removedEntityId -> it.add(ChangeEntry.RemoveEntity(removedEntityId)) }
    }

    newUpdateChangeLog {
      removedEntities.forEach { removedEntityId ->
        val existingChange = it[removedEntityId]
        val removeEvent = ChangeEntry.RemoveEntity(removedEntityId)
        if (existingChange == null) {
          it[removedEntityId] = removeEvent to null
        }
        else {
          when (val firstChange = existingChange.first) {
            is ChangeEntry.AddEntity<*> -> it.remove(removedEntityId)
            is ChangeEntry.RemoveEntity -> LOG.error("Trying to remove the entity twice. $removedEntityId")
            is ChangeEntry.ChangeEntitySource<*> -> it[removedEntityId] = removeEvent to null
            is ChangeEntry.ReplaceEntity<*> -> it[removedEntityId] = removeEvent to null
          }
        }
      }
    }
  }

  override fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E> = EntityReferenceImpl((e as WorkspaceEntityBase).id)

  private fun ArrayListMultimap<Any, Pair<WorkspaceEntityData<out WorkspaceEntity>, EntityId>>.find(entity: WorkspaceEntityData<out WorkspaceEntity>,
                                                                                    storage: AbstractEntityStorage): Pair<WorkspaceEntityData<out WorkspaceEntity>, EntityId>? {
    val possibleValues = this[entity.identificator(storage)]
    val persistentId = entity.persistentId(storage)
    return if (persistentId != null) {
      possibleValues.singleOrNull()
    }
    else {
      possibleValues.find { it.first == entity }
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
   *
   *
   *  TODO  Spacial cases: when source filter returns true for all entity sources.
   */
  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: WorkspaceEntityStorage) {
    replaceWith as AbstractEntityStorage

    val initialStore = this.toStorage()
    val initialChangeLogSize = this.changeLog.size

    LOG.debug { "Performing replace by source" }

    // Map of entities in THIS builder with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val localMatchedEntities = ArrayListMultimap.create<Any, Pair<WorkspaceEntityData<out WorkspaceEntity>, EntityId>>()
    // Map of entities in replaceWith store with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val replaceWithMatchedEntities = ArrayListMultimap.create<Any, EntityId>()

    // Map of entities in THIS builder that have a reference to matched entity. Key is either hashCode or PersistentId
    val localUnmatchedReferencedNodes = ArrayListMultimap.create<Any, EntityId>()

    // Association of the PId in the local store to the PId in the remote store
    val replaceMap = HashBiMap.create<EntityId, EntityId>()

    LOG.debug { "1) Traverse all entities and store matched only" }
    this.indexes.entitySourceIndex.entries().filter { sourceFilter(it) }.forEach { entitySource ->
      this.indexes.entitySourceIndex.getIdsByEntry(entitySource)?.forEach {
        val entityData = this.entityDataByIdOrDie(it)
        localMatchedEntities.put(entityData.identificator(this), entityData to it)
      }
    }

    LOG.debug { "1.1) Cleanup references" }
    //   If the reference leads to the matched entity, we can safely remove this reference.
    //   If the reference leads to the unmatched entity, we should save the entity to try to restore the reference later.
    for ((matchedEntityData, entityId) in localMatchedEntities.values()) {
      // Traverse parents of the entity
      for ((connectionId, parentId) in this.refs.getParentRefsOfChild(entityId)) {
        val parentEntity = this.entityDataByIdOrDie(parentId)
        if (sourceFilter(parentEntity.entitySource)) {
          // Remove the connection between matched entities
          this.refs.removeParentToChildRef(connectionId, parentId, entityId)
        }
        else {
          // Save the entity for restoring reference to it later
          localUnmatchedReferencedNodes.put(parentEntity.identificator(this), parentId)
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
            localUnmatchedReferencedNodes.put(childEntity.identificator(this), childId)
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
                             ?.map { replaceWith.entityDataByIdOrDie(it) to it } ?: continue
      for ((matchedEntityData, matchedEntityId) in entityDataList) {
        replaceWithMatchedEntities.put(matchedEntityData.identificator(replaceWith), matchedEntityId)

        // Find if the entity exists in local store
        val localNodeAndId = localMatchedEntities.find(matchedEntityData, replaceWith)
        if (localNodeAndId != null) {
          val (localNode, localNodePid) = localNodeAndId
          // This entity already exists. Store the association of pids
          replaceMap[localNodePid] = matchedEntityId
          val dataDiffersByProperties = !localNode.equalsIgnoringEntitySource(matchedEntityData)
          val dataDiffersByEntitySource = localNode.entitySource != matchedEntityData.entitySource
          if (localNode.hasPersistentId() && (dataDiffersByEntitySource || dataDiffersByProperties)) {
            // Entity exists in local store, but has changes. Generate replace operation
            val clonedEntity = matchedEntityData.clone()
            val persistentIdBefore = matchedEntityData.persistentId(replaceWith) ?: error("PersistentId expected for $matchedEntityData")
            clonedEntity.id = localNode.id
            val clonedEntityId = matchedEntityId.copy(arrayId = clonedEntity.id)
            this.entitiesByType.replaceById(clonedEntity as WorkspaceEntityData<WorkspaceEntity>, clonedEntityId.clazz)
            val pid = clonedEntityId

            updatePersistentIdIndexes(clonedEntity.createEntity(this), persistentIdBefore, clonedEntity)
            replaceWith.indexes.virtualFileIndex.getVirtualFileUrlInfoByEntityId(matchedEntityId)
              .groupBy({ it.propertyName }, { it.vfu })
              .forEach { (property, vfus) ->
                this.indexes.virtualFileIndex.index(pid, property, vfus)
              }
            replaceWith.indexes.entitySourceIndex.getEntryById(matchedEntityId)?.also { this.indexes.entitySourceIndex.index(pid, it) }
            this.indexes.updateExternalMappingForEntityId(matchedEntityId, pid, replaceWith.indexes)

            if (dataDiffersByProperties) {
              updateChangeLog { it.add(ChangeEntry.ReplaceEntity(clonedEntity, emptyList(), emptyList(), emptyMap())) }
              newUpdateChangeLog {
                val existingChange = it[pid]
                val replaceEvent = ChangeEntry.ReplaceEntity(clonedEntity, emptyList(), emptyList(), emptyMap())
                if (existingChange == null) {
                  it[pid] = replaceEvent to null
                }
                else {
                  when (val firstChange = existingChange.first) {
                    is ChangeEntry.AddEntity<*> -> {
                      it[pid] = ChangeEntry.AddEntity(clonedEntity, pid.clazz) to null
                    }
                    is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $clonedEntity")
                    is ChangeEntry.ChangeEntitySource<*> -> it[pid] = replaceEvent to firstChange
                    is ChangeEntry.ReplaceEntity<*> -> { /* Keep the old event */ }
                  }
                }
              }
            }
            if (dataDiffersByEntitySource) {
              updateChangeLog { it.add(ChangeEntry.ChangeEntitySource(clonedEntity)) }
              newUpdateChangeLog {
                val existingChange = it[pid]
                val changeSourceEvent = ChangeEntry.ChangeEntitySource(clonedEntity)
                if (existingChange == null) {
                  it[pid] = changeSourceEvent to null
                }
                else {
                  when (val firstChange = existingChange.first) {
                    is ChangeEntry.AddEntity<*> -> {
                      it[pid] = ChangeEntry.AddEntity(clonedEntity, pid.clazz) to null
                    }
                    is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $clonedEntity")
                    is ChangeEntry.ChangeEntitySource<*> -> it[pid] = changeSourceEvent to null
                    is ChangeEntry.ReplaceEntity<*> -> it[pid] = firstChange to changeSourceEvent
                  }
                }
              }
            }
          }


          if (localNode == matchedEntityData) {
            this.indexes.updateExternalMappingForEntityId(matchedEntityId, localNodePid, replaceWith.indexes)
          }
          // Remove added entity
          localMatchedEntities.remove(localNode.identificator(this), localNodeAndId)
        }
        else {
          // This is a new entity for this store. Perform add operation
          val entityClass = ClassConversion.entityDataToEntity(matchedEntityData.javaClass).toClassId()
          val newEntity = this.entitiesByType.cloneAndAdd(matchedEntityData as WorkspaceEntityData<WorkspaceEntity>, entityClass)
          val newPid = matchedEntityId.copy(arrayId = newEntity.id)
          replaceMap[newPid] = matchedEntityId

          replaceWith.indexes.virtualFileIndex.getVirtualFileUrlInfoByEntityId(matchedEntityId)
            .groupBy({ it.propertyName }, { it.vfu })
            .forEach { (property, vfus) ->
              this.indexes.virtualFileIndex.index(newPid, property, vfus)
            }
          replaceWith.indexes.entitySourceIndex.getEntryById(matchedEntityId)?.also { this.indexes.entitySourceIndex.index(newPid, it) }
          replaceWith.indexes.persistentIdIndex.getEntryById(matchedEntityId)?.also { this.indexes.persistentIdIndex.index(newPid, it) }
          this.indexes.updateExternalMappingForEntityId(matchedEntityId, newPid, replaceWith.indexes)
          if (newEntity is SoftLinkable) indexes.updateSoftLinksIndex(newEntity)

          createAddEvent(newEntity)
        }
      }
    }

    LOG.debug { "3) Remove old entities" }
    //   After previous operation localMatchedEntities contain only entities that exist in local store, but don't exist in replaceWith store.
    //   Those entities should be just removed.
    for ((localEntity, entityId) in localMatchedEntities.values()) {
      val entityClass = ClassConversion.entityDataToEntity(localEntity.javaClass).toClassId()
      this.entitiesByType.remove(localEntity.id, entityClass)
      indexes.removeFromIndices(entityId)
      if (localEntity is SoftLinkable) indexes.removeFromSoftLinksIndex(localEntity)
      updateChangeLog { it.add(ChangeEntry.RemoveEntity(entityId)) }
      newUpdateChangeLog {
        val existingChange = it[entityId]
        val removeEvent = ChangeEntry.RemoveEntity(entityId)
        if (existingChange == null) {
          it[entityId] = removeEvent to null
        }
        else {
          when (val firstChange = existingChange.first) {
            is ChangeEntry.AddEntity<*> -> it.remove(entityId)
            is ChangeEntry.RemoveEntity -> LOG.error("Trying to remove the entity twice. $entityId")
            is ChangeEntry.ChangeEntitySource<*> -> it[entityId] = removeEvent to null
            is ChangeEntry.ReplaceEntity<*> -> it[entityId] = removeEvent to null
          }
        }
      }
    }

    val lostChildren = HashSet<EntityId>()

    LOG.debug { "4) Restore references between matched and unmatched entities" }
    //    At this moment the operation may fail because of inconsistency.
    //    E.g. after this operation we can't have non-null references without corresponding entity.
    //      This may happen if we remove the matched entity, but don't have a replacement for it.
    for (unmatchedId in localUnmatchedReferencedNodes.values()) {
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
            else {
              this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
              lostChildren += unmatchedId
            }
          }
        }
        for ((connectionId, childIds) in this.refs.getChildrenRefsOfParentBy(unmatchedId)) {
          for (childId in childIds) {
            val child = this.entityDataById(childId)
            if (child == null) {
              if (connectionId.canRemoveChild()) {
                this.refs.removeParentToChildRef(connectionId, unmatchedId, childId)
              }
              else rbsFailedAndReport("Cannot remove link to child entity. $connectionId", sourceFilter, initialStore, replaceWith, this, initialChangeLogSize)
            }
          }
        }
      }
      else {
        // ----------------- Update parent references ---------------

        val removedConnections = HashMap<ConnectionId, EntityId>()
        // Remove parents in local store
        for ((connectionId, parentId) in this.refs.getParentRefsOfChild(unmatchedId)) {
          val parentData = this.entityDataById(parentId)
          if (parentData != null && !sourceFilter(parentData.entitySource)) continue
          this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
          removedConnections[connectionId] = parentId
        }

        // Transfer parents from replaceWith storage
        for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(unmatchedId)) {
          if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId).entitySource)) continue
          val localParentId = replaceMap.inverse().getValue(parentId)
          this.refs.updateParentOfChild(connectionId, unmatchedId, localParentId)
          removedConnections.remove(connectionId)
        }

        // TODO: 05.06.2020 The similar logic should exist for children references
        // Check not restored connections
        for ((connectionId, parentId) in removedConnections) {
          if (!connectionId.canRemoveParent()) rbsFailedAndReport("Cannot restore connection to $parentId; $connectionId", sourceFilter,
                                                                  initialStore, replaceWith, this, initialChangeLogSize)
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

    // Some children left without parents. We should delete these children as well.
    for (entityId in lostChildren) {
      if (this.refs.getParentRefsOfChild(entityId).any { !it.key.isChildNullable }) {
        rbsFailedAndReport("Trying to remove lost children. Cannot perform operation because some parents have strong ref to this child", sourceFilter, initialStore, replaceWith, this, initialChangeLogSize)
      }
      removeEntity(entityId)
    }

    LOG.debug { "5) Restore references in matching ids" }
    for (nodeId in replaceWithMatchedEntities.values()) {
      for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(nodeId)) {
        if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId).entitySource)) {
          // replaceWith storage has a link to unmatched entity. We should check if we can "transfer" this link to the current storage
          if (!connectionId.isParentNullable) {
            val localParent = this.entityDataById(parentId)
            if (localParent == null) rbsFailedAndReport(
              "Cannot link entities. Child entity doesn't have a parent after operation; $connectionId", sourceFilter, initialStore,
              replaceWith, this, initialChangeLogSize)

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
    this.assertConsistencyInStrictModeForRbs("Check after replaceBySource", sourceFilter, initialStore, replaceWith, this, initialChangeLogSize)

    LOG.debug { "Replace by source finished" }
  }

  private fun rbsFailedAndReport(message: String,
                                 sourceFilter: (EntitySource) -> Boolean,
                                 left: WorkspaceEntityStorage,
                                 right: WorkspaceEntityStorage,
                                 resulting: WorkspaceEntityStorageBuilder,
                                 initialChangeLogSize: Int) {
    reportConsistencyIssue(message, ReplaceBySourceException(message), sourceFilter, left, right, resulting, initialChangeLogSize)
  }

  private fun addDiffAndReport(message: String,
                               left: WorkspaceEntityStorage,
                               right: WorkspaceEntityStorage,
                               resulting: WorkspaceEntityStorageBuilder,
                               initialChangeLogSize: Int) {
    reportConsistencyIssue(message, AddDiffException(message), null, left, right, resulting, initialChangeLogSize)
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
        is ChangeEntry.ChangeEntitySource<*> -> {
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
    newUpdateChangeLog { it.clear() }
  }

  override fun toStorage(): WorkspaceEntityStorageImpl {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    val storage = WorkspaceEntityStorageImpl(newEntities, newRefs, newIndexes)
    return storage
  }

  override fun isEmpty(): Boolean = changeLogImpl.isEmpty()

  override fun addDiff(diff: WorkspaceEntityStorageDiffBuilder) {

    if (this === diff) LOG.error("Trying to apply diff to itself")
    diff as WorkspaceEntityStorageBuilderImpl
    val initialStorage = this.toStorage()
    val replaceMap = HashBiMap.create<EntityId, EntityId>()
    val initialChangeLogSize = 0

    val diffLog = diff.newChangeLog
    for ((entityId, changePair) in diffLog) {
      val firstChange = changePair.first
      when (firstChange) {
        is ChangeEntry.AddEntity<out WorkspaceEntity> -> {
          firstChange as ChangeEntry.AddEntity<WorkspaceEntity>

          //region Unique persistent id check
          val newPersistentId = firstChange.entityData.persistentId(this)
          if (newPersistentId != null) {
            val existingIds = this.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
            if (existingIds != null && existingIds.isNotEmpty()) {
              // This persistent id exists already.
              val existingEntity = this.entityDataByIdOrDie(existingIds.single()).createEntity(this)
              removeEntity(existingEntity)
              addDiffAndReport(
                """
                  addDiff: persistent id already exists. Replacing entity with the new one.
                  Persistent id: $newPersistentId
                  
                  Existing entity data: $existingEntity
                  New entity data: ${firstChange.entityData}
                  """.trimIndent(), initialStorage, diff, this, initialChangeLogSize)
            }
          }
          //endregion

          val sourceEntityId = firstChange.entityData.createPid()


          // Adding new entity
          val targetEntityData: WorkspaceEntityData<WorkspaceEntity>
          val targetEntityId: EntityId
          val idFromReplaceMap = replaceMap[sourceEntityId]
          if (idFromReplaceMap != null) {
            // Okay, we need to add the entity at the particular id
            targetEntityData = entitiesByType.cloneAndAddAt(firstChange.entityData, idFromReplaceMap)
            targetEntityId = idFromReplaceMap
          }
          else {
            // Add new entity to store (without references)
            targetEntityData = entitiesByType.cloneAndAdd(firstChange.entityData, firstChange.clazz)
            targetEntityId = targetEntityData.createPid()
            replaceMap[sourceEntityId] = targetEntityId
          }
          // Restore links to soft references
          if (targetEntityData is SoftLinkable) indexes.updateSoftLinksIndex(targetEntityData)

          // Restore children references
          val allSourceChildren = diff.refs.getChildrenRefsOfParentBy(sourceEntityId)
          for ((connectionId, sourceChildrenIds) in allSourceChildren) {
            val targetChildrenIds = mutableSetOf<EntityId>()
            for (sourceChildId in sourceChildrenIds) {
              if (diffLog[sourceChildId]?.first is ChangeEntry.AddEntity<*>) {
                // This particular entity is added in the same transaction.
                val possibleTargetChildId = replaceMap[sourceChildId]
                if (possibleTargetChildId != null) {
                  // Entity was already added to the structure
                  targetChildrenIds += possibleTargetChildId
                }
                else {
                  // This entity isn't added yet. Add a placeholder
                  val placeholderId = entitiesByType.book(sourceChildId.clazz)
                  replaceMap[sourceChildId] = placeholderId
                  targetChildrenIds += placeholderId
                }
              }
              else {
                if (this.entityDataById(sourceChildId) != null) {
                  targetChildrenIds += sourceChildId
                }
                else if (!connectionId.canRemoveChild()) {
                  addDiffAndReport("Cannot restore dependency. $connectionId, $sourceChildId", initialStorage, diff, this,
                                   initialChangeLogSize)
                }
              }
            }
            this.refs.updateChildrenOfParent(connectionId, targetEntityId, targetChildrenIds)
          }


          // Restore parent references
          val allParents = diff.refs.getParentRefsOfChild(sourceEntityId)
          for ((connectionId, sourceParentId) in allParents) {
            val targetParentId = if (diffLog[sourceParentId]?.first is ChangeEntry.AddEntity<*>) {
              replaceMap[sourceParentId] ?: run {
                // This entity isn't added to the current builder yet. Add a placeholder
                val placeholderId = entitiesByType.book(sourceParentId.clazz)
                replaceMap[sourceParentId] = placeholderId
                placeholderId
              }
            }
            else {
              if (this.entityDataById(sourceParentId) != null) sourceParentId
              else {
                if (!connectionId.canRemoveParent()) {
                  addDiffAndReport("Cannot restore dependency. $connectionId, $sourceParentId", initialStorage, diff, this,
                                   initialChangeLogSize)
                }
                null
              }
            }
            if (targetParentId != null) {
              refs.updateParentOfChild(connectionId, targetEntityId, targetParentId)
            }
          }

          indexes.updateIndices(firstChange.entityData.createPid(), targetEntityData, diff)
          updateChangeLog {
            it.add(ChangeEntry.AddEntity(targetEntityData, firstChange.clazz))
          }
          newUpdateChangeLog {

            // XXX: This check should exist, but some tests fails with it.
            //if (targetEntityId in it) LOG.error("Trying to add entity again. ")

            it[targetEntityId] = ChangeEntry.AddEntity(targetEntityData, firstChange.clazz) to null
          }
        }
        is ChangeEntry.RemoveEntity -> {
          val outdatedId = firstChange.id
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)
          indexes.removeFromIndices(usedPid)
          replaceMap.inverse().remove(usedPid)
          if (this.entityDataById(usedPid) != null) {
            removeEntity(usedPid)
          }
          updateChangeLog { it.add(ChangeEntry.RemoveEntity(usedPid)) }
          newUpdateChangeLog {
            val existingChange = it[entityId]
            val removeEvent = ChangeEntry.RemoveEntity(entityId)
            if (existingChange == null) {
              it[entityId] = removeEvent to null
            }
            else {
              when (existingChange.first) {
                is ChangeEntry.AddEntity<*> -> it.remove(entityId)
                is ChangeEntry.RemoveEntity -> LOG.error("Trying to remove the entity twice. $entityId")
                is ChangeEntry.ChangeEntitySource<*> -> it[entityId] = removeEvent to null
                is ChangeEntry.ReplaceEntity<*> -> it[entityId] = removeEvent to null
              }
            }
          }
        }
        is ChangeEntry.ReplaceEntity<out WorkspaceEntity> -> {
          // TODO second change (entity source)
          firstChange as ChangeEntry.ReplaceEntity<WorkspaceEntity>

          val updatedNewChildren = firstChange.newChildren.map { (connectionId, id) -> connectionId to replaceMap.getOrDefault(id, id) }
          val updatedRemovedChildren = firstChange.removedChildren.map { (connectionId, id) ->
            connectionId to replaceMap.getOrDefault(id, id)
          }
          val updatedModifiedParents = firstChange.modifiedParents.mapValues {
            if (it.value == null) null
            else replaceMap.getOrDefault(it.value, it.value)
          }

          val sourceEntityId = firstChange.newData.createPid()
          val targetEntityId = replaceMap.getOrDefault(sourceEntityId, sourceEntityId)
          val newTargetEntityData = firstChange.newData.clone()
          newTargetEntityData.id = targetEntityId.arrayId

          val newPersistentId = firstChange.newData.persistentId(this)
          if (newPersistentId != null) {
            val existingIds = this.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
            if (existingIds != null && existingIds.isNotEmpty() && existingIds.single() != newTargetEntityData.createPid()) {
              // This persistent id exists already.
              val existingEntity = this.entityDataByIdOrDie(existingIds.single()).createEntity(this)
              removeEntity(existingEntity)
              addDiffAndReport(
                """
                  addDiff: persistent id already exists. Removing old entity
                  Persistent id: $newPersistentId
                  
                  Existing entity data: $existingEntity
                  New entity data: ${firstChange.newData}
                  """.trimIndent(), initialStorage, diff, this, initialChangeLogSize)
            }
          }

          // We don't modify entity that doesn't exist in this version of storage
          val existingTargetEntityData = this.entityDataById(targetEntityId) ?: continue

          // Replace entity doesn't modify entitySource
          newTargetEntityData.entitySource = existingTargetEntityData.entitySource

          indexes.updateIndices(sourceEntityId, newTargetEntityData, diff)
          updateChangeLog {
            it.add(ChangeEntry.ReplaceEntity(newTargetEntityData, updatedNewChildren, updatedRemovedChildren, updatedModifiedParents))
          }
          newUpdateChangeLog {
            val existingChange = it[targetEntityId]
            val replaceEvent = ChangeEntry.ReplaceEntity(newTargetEntityData, updatedNewChildren, updatedRemovedChildren,
                                                         updatedModifiedParents)
            if (existingChange == null) {
              it[targetEntityId] = replaceEvent to null
            }
            else {
              when (val firstChange = existingChange.first) {
                is ChangeEntry.AddEntity<*> -> {
                  it[targetEntityId] = ChangeEntry.AddEntity(newTargetEntityData, targetEntityId.clazz) to null
                }
                is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $newTargetEntityData")
                is ChangeEntry.ChangeEntitySource<*> -> it[targetEntityId] = replaceEvent to firstChange
                is ChangeEntry.ReplaceEntity<*> -> {
                  val newAddedChildren = (firstChange.newChildren.toSet() - updatedRemovedChildren.toSet()).toList()
                  val newRemovedChildren = (firstChange.removedChildren.toSet() - updatedNewChildren.toSet()).toList()
                  val newChangedParents = firstChange.modifiedParents + updatedModifiedParents
                  it[targetEntityId] = ChangeEntry.ReplaceEntity(newTargetEntityData, newAddedChildren, newRemovedChildren,
                                                                 newChangedParents) to existingChange.second
                }
              }
            }
          }

          // Can we move this method before adding change log?

          val newEntityId = newTargetEntityData.createPid()
          val oldPersistentId = entityDataById(newEntityId)?.persistentId(this)

          /// Replace entity data. id should not be changed
          entitiesByType.replaceById(newTargetEntityData, sourceEntityId.clazz)

          // Restore soft references
          updatePersistentIdIndexes(newTargetEntityData.createEntity(this), oldPersistentId, newTargetEntityData)


          val addedChildrenMapRaw = HashMultimap.create<ConnectionId, EntityId>()
          firstChange.newChildren.forEach { addedChildrenMapRaw.put(it.first, it.second) }

          val removedChildrenMapRaw = HashMultimap.create<ConnectionId, EntityId>()
          firstChange.removedChildren.forEach { removedChildrenMapRaw.put(it.first, it.second) }

          val updatedModifiedParentsRaw = firstChange.modifiedParents.mapValues { it.value }

          val existingChildrenRaw = this.refs.getChildrenRefsOfParentBy(newEntityId)

          for ((connectionId, children) in existingChildrenRaw) {
            // Take current children....
            val mutableChildren = children.toMutableSet()

            val addedChildrenSet = addedChildrenMapRaw[connectionId] ?: emptySet()
            for (addedChild in addedChildrenSet) {
              if (diffLog[addedChild]?.first is ChangeEntry.AddEntity<*>) {
                val possibleNewChildId = replaceMap[addedChild]
                if (possibleNewChildId != null) {
                  mutableChildren += possibleNewChildId
                }
                else {
                  val bookedChildId = entitiesByType.book(addedChild.clazz)
                  replaceMap[addedChild] = bookedChildId
                  mutableChildren += bookedChildId
                }
              }
              else {
                if (this.entityDataById(addedChild) != null) {
                  mutableChildren += addedChild
                }
              }
            }

            val removedChildrenSet = removedChildrenMapRaw[connectionId] ?: emptySet()
            for (removedChild in removedChildrenSet) {
              val removed = mutableChildren.remove(removedChild)
              if (!removed) addDiffAndReport("Trying to remove child that isn't present", initialStorage,
                                                                     diff, this, initialChangeLogSize)
            }

            // .... Update if something changed
            if (children != mutableChildren) {
              refs.updateChildrenOfParent(connectionId, newEntityId, mutableChildren)
            }
            addedChildrenMapRaw.removeAll(connectionId)
            removedChildrenMapRaw.removeAll(connectionId)
          }

          // Do we have more children to remove? This should not happen
          if (!removedChildrenMapRaw.isEmpty) addDiffAndReport("Trying to remove children that aren't present", initialStorage,
                                                               diff, this, initialChangeLogSize)

          // Do we have more children to add? Add them
          for ((connectionId, children) in addedChildrenMapRaw.asMap()) {
            val mutableChildren = children.toMutableSet()

            for (child in children) {
              if (diffLog[child]?.first is ChangeEntry.AddEntity<*>) {
                val possibleNewChildId = replaceMap[child]
                if (possibleNewChildId != null) {
                  mutableChildren += possibleNewChildId
                }
                else {
                  val bookedChildId = entitiesByType.book(child.clazz)
                  replaceMap[child] = bookedChildId
                  mutableChildren += bookedChildId
                }
              }
              else {
                if (this.entityDataById(child) != null) {
                  mutableChildren += child
                }
              }
            }

            refs.updateChildrenOfParent(connectionId, newEntityId, mutableChildren)
          }


          val modifiedParentsMapRaw = updatedModifiedParentsRaw.toMutableMap()
          val existingParentsRaw = refs.getParentRefsOfChild(newEntityId)
          for ((connectionId, existingParent) in existingParentsRaw) {
            if (connectionId in modifiedParentsMapRaw) {
              val newParent = modifiedParentsMapRaw.getValue(connectionId)
              if (newParent == null) {
                // This child doesn't have a parent anymore
                if (!connectionId.canRemoveParent()) addDiffAndReport("Cannrt restore some dependencies; $connectionId", initialStorage,
                                                                      diff, this, initialChangeLogSize)
                else refs.removeParentToChildRef(connectionId, existingParent, newEntityId)
              }
              else {
                if (diffLog[newParent]?.first is ChangeEntry.AddEntity<*>) {
                  var possibleNewParent = replaceMap[newParent]
                  if (possibleNewParent == null) {
                    possibleNewParent = entitiesByType.book(newParent.clazz)
                    replaceMap[newParent] = possibleNewParent
                  }
                  refs.updateParentOfChild(connectionId, newEntityId, possibleNewParent)
                }
                else {
                  if (this.entityDataById(newParent) != null) {
                    refs.updateParentOfChild(connectionId, newEntityId, newParent)
                  }
                  else {
                    if (!connectionId.canRemoveParent()) addDiffAndReport("Cannot restore some dependencies; $connectionId", initialStorage,
                                                                          diff, this, initialChangeLogSize)
                    refs.removeParentToChildRef(connectionId, existingParent, newEntityId)
                  }
                }
              }
              modifiedParentsMapRaw.remove(connectionId)
            }
          }

          // Any new parents? Add them
          for ((connectionId, parentId) in modifiedParentsMapRaw) {
            if (parentId == null) continue
            if (diffLog[parentId]?.first is ChangeEntry.AddEntity<*>) {
              var possibleNewParent = replaceMap[parentId]
              if (possibleNewParent == null) {
                possibleNewParent = entitiesByType.book(parentId.clazz)
                replaceMap[parentId] = possibleNewParent
              }
              refs.updateParentOfChild(connectionId, newEntityId, possibleNewParent)
            }
            else {
              if (this.entityDataById(parentId) != null) {
                refs.updateParentOfChild(connectionId, newEntityId, parentId)
              }
            }
          }
        }
        is ChangeEntry.ChangeEntitySource<out WorkspaceEntity> -> {
          firstChange as ChangeEntry.ChangeEntitySource<WorkspaceEntity>

          val outdatedId = firstChange.newData.createPid()
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)

          // We don't modify entity that isn't exist in this version of storage
          val existingEntityData = this.entityDataById(usedPid)
          if (existingEntityData != null) {
            val newEntitySource = firstChange.newData.entitySource
            existingEntityData.entitySource = newEntitySource
            this.indexes.entitySourceIndex.index(usedPid, newEntitySource)
            updateChangeLog { it.add(ChangeEntry.ChangeEntitySource(existingEntityData)) }
            newUpdateChangeLog {
              val existingChange = it[usedPid]
              val changeSourceEvent = ChangeEntry.ChangeEntitySource(existingEntityData)
              if (existingChange == null) {
                it[usedPid] = changeSourceEvent to null
              }
              else {
                when (val firstChange = existingChange.first) {
                  is ChangeEntry.AddEntity<*> -> {
                    it[usedPid] = ChangeEntry.AddEntity(existingEntityData, usedPid.clazz) to null
                  }
                  is ChangeEntry.RemoveEntity -> LOG.error("Trying to update removed entity. Skip change event. $existingEntityData")
                  is ChangeEntry.ChangeEntitySource<*> -> it[usedPid] = changeSourceEvent to null
                  is ChangeEntry.ReplaceEntity<*> -> it[usedPid] = firstChange to changeSourceEvent
                }
              }
            }
          }
        }
      }
    }
    indexes.applyExternalMappingChanges(diff, replaceMap)

    // Assert consistency
    this.assertConsistencyInStrictModeForRbs("Check after add Diff", { true }, initialStorage, diff, this, initialChangeLogSize)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T> {
    val mapping = indexes.externalMappings.computeIfAbsent(identifier) { MutableExternalEntityMappingImpl<T>() } as MutableExternalEntityMappingImpl<T>
    mapping.setTypedEntityStorage(this)
    return mapping
  }

  fun removeExternalMapping(identifier: String) {
    indexes.externalMappings[identifier]?.clearMapping()
  }

  // modificationCount is not incremented
  private fun removeEntity(idx: EntityId): Collection<EntityId> {
    val accumulator: MutableSet<EntityId> = mutableSetOf(idx)

    accumulateEntitiesToRemove(idx, accumulator)

    for (id in accumulator) {
      val entityData = entityDataById(id)
      if (entityData is SoftLinkable) indexes.removeFromSoftLinksIndex(entityData)
      entitiesByType.remove(id.arrayId, id.clazz)
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

  internal fun <T : WorkspaceEntity> createAddEvent(pEntityData: WorkspaceEntityData<T>) {
    val pid = pEntityData.createPid()
    updateChangeLog { it.add(ChangeEntry.AddEntity(pEntityData, pid.clazz)) }

    newUpdateChangeLog {
      if (pid in it) LOG.error("Trying to add entity again. $pEntityData")
      it[pid] = ChangeEntry.AddEntity(pEntityData, pid.clazz) to null
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
    if (pids.size > 1) {
      val entities = pids.associateWith { this.entityDataById(it) }.entries.joinToString("\n") { (k, v) -> "$k : $v : EntitySource: ${v?.entitySource}" }
      LOG.error("""Cannot resolve persistent id $id. The store contains ${pids.size} associated entities:
        |$entities
      """.trimMargin())
      return entityDataById(pids.first())?.createEntity(this) as E?
    }
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

  override fun getVirtualFileUrlIndex(): VirtualFileUrlIndex {
    indexes.virtualFileIndex.setTypedEntityStorage(this)
    return indexes.virtualFileIndex
  }

  internal fun assertConsistency() {
    entitiesByType.assertConsistency(this)
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

  private fun checkStrongConnection(connectionKeys: IntSet, entityFamilyClass: Int, connectionTo: Int) {

    var counter = 0
    val entityFamily = entitiesByType.entityFamilies[entityFamilyClass]
                       ?: error("Entity family ${entityFamilyClass.findWorkspaceEntity()} doesn't exist")
    entityFamily.entities.forEachIndexed { i, entity ->
      if (entity == null) return@forEachIndexed
      assert(i in connectionKeys) { "Entity $entity doesn't have a correct connection to ${connectionTo.findWorkspaceEntity()}" }
      counter++
    }

    assert(counter == connectionKeys.size) { "Store is inconsistent" }
  }

  private fun checkStrongAbstractConnection(connectionKeys: Set<EntityId>, entityFamilyClasses: Set<Int>, debugInfo: String) {
    val keys = connectionKeys.toMutableSet()
    entityFamilyClasses.forEach { entityFamilyClass ->
      checkAllStrongConnections(entityFamilyClass, keys, debugInfo)
    }
    assert(keys.isEmpty()) { "Store is inconsistent. $debugInfo" }
  }

  private fun checkAllStrongConnections(entityFamilyClass: Int, keys: MutableSet<EntityId>, debugInfo: String) {
    val entityFamily = entitiesByType.entityFamilies[entityFamilyClass] ?: error("Entity family doesn't exist. $debugInfo")
    entityFamily.entities.forEachIndexed { i, entity ->
      if (entity == null) return@forEachIndexed
      val removed = keys.remove(entity.createPid())
      assert(removed) { "Entity $entity doesn't have a correct connection. $debugInfo" }
    }
  }

  internal fun assertConsistencyInStrictModeForRbs(message: String, sourceFilter: (EntitySource) -> Boolean, left: WorkspaceEntityStorage, right: WorkspaceEntityStorage, resulting: WorkspaceEntityStorageBuilder, initialChangeLogSize: Int) {
    if (StrictMode.rbsEnabled) {
      try {
        this.assertConsistency()
      }
      catch (e: Throwable) {
        reportConsistencyIssue(message, e, sourceFilter, left, right, resulting, initialChangeLogSize)
      }
    }
  }

  internal fun reportConsistencyIssue(message: String,
                                      e: Throwable,
                                      sourceFilter: ((EntitySource) -> Boolean)?,
                                      left: WorkspaceEntityStorage,
                                      right: WorkspaceEntityStorage,
                                      resulting: WorkspaceEntityStorageBuilder,
                                      initialChangeLogSize: Int) {
    val entitySourceFilter = if (sourceFilter != null) {
      val allEntitySources = (left as AbstractEntityStorage).indexes.entitySourceIndex.entries().toHashSet()
      allEntitySources.addAll((right as AbstractEntityStorage).indexes.entitySourceIndex.entries())
      allEntitySources.sortedBy { it.toString() }.fold("") { acc, source -> acc + if (sourceFilter(source)) "1" else "0" }
    }
    else null

    val displayText = "Content of the workspace model in binary format"
    var _message = "$message\n\n!Please include all attachments to the report!"
    _message += "\n\nEntity source filter: $entitySourceFilter"
    _message += "\n\nVersion: ${EntityStorageSerializerImpl.SERIALIZER_VERSION}"

    val leftAttachment = left.asAttachment("Left_Store", displayText)
    val rightAttachment = right.asAttachment("Right_Store", displayText)
    val resAttachment = resulting.asAttachment("Res_Store", displayText)
    val classToIntConverterAttachment = createAttachment("ClassToIntConverter", "Class to int converter") { serializer, stream ->
      serializer.serializeClassToIntConverter(stream)
    }
    val leftLogAttachment = createAttachment("Left_Diff_Log", "Log of left builder") { serializer, stream ->
      serializer.serializeDiffLog(stream, (resulting as WorkspaceEntityStorageBuilderImpl).changeLogImpl.take(initialChangeLogSize))
    }

    var attachments = arrayOf(leftAttachment, rightAttachment, resAttachment, classToIntConverterAttachment, leftLogAttachment)
    if (right is WorkspaceEntityStorageBuilder) {
      attachments += createAttachment("Right_Diff_Log", "Log of right builder") { serializer, stream ->
        right as WorkspaceEntityStorageBuilderImpl
        serializer.serializeDiffLog(stream, right.changeLogImpl)
      }
    }
    LOG.error(_message, e, *attachments)
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

  companion object {
    val LOG = logger<AbstractEntityStorage>()
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
        Class.forName(getPackage(clazz) + clazz.java.simpleName.drop(10), true, clazz.java.classLoader).kotlin
      }
      catch (e: ClassNotFoundException) {
        error("Cannot get modifiable class for $clazz")
      }
    } as KClass<T>
  }

  fun <T : WorkspaceEntity> entityToEntityData(clazz: KClass<out T>): KClass<WorkspaceEntityData<T>> {
    return entityToEntityDataCache.getOrPut(clazz) {
      (Class.forName(clazz.java.name + "Data", true, clazz.java.classLoader) as Class<WorkspaceEntityData<T>>).kotlin
    } as KClass<WorkspaceEntityData<T>>
  }

  fun <M : WorkspaceEntityData<out T>, T : WorkspaceEntity> entityDataToEntity(clazz: Class<out M>): Class<T> {
    return entityDataToEntityCache.getOrPut(clazz) {
      (Class.forName(clazz.name.dropLast(4), true, clazz.classLoader) as Class<T>)
    } as Class<T>
  }

  fun <D : WorkspaceEntityData<T>, T : WorkspaceEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableWorkspaceEntity<T>> {
    return entityDataToModifiableEntityCache.getOrPut(clazz) {
      Class.forName(getPackage(clazz) + "Modifiable" + clazz.java.simpleName.dropLast(4), true, clazz.java.classLoader).kotlin as KClass<ModifiableWorkspaceEntity<T>>
    } as KClass<ModifiableWorkspaceEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String = packageCache.getOrPut(clazz) { clazz.java.name.dropLastWhile { it != '.' } }
}
