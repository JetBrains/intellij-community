// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.Compressor
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.exceptions.PersistentIdAlreadyExistsException
import com.intellij.workspaceModel.storage.impl.exceptions.ReplaceBySourceException
import com.intellij.workspaceModel.storage.impl.external.EmptyExternalEntityMapping
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex.MutableVirtualFileIndex.Companion.VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal data class EntityReferenceImpl<E : WorkspaceEntity>(private val id: EntityId) : EntityReference<E>() {
  override fun resolve(storage: WorkspaceEntityStorage): E? {
    @Suppress("UNCHECKED_CAST")
    return (storage as AbstractEntityStorage).entityDataById(id)?.createEntity(storage) as? E
  }
}

internal class WorkspaceEntityStorageImpl constructor(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: StorageIndexes,
  consistencyCheckingMode: ConsistencyCheckingMode
) : AbstractEntityStorage(consistencyCheckingMode) {

  // This cache should not be transferred to other versions of storage
  private val persistentIdCache = ConcurrentHashMap<PersistentEntityId<*>, WorkspaceEntity>()

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val entity = persistentIdCache.getOrPut(id) { super.resolve(id) ?: NULl_ENTITY }
    return if (entity !== NULl_ENTITY) entity as E else null
  }

  companion object {
    private val NULl_ENTITY = ObjectUtils.sentinel("null entity", WorkspaceEntity::class.java)
    val EMPTY = WorkspaceEntityStorageImpl(ImmutableEntitiesBarrel.EMPTY, RefsTable(), StorageIndexes.EMPTY,
                                           ConsistencyCheckingMode.default())
  }
}

