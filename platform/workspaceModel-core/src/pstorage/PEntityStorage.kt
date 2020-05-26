// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.workspace.api.*
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex.MutableExternalEntityIndex
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


internal typealias ChildrenConnectionsInfo = Map<ConnectionId, Set<PId>>
internal typealias ParentConnectionsInfo = Map<ConnectionId, PId>

internal fun ChildrenConnectionsInfo.replaceByMapChildren(replaceMap: Map<PId, PId>): ChildrenConnectionsInfo {
  return mapValues { it.value.map { v -> replaceMap.getOrDefault(v, v) }.toSet() }
}

internal fun ParentConnectionsInfo.replaceByMapParent(replaceMap: Map<PId, PId>): ParentConnectionsInfo {
  return mapValues { replaceMap.getOrDefault(it.value, it.value) }
}

internal class PEntityReference<E : TypedEntity>(private val id: PId) : EntityReference<E>() {
  override fun resolve(storage: TypedEntityStorage): E {
    return (storage as AbstractPEntityStorage).entityDataByIdOrDie(id).createEntity(storage) as E
  }
}

internal class PEntityStorage constructor(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: StorageIndexes
) : AbstractPEntityStorage() {
  override fun assertConsistency() {
    entitiesByType.assertConsistency()

    assertConsistencyBase()
  }

  companion object {
    val EMPTY = PEntityStorage(ImmutableEntitiesBarrel.EMPTY, RefsTable(), StorageIndexes.EMPTY)
  }
}

