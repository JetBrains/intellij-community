// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.*
import com.intellij.workspace.api.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible


internal typealias ChildrenConnectionsInfo<T> = Map<ConnectionId<T, out TypedEntity>, Set<PId<out TypedEntity>>>
internal typealias ParentConnectionsInfo<SUBT> = Map<ConnectionId<out TypedEntity, SUBT>, PId<out TypedEntity>>

internal fun <T : TypedEntity> ChildrenConnectionsInfo<T>.replaceByMapChildren(replaceMap: Map<PId<*>, PId<*>>): ChildrenConnectionsInfo<T> {
  return mapValues { it.value.map { v -> replaceMap.getOrDefault(v, v) }.toSet() }
}

internal fun <T : TypedEntity> ParentConnectionsInfo<T>.replaceByMapParent(replaceMap: Map<PId<*>, PId<*>>): ParentConnectionsInfo<T> {
  return mapValues { replaceMap.getOrDefault(it.value, it.value) }
}

internal class PEntityReference<E : TypedEntity>(private val id: PId<E>) : EntityReference<E>() {
  override fun resolve(storage: TypedEntityStorage): E = (storage as AbstractPEntityStorage).entityDataByIdOrDie(id).createEntity(storage)
}

internal class PEntityStorage constructor(
  override val entitiesByType: EntitiesBarrel,
  override val refs: RefsTable
) : AbstractPEntityStorage() {
  override fun assertConsistency() {
    entitiesByType.assertConsistency()

    assertConsistencyBase()
  }
}