internal class WorkspaceEntityStorageBuilderImpl(
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable,
  override val indexes: MutableStorageIndexes,
  consistencyCheckingMode: ConsistencyCheckingMode
) : WorkspaceEntityStorageBuilder, AbstractEntityStorage(consistencyCheckingMode) {

  internal val changeLog = WorkspaceBuilderChangeLog()

  internal fun incModificationCount() {
    this.changeLog.modificationCount++
  }

  override val modificationCount: Long
    get() = this.changeLog.modificationCount

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
    @Suppress("UNCHECKED_CAST")
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
        val existingEntityData = entityDataByIdOrDie(ids.single())
        val existingEntity = existingEntityData.createEntity(this)
        removeEntity(existingEntity)
        LOG.error("""
          addEntity: persistent id already exists. Replacing entity with the new one.
          Persistent id: $persistentId
          
          Existing entity data: $existingEntityData
          New entity data: $pEntityData
          
          Broken consistency: $brokenConsistency
        """.trimIndent(), PersistentIdAlreadyExistsException(persistentId))
      }
    }

    // Add the change to changelog
    createAddEvent(pEntityData)

    // Update indexes
    indexes.entityAdded(pEntityData, this)

    LOG.debug { "New entity added: $clazz-${pEntityData.id}" }

    return pEntityData.createEntity(this)
  }

  override fun <M : ModifiableWorkspaceEntity<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    // Get entity data that will be modified
    @Suppress("UNCHECKED_CAST")
    val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>

    @Suppress("UNCHECKED_CAST")
    val modifiableEntity = copiedData.wrapAsModifiable(this) as M

    val beforePersistentId = if (e is WorkspaceEntityWithPersistentId) e.persistentId() else null

    val entityId = e.id

    val beforeParents = this.refs.getParentRefsOfChild(entityId)
    val beforeChildren = this.refs.getChildrenRefsOfParentBy(entityId).flatMap { (key, value) -> value.map { key to it } }

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

          val existingEntityData = entityDataByIdOrDie(ids.single())
          val existingEntity = existingEntityData.createEntity(this)
          removeEntity(existingEntity)
          LOG.error("""
            modifyEntity: persistent id already exists. Replacing entity with the new one.
            Old entity: $existingEntityData
            Persistent id: $copiedData
            
            Broken consistency: $brokenConsistency
          """.trimIndent(), PersistentIdAlreadyExistsException(newPersistentId))
        }
      }
      else {
        LOG.error("Persistent id expected for entity: $copiedData")
      }
    }

    // Add an entry to changelog
    addReplaceEvent(this, entityId, beforeChildren, beforeParents, copiedData)

    val updatedEntity = copiedData.createEntity(this)

    updatePersistentIdIndexes(updatedEntity, beforePersistentId, copiedData)

    return updatedEntity
  }

  internal fun <T : WorkspaceEntity> updatePersistentIdIndexes(updatedEntity: WorkspaceEntity,
                                                               beforePersistentId: PersistentEntityId<*>?,
                                                               copiedData: WorkspaceEntityData<T>) {
    val entityId = (updatedEntity as WorkspaceEntityBase).id
    if (updatedEntity is WorkspaceEntityWithPersistentId) {
      val newPersistentId = updatedEntity.persistentId()
      if (beforePersistentId != null && beforePersistentId != newPersistentId) {
        indexes.persistentIdIndex.index(entityId, newPersistentId)
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
      this.changeLog.addReplaceEvent(entityId, entity, emptyList(), emptyList(), emptyMap())

      updatePersistentIdIndexes(entity.createEntity(this), editingBeforePersistentId, entity)
    }
  }

  override fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T {
    @Suppress("UNCHECKED_CAST")
    val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>
    copiedData.entitySource = newSource

    val entityId = copiedData.createEntityId()

    this.changeLog.addChangeSourceEvent(entityId, copiedData)

    indexes.entitySourceIndex.index(entityId, newSource)
    newSource.virtualFileUrl?.let { indexes.virtualFileIndex.index(entityId, VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY, it) }
    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: WorkspaceEntity) {
    LOG.debug { "Removing ${e.javaClass}..." }
    e as WorkspaceEntityBase
    val removedEntities = removeEntity(e.id)

    removedEntities.forEach {
      LOG.debug { "Cascade removing: ${ClassToIntConverter.getClassOrDie(it.clazz)}-${it.arrayId}" }
      this.changeLog.addRemoveEvent(it)
    }
  }

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

    LOG.debug { "Performing replace by source" }

    // Map of entities in THIS builder with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val localMatchedEntities = ArrayListMultimap.create<Any, Pair<WorkspaceEntityData<out WorkspaceEntity>, EntityId>>()
    // Map of entities in replaceWith store with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val replaceWithMatchedEntities = ArrayListMultimap.create<Any, EntityId>()

    // Map of entities in THIS builder that have a reference to matched entity. Key is either hashCode or PersistentId
    val localUnmatchedReferencedNodes = ArrayListMultimap.create<Any, EntityId>()

    // Association of the EntityId in the local store to the EntityId in the remote store
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
    for ((_, entityId) in localMatchedEntities.values()) {
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

        // We should check if the issue still exists in this builder because it can be removed if it's referenced by another entity
        //   that had persistent id clash.
        val entityStillExists = localNodeAndId?.second?.let { this.entityDataById(it) != null } ?: false
        if (entityStillExists && localNodeAndId != null) {
          val (localNode, localNodeEntityId) = localNodeAndId
          // This entity already exists. Store the association of EntityIdss
          replaceMap[localNodeEntityId] = matchedEntityId
          val dataDiffersByProperties = !localNode.equalsIgnoringEntitySource(matchedEntityData)
          val dataDiffersByEntitySource = localNode.entitySource != matchedEntityData.entitySource
          if (localNode.hasPersistentId() && (dataDiffersByEntitySource || dataDiffersByProperties) && matchedEntityData.entitySource !is DummyParentEntitySource) {
            // Entity exists in local store, but has changes. Generate replace operation
            replaceOperation(matchedEntityData, replaceWith, localNode, matchedEntityId, dataDiffersByProperties, dataDiffersByEntitySource)
          }


          if (localNode == matchedEntityData) {
            this.indexes.updateExternalMappingForEntityId(matchedEntityId, localNodeEntityId, replaceWith.indexes)
          }
          // Remove added entity
          localMatchedEntities.remove(localNode.identificator(this), localNodeAndId)
        }
        else {
          // This is a new entity for this store. Perform add operation

          val persistentId = matchedEntityData.persistentId(this)
          if (persistentId != null) {
            val existingEntity = this.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.firstOrNull()
            if (existingEntity != null) {
              // Bad news, we have this persistent id already. CPP-22547
              // This may happened if local entity has entity source and remote entity has a different entity source
              // Technically we should throw an exception, but now we just remove local entity
              // Entity exists in local store, but has changes. Generate replace operation

              val localNode = this.entityDataByIdOrDie(existingEntity)

              val dataDiffersByProperties = !localNode.equalsIgnoringEntitySource(matchedEntityData)
              val dataDiffersByEntitySource = localNode.entitySource != matchedEntityData.entitySource

              replaceOperation(matchedEntityData, replaceWith, localNode, matchedEntityId, dataDiffersByProperties,
                               dataDiffersByEntitySource)

              replaceMap[existingEntity] = matchedEntityId
              continue
            }
          }

          val entityClass = ClassConversion.entityDataToEntity(matchedEntityData.javaClass).toClassId()
          val newEntity = this.entitiesByType.cloneAndAdd(matchedEntityData, entityClass)
          val newEntityId = matchedEntityId.copy(arrayId = newEntity.id)
          replaceMap[newEntityId] = matchedEntityId

          this.indexes.virtualFileIndex.updateIndex(matchedEntityId, newEntityId, replaceWith.indexes.virtualFileIndex)
          replaceWith.indexes.entitySourceIndex.getEntryById(matchedEntityId)?.also {
            this.indexes.entitySourceIndex.index(newEntityId, it)
          }
          replaceWith.indexes.persistentIdIndex.getEntryById(matchedEntityId)?.also {
            this.indexes.persistentIdIndex.index(newEntityId, it)
          }
          this.indexes.updateExternalMappingForEntityId(matchedEntityId, newEntityId, replaceWith.indexes)
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
      this.changeLog.addRemoveEvent(entityId)
    }

    val lostChildren = HashSet<EntityId>()

    LOG.debug { "4) Restore references between matched and unmatched entities" }
    //    At this moment the operation may fail because of inconsistency.
    //    E.g. after this operation we can't have non-null references without corresponding entity.
    //      This may happen if we remove the matched entity, but don't have a replacement for it.
    for (unmatchedId in localUnmatchedReferencedNodes.values()) {
      val replaceWithUnmatchedEntity = replaceWith.entityDataById(unmatchedId)
      if (replaceWithUnmatchedEntity == null || replaceWithUnmatchedEntity != this.entityDataByIdOrDie(unmatchedId)) {
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
              else rbsFailedAndReport("Cannot remove link to child entity. $connectionId", sourceFilter, initialStore, replaceWith, this)
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
                                                                  initialStore, replaceWith, this)
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
        rbsFailedAndReport("Trying to remove lost children. Cannot perform operation because some parents have strong ref to this child",
                           sourceFilter, initialStore, replaceWith, this)
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
              replaceWith, this)

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
    if (!this.brokenConsistency && !replaceWith.brokenConsistency) {
      this.assertConsistencyInStrictMode("Check after replaceBySource", sourceFilter, initialStore, replaceWith)
    }
    else {
      this.brokenConsistency = true
    }

    LOG.debug { "Replace by source finished" }
  }

  private fun replaceOperation(matchedEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                               replaceWith: AbstractEntityStorage,
                               localNode: WorkspaceEntityData<out WorkspaceEntity>,
                               matchedEntityId: EntityId,
                               dataDiffersByProperties: Boolean,
                               dataDiffersByEntitySource: Boolean) {
    val clonedEntity = matchedEntityData.clone()
    val persistentIdBefore = matchedEntityData.persistentId(replaceWith) ?: error("PersistentId expected for $matchedEntityData")
    clonedEntity.id = localNode.id
    val clonedEntityId = matchedEntityId.copy(arrayId = clonedEntity.id)
    this.entitiesByType.replaceById(clonedEntity, clonedEntityId.clazz)

    updatePersistentIdIndexes(clonedEntity.createEntity(this), persistentIdBefore, clonedEntity)
    this.indexes.virtualFileIndex.updateIndex(matchedEntityId, clonedEntityId, replaceWith.indexes.virtualFileIndex)
    replaceWith.indexes.entitySourceIndex.getEntryById(matchedEntityId)?.also { this.indexes.entitySourceIndex.index(clonedEntityId, it) }
    this.indexes.updateExternalMappingForEntityId(matchedEntityId, clonedEntityId, replaceWith.indexes)

    if (dataDiffersByProperties) {
      this.changeLog.addReplaceEvent(clonedEntityId, clonedEntity, emptyList(), emptyList(), emptyMap())
    }
    if (dataDiffersByEntitySource) {
      this.changeLog.addChangeSourceEvent(clonedEntityId, clonedEntity)
    }
  }

  private fun rbsFailedAndReport(message: String,
                                 sourceFilter: (EntitySource) -> Boolean,
                                 left: WorkspaceEntityStorage,
                                 right: WorkspaceEntityStorage,
                                 resulting: WorkspaceEntityStorageBuilder) {
    reportConsistencyIssue(message, ReplaceBySourceException(message), sourceFilter, left, right, resulting)
  }

  internal fun addDiffAndReport(message: String,
                                left: WorkspaceEntityStorage,
                                right: WorkspaceEntityStorage,
                                resulting: WorkspaceEntityStorageBuilder) {
    reportConsistencyIssue(message, AddDiffException(message), null, left, right, resulting)
  }

  sealed class EntityDataChange<T : WorkspaceEntityData<out WorkspaceEntity>> {
    data class Added<T : WorkspaceEntityData<out WorkspaceEntity>>(val entity: T) : EntityDataChange<T>()
    data class Removed<T : WorkspaceEntityData<out WorkspaceEntity>>(val entity: T) : EntityDataChange<T>()
    data class Replaced<T : WorkspaceEntityData<out WorkspaceEntity>>(val oldEntity: T, val newEntity: T) : EntityDataChange<T>()
  }

  override fun collectChanges(original: WorkspaceEntityStorage): Map<Class<*>, List<EntityChange<*>>> {
    val originalImpl = original as AbstractEntityStorage

    val res = HashMap<Class<*>, MutableList<EntityChange<*>>>()
    for ((entityId, change) in this.changeLog.changeLog) {
      when (change) {
        is ChangeEntry.AddEntity<*> -> {
          val addedEntity = change.entityData.createEntity(this) as WorkspaceEntityBase
          res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }.add(EntityChange.Added(addedEntity))
        }
        is ChangeEntry.RemoveEntity -> {
          val removedData = originalImpl.entityDataById(change.id) ?: continue
          val removedEntity = removedData.createEntity(originalImpl) as WorkspaceEntityBase
          res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }.add(EntityChange.Removed(removedEntity))
        }
        is ChangeEntry.ReplaceEntity<*> -> {
          val oldData = originalImpl.entityDataById(entityId) ?: continue
          val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
          val replaceToData = change.newData.createEntity(this) as WorkspaceEntityBase
          res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
            .add(EntityChange.Replaced(replacedData, replaceToData))
        }
        is ChangeEntry.ChangeEntitySource<*> -> {
          val oldData = originalImpl.entityDataById(entityId) ?: continue
          val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
          val replaceToData = change.newData.createEntity(this) as WorkspaceEntityBase
          res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
            .add(EntityChange.Replaced(replacedData, replaceToData))
        }
        is ChangeEntry.ReplaceAndChangeSource<*> -> {
          val oldData = originalImpl.entityDataById(entityId) ?: continue
          val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
          val replaceToData = change.dataChange.newData.createEntity(this) as WorkspaceEntityBase
          res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
            .add(EntityChange.Replaced(replacedData, replaceToData))
        }
      }
    }

    return res
  }

  override fun toStorage(): WorkspaceEntityStorageImpl {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    return WorkspaceEntityStorageImpl(newEntities, newRefs, newIndexes, consistencyCheckingMode)
  }

  override fun isEmpty(): Boolean = this.changeLog.changeLog.isEmpty()

  override fun addDiff(diff: WorkspaceEntityStorageDiffBuilder) {
    diff as WorkspaceEntityStorageBuilderImpl
    AddDiffOperation(this, diff).addDiff()
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T> {
    val mapping = indexes.externalMappings.computeIfAbsent(
      identifier) { MutableExternalEntityMappingImpl<T>() } as MutableExternalEntityMappingImpl<T>
    mapping.setTypedEntityStorage(this)
    return mapping
  }

  override fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex {
    val virtualFileIndex = indexes.virtualFileIndex
    virtualFileIndex.setTypedEntityStorage(this)
    return virtualFileIndex
  }

  fun removeExternalMapping(identifier: String) {
    indexes.externalMappings[identifier]?.clearMapping()
  }

  // modificationCount is not incremented
  internal fun removeEntity(idx: EntityId): Collection<EntityId> {
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
    val entityId = pEntityData.createEntityId()
    this.changeLog.addAddEvent(entityId, pEntityData)
  }

  /**
   * Cleanup references and accumulate hard linked entities in [accumulator]
   */
  private fun accumulateEntitiesToRemove(id: EntityId, accumulator: MutableSet<EntityId>) {
    val children = refs.getChildrenRefsOfParentBy(id)
    for ((connectionId, childrenIds) in children) {
      for (childId in childrenIds) {
        if (childId in accumulator) continue
        accumulator.add(childId)
        accumulateEntitiesToRemove(childId, accumulator)
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

    fun create(consistencyCheckingMode: ConsistencyCheckingMode): WorkspaceEntityStorageBuilderImpl {
      return from(WorkspaceEntityStorageImpl.EMPTY, consistencyCheckingMode)
    }

    fun from(storage: WorkspaceEntityStorage, consistencyCheckingMode: ConsistencyCheckingMode): WorkspaceEntityStorageBuilderImpl {
      storage as AbstractEntityStorage
      return when (storage) {
        is WorkspaceEntityStorageImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
          val copiedRefs = MutableRefsTable.from(storage.refs)
          val copiedIndex = storage.indexes.toMutable()
          WorkspaceEntityStorageBuilderImpl(copiedBarrel, copiedRefs, copiedIndex, consistencyCheckingMode)
        }
        is WorkspaceEntityStorageBuilderImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType.toImmutable())
          val copiedRefs = MutableRefsTable.from(storage.refs.toImmutable())
          val copiedIndexes = storage.indexes.toMutable()
          WorkspaceEntityStorageBuilderImpl(copiedBarrel, copiedRefs, copiedIndexes, consistencyCheckingMode)
        }
      }
    }

    internal fun <T : WorkspaceEntity> addReplaceEvent(builder: WorkspaceEntityStorageBuilderImpl, entityId: EntityId,
                                                       beforeChildren: List<Pair<ConnectionId, EntityId>>,
                                                       beforeParents: Map<ConnectionId, EntityId>,
                                                       copiedData: WorkspaceEntityData<T>) {
      val parents = builder.refs.getParentRefsOfChild(entityId)
      val unmappedChildren = builder.refs.getChildrenRefsOfParentBy(entityId)
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

      builder.changeLog.addReplaceEvent(entityId, copiedData, addedChildren, removedChildren, parentsMapRes)
    }
  }
}