internal class PEntityStorageBuilder(
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable,
  override val indexes: MutableStorageIndexes
) : TypedEntityStorageBuilder, AbstractPEntityStorage() {

  private val changeLogImpl: MutableList<ChangeEntry> = mutableListOf()

  private val changeLog: List<ChangeEntry>
    get() = changeLogImpl

  internal inline fun updateChangeLog(updater: (MutableList<ChangeEntry>) -> Unit) {
    updater(changeLogImpl)
    modificationCount++
  }

  internal sealed class ChangeEntry {
    data class AddEntity<E : TypedEntity>(
      val entityData: PEntityData<E>,
      val clazz: Int,
      val children: ChildrenConnectionsInfo,
      val parents: ParentConnectionsInfo
    ) : ChangeEntry()

    data class RemoveEntity(val id: PId) : ChangeEntry()

    data class ReplaceEntity<E : TypedEntity>(
      val newData: PEntityData<E>,
      val children: ChildrenConnectionsInfo,
      val parents: ParentConnectionsInfo
    ) : ChangeEntry()
  }

  override var modificationCount: Long = 0
    private set

  override fun assertConsistency() {
    assertConsistencyBase()
  }

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> addEntity(clazz: Class<M>,
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
    (modifiableEntity as PModifiableTypedEntity<*>).allowModifications {
      modifiableEntity.initializer()
    }

    // Add the change to changelog
    createAddEvent(pEntityData)

    // Update indexes
    indexes.entityAdded(pEntityData, this)

    return pEntityData.createEntity(this)
  }

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    // Get entity data that will be modified
    val copiedData = entitiesByType.getEntityDataForModification((e as PTypedEntity).id) as PEntityData<T>
    val modifiableEntity = copiedData.wrapAsModifiable(this) as M

    val beforePersistentId = if (e is TypedEntityWithPersistentId) e.persistentId() else null
    val beforeSoftLinks = if (copiedData is PSoftLinkable) copiedData.getLinks() else null

    // Execute modification code
    (modifiableEntity as PModifiableTypedEntity<*>).allowModifications {
      modifiableEntity.change()
    }

    // Add an entry to changelog
    val pid = e.id
    val parents = this.refs.getParentRefsOfChild(pid)
    val children = this.refs.getChildrenRefsOfParentBy(pid)
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(copiedData, children, parents)) }

    val updatedEntity = copiedData.createEntity(this)

    if (updatedEntity is TypedEntityWithPersistentId) indexes.persistentIdIndex.index(pid, updatedEntity.persistentId())
    indexes.updateSoftReferences(beforePersistentId, beforeSoftLinks, copiedData, this)

    return updatedEntity
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = entitiesByType.getEntityDataForModification((e as PTypedEntity).id) as PEntityData<T>
    copiedData.entitySource = newSource

    val pid = e.id
    val parents = this.refs.getParentRefsOfChild(pid)
    val children = this.refs.getChildrenRefsOfParentBy(pid)
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(copiedData, children, parents)) }

    indexes.entitySourceIndex.index(copiedData.createPid(), newSource)
    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: TypedEntity) {
    e as PTypedEntity

    removeEntity(e.id)
    updateChangeLog { it.add(ChangeEntry.RemoveEntity(e.id)) }
  }

  override fun <E : TypedEntity> createReference(e: E): EntityReference<E> = PEntityReference((e as PTypedEntity).id)

  private fun ArrayListMultimap<Any, PEntityData<out TypedEntity>>.find(entity: PEntityData<out TypedEntity>,
                                                                        storage: AbstractPEntityStorage): PEntityData<out TypedEntity>? {
    val possibleValues = this[entity.identificator(storage)]
    val persistentId = entity.persistentId(storage)
    return if (persistentId != null) {
      possibleValues.find { it.persistentId(this@PEntityStorageBuilder) == persistentId }
    }
    else {
      possibleValues.find { it == entity }
    }
  }

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage) {
    replaceWith as AbstractPEntityStorage

    LOG.debug { "Performing replace by source" }

    // Map of entities in THIS builder with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val localMatchedEntities = ArrayListMultimap.create<Any, PEntityData<out TypedEntity>>()
    // Map of entities in replaceWith store with the entitySource that matches the predicate. Key is either hashCode or PersistentId
    val replaceWithMatchedEntities = ArrayListMultimap.create<Any, PEntityData<out TypedEntity>>()

    // Map of entities in THIS builder that have a reference to matched entity. Key is either hashCode or PersistentId
    val localUnmatchedReferencedNodes = ArrayListMultimap.create<Any, PEntityData<out TypedEntity>>()

    // Association of the PId in the local store to the PId in the remote store
    val replaceMap = HashBiMap.create<PId, PId>()

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
            clonedEntity.id = localNode.id
            this.entitiesByType.replaceById(clonedEntity as PEntityData<TypedEntity>, clonedEntity.createPid().clazz)
            val pid = clonedEntity.createPid()
            val parents = this.refs.getParentRefsOfChild(pid)
            val children = this.refs.getChildrenRefsOfParentBy(pid)
            indexes.updateIndices(oldPid, pid, replaceWith)
            updateChangeLog { it.add(ChangeEntry.ReplaceEntity(clonedEntity, children, parents)) }
          }
          // Remove added entity
          localMatchedEntities.remove(localNode.identificator(this), localNode)
        }
        else {
          // This is a new entity for this store. Perform add operation
          val entityClass = ClassConversion.entityDataToEntity(matchedEntityData.javaClass).toClassId()
          val newEntity = this.entitiesByType.cloneAndAdd(matchedEntityData as PEntityData<TypedEntity>, entityClass)
          val newPid = newEntity.createPid()
          replaceMap[newPid] = oldPid
          indexes.updateIndices(oldPid, newPid, replaceWith)
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
            if (connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY
                || connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY
                || connectionId.isChildNullable) {
              this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
            }
            else error("Cannot link old entity to the new one")
          }
        }
        for ((connectionId, childIds) in this.refs.getChildrenRefsOfParentBy(unmatchedId)) {
          for (childId in childIds) {
            val child = this.entityDataById(childId)
            if (child == null) {
              if (connectionId.isParentNullable) {
                this.refs.removeParentToChildRef(connectionId, unmatchedId, childId)
              }
              else error("Cannot link old entity to the new one")
            }
          }
        }
      }
      else {
        for ((connectionId, parentId) in this.refs.getParentRefsOfChild(unmatchedId)) {
          if (!sourceFilter(this.entityDataByIdOrDie(parentId).entitySource)) continue
          this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
        }

        for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(unmatchedId)) {
          if (!sourceFilter(this.entityDataByIdOrDie(parentId).entitySource)) continue
          val localParentId = replaceMap.inverse().getValue(parentId)
          this.refs.updateParentOfChild(connectionId, unmatchedId, localParentId)
        }

        for ((connectionId, childrenId) in this.refs.getChildrenRefsOfParentBy(unmatchedId)) {
          for (childId in childrenId) {
            if (!sourceFilter(this.entityDataByIdOrDie(childId).entitySource)) continue
            this.refs.removeParentToChildRef(connectionId, unmatchedId, childId)
          }
        }

        for ((connectionId, childrenId) in replaceWith.refs.getChildrenRefsOfParentBy(unmatchedId)) {
          for (childId in childrenId) {
            if (!sourceFilter(this.entityDataByIdOrDie(childId).entitySource)) continue
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
        if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId).entitySource)) continue

        val localChildId = replaceMap.inverse().getValue(nodeId)
        val localParentId = replaceMap.inverse().getValue(parentId)

        this.refs.updateParentOfChild(connectionId, localChildId, localParentId)
      }
    }

    LOG.debug { "Replace by source finished" }
  }

  sealed class EntityDataChange<T : PEntityData<out TypedEntity>> {
    data class Added<T : PEntityData<out TypedEntity>>(val entity: T) : EntityDataChange<T>()
    data class Removed<T : PEntityData<out TypedEntity>>(val entity: T) : EntityDataChange<T>()
    data class Replaced<T : PEntityData<out TypedEntity>>(val oldEntity: T, val newEntity: T) : EntityDataChange<T>()
  }

  override fun collectChanges(original: TypedEntityStorage): Map<Class<*>, List<EntityChange<*>>> {

    // TODO: 27.03.2020 Since we have an instance of original storage, we actually can provide a method without an argument

    val originalImpl = original as PEntityStorage
    //this can be optimized to avoid creation of entity instances which are thrown away and copying the results from map to list
    // LinkedHashMap<Long, EntityChange<T>>
    val changes = LinkedHashMap<PId, Pair<Class<*>, EntityChange<*>>>()
    for (change in changeLog) {
      when (change) {
        is ChangeEntry.AddEntity<*> -> {
          val addedEntity = change.entityData.createEntity(this) as PTypedEntity
          changes[addedEntity.id] = addedEntity.id.clazz.findEntityClass<TypedEntity>() to EntityChange.Added(addedEntity)
        }
        is ChangeEntry.RemoveEntity -> {
          val removedData = originalImpl.entityDataById(change.id)
          val oldChange = changes.remove(change.id)
          if (oldChange?.second !is EntityChange.Added && removedData != null) {
            val removedEntity = removedData.createEntity(originalImpl) as PTypedEntity
            changes[removedEntity.id] = change.id.clazz.findEntityClass<TypedEntity>() to EntityChange.Removed(removedEntity)
          }
        }
        is ChangeEntry.ReplaceEntity<*> -> {
          val id = change.newData.createPid()
          val oldChange = changes.remove(id)
          if (oldChange?.second is EntityChange.Added) {
            val addedEntity = change.newData.createEntity(this) as PTypedEntity
            changes[addedEntity.id] = addedEntity.id.clazz.findEntityClass<TypedEntity>() to EntityChange.Added(addedEntity)
          }
          else {
            val oldData = originalImpl.entityDataById(id)
            if (oldData != null) {
              val replacedData = oldData.createEntity(originalImpl) as PTypedEntity
              val replaceToData = change.newData.createEntity(this) as PTypedEntity
              changes[replacedData.id] = replacedData.id.clazz.findEntityClass<TypedEntity>() to EntityChange.Replaced(
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

  override fun toStorage(): PEntityStorage {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    return PEntityStorage(newEntities, newRefs, newIndexes)
  }

  override fun isEmpty(): Boolean = changeLogImpl.isEmpty()

  override fun addDiff(diff: TypedEntityStorageDiffBuilder): Map<TypedEntity, TypedEntity> {
    val replaceMap = HashMap<PId, PId>()
    val builder = diff as PEntityStorageBuilder
    val diffLog = builder.changeLog
    for (change in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity<out TypedEntity> -> {
          change as ChangeEntry.AddEntity<TypedEntity>

          val updatedChildren = change.children.replaceByMapChildren(replaceMap)
          val updatedParents = change.parents.replaceByMapParent(replaceMap)

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
          indexes.removeFromIndices(outdatedId)
          if (this.entityDataById(usedPid) != null) {
            removeEntity(usedPid)
            replaceMap.remove(outdatedId, usedPid)
          }
          updateChangeLog { it.add(ChangeEntry.RemoveEntity(usedPid)) }
        }
        is ChangeEntry.ReplaceEntity<out TypedEntity> -> {
          change as ChangeEntry.ReplaceEntity<TypedEntity>

          val updatedChildren = change.children.replaceByMapChildren(replaceMap)
          val updatedParents = change.parents.replaceByMapParent(replaceMap)

          val outdatedId = change.newData.createPid()
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)
          val newData = change.newData.clone()
          newData.id = usedPid.arrayId

          indexes.updateIndices(outdatedId, newData.createPid(), builder)
          updateChangeLog { it.add(ChangeEntry.ReplaceEntity(newData, updatedChildren, updatedParents)) }
          if (this.entityDataById(usedPid) != null) {
            replaceEntityWithRefs(newData, outdatedId.clazz, updatedChildren, updatedParents)
          }
        }
      }
    }
    indexes.applyExternalIndexChanges(diff)
    // TODO: 27.03.2020 Here should be consistency check
    val res = HashMap<TypedEntity, TypedEntity>()
    replaceMap.forEach { (oldId, newId) ->
      if (oldId != newId) {
        res[diff.entityDataByIdOrDie(oldId).createEntity(diff)] = this.entityDataByIdOrDie(newId).createEntity(this)
      }
    }
    return res
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getOrCreateExternalIndex(identifier: String): MutableExternalEntityIndex<T> {
    val index = indexes.externalIndices.computeIfAbsent(identifier) { MutableExternalEntityIndex<T>() } as MutableExternalEntityIndex<T>
    index.setTypedEntityStorage(this)
    return index
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExternalIndex(identifier: String): MutableExternalEntityIndex<T>? {
    val index = indexes.externalIndices[identifier] as? MutableExternalEntityIndex<T>
    index?.setTypedEntityStorage(this)
    return index
  }

  fun removeExternalIndex(identifier: String) {
    indexes.externalIndices.remove(identifier)
  }

  // modificationCount is not incremented
  private fun removeEntity(idx: PId) {
    val accumulator: MutableSet<PId> = mutableSetOf(idx)

    accumulateEntitiesToRemove(idx, accumulator)

    for (id in accumulator) {
      val entityData = entityDataById(id)
      if (entityData is PSoftLinkable) indexes.removeFromSoftLinksIndex(entityData)
      entitiesByType.remove(id.arrayId, id.clazz)
    }

    // Update index
    //   Please don't join it with the previous loop
    for (id in accumulator) indexes.removeFromIndices(id)
  }

  private fun PEntityData<*>.hasPersistentId(): Boolean {
    val entity = this.createEntity(this@PEntityStorageBuilder)
    return entity is TypedEntityWithPersistentId
  }

  private fun PEntityData<*>.identificator(storage: AbstractPEntityStorage): Any {
    return this.persistentId(storage) ?: this.hashCode()
  }

  private fun <T : TypedEntity> createAddEvent(pEntityData: PEntityData<T>) {
    val pid = pEntityData.createPid()
    val parents = refs.getParentRefsOfChild(pid)
    val children = refs.getChildrenRefsOfParentBy(pid)
    updateChangeLog { it.add(ChangeEntry.AddEntity(pEntityData, pid.clazz, children, parents)) }
  }

  private fun <T : TypedEntity> cloneEntity(entity: PEntityData<T>, clazz: Int,
                                            replaceMap: MutableMap<PId, PId>): Pair<PEntityData<T>, PId> {
    // Add new entity to store (without references)
    val cloned = entitiesByType.cloneAndAdd(entity, clazz)
    val clonedPid = cloned.createPid()
    if (clonedPid != entity.createPid()) {
      replaceMap[entity.createPid()] = clonedPid
    }

    // Restore links to soft references
    if (cloned is PSoftLinkable) indexes.updateSoftLinksIndex(cloned)

    return cloned to clonedPid
  }

  // modificationCount is not incremented
  private fun updateEntityRefs(entityId: PId, updatedChildren: ChildrenConnectionsInfo,
                               updatedParents: ParentConnectionsInfo) {
    // Restore children references of the entity
    for ((connectionId, children) in updatedChildren) {
      refs.updateChildrenOfParent(connectionId, entityId, children.toList())
    }

    // Restore parent references of the entity
    for ((connection, parent) in updatedParents) {
      refs.updateParentOfChild(connection, entityId, parent)
    }
  }

  // modificationCount is not incremented
  private fun <T : TypedEntity> replaceEntityWithRefs(newEntity: PEntityData<T>,
                                                      clazz: Int,
                                                      updatedChildren: ChildrenConnectionsInfo,
                                                      updatedParents: ParentConnectionsInfo) {

    val id = newEntity.createPid()
    val existingEntityData = entityDataById(id)
    val beforePersistentId = existingEntityData?.persistentId(this)
    val beforeSoftLinks = if (existingEntityData is PSoftLinkable) existingEntityData.getLinks() else null

    /// Replace entity data. id should not be changed
    entitiesByType.replaceById(newEntity, clazz)

    // Restore soft references
    indexes.updateSoftReferences(beforePersistentId, beforeSoftLinks, entityDataByIdOrDie(id), this)
    updateEntityRefs(id, updatedChildren, updatedParents)
  }

  /**
   * Cleanup references and accumulate hard linked entities in [accumulator]
   */
  private fun accumulateEntitiesToRemove(id: PId, accumulator: MutableSet<PId>) {
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

    private val LOG = logger<PEntityStorageBuilder>()

    fun create() = from(PEntityStorage.EMPTY)

    fun from(storage: TypedEntityStorage): PEntityStorageBuilder {
      storage as AbstractPEntityStorage
      return when (storage) {
        is PEntityStorage -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
          val copiedRefs = MutableRefsTable.from(storage.refs)
          val copiedIndex = storage.indexes.toMutable()
          PEntityStorageBuilder(copiedBarrel, copiedRefs, copiedIndex)
        }
        is PEntityStorageBuilder -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType.toImmutable())
          val copiedRefs = MutableRefsTable.from(storage.refs.toImmutable())
          val copiedIndexes = storage.indexes.toImmutable().toMutable()
          PEntityStorageBuilder(copiedBarrel, copiedRefs, copiedIndexes)
        }
      }
    }
  }
}

internal sealed class AbstractPEntityStorage : TypedEntityStorage {

  internal abstract val entitiesByType: EntitiesBarrel
  internal abstract val refs: AbstractRefsTable
  internal abstract val indexes: StorageIndexes

  abstract fun assertConsistency()

  override fun <E : TypedEntity> entities(entityClass: Class<E>): Sequence<E> {
    return entitiesByType[entityClass.toClassId()]?.all()?.map { it.createEntity(this) } as? Sequence<E> ?: emptySequence()
  }

  internal fun entityDataById(id: PId): PEntityData<out TypedEntity>? = entitiesByType[id.clazz]?.get(id.arrayId)

  internal fun entityDataByIdOrDie(id: PId): PEntityData<out TypedEntity> {
    return entitiesByType[id.clazz]?.get(id.arrayId) ?: error("Cannot find an entity by id $id")
  }

  override fun <E : TypedEntity, R : TypedEntity> referrers(e: E, entityClass: KClass<R>,
                                                            property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    TODO()
    //return entities(entityClass.java).filter { property.get(it).resolve(this) == e }
  }

  override fun <E : TypedEntityWithPersistentId, R : TypedEntity> referrers(id: PersistentEntityId<E>, entityClass: Class<R>): Sequence<R> {
    TODO("Not yet implemented")
  }

  override fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val pids = indexes.persistentIdIndex.getIdsByEntry(id) ?: return null
    if (pids.isEmpty()) return null
    if (pids.size > 1) error("Cannot resolve persistent id. The store contains more than one associated entities")
    val pid = pids.single()
    return entityDataById(pid)?.createEntity(this) as E?
  }

  // Do not remove cast to Class<out TypedEntity>. kotlin fails without it
  @Suppress("USELESS_CAST")
  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out TypedEntity>, List<TypedEntity>>> {
    return indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
      indexes.entitySourceIndex
        .getIdsByEntry(source)!!.map { this.entityDataByIdOrDie(it).createEntity(this) }
        .groupBy { it.javaClass as Class<out TypedEntity> }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExternalIndex(identifier: String): ExternalEntityIndex<T>? {
    val index = indexes.externalIndices[identifier] as? ExternalEntityIndex<T>
    index?.setTypedEntityStorage(this)
    return index
  }

  protected fun assertConsistencyBase() {
    // Rules:
    //  1) Refs should not have links without a corresponding entity
    //    1.1) For abstract containers: PId has the class of ConnectionId
    //  2) child entity should have only one parent --------------------------- Not Yet Implemented TODO
    //  3) There is no child without a parent under the hard reference -------- Not Yet Implemented TODO

    refs.oneToManyContainer.forEach { (connectionId, map) ->
      map.forEachKey { childId, parentId ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(connectionId.parentClass, parentId)
        assertResolvable(connectionId.childClass, childId)
      }
    }

    refs.oneToOneContainer.forEach { (connectionId, map) ->
      map.forEachKey { childId, parentId ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(connectionId.parentClass, parentId)
        assertResolvable(connectionId.childClass, childId)
      }
    }

    refs.oneToAbstractManyContainer.forEach { (connectionId, map) ->
      map.forEach { (childId, parentId) ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(parentId.clazz, parentId.arrayId)
        assertResolvable(childId.clazz, childId.arrayId)

        //  1.1) For abstract containers: PId has the class of ConnectionId
        assertCorrectEntityClass(connectionId.parentClass, parentId)
        assertCorrectEntityClass(connectionId.childClass, childId)
      }
    }

    refs.abstractOneToOneContainer.forEach { (connectionId, map) ->
      map.forEach { (childId, parentId) ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(parentId.clazz, parentId.arrayId)
        assertResolvable(childId.clazz, childId.arrayId)

        //  1.1) For abstract containers: PId has the class of ConnectionId
        assertCorrectEntityClass(connectionId.parentClass, parentId)
        assertCorrectEntityClass(connectionId.childClass, childId)
      }
    }
  }

  private fun assertResolvable(clazz: Int, id: Int) {
    assert(entitiesByType[clazz]?.get(id) != null) {
      "Reference to $clazz-:-$id cannot be resolved"
    }
  }

  private fun assertCorrectEntityClass(connectionClass: Int, entityId: PId) {
    assert(connectionClass.findEntityClass<TypedEntity>().isAssignableFrom(entityId.clazz.findEntityClass<TypedEntity>())) {
      "Entity storage with connection class $connectionClass contains entity data of wrong type $entityId"
    }
  }
}

internal object ClassConversion {

  private val modifiableToEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val entityToEntityDataCache = HashMap<KClass<*>, KClass<*>>()
  private val entityDataToEntityCache = HashMap<Class<*>, Class<*>>()
  private val entityDataToModifiableEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val packageCache = HashMap<KClass<*>, String>()

  fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifiableEntityToEntity(clazz: KClass<out M>): KClass<T> {
    return modifiableToEntityCache.getOrPut(clazz) {
      try {
        Class.forName(getPackage(clazz) + clazz.java.simpleName.drop(10)).kotlin
      }
      catch (e: ClassNotFoundException) {
        error("Cannot get modifiable class for $clazz")
      }
    } as KClass<T>
  }

  fun <T : TypedEntity> entityToEntityData(clazz: KClass<out T>): KClass<PEntityData<T>> {
    return entityToEntityDataCache.getOrPut(clazz) {
      (Class.forName(clazz.java.name + "Data") as Class<PEntityData<T>>).kotlin
    } as KClass<PEntityData<T>>
  }

  fun <M : PEntityData<out T>, T : TypedEntity> entityDataToEntity(clazz: Class<out M>): Class<T> {
    return entityDataToEntityCache.getOrPut(clazz) {
      (Class.forName(clazz.name.dropLast(4)) as Class<T>)
    } as Class<T>
  }

  fun <D : PEntityData<T>, T : TypedEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableTypedEntity<T>> {
    return entityDataToModifiableEntityCache.getOrPut(clazz) {
      Class.forName(getPackage(clazz) + "Modifiable" + clazz.java.simpleName.dropLast(4)).kotlin as KClass<ModifiableTypedEntity<T>>
    } as KClass<ModifiableTypedEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String = packageCache.getOrPut(clazz) { clazz.java.name.dropLastWhile { it != '.' } }
}
