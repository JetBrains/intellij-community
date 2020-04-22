// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.workspace.api.*
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex.MutableExternalEntityIndex
import com.intellij.workspace.api.pstorage.indices.EntitySourceIndex
import com.intellij.workspace.api.pstorage.indices.EntitySourceIndex.MutableEntitySourceIndex
import com.intellij.workspace.api.pstorage.indices.PersistentIdIndex
import com.intellij.workspace.api.pstorage.indices.PersistentIdIndex.MutablePersistentIdIndex
import com.intellij.workspace.api.pstorage.indices.VirtualFileIndex
import com.intellij.workspace.api.pstorage.indices.VirtualFileIndex.MutableVirtualFileIndex
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


internal typealias ChildrenConnectionsInfo<T> = Map<ConnectionId<T, TypedEntity>, Set<PId<out TypedEntity>>>
internal typealias ParentConnectionsInfo<SUBT> = Map<ConnectionId<TypedEntity, SUBT>, PId<out TypedEntity>>

internal fun <T : TypedEntity> ChildrenConnectionsInfo<T>.replaceByMapChildren(replaceMap: Map<PId<out TypedEntity>, PId<out TypedEntity>>): ChildrenConnectionsInfo<T> {
  return mapValues { it.value.map { v -> replaceMap.getOrDefault(v, v) }.toSet() }
}

internal fun <T : TypedEntity> ParentConnectionsInfo<T>.replaceByMapParent(replaceMap: Map<PId<out TypedEntity>, PId<out TypedEntity>>): ParentConnectionsInfo<T> {
  return mapValues { replaceMap.getOrDefault(it.value, it.value) }
}

internal class PEntityReference<E : TypedEntity>(private val id: PId<E>) : EntityReference<E>() {
  override fun resolve(storage: TypedEntityStorage): E = (storage as AbstractPEntityStorage).entityDataByIdOrDie(id).createEntity(storage)
}

internal class PEntityStorage constructor(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val softLinks: Multimap<PersistentEntityId<*>, PId<*>>,
  override val virtualFileIndex: VirtualFileIndex,
  override val entitySourceIndex: EntitySourceIndex,
  override val persistentIdIndex: PersistentIdIndex,
  override val externalIndices: Map<String, ExternalEntityIndex<*>>
) : AbstractPEntityStorage() {
  override fun assertConsistency() {
    entitiesByType.assertConsistency()

    assertConsistencyBase()
  }

  companion object {
    val EMPTY = PEntityStorage(ImmutableEntitiesBarrel.EMPTY, RefsTable(), HashMultimap.create(), VirtualFileIndex(),
                               EntitySourceIndex(), PersistentIdIndex(), mapOf())
  }
}