internal sealed class AbstractEntityStorage(internal val consistencyCheckingMode: ConsistencyCheckingMode) : WorkspaceEntityStorage {

  internal abstract val entitiesByType: EntitiesBarrel
  internal abstract val refs: AbstractRefsTable
  internal abstract val indexes: StorageIndexes

  internal var brokenConsistency: Boolean = false

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    @Suppress("UNCHECKED_CAST")
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

  override fun <E : WorkspaceEntityWithPersistentId, R : WorkspaceEntity> referrers(id: PersistentEntityId<E>,
                                                                                    entityClass: Class<R>): Sequence<R> {
    val classId = entityClass.toClassId()

    @Suppress("UNCHECKED_CAST")
    return indexes.softLinks.getIdsByEntry(id).asSequence()
      .filter { it.clazz == classId }
      .map { entityDataByIdOrDie(it).createEntity(this) as R }
  }

  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val entityIds = indexes.persistentIdIndex.getIdsByEntry(id) ?: return null
    if (entityIds.isEmpty()) return null
    if (entityIds.size > 1) {
      val entities = entityIds.associateWith { this.entityDataById(it) }.entries.joinToString(
        "\n") { (k, v) -> "$k : $v : EntitySource: ${v?.entitySource}" }
      LOG.error("""Cannot resolve persistent id $id. The store contains ${entityIds.size} associated entities:
        |$entities
        |Broken consistency: $brokenConsistency
      """.trimMargin())
      @Suppress("UNCHECKED_CAST")
      return entityDataById(entityIds.first())?.createEntity(this) as E?
    }
    val entityId = entityIds.single()
    @Suppress("UNCHECKED_CAST")
    return entityDataById(entityId)?.createEntity(this) as E?
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