internal class PEntityStorageBuilder(
  private val origStorage: PEntityStorage,
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable
) : TypedEntityStorageBuilder, AbstractPEntityStorage() {

  private val changeLogImpl: MutableList<ChangeEntry> = mutableListOf()

  private val changeLog: List<ChangeEntry>
    get() = changeLogImpl

  private inline fun updateChangeLog(updater: (MutableList<ChangeEntry>) -> Unit) {
    updater(changeLogImpl)
    modificationCount++
  }

  private constructor() : this(PEntityStorage(EntitiesBarrel(), RefsTable()), MutableEntitiesBarrel(), MutableRefsTable())

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
    val primaryConstructor = entityDataClass.primaryConstructor!!
    primaryConstructor.isAccessible = true
    val pEntityData = primaryConstructor.call()
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

    return pEntityData.createEntity(this)
  }

  // modificationCount is not incremented
  // TODO: 27.03.2020 T and E should be the same type. Looks like an error in kotlin inheritance algorithm
  private fun <T : TypedEntity, E : TypedEntity> addEntityWithRefs(entity: PEntityData<T>,
                                                                   clazz: Class<E>,
                                                                   storage: AbstractPEntityStorage,
                                                                   replaceMap: MutableMap<PId<*>, PId<*>>) {
    clazz as Class<T>
    entitiesByType.add(entity, clazz)

    handleReferences(storage, entity, clazz, replaceMap)
  }

  // modificationCount is not incremented
  private fun <T : TypedEntity> cloneAndAddEntityWithRefs(entity: PEntityData<T>,
                                                          clazz: Class<T>,
                                                          updatedChildren: ChildrenConnectionsInfo<T>,
                                                          updatedParents: ParentConnectionsInfo<T>,
                                                          replaceMap: MutableMap<PId<*>, PId<*>>): PEntityData<T> {
    // Add new entity to store (without references)
    val cloned = entitiesByType.cloneAndAdd(entity, clazz)
    val replaceToPid = cloned.createPid()
    if (replaceToPid != entity.createPid()) {
      replaceMap[entity.createPid()] = replaceToPid
    }

    // Restore children references of the entity
    for ((connectionId, children) in updatedChildren) {
      refs.updateChildrenOfParent(connectionId, replaceToPid, children.toList())
    }

    // Restore parent references of the entity
    for ((connection, parent) in updatedParents) {
      refs.updateParentOfChild(connection, replaceToPid, parent)
    }

    return cloned
  }

  // modificationCount is not incremented
  // TODO: 27.03.2020 T and E should be the same type. Looks like an error in kotlin inheritance algorithm
  private fun <T : TypedEntity, E : TypedEntity> replaceEntityWithRefs(newEntity: PEntityData<T>,
                                                                       clazz: Class<E>,
                                                                       storage: AbstractPEntityStorage,
                                                                       replaceMap: MutableMap<PId<*>, PId<*>>) {
    clazz as Class<T>

    entitiesByType.replaceById(newEntity, clazz)

    handleReferences(storage, newEntity, clazz, replaceMap)
  }

  // modificationCount is not incremented
  private fun <T : TypedEntity> replaceEntityWithRefs(newEntity: PEntityData<T>,
                                                      clazz: Class<T>,
                                                      updatedChildren: ChildrenConnectionsInfo<T>,
                                                      updatedParents: ParentConnectionsInfo<T>) {
    /// Replace entity data. id should not be changed
    entitiesByType.replaceById(newEntity, clazz)
    val replaceToPid = newEntity.createPid()

    // Restore children references of the entity
    for ((connectionId, children) in updatedChildren) {
      refs.updateChildrenOfParent(connectionId, replaceToPid, children.toList())
    }

    // Restore parent references of the entity
    for ((connection, parent) in updatedParents) {
      refs.updateParentOfChild(connection, replaceToPid, parent)
    }
  }

  private fun <T : TypedEntity> handleReferences(storage: AbstractPEntityStorage,
                                                 newEntity: PEntityData<T>,
                                                 clazz: Class<T>,
                                                 replaceMap: MutableMap<PId<out TypedEntity>, PId<out TypedEntity>>) {
    val entityPid = newEntity.createPid()
    val replaceToPid = replaceMap.getOrDefault(entityPid, entityPid) as PId<T>
    val childrenRefsByConnectionId = storage.refs.getChildrenRefsOfParentBy(entityPid, false)
    for ((connectionId, children) in childrenRefsByConnectionId) {
      val replaceToChildren = children.map { replaceMap.getOrDefault(it, it) }
      refs.updateChildrenOfParent(connectionId, replaceToPid, replaceToChildren)
    }

    val parentRefs = storage.refs.getParentRefsOfChild(entityPid, false)
    for ((connection, parent) in parentRefs) {
      val realParent = replaceMap.getOrDefault(parent, parent) as PId<TypedEntity>
      refs.updateParentOfChild(connection as ConnectionId<TypedEntity, T>, replaceToPid, realParent)
    }
  }

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    // Get entity data that will be modified
    val copiedData = entitiesByType.getEntityDataForModification((e as PTypedEntity).id) as PEntityData<T>
    val modifiableEntity = copiedData.wrapAsModifiable(this) as M

    // Execute modification code
    (modifiableEntity as PModifiableTypedEntity<*>).allowModifications {
      modifiableEntity.change()
    }

    // Add an entry to changelog
    val pid = e.id as PId<T>
    val parents = this.refs.getParentRefsOfChild(pid, false)
    val children = this.refs.getChildrenRefsOfParentBy(pid, false)
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(copiedData, children, parents)) }

    return copiedData.createEntity(this)
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = entitiesByType.getEntityDataForModification((e as PTypedEntity).id) as PEntityData<T>
    copiedData.entitySource = newSource
    modificationCount++
    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: TypedEntity) {
    removeEntity((e as PTypedEntity).id)
    updateChangeLog { it.add(ChangeEntry.RemoveEntity(e.id)) }
  }

  // modificationCount is not incremented
  private fun <E : TypedEntity> removeEntity(idx: PId<E>) {
    val accumulator: MutableSet<PId<out TypedEntity>> = mutableSetOf(idx)

    accumulateEntitiesToRemove(idx, accumulator)

    for (id in accumulator) {
      entitiesByType.remove(id.arrayId, id.clazz.java)
    }
  }

  /**
   * Cleanup references and accumulate hard linked entities in [accumulator]
   */
  private fun <T : TypedEntity> accumulateEntitiesToRemove(id: PId<T>,
                                                           accumulator: MutableSet<PId<out TypedEntity>>) {


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
      refs.removeParentToChildRef(connectionId as ConnectionId<TypedEntity, T>, parent as PId<TypedEntity>, id)
    }
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                              parentId: PId<T>,
                                                                              children: Sequence<SUBT>) {
    refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, children)
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                                      parentId: PId<T>,
                                                                                      children: Sequence<SUBT>) {
    refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, children)
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToAbstractOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                                  childId: PId<SUBT>,
                                                                                  parent: T) {
    refs.updateOneToAbstractOneParentOfChild(connectionId, childId, parent)
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToOneChildOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                          parentId: PId<T>,
                                                                          child: SUBT?) {
    if (child != null) {
      refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, child)
    }
    else {
      refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
    }
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToManyParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                           childId: PId<SUBT>,
                                                                           parent: T?) {
    if (parent != null) {
      refs.updateOneToManyParentOfChild(connectionId, childId.arrayId, parent)
    }
    else {
      refs.removeOneToManyRefsByChild(connectionId, childId.arrayId)
    }
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
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

  private fun PEntityData<*>.persistentId() =
    (this.createEntity(this@PEntityStorageBuilder) as TypedEntityWithPersistentId).persistentId()

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage) {

    val entitiesByPersistentIdHash = groupByPersistentIdHash(this)
    val replaceWithEntitiesByPersistentIdHash = groupByPersistentIdHash(replaceWith as AbstractPEntityStorage)

    val replaceMap = HashBiMap.create<PId<*>, PId<*>>()
    val emptyBiMap = HashBiMap.create<PId<*>, PId<*>>()
    val entitiesToRemove = mutableSetOf<PId<*>>()
    val entitiesToAdd = mutableSetOf<PId<*>>()

    // TODO Cache sourceFilter result by entitySource to call it only once per entitySource

    // TODO The following code assumes eligible persistent id entities to be without parents
    // TODO  add some support for entities with parents. Like when they're all deep equal

    for ((idHash, oldEntities) in entitiesByPersistentIdHash.asMap()) {
      val newEntities = replaceWithEntitiesByPersistentIdHash[idHash]?.toMutableList() ?: mutableListOf()
      for ((oldData, clazz) in oldEntities.toList()) {
        if (!sourceFilter(oldData.entitySource)) continue

        // Currently persistent id entities must not have any parents
        if (refs.getParentRefsOfChild(oldData.createPid(), false).isNotEmpty()) continue

        val persistentId = oldData.persistentId()

        val newData = newEntities.firstOrNull { it.first.persistentId() == persistentId && sourceFilter(it.first.entitySource) }
        if (newData != null) {
          replaceMap[oldData.createPid()] = newData.first.createPid()

          if (!shallowEquals(oldData, newData.first, emptyBiMap, replaceWith)) {
            val replaceWithData = newData.first.clone() as PEntityData<TypedEntity>

            replaceEntityWithRefs(replaceWithData, newData.second, this, HashMap())
            // TODO: 08.04.2020 Handle references update
            updateChangeLog { it.add(ChangeEntry.ReplaceEntity(replaceWithData, emptyMap(), emptyMap())) }
          }

          newEntities.remove(newData)
        }
        else {
          // Remove right here?
          // TODO Don't forget to check sourceFilter
          entitiesToRemove.add(oldData.createPid())
        }
      }
    }

    for ((idHash, newEntities) in replaceWithEntitiesByPersistentIdHash.asMap()) {
      val oldEntities = entitiesByPersistentIdHash[idHash] ?: mutableSetOf()
      for ((newData, clazz) in newEntities) {
        if (!sourceFilter(newData.entitySource)) continue
        // Currently persistent id entities must not have any parents
        if (replaceWith.refs.getParentRefsOfChild(newData.createPid(), false).isNotEmpty()) continue

        val persistentId = newData.persistentId()

        val oldData = oldEntities.firstOrNull { it.first.persistentId() == persistentId && sourceFilter(it.first.entitySource) }
        if (oldData == null) {
          // Add sub-graph right here?
          // TODO Don't forget to check sourceFilter
          entitiesToAdd.add(newData.createPid())
        }
      }
    }

    // TODO References to cold entities (not having persistent id as a root)
    // TODO Ref to cold entities from persistent id
    // TODO test entity refs couple of persistent ids with a different path length to each
    // TODO Test cold entities (not related to persistent ids)
    // TODO Compare cold entities by hash, probably pre-calculate this hash

    // assumes start nodes have no parents
    val queue: Queue<Pair<PId<*>, PId<*>>> = Queues.newArrayDeque(replaceMap.toList())
    while (queue.isNotEmpty()) {
      val (oldId, newId) = queue.remove()

      // new nodes - children

      // TODO hash
      // TODO: 03.04.2020 oneToMany children

      val newChildren = replaceWith.refs.getChildrenRefsOfParentBy(newId, false)
        .flatMap { it.value }
        .filter { it !in entitiesToAdd && !replaceMap.containsKey(it) }
        .map { replaceWith.entityDataByIdOrDie(it) }
      val oldChildren = this.refs.getChildrenRefsOfParentBy(oldId, false)
        .flatMap { it.value }
        .filter { it !in entitiesToRemove && !replaceMap.containsKey(it) }
        .map { this.entityDataByIdOrDie(it) }

      val eq = classifyByEquals(
        c1 = oldChildren,
        c2 = newChildren,
        hashFunc1 = this::shallowHashCode, hashFunc2 = this::shallowHashCode,
        equalsFunc = { v1, v2 -> shallowEquals(v1, v2, replaceMap, replaceWith) })

      for ((oldChildData, newChildData) in eq.equal) {
        val newPId = newChildData.createPid()
        val oldPId = oldChildData.createPid()
        if (newPId in entitiesToAdd) error("id=${newChildData.id} already exists in entriesToAdd")
        if (oldPId in entitiesToRemove) error("id=${oldChildData.id} already exists in entitiesToRemove")

        queue.add(oldPId to newPId)
        replaceMap[oldPId] = newPId
      }

      // TODO Check we won't get any persistent id nodes?
      for (data in eq.onlyIn1) {
        traverseNodes(this, data.createPid()) { id ->
          if (replaceMap.containsKey(id)) {
            error("Trying to remove node with id=$id: it's already marked for replacement")
          }

          entitiesToRemove.add(id)
        }
      }

      // TODO Check we won't get any persistent id nodes?
      for (data in eq.onlyIn2) {
        traverseNodes(replaceWith, (data.createEntity(this) as PTypedEntity).id) { id ->
          if (replaceMap.containsValue(id)) {
            error("Trying to add node with id=$id: it's already marked for replacement")
          }

          entitiesToAdd.add(id)
        }
      }
    }

    // Process all non-persistent-id related nodes
    // TODO Check for external links, sourceFilter must filter out a connected component

    val destEntitiesToCompare = mutableSetOf<PEntityData<out TypedEntity>>()
    foreachNotProcessedEntity(this, sourceFilter, replaceMap, entitiesToRemove) { data ->
      destEntitiesToCompare.add(data)
    }

    val sourceEntitiesToCompare = mutableSetOf<PEntityData<out TypedEntity>>()
    foreachNotProcessedEntity(replaceWith, sourceFilter, replaceMap.inverse(), entitiesToAdd) { data ->
      sourceEntitiesToCompare.add(data)
    }

    val equalsCache = mutableMapOf<Pair<PId<out TypedEntity>, PId<out TypedEntity>>, Boolean>()
    val eq = ProxyBasedEntityStorage.classifyByEquals(
      destEntitiesToCompare, sourceEntitiesToCompare,
      this::shallowHashCode, this::shallowHashCode
    ) { e1, e2 ->
      deepEquals(
        data1 = e1,
        data2 = e2,
        replaceMap = replaceMap,
        storage1 = this,
        storage2 = replaceWith,
        /*
                backReferrers1 = backReferrers,
                backReferrers2 = replaceWithBackReferrers,
        */
        equalsCache = equalsCache)
    }

    for (data in eq.onlyIn1) {
      val id = data.createPid()
      entitiesToRemove.add(id)
    }

    for (data in eq.onlyIn2) {
      val id = data.createPid()
      entitiesToAdd.add(id)
    }

    for ((oldChildData, newChildData) in eq.equal) {
      val oldId = oldChildData.createPid()
      val newId = newChildData.createPid()
      replaceMap[oldId] = newId
    }

    for (idToRemove in entitiesToRemove) {

      if (this.entityDataById(idToRemove) != null) {
        removeEntity(idToRemove)
        updateChangeLog { it.add(ChangeEntry.RemoveEntity(idToRemove)) }
      }
    }

    for (idToAdd in entitiesToAdd) {
      if (!replaceMap.containsValue(idToAdd)) {
        recursiveAddEntity(idToAdd/*, replaceWithBackReferrers*/, replaceWith, replaceMap, sourceFilter)
      }
    }

  }

  private fun recursiveAddEntity(id: PId<out TypedEntity>,
    /*backReferrers: MultiMap<PId<out TypedEntity>, PId<out TypedEntity>>,*/
                                 storage: AbstractPEntityStorage,
                                 replaceMap: BiMap<PId<out TypedEntity>, PId<out TypedEntity>>,
                                 sourceFilter: (EntitySource) -> Boolean) {
    val parents = storage.refs.getParentRefsOfChild(id, false)
    for ((conId, parentId) in parents) {
      if (!replaceMap.containsValue(parentId)) {
        if (sourceFilter(storage.entityDataByIdOrDie(parentId).entitySource)) {
          recursiveAddEntity(parentId/*, backReferrers*/, storage, replaceMap, sourceFilter)
        }
        else {
          replaceMap[parentId] = parentId
        }
      }
    }

    val data = storage.entityDataByIdOrDie(id)
    val newData = data.clone()
    replaceMap[newData.createPid()] = id
    //copyEntityProperties(data, newData, replaceMap.inverse())
    addEntityWithRefs(newData, id.clazz.java, storage, HashMap())
    //addEntity(newData, null, handleReferrers = true)
    updateChangeLog { it.add(createAddEntity(newData, id.clazz.java)) }
  }

  private fun <E : TypedEntity, T : TypedEntity> createAddEntity(data: PEntityData<E>, clazz: Class<T>): ChangeEntry.AddEntity<E> {
    // Handle children and parent references
    return ChangeEntry.AddEntity(data, clazz as Class<E>, emptyMap(), emptyMap())
  }

  private fun deepEquals(data1: PEntityData<out TypedEntity>,
                         data2: PEntityData<out TypedEntity>,
                         replaceMap: Map<PId<*>, PId<*>>,
                         storage1: AbstractPEntityStorage,
                         storage2: AbstractPEntityStorage,
    /*
                             backReferrers1: MultiMap<Long, Long>,
                             backReferrers2: MultiMap<Long, Long>,
    */
                         equalsCache: MutableMap<Pair<PId<out TypedEntity>, PId<out TypedEntity>>, Boolean>): Boolean {

    val id1 = data1.createPid()
    val id2 = data2.createPid()
    val cachedResult = equalsCache[id1 to id2]
    if (cachedResult != null) return cachedResult

    if (replaceMap[id1] == id2) return true


    // TODO: 03.04.2020 OneToMany children
    val data1parents = storage1.refs.getParentRefsOfChild(id1, false).map { storage1.entityDataByIdOrDie(it.value) }
    val data2parents = storage2.refs.getParentRefsOfChild(id2, false).map { storage2.entityDataByIdOrDie(it.value) }

    val eq = classifyByEquals(data1parents, data2parents, this::shallowHashCode, this::shallowHashCode) { e1, e2 ->
      deepEquals(e1, e2, replaceMap, storage1, storage2/*, backReferrers1, backReferrers2*/, equalsCache)
    }

    val result = eq.onlyIn1.isEmpty() && eq.onlyIn2.isEmpty()
    equalsCache[id1 to id2] = result
    return result
  }


  private fun foreachNotProcessedEntity(storage: AbstractPEntityStorage,
                                        sourceFilter: (EntitySource) -> Boolean,
                                        replaceMap: Map<PId<out TypedEntity>, PId<out TypedEntity>>,
                                        otherProcessedSet: Set<PId<out TypedEntity>>,
                                        block: (PEntityData<out TypedEntity>) -> Unit) {
    for (entityFamily in storage.entitiesByType.all().values) {
      entityFamily.all().filter { sourceFilter(it.entitySource) }.forEach {
        val id = it.createPid()
        if (!replaceMap.containsKey(id) && !otherProcessedSet.contains(id)) {
          block(it)
        }
      }
    }
  }

  private fun traverseNodes(storage: AbstractPEntityStorage, startNode: PId<out TypedEntity>, block: (PId<out TypedEntity>) -> Unit) {
    val queue = Queues.newArrayDeque(listOf(startNode))
    while (queue.isNotEmpty()) {
      val id = queue.remove()
      block(id)

      // TODO: 03.04.2020 OneToOneChildren
      queue.addAll(storage.refs.getChildrenRefsOfParentBy(id, false).flatMap { it.value })
    }
  }

  private fun shallowHashCode(data: PEntityData<out TypedEntity>): Int = data.hashCode()

  data class EqualityResult<T1, T2>(
    val onlyIn1: List<T1>,
    val onlyIn2: List<T2>,
    val equal: List<Pair<T1, T2>>
  )

  private fun shallowEquals(oldData: PEntityData<out TypedEntity>,
                            newData: PEntityData<out TypedEntity>,
                            emptyBiMap: HashBiMap<PId<*>, PId<*>>?,
                            newStorage: AbstractPEntityStorage): Boolean = oldData == newData


  private fun groupByPersistentIdHash(storage: AbstractPEntityStorage): Multimap<Int, Pair<PEntityData<*>, Class<out TypedEntity>>> {
    val res = HashMultimap.create<Int, Pair<PEntityData<*>, Class<out TypedEntity>>>()
    for ((clazz, entityFamily) in storage.entitiesByType.all()) {
      for (pEntityData in entityFamily.all()) {
        if (!TypedEntityWithPersistentId::class.java.isAssignableFrom(clazz)) continue
        val entity = pEntityData.createEntity(storage) as TypedEntityWithPersistentId

        res.put(entity.persistentId().hashCode(), pEntityData to clazz)
      }
    }
    return res
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
    return PEntityStorage(newEntities, newRefs)
  }

  override fun isEmpty(): Boolean = changeLogImpl.isEmpty()

  override fun addDiff(diff: TypedEntityStorageDiffBuilder) {

    // TODO: 08.04.2020 probably we should accept only diffs based on the same or empty snapshot

    val replaceMap = HashMap<PId<out TypedEntity>, PId<out TypedEntity>>()
    val diffLog = (diff as PEntityStorageBuilder).changeLog
    for (change in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity<out TypedEntity> -> {
          change as ChangeEntry.AddEntity<TypedEntity>

          val updatedChildren = change.children.replaceByMapChildren(replaceMap)
          val updatedParents = change.parents.replaceByMapParent(replaceMap)

          val addedEntity = cloneAndAddEntityWithRefs(change.entityData, change.clazz, updatedChildren, updatedParents, replaceMap)
          updateChangeLog {
            it.add(ChangeEntry.AddEntity(addedEntity, change.clazz, updatedChildren, updatedParents))
          }
        }
        is ChangeEntry.RemoveEntity -> {
          val outdatedId = change.id
          val usedPid = replaceMap.getOrDefault(outdatedId, outdatedId)
          if (this.entityDataById(usedPid) != null) {
            removeEntity(usedPid)
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

          if (this.entityDataById(usedPid) != null) {
            replaceEntityWithRefs(newData, outdatedId.clazz.java, updatedChildren, updatedParents)
          }
          updateChangeLog { it.add(ChangeEntry.ReplaceEntity(newData, updatedChildren, updatedParents)) }
        }
      }
    }
    // TODO: 27.03.2020 Here should be consistency check
  }

  companion object {

    fun create() = PEntityStorageBuilder()

    fun from(storage: TypedEntityStorage): PEntityStorageBuilder {
      storage as AbstractPEntityStorage
      return when (storage) {
        is PEntityStorage -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
          val copiedRefs = MutableRefsTable.from(storage.refs)
          PEntityStorageBuilder(storage, copiedBarrel, copiedRefs)
        }
        is PEntityStorageBuilder -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType.toImmutable())
          val copiedRefs = MutableRefsTable.from(storage.refs.toImmutable())
          PEntityStorageBuilder(storage.toStorage(), copiedBarrel, copiedRefs)
        }
      }
    }

    internal fun <T1, T2> classifyByEquals(c1: Iterable<T1>,
                                           c2: Iterable<T2>,
                                           hashFunc1: (T1) -> Int,
                                           hashFunc2: (T2) -> Int,
                                           equalsFunc: (T1, T2) -> Boolean): EqualityResult<T1, T2> {
      val hashes1 = c1.groupBy(hashFunc1)
      val hashes2 = c2.groupBy(hashFunc2)

      val onlyIn1 = mutableListOf<T1>()
      for (key in hashes1.keys - hashes2.keys) {
        onlyIn1.addAll(hashes1.getValue(key))
      }

      val onlyIn2 = mutableListOf<T2>()
      for (key in hashes2.keys - hashes1.keys) {
        onlyIn2.addAll(hashes2.getValue(key))
      }

      val equal = mutableListOf<Pair<T1, T2>>()
      for (key in hashes1.keys.intersect(hashes2.keys)) {
        val l1 = hashes1.getValue(key)
        val l2 = hashes2.getValue(key)

        if (l1.size == 1 && l2.size == 1 && equalsFunc(l1.single(), l2.single())) {
          equal.add(l1.single() to l2.single())
        }
        else {
          val ml1 = l1.toMutableList()
          val ml2 = l2.toMutableList()

          for (itemFrom1 in ml1) {
            val index2 = ml2.indexOfFirst { equalsFunc(itemFrom1, it) }
            if (index2 < 0) {
              onlyIn1.add(itemFrom1)
            }
            else {
              val itemFrom2 = ml2.removeAt(index2)
              equal.add(itemFrom1 to itemFrom2)
            }
          }

          for (itemFrom2 in ml2) {
            onlyIn2.add(itemFrom2)
          }
        }
      }

      return EqualityResult(onlyIn1 = onlyIn1, onlyIn2 = onlyIn2, equal = equal)
    }
  }
}