internal class PEntityStorageBuilder(
  private val origStorage: PEntityStorage,
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable,
  override val softLinks: Multimap<PersistentEntityId<*>, PId<*>>,
  override val virtualFileIndex: MutableVirtualFileIndex,
  override val entitySourceIndex: MutableEntitySourceIndex,
  override val persistentIdIndex: MutablePersistentIdIndex,
  override val externalIndices: MutableMap<String, MutableExternalEntityIndex<*>>
) : TypedEntityStorageBuilder, AbstractPEntityStorage() {

  private val changeLogImpl: MutableList<ChangeEntry> = mutableListOf()

  private val changeLog: List<ChangeEntry>
    get() = changeLogImpl

  private inline fun updateChangeLog(updater: (MutableList<ChangeEntry>) -> Unit) {
    updater(changeLogImpl)
    modificationCount++
  }

  private sealed class ChangeEntry {
    data class AddEntity<E : TypedEntity>(
      val entityData: PEntityData<E>,
      val clazz: Class<E>,
      val children: ChildrenConnectionsInfo<E>,
      val parents: ParentConnectionsInfo<E>
    ) : ChangeEntry()

    data class RemoveEntity(val id: PId<*>) : ChangeEntry()

    data class ReplaceEntity<E : TypedEntity>(
      val newData: PEntityData<E>,
      val children: ChildrenConnectionsInfo<E>,
      val parents: ParentConnectionsInfo<E>
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

    // Construct entity data
    val pEntityData = entityDataClass.java.newInstance()
    pEntityData.entitySource = source

    // Add entity data to the structure
    entitiesByType.add(pEntityData, unmodifiableEntityClass)

    // Wrap it with modifiable and execute initialization code
    val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
    (modifiableEntity as PModifiableTypedEntity<*>).allowModifications {
      modifiableEntity.initializer()
    }

    // Add the change to changelog
    val pid = pEntityData.createPid()
    val parents = refs.getParentRefsOfChild(pid, false)
    val children = refs.getChildrenRefsOfParentBy(pid, false)
    updateChangeLog { it.add(ChangeEntry.AddEntity(pEntityData, unmodifiableEntityClass, children, parents)) }

    if (pEntityData is PSoftLinkable) {
      for (link in pEntityData.getLinks()) {
        softLinks.put(link, pEntityData.createPid())
      }
    }

    entitySourceIndex.index(pid, source)
    val createdEntity = pEntityData.createEntity(this)
    if (createdEntity is TypedEntityWithPersistentId) persistentIdIndex.index(pid, createdEntity.persistentId())
    return createdEntity
  }

  private fun <T : TypedEntity> cloneEntity(entity: PEntityData<T>, clazz: Class<T>,
                                            replaceMap: MutableMap<PId<*>, PId<*>>): Pair<PEntityData<T>, PId<T>> {
    // Add new entity to store (without references)
    val cloned = entitiesByType.cloneAndAdd(entity, clazz)
    val replaceToPid = cloned.createPid()
    if (replaceToPid != entity.createPid()) {
      replaceMap[entity.createPid()] = replaceToPid
    }

    // Restore links to soft references
    if (cloned is PSoftLinkable) {
      for (link in cloned.getLinks()) {
        softLinks.put(link, cloned.createPid())
      }
    }

    return cloned to replaceToPid
  }

  // modificationCount is not incremented
  private fun <T : TypedEntity> updateEntityRefs(entityId: PId<T>, updatedChildren: ChildrenConnectionsInfo<T>,
                                                 updatedParents: ParentConnectionsInfo<T>) {
    // Restore children references of the entity
    for ((connectionId, children) in updatedChildren) {
      refs.updateChildrenOfParent(connectionId, entityId, children.toList())
    }

    // Restore parent references of the entity
    for ((connection, parent) in updatedParents) {
      refs.updateParentOfChild(connection, entityId, parent)
    }
  }

  private fun addChangesToIndices(oldEntityId: PId<out TypedEntity>, newEntityId: PId<out TypedEntity>, builder: PEntityStorageBuilder) {
    builder.virtualFileIndex.getVirtualFiles(oldEntityId)?.forEach { virtualFileIndex.index(newEntityId, listOf(it)) }
    builder.entitySourceIndex.getEntitySource(oldEntityId)?.also { entitySourceIndex.index(newEntityId, it) }
    builder.persistentIdIndex.getPersistentId(oldEntityId)?.also { persistentIdIndex.index(newEntityId, it) }
  }

  private fun updateIndices(oldEntityId: PId<out TypedEntity>, newEntityId: PId<out TypedEntity>, builder: PEntityStorageBuilder) {
    builder.virtualFileIndex.getVirtualFiles(oldEntityId)?.forEach { virtualFileIndex.index(newEntityId, listOf(it)) }
    builder.entitySourceIndex.getEntitySource(oldEntityId)?.also { entitySourceIndex.index(newEntityId, it) }
    builder.persistentIdIndex.getPersistentId(oldEntityId)?.also { persistentIdIndex.index(newEntityId, it) }
  }

  private fun removeFromIndices(entityId: PId<out TypedEntity>) {
    virtualFileIndex.index(entityId)
    entitySourceIndex.index(entityId)
    persistentIdIndex.index(entityId)
  }

  // modificationCount is not incremented
  private fun <T : TypedEntity> replaceEntityWithRefs(newEntity: PEntityData<T>,
                                                      clazz: Class<T>,
                                                      updatedChildren: ChildrenConnectionsInfo<T>,
                                                      updatedParents: ParentConnectionsInfo<T>) {

    val id = newEntity.createPid()
    val existingEntity = entityDataById(id)
    val beforePersistentId = if (existingEntity is TypedEntityWithPersistentId) existingEntity.persistentId() else null
    val beforeSoftLinks = if (existingEntity is PSoftLinkable) existingEntity.getLinks() else null

    /// Replace entity data. id should not be changed
    entitiesByType.replaceById(newEntity, clazz)

    // Restore soft references
    updateSoftReferences(beforePersistentId, beforeSoftLinks, entityDataByIdOrDie(id))

    // Restore children references of the entity
    for ((connectionId, children) in updatedChildren) {
      refs.updateChildrenOfParent(connectionId, id, children.toList())
    }

    // Restore parent references of the entity
    for ((connection, parent) in updatedParents) {
      refs.updateParentOfChild(connection, id, parent)
    }
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
    val pid = e.id as PId<T>
    val parents = this.refs.getParentRefsOfChild(pid, false)
    val children = this.refs.getChildrenRefsOfParentBy(pid, false)
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(copiedData, children, parents)) }

    val updatedEntity = copiedData.createEntity(this)

    if (updatedEntity is TypedEntityWithPersistentId) persistentIdIndex.index(pid, updatedEntity.persistentId())
    updateSoftReferences(beforePersistentId, beforeSoftLinks, copiedData)

    return updatedEntity
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun <T : TypedEntity> updateSoftReferences(beforePersistentId: PersistentEntityId<*>?,
                                                     beforeSoftLinks: List<PersistentEntityId<*>>?,
                                                     copiedData: PEntityData<T>) {
    val updatedEntity = copiedData.createEntity(this)
    val pid = (updatedEntity as PTypedEntity).id
    if (beforePersistentId != null) {
      val afterPersistentId = (updatedEntity as TypedEntityWithPersistentId).persistentId()
      if (beforePersistentId != afterPersistentId) {
        val updatedIds = mutableListOf(beforePersistentId to afterPersistentId)
        while (updatedIds.isNotEmpty()) {
          val (beforeId, afterId) = updatedIds.removeFirst()
          softLinks[beforeId]?.forEach { id: PId<*> ->
            val pEntityData = this.entitiesByType.getEntityDataForModification(id) as PEntityData<TypedEntity>
            val updated = (pEntityData as PSoftLinkable).updateLink(beforeId, afterId, updatedIds)

            if (updated) {
              val softLinkedPid = pEntityData.createPid()
              val softLinkedParents = this.refs.getParentRefsOfChild(softLinkedPid, false)
              val softLinkedChildren = this.refs.getChildrenRefsOfParentBy(softLinkedPid, false)
              updateChangeLog { it.add(ChangeEntry.ReplaceEntity(pEntityData, softLinkedChildren, softLinkedParents)) }
            }
          }
        }
      }
    }

    if (beforeSoftLinks != null) {
      val afterSoftLinks = (copiedData as PSoftLinkable).getLinks()
      if (beforeSoftLinks != afterSoftLinks) {
        beforeSoftLinks.forEach { this.softLinks.remove(it, pid) }
        afterSoftLinks.forEach { this.softLinks.put(it, pid) }
      }
    }
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = entitiesByType.getEntityDataForModification((e as PTypedEntity).id) as PEntityData<T>
    copiedData.entitySource = newSource

    val pid = e.id as PId<T>
    val parents = this.refs.getParentRefsOfChild(pid, false)
    val children = this.refs.getChildrenRefsOfParentBy(pid, false)
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(copiedData, children, parents)) }

    entitySourceIndex.index(copiedData.createPid(), newSource)
    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: TypedEntity) {
    e as PTypedEntity

    removeEntity(e.id)
    virtualFileIndex.index(e.id)
    entitySourceIndex.index(e.id)
    persistentIdIndex.index(e.id)
    updateChangeLog { it.add(ChangeEntry.RemoveEntity(e.id)) }
  }

  // modificationCount is not incremented
  private fun <E : TypedEntity> removeEntity(idx: PId<E>) {
    val accumulator: MutableSet<PId<out TypedEntity>> = mutableSetOf(idx)

    accumulateEntitiesToRemove(idx, accumulator)

    for (id in accumulator) {
      entitiesByType.remove(id.arrayId, id.clazz.java)
      val entityData = entityDataById(id)
      if (entityData is PSoftLinkable) {
        for (link in entityData.getLinks()) {
          this.softLinks.remove(link, id)
        }
      }
    }
  }

  /**
   * Cleanup references and accumulate hard linked entities in [accumulator]
   */
  private fun accumulateEntitiesToRemove(id: PId<out TypedEntity>, accumulator: MutableSet<PId<out TypedEntity>>) {
    val children = refs.getChildrenRefsOfParentBy(id, true)
    for ((connectionId, children) in children) {
      for (child in children) {
        if (child in accumulator) continue
        accumulator.add(child)
        accumulateEntitiesToRemove(child, accumulator)
        refs.removeRefsByParent(connectionId, id)
      }
    }

    val parents = refs.getParentRefsOfChild(id, true)
    for ((connectionId, parent) in parents) {
      refs.removeParentToChildRef(connectionId, parent, id)
    }
  }

  internal fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                              parentId: PId<T>,
                                                                              children: Sequence<SUBT>) {
    refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, children)
  }

  internal fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                                      parentId: PId<T>,
                                                                                      children: Sequence<SUBT>) {
    refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, children)
  }

  internal fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToAbstractOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                                  childId: PId<SUBT>,
                                                                                  parent: T) {
    refs.updateOneToAbstractOneParentOfChild(connectionId, childId, parent)
  }

  internal fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToOneChildOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                          parentId: PId<T>,
                                                                          child: SUBT?) {
    if (child != null) {
      refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, child)
    }
    else {
      refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
    }
  }

  internal fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToManyParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                           childId: PId<SUBT>,
                                                                           parent: T?) {
    if (parent != null) {
      refs.updateOneToManyParentOfChild(connectionId, childId.arrayId, parent)
    }
    else {
      refs.removeOneToManyRefsByChild(connectionId, childId.arrayId)
    }
  }

  internal fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                          childId: PId<SUBT>,
                                                                          parent: T?) {
    if (parent != null) {
      refs.updateOneToOneParentOfChild(connectionId, childId.arrayId, parent)
    }
    else {
      refs.removeOneToOneRefByChild(connectionId, childId.arrayId)
    }
  }

  override fun <E : TypedEntity> createReference(e: E): EntityReference<E> = PEntityReference((e as PTypedEntity).id as PId<E>)

  private fun PEntityData<*>.hasPersistentId(): Boolean {
    val entity = this.createEntity(this@PEntityStorageBuilder)
    return entity is TypedEntityWithPersistentId
  }

  private fun PEntityData<*>.identificator(storage: AbstractPEntityStorage): Any {
    val entity = this.createEntity(storage)
    return if (entity is TypedEntityWithPersistentId) {
      entity.persistentId()
    }
    else {
      this.hashCode()
    }
  }

  private fun ArrayListMultimap<Any, PEntityData<out TypedEntity>>.find(entity: PEntityData<out TypedEntity>,
                                                                        storage: AbstractPEntityStorage): PEntityData<out TypedEntity>? {
    val possibleValues = this[entity.identificator(storage)]
    return if (entity.hasPersistentId()) {
      possibleValues.find {
        (it.createEntity(this@PEntityStorageBuilder) as TypedEntityWithPersistentId).persistentId() ==
          (entity.createEntity(this@PEntityStorageBuilder) as TypedEntityWithPersistentId).persistentId()
      }
    }
    else {
      possibleValues.find { it == entity }
    }
  }

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage) {
    replaceWith as AbstractPEntityStorage

    val leftMatchedNodes = ArrayListMultimap.create<Any, PEntityData<out TypedEntity>>()
    val rightMatchedNodes = ArrayListMultimap.create<Any, PEntityData<out TypedEntity>>()
    val unmatchedLeftReferencedNodes = ArrayListMultimap.create<Any, PEntityData<out TypedEntity>>()

    // Local to remote
    val replaceMap = HashBiMap.create<PId<out TypedEntity>, PId<out TypedEntity>>()

    // 1) Traverse all entities and store matched only
    this.entitiesByType.asSequence().map { it.value }.flatMap { it.all() }
      .filter { sourceFilter(it.entitySource) }
      .forEach { leftMatchedNodes.put(it.identificator(this), it) }

    // 1.1) Cleanup references
    for (matchedEntityData in leftMatchedNodes.values()) {
      val entityId = matchedEntityData.createPid()
      for ((connectionId, parentId) in this.refs.getParentRefsOfChild(entityId, false)) {
        val parentEntity = this.entityDataByIdOrDie(parentId)
        if (sourceFilter(parentEntity.entitySource)) {
          // Remove the connection between matched entities
          this.refs.removeParentToChildRef(connectionId, parentId, entityId)
        }
        else {
          unmatchedLeftReferencedNodes.put(parentEntity.identificator(this), parentEntity)
        }
      }

      for ((connectionId, childrenIds) in this.refs.getChildrenRefsOfParentBy(entityId, false)) {
        for (childId in childrenIds) {
          val childEntity = this.entityDataByIdOrDie(childId)
          if (sourceFilter(childEntity.entitySource)) {
            this.refs.removeParentToChildRef(connectionId, entityId, childId)
          }
          else {
            unmatchedLeftReferencedNodes.put(childEntity.identificator(this), childEntity)
          }
        }
      }
    }

    // 2) Traverse entities of the enemy
    for ((clazz, entityFamily) in replaceWith.entitiesByType) {
      for (matchedEntityData in entityFamily.all().filter { sourceFilter(it.entitySource) }) {
        rightMatchedNodes.put(matchedEntityData.identificator(replaceWith), matchedEntityData)

        val leftNode = leftMatchedNodes.find(matchedEntityData, replaceWith)
        if (leftNode != null) {
          replaceMap[leftNode.createPid()] = matchedEntityData.createPid()
          if (leftNode.hasPersistentId() && leftNode != matchedEntityData) {
            val clonedEntity = matchedEntityData.clone()
            clonedEntity.id = leftNode.id
            this.entitiesByType.replaceById(clonedEntity as PEntityData<TypedEntity>, clonedEntity.createPid().clazz.java)
            val pid = clonedEntity.createPid()
            val parents = this.refs.getParentRefsOfChild(pid, false)
            val children = this.refs.getChildrenRefsOfParentBy(pid, false)
            updateChangeLog { it.add(ChangeEntry.ReplaceEntity(clonedEntity, children, parents)) }
          }
          leftMatchedNodes.remove(leftNode.identificator(this), leftNode)
        }
        else {
          val newEntity = this.entitiesByType.cloneAndAdd(matchedEntityData as PEntityData<TypedEntity>, clazz as Class<TypedEntity>)
          val pid = newEntity.createPid()
          replaceMap[pid] = matchedEntityData.createPid()
          val parents = this.refs.getParentRefsOfChild(pid, false)
          val children = this.refs.getChildrenRefsOfParentBy(pid, false)
          updateChangeLog { it.add(ChangeEntry.AddEntity(newEntity, newEntity.createPid().clazz.java, children, parents)) }
        }
      }
    }

    // 3) Remove old entities
    for (leftEntity in leftMatchedNodes.values()) {
      // XXX do not create entity
      this.entitiesByType.remove(leftEntity.id, leftEntity.createEntity(this).javaClass)
      updateChangeLog { it.add(ChangeEntry.RemoveEntity(leftEntity.createPid())) }
    }

    // 4) Restore references between matched and unmatched entities
    for (unmatchedNode in unmatchedLeftReferencedNodes.values()) {
      val unmatchedId = unmatchedNode.createPid()
      val unmatchedEntityData = replaceWith.entityDataById(unmatchedId)
      if (unmatchedEntityData == null) {
        // TODO: 14.04.2020 Don't forget about entities with persistence id
        for ((connectionId, parentId) in this.refs.getParentRefsOfChild(unmatchedId, false)) {
          val parent = this.entityDataById(parentId)

          if (parent == null) {
            if (connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY
                || connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY
                || connectionId.isChildNullable) {
              this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
            }
            else {
              error("Cannot link old entity to the new one")
            }
          }
        }
        for ((connectionId, childIds) in this.refs.getChildrenRefsOfParentBy(unmatchedId, false)) {
          for (childId in childIds) {
            val child = this.entityDataById(childId)
            if (child == null) {
              if (connectionId.isParentNullable) {
                this.refs.removeParentToChildRef(connectionId, unmatchedId, childId)
              }
              else {
                error("Cannot link old entity to the new one")
              }
            }
          }
        }
      }
      else {
        for ((connectionId, parentId) in this.refs.getParentRefsOfChild(unmatchedId, false)) {
          if (!sourceFilter(this.entityDataByIdOrDie(parentId).entitySource)) continue
          this.refs.removeParentToChildRef(connectionId, parentId, unmatchedId)
        }

        for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(unmatchedId, false)) {
          if (!sourceFilter(this.entityDataByIdOrDie(parentId).entitySource)) continue
          val localParentId = replaceMap.inverse().get(parentId)!!
          this.refs.updateParentOfChild(connectionId, unmatchedId, localParentId)
        }

        for ((connectionId, childrenId) in this.refs.getChildrenRefsOfParentBy(unmatchedId, false)) {
          for (childId in childrenId) {
            if (!sourceFilter(this.entityDataByIdOrDie(childId).entitySource)) continue
            this.refs.removeParentToChildRef(connectionId, unmatchedId, childId)
          }
        }

        for ((connectionId, childrenId) in replaceWith.refs.getChildrenRefsOfParentBy(unmatchedId, false)) {
          for (childId in childrenId) {
            if (!sourceFilter(this.entityDataByIdOrDie(childId).entitySource)) continue
            val localChildId = replaceMap.inverse()[childId]!!
            this.refs.updateParentOfChild(connectionId, localChildId, unmatchedId)
          }
        }
      }
    }

    // 5) Restore references in matching ids
    for (rightMatchedNode in rightMatchedNodes.values()) {
      val nodeId = rightMatchedNode.createPid()
      for ((connectionId, parentId) in replaceWith.refs.getParentRefsOfChild(nodeId, false)) {
        if (!sourceFilter(replaceWith.entityDataByIdOrDie(parentId).entitySource)) continue

        val localChildId = replaceMap.inverse().get(nodeId)!!
        val localParentId = replaceMap.inverse().get(parentId)!!

        this.refs.updateParentOfChild(connectionId, localChildId, localParentId)
      }
    }
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
    val changes = LinkedHashMap<PId<*>, Pair<Class<*>, EntityChange<*>>>()
    for (change in changeLog) {
      when (change) {
        is ChangeEntry.AddEntity<*> -> {
          val addedEntity = change.entityData.createEntity(this) as PTypedEntity
          changes[addedEntity.id] = addedEntity.id.clazz.java to EntityChange.Added(addedEntity)
        }
        is ChangeEntry.RemoveEntity -> {
          val removedData = originalImpl.entityDataById(change.id)
          val oldChange = changes.remove(change.id)
          if (oldChange?.second !is EntityChange.Added && removedData != null) {
            val removedEntity = removedData.createEntity(originalImpl) as PTypedEntity
            changes[removedEntity.id] = change.id.clazz.java to EntityChange.Removed(removedEntity)
          }
        }
        is ChangeEntry.ReplaceEntity<*> -> {
          val id = change.newData.createPid()
          val oldChange = changes.remove(id)
          if (oldChange?.second is EntityChange.Added) {
            val addedEntity = change.newData.createEntity(this) as PTypedEntity
            changes[addedEntity.id] = addedEntity.id.clazz.java to EntityChange.Added(addedEntity)
          }
          else {
            val oldData = originalImpl.entityDataById(id)
            if (oldData != null) {
              val replacedData = oldData.createEntity(originalImpl) as PTypedEntity
              val replaceToData = change.newData.createEntity(this) as PTypedEntity
              changes[replacedData.id] = replacedData.id.clazz.java to EntityChange.Replaced(replacedData, replaceToData)
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
    val copiedLinks = HashMultimap.create(this.softLinks)
    val newVirtualFileIndex = virtualFileIndex.toImmutable()
    val newEntitySourceIndex = entitySourceIndex.toImmutable()
    val newPersistentIdIndex = persistentIdIndex.toImmutable()
    val newExternalIndices = MutableExternalEntityIndex.toImmutable(externalIndices)
    return PEntityStorage(newEntities, newRefs, copiedLinks, newVirtualFileIndex, newEntitySourceIndex, newPersistentIdIndex, newExternalIndices)
  }

  override fun isEmpty(): Boolean = changeLogImpl.isEmpty()

  override fun addDiff(diff: TypedEntityStorageDiffBuilder): Map<TypedEntity, TypedEntity> {

    // TODO: 08.04.2020 probably we should accept only diffs based on the same or empty snapshot

    val replaceMap = HashMap<PId<out TypedEntity>, PId<out TypedEntity>>()
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
          addChangesToIndices(change.entityData.createPid(), entity2id.second, builder)
          updateChangeLog {
            it.add(ChangeEntry.AddEntity(entity2id.first, change.clazz, updatedChildren, updatedParents))
          }
        }
        is ChangeEntry.RemoveEntity -> {
          val outdatedId = change.id
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)
          removeFromIndices(outdatedId)
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

          updateIndices(outdatedId, newData.createPid(), builder)
          if (this.entityDataById(usedPid) != null) {
            replaceEntityWithRefs(newData, outdatedId.clazz.java, updatedChildren, updatedParents)
          }
          updateChangeLog { it.add(ChangeEntry.ReplaceEntity(newData, updatedChildren, updatedParents)) }
        }
      }
    }
    applyExternalIndexChanges(diff)
    // TODO: 27.03.2020 Here should be consistency check
    val res = HashMap<TypedEntity, TypedEntity>()
    replaceMap.forEach { (oldId, newId) ->
      res[diff.entityDataByIdOrDie(oldId).createEntity(diff)] = this.entityDataByIdOrDie(newId).createEntity(this)
    }
    return res
  }

  private fun applyExternalIndexChanges(diff: PEntityStorageBuilder) {
    val removed = externalIndices.keys.toMutableSet()
    removed.removeAll(diff.externalIndices.keys)
    removed.forEach { externalIndices.remove(it) }

    val added = diff.externalIndices.keys.toMutableSet()
    added.removeAll(externalIndices.keys)
    added.forEach { externalIndices[it] = MutableExternalEntityIndex.from(diff.externalIndices[it]!!) }

    diff.externalIndices.forEach { (identifier, index) ->
      externalIndices[identifier]?.applyChanges(index)
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> getOrCreateExternalIndex(identifier: String): MutableExternalEntityIndex<T> {
    return externalIndices.computeIfAbsent(identifier) { MutableExternalEntityIndex<T>() }
      as MutableExternalEntityIndex<T>
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExternalIndex(identifier: String): MutableExternalEntityIndex<T>? {
    return externalIndices[identifier] as? MutableExternalEntityIndex<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> removeExternalIndex(identifier: String) {
    externalIndices.remove(identifier)
  }

  companion object {

    fun create() = from(PEntityStorage.EMPTY)

    fun from(storage: TypedEntityStorage): PEntityStorageBuilder {
      storage as AbstractPEntityStorage
      return when (storage) {
        is PEntityStorage -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
          val copiedRefs = MutableRefsTable.from(storage.refs)
          val copiedSoftLinks = HashMultimap.create(storage.softLinks)
          val copiedVirtualFileIndex = MutableVirtualFileIndex.from(storage.virtualFileIndex)
          val copiedEntitySourceIndex = MutableEntitySourceIndex.from(storage.entitySourceIndex)
          val copiedPersistentIdIndex = MutablePersistentIdIndex.from(storage.persistentIdIndex)
          val copiedExternalIndices = MutableExternalEntityIndex.fromMap(storage.externalIndices)
          PEntityStorageBuilder(storage, copiedBarrel, copiedRefs, copiedSoftLinks, copiedVirtualFileIndex, copiedEntitySourceIndex,
                                copiedPersistentIdIndex, copiedExternalIndices)
        }
        is PEntityStorageBuilder -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType.toImmutable())
          val copiedRefs = MutableRefsTable.from(storage.refs.toImmutable())
          val copiedSoftLinks = HashMultimap.create(storage.softLinks)
          // TODO:: Rewrite
          val copiedVirtualFileIndex = MutableVirtualFileIndex.from(storage.virtualFileIndex.toImmutable())
          val copiedEntitySourceIndex = MutableEntitySourceIndex.from(storage.entitySourceIndex.toImmutable())
          val copiedPersistentIdIndex = MutablePersistentIdIndex.from(storage.persistentIdIndex.toImmutable())
          val copiedExternalIndices = MutableExternalEntityIndex.fromMutableMap(storage.externalIndices)
          PEntityStorageBuilder(storage.toStorage(), copiedBarrel, copiedRefs, copiedSoftLinks, copiedVirtualFileIndex,
                                copiedEntitySourceIndex, copiedPersistentIdIndex, copiedExternalIndices)
        }
      }
    }

  }
}