  override fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E> = EntityReferenceImpl((e as WorkspaceEntityBase).id)

  internal fun assertConsistency() {
    entitiesByType.assertConsistency(this)
    // Rules:
    //  1) Refs should not have links without a corresponding entity
    //    1.1) For abstract containers: EntityId has the class of ConnectionId
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

        //  1.1) For abstract containers: EntityId has the class of ConnectionId
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

        //  1.1) For abstract containers: EntityId has the class of ConnectionId
        assertCorrectEntityClass(connectionId.parentClass, parentId)
        assertCorrectEntityClass(connectionId.childClass, childId)
      }

      //  2) Connections satisfy connectionId requirements
      if (!connectionId.isParentNullable) {
        checkStrongAbstractConnection(map.keys.toMutableSet(), map.keys.toMutableSet().map { it.clazz }.toSet(), connectionId.debugStr())
      }
      if (!connectionId.isChildNullable) {
        checkStrongAbstractConnection(map.values.toMutableSet(), map.values.toMutableSet().map { it.clazz }.toSet(),
                                      connectionId.debugStr())
      }
    }

    indexes.assertConsistency(this)
  }

  private fun checkStrongConnection(connectionKeys: IntSet, entityFamilyClass: Int, connectionTo: Int) {
    if (connectionKeys.isEmpty()) return

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
    entityFamily.entities.forEach { entity ->
      if (entity == null) return@forEach
      val removed = keys.remove(entity.createEntityId())
      assert(removed) { "Entity $entity doesn't have a correct connection. $debugInfo" }
    }
  }

  internal fun assertConsistencyInStrictMode(message: String,
                                             sourceFilter: ((EntitySource) -> Boolean)?,
                                             left: WorkspaceEntityStorage?,
                                             right: WorkspaceEntityStorage?) {
    if (consistencyCheckingMode != ConsistencyCheckingMode.DISABLED) {
      try {
        this.assertConsistency()
      }
      catch (e: Throwable) {
        brokenConsistency = true
        val storage = if (this is WorkspaceEntityStorageBuilder) this.toStorage() as AbstractEntityStorage else this
        val report = { reportConsistencyIssue(message, e, sourceFilter, left, right, storage) }
        if (consistencyCheckingMode == ConsistencyCheckingMode.ASYNCHRONOUS) {
          consistencyChecker.execute(report)
        }
        else {
          report()
        }
      }
    }
  }

  internal fun reportConsistencyIssue(message: String,
                                      e: Throwable,
                                      sourceFilter: ((EntitySource) -> Boolean)?,
                                      left: WorkspaceEntityStorage?,
                                      right: WorkspaceEntityStorage?,
                                      resulting: WorkspaceEntityStorage) {
    val entitySourceFilter = if (sourceFilter != null) {
      val allEntitySources = (left as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries()?.toHashSet() ?: hashSetOf()
      allEntitySources.addAll((right as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries() ?: emptySet())
      allEntitySources.sortedBy { it.toString() }.fold("") { acc, source -> acc + if (sourceFilter(source)) "1" else "0" }
    }
    else null

    var _message = "$message\n\nEntity source filter: $entitySourceFilter"
    _message += "\n\nVersion: ${EntityStorageSerializerImpl.SERIALIZER_VERSION}"

    val dumpDirectory = getStoreDumpDirectory()
    _message += "\nSaving store content at: $dumpDirectory"
    val zipFile = serializeContentToFolder(dumpDirectory, left, right, resulting)
    if (zipFile != null) {
      val attachment = Attachment("workspaceModelDump.zip", zipFile.readBytes(), "Zip of workspace model store")
      attachment.isIncluded = true
      LOG.error(_message, e, attachment)
    }
    else {
      LOG.error(_message, e)
    }
  }

  private fun serializeContentToFolder(contentFolder: Path,
                                       left: WorkspaceEntityStorage?,
                                       right: WorkspaceEntityStorage?,
                                       resulting: WorkspaceEntityStorage): File? {
    left?.let { serializeEntityStorage(contentFolder.resolve("Left_Store"), it) }
    right?.let { serializeEntityStorage(contentFolder.resolve("Right_Store"), it) }
    serializeEntityStorage(contentFolder.resolve("Res_Store"), resulting)
    serializeContent(contentFolder.resolve("ClassToIntConverter")) { serializer, stream -> serializer.serializeClassToIntConverter(stream) }

    if (right is WorkspaceEntityStorageBuilder) {
      serializeContent(contentFolder.resolve("Right_Diff_Log")) { serializer, stream ->
        right as WorkspaceEntityStorageBuilderImpl
        serializer.serializeDiffLog(stream, right.changeLog)
      }
    }

    return if (!executingOnTC()) {
      val zipFile = contentFolder.parent.resolve(contentFolder.name + ".zip").toFile()
      Compressor.Zip(zipFile).use { it.addDirectory(contentFolder.toFile()) }
      FileUtil.delete(contentFolder)
      zipFile
    }
    else null
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

    private val consistencyChecker = AppExecutorUtil.createBoundedApplicationPoolExecutor("Check workspace model consistency", 1)
  }
}

/** This function exposes `brokenConsistency` property to the outside and should be removed along with the property itself */
val WorkspaceEntityStorage.isConsistent: Boolean
  get() = !(this as AbstractEntityStorage).brokenConsistency

internal object ClassConversion {

  private val modifiableToEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val entityToEntityDataCache = HashMap<KClass<*>, KClass<*>>()
  private val entityDataToEntityCache = HashMap<Class<*>, Class<*>>()
  private val entityDataToModifiableEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val packageCache = HashMap<KClass<*>, String>()

  fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifiableEntityToEntity(clazz: KClass<out M>): KClass<T> {
    @Suppress("UNCHECKED_CAST")
    return modifiableToEntityCache.getOrPut(clazz) {
      try {
        Class.forName(getPackage(clazz) + clazz.java.simpleName.drop(10), true, clazz.java.classLoader).kotlin
      }
      catch (e: ClassNotFoundException) {
        error("Cannot get modifiable class for $clazz")
      }
    } as KClass<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkspaceEntity> entityToEntityData(clazz: KClass<out T>): KClass<WorkspaceEntityData<T>> {
    return entityToEntityDataCache.getOrPut(clazz) {
      (Class.forName(clazz.java.name + "Data", true, clazz.java.classLoader) as Class<WorkspaceEntityData<T>>).kotlin
    } as KClass<WorkspaceEntityData<T>>
  }

  @Suppress("UNCHECKED_CAST")
  fun <M : WorkspaceEntityData<out T>, T : WorkspaceEntity> entityDataToEntity(clazz: Class<out M>): Class<T> {
    return entityDataToEntityCache.getOrPut(clazz) {
      (Class.forName(clazz.name.dropLast(4), true, clazz.classLoader) as Class<T>)
    } as Class<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <D : WorkspaceEntityData<T>, T : WorkspaceEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableWorkspaceEntity<T>> {
    return entityDataToModifiableEntityCache.getOrPut(clazz) {
      Class.forName(getPackage(clazz) + "Modifiable" + clazz.java.simpleName.dropLast(4), true,
                    clazz.java.classLoader).kotlin as KClass<ModifiableWorkspaceEntity<T>>
    } as KClass<ModifiableWorkspaceEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String = packageCache.getOrPut(clazz) { clazz.java.name.dropLastWhile { it != '.' } }
}