internal sealed class AbstractPEntityStorage : TypedEntityStorage {

  internal abstract val entitiesByType: AbstractEntitiesBarrel
  internal abstract val refs: AbstractRefsTable

  abstract fun assertConsistency()

  protected fun assertConsistencyBase() {
    // Rules:
    //  1) Refs should not have links without a corresponding entity
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

    refs.oneToAbstractManyContainer.forEach { (_, map) ->
      map.forEach { (childId, parentId) ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(parentId.clazz, parentId.arrayId)
        assertResolvable(parentId.clazz, childId.arrayId)
      }
    }
  }

  private fun assertResolvable(clazz: KClass<out TypedEntity>, id: Int) {
    assert(entitiesByType[clazz.java]?.get(id) != null) {
      "Reference to $clazz-:-$id cannot be resolved"
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

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                          parentId: PId<T>): Sequence<SUBT> {
    val entitiesList = entitiesByType[connectionId.childClass.java] ?: return emptySequence()
    return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map { entitiesList[it]!!.createEntity(this) } ?: emptySequence()
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToAbstractManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                                  parentId: PId<T>): Sequence<SUBT> {
    return refs.getOneToAbstractManyChildren(connectionId, parentId)?.asSequence()?.map { pid ->
      entityDataByIdOrDie(pid).createEntity(this) as SUBT
    } ?: emptySequence()
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractAbstractOneToOneChildren(connectionId: ConnectionId<T, SUBT>,
                                                                                 parentId: PId<T>): Sequence<SUBT> {
    return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { pid ->
      sequenceOf(entityDataByIdOrDie(pid).createEntity(this) as SUBT)
    } ?: emptySequence()
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToAbstractOneParent(connectionId: ConnectionId<T, SUBT>,
                                                                               childId: PId<SUBT>): T? {
    return refs.getOneToAbstractOneParent(connectionId, childId)?.let { entityDataByIdOrDie(it).createEntity(this) as T }
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToOneChild(connectionId: ConnectionId<T, SUBT>,
                                                                      parentId: PId<T>): SUBT? {
    val entitiesList = entitiesByType[connectionId.childClass.java] ?: return null
    return refs.getOneToOneChild(connectionId, parentId.arrayId) { entitiesList[it]!!.createEntity(this) }
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToOneParent(connectionId: ConnectionId<T, SUBT>,
                                                                       childId: PId<SUBT>): T? {
    val entitiesList = entitiesByType[connectionId.parentClass.java] ?: return null
    return refs.getOneToOneParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) }
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToManyParent(connectionId: ConnectionId<T, SUBT>, childId: PId<SUBT>): T? {
    val entitiesList = entitiesByType[connectionId.parentClass.java] ?: return null
    return refs.getOneToManyParent(connectionId, childId.arrayId) { entitiesList[it]!!.createEntity(this) }
  }


  override fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    return entitiesByType.all().asSequence().flatMap { it.value.all() }.filterNotNull()
      .map { it.createEntity(this) }.filterIsInstance<TypedEntityWithPersistentId>().find { it.persistentId() == id } as E?
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out TypedEntity>, List<TypedEntity>>> {
    val res = HashMap<EntitySource, MutableMap<Class<out TypedEntity>, MutableList<TypedEntity>>>()
    entitiesByType.all().forEach { (type, entities) ->
      entities.all().forEach {
        if (sourceFilter(it.entitySource)) {
          val mutableMapRes = res.getOrPut(it.entitySource, { mutableMapOf() })
          mutableMapRes.getOrPut(type, { mutableListOf() }).add(it.createEntity(this))
        }
      }
    }
    return res
  }
}

internal object ClassConversion {

  private val modifiableToEntityCache = HashMap<KClass<*>, KClass<*>>()

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
    return (Class.forName(clazz.qualifiedName + "Data") as Class<PEntityData<T>>).kotlin
  }

  fun <M : PEntityData<out T>, T : TypedEntity> entityDataToEntity(clazz: KClass<out M>): KClass<T> {
    return (Class.forName(clazz.qualifiedName!!.dropLast(4)) as Class<T>).kotlin
  }

  fun <D : PEntityData<T>, T : TypedEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableTypedEntity<T>> {
    return Class.forName(getPackage(clazz) + "Modifiable" + clazz.simpleName!!.dropLast(4)).kotlin as KClass<ModifiableTypedEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String {
    return clazz.qualifiedName!!.dropLastWhile { it != '.' }
  }
}