internal sealed class AbstractPEntityStorage : TypedEntityStorage {

  internal abstract val entitiesByType: EntitiesBarrel
  internal abstract val refs: AbstractRefsTable
  internal abstract val softLinks: Multimap<PersistentEntityId<*>, PId<*>>
  internal abstract val virtualFileIndex: VirtualFileIndex
  internal abstract val entitySourceIndex: EntitySourceIndex
  internal abstract val persistentIdIndex: PersistentIdIndex
  internal abstract val externalIndices: Map<String, ExternalEntityIndex<*>>

  abstract fun assertConsistency()

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

  private fun assertResolvable(clazz: KClass<out TypedEntity>, id: Int) {
    assert(entitiesByType[clazz.java]?.get(id) != null) {
      "Reference to $clazz-:-$id cannot be resolved"
    }
  }

  private fun assertCorrectEntityClass(connectionClass: KClass<out TypedEntity>, entityId: PId<out TypedEntity>) {
    assert(connectionClass.java.isAssignableFrom(entityId.clazz.java)) {
      "Entity storage with connection class $connectionClass contains entity data of wrong type $entityId"
    }
  }

  override fun <E : TypedEntity> entities(entityClass: Class<E>): Sequence<E> {
    return entitiesByType[entityClass]?.all()?.map { it.createEntity(this) } ?: emptySequence()
  }

  internal fun <E : TypedEntity> entityDataById(id: PId<E>): PEntityData<E>? {
    return entitiesByType[id.clazz.java]?.get(id.arrayId)
  }

  internal fun <E : TypedEntity> entityDataByIdOrDie(id: PId<E>): PEntityData<E> {
    return entitiesByType[id.clazz.java]?.get(id.arrayId) ?: error("Cannot find an entity by id $id")
  }

  override fun <E : TypedEntity, R : TypedEntity> referrers(e: E,
                                                            entityClass: KClass<R>,
                                                            property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    TODO()
    //return entities(entityClass.java).filter { property.get(it).resolve(this) == e }
  }

  override fun <E : TypedEntityWithPersistentId, R : TypedEntity> referrers(id: PersistentEntityId<E>, entityClass: Class<R>): Sequence<R> {
    TODO("Not yet implemented")
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> extractOneToManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                          parentId: PId<T>): Sequence<SUBT> {
    val entitiesList = entitiesByType[connectionId.childClass.java] ?: return emptySequence()
    return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map { entitiesList[it]!!.createEntity(this) } ?: emptySequence()
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> extractOneToAbstractManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                                  parentId: PId<T>): Sequence<SUBT> {
    return refs.getOneToAbstractManyChildren(connectionId, parentId)?.asSequence()?.map { pid ->
      entityDataByIdOrDie(pid).createEntity(this)
    } ?: emptySequence()
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> extractAbstractOneToOneChildren(connectionId: ConnectionId<T, SUBT>,
                                                                                 parentId: PId<T>): Sequence<SUBT> {
    return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { pid ->
      sequenceOf(entityDataByIdOrDie(pid).createEntity(this))
    } ?: emptySequence()
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> extractOneToAbstractOneParent(connectionId: ConnectionId<T, SUBT>,
                                                                               childId: PId<SUBT>): T? {
    return refs.getOneToAbstractOneParent(connectionId, childId)?.let { entityDataByIdOrDie(it).createEntity(this) as T }
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> extractOneToOneChild(connectionId: ConnectionId<T, SUBT>,
                                                                      parentId: PId<T>): SUBT? {
    val entitiesList = entitiesByType[connectionId.childClass.java] ?: return null
    return refs.getOneToOneChild(connectionId, parentId.arrayId) { entitiesList[it]!!.createEntity(this) }
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> extractOneToOneParent(connectionId: ConnectionId<T, SUBT>,
                                                                       childId: PId<SUBT>): T? {
    val entitiesList = entitiesByType[connectionId.parentClass.java] ?: return null
    return refs.getOneToOneParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) }
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> extractOneToManyParent(connectionId: ConnectionId<T, SUBT>, childId: PId<SUBT>): T? {
    val entitiesList = entitiesByType[connectionId.parentClass.java] ?: return null
    return refs.getOneToManyParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) }
  }

  override fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    return entitiesByType.all()
      .filterValues { it.all().firstOrNull()?.createEntity(this) is TypedEntityWithPersistentId }
      .asSequence()
      .flatMap { it.value.all().map { it.createEntity(this) as TypedEntityWithPersistentId } }
      .find { it.persistentId() == id } as E?
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out TypedEntity>, List<TypedEntity>>> {
    val res = HashMap<EntitySource, MutableMap<Class<out TypedEntity>, MutableList<TypedEntity>>>()
    entitiesByType.forEach { (type, entities) ->
      entities.all().forEach {
        if (sourceFilter(it.entitySource)) {
          val mutableMapRes = res.getOrPut(it.entitySource, { mutableMapOf() })
          mutableMapRes.getOrPut(type, { mutableListOf() }).add(it.createEntity(this))
        }
      }
    }
    return res
  }

  @Suppress("UNCHECKED_CAST")
  open fun <T> getExternalIndex(identifier: String): ExternalEntityIndex<T>? {
    return externalIndices[identifier] as? ExternalEntityIndex<T>
  }
}

internal object ClassConversion {

  private val modifiableToEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val entityToEntityDataCache = HashMap<KClass<*>, KClass<*>>()
  private val entityDataToEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val entityDataToModifiableEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val packageCache = HashMap<KClass<*>, String>()

  fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifiableEntityToEntity(clazz: KClass<out M>): KClass<T> {
    return modifiableToEntityCache.getOrPut(clazz) {
      try {
        Class.forName(getPackage(clazz) + clazz.simpleName!!.drop(10)).kotlin
      }
      catch (e: ClassNotFoundException) {
        error("Cannot get modifiable class for $clazz")
      }
    } as KClass<T>
  }

  fun <T : TypedEntity> entityToEntityData(clazz: KClass<out T>): KClass<PEntityData<T>> {
    return entityToEntityDataCache.getOrPut(clazz) {
      (Class.forName(clazz.qualifiedName + "Data") as Class<PEntityData<T>>).kotlin
    } as KClass<PEntityData<T>>
  }

  fun <M : PEntityData<out T>, T : TypedEntity> entityDataToEntity(clazz: KClass<out M>): KClass<T> {
    return entityDataToEntityCache.getOrPut(clazz) {
      (Class.forName(clazz.qualifiedName!!.dropLast(4)) as Class<T>).kotlin
    } as KClass<T>
  }

  fun <D : PEntityData<T>, T : TypedEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableTypedEntity<T>> {
    return entityDataToModifiableEntityCache.getOrPut(clazz) {
      Class.forName(getPackage(clazz) + "Modifiable" + clazz.simpleName!!.dropLast(4)).kotlin as KClass<ModifiableTypedEntity<T>>
    } as KClass<ModifiableTypedEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String = packageCache.getOrPut(clazz) { clazz.qualifiedName!!.dropLastWhile { it != '.' } }
}
