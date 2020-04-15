// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.*
import com.intellij.workspace.api.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible


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

  private fun PEntityData<*>.hasPersistentId(): Boolean {
    val entity = this.createEntity(this@PEntityStorageBuilder)
    return entity is TypedEntityWithPersistentId
  }

  private fun PEntityData<*>.identificator(): Any {
    val entity = this.createEntity(this@PEntityStorageBuilder)
    return if (entity is TypedEntityWithPersistentId) {
      entity.persistentId()
    }
    else {
      this.hashCode()
    }
  }

  private fun ArrayListMultimap<Any, PEntityData<out TypedEntity>>.find(entity: PEntityData<out TypedEntity>): PEntityData<out TypedEntity>? {
    val possibleValues = this[entity.identificator()]
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
    this.entitiesByType.all().asSequence().map { it.value }.flatMap { it.all() }
      .filter { sourceFilter(it.entitySource) }
      .forEach { leftMatchedNodes.put(it.identificator(), it) }

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
          unmatchedLeftReferencedNodes.put(parentEntity.identificator(), parentEntity)
        }
      }

      for ((connectionId, childrenIds) in this.refs.getChildrenRefsOfParentBy(entityId, false)) {
        for (childId in childrenIds) {
          val childEntity = this.entityDataByIdOrDie(childId)
          if (sourceFilter(childEntity.entitySource)) {
            this.refs.removeParentToChildRef(connectionId, entityId, childId)
          }
          else {
            unmatchedLeftReferencedNodes.put(childEntity.identificator(), childEntity)
          }
        }
      }
    }

    // 2) Traverse entities of the enemy
    for ((clazz, entityFamily) in replaceWith.entitiesByType) {
      for (matchedEntityData in entityFamily.all().filter { sourceFilter(it.entitySource) }) {
        rightMatchedNodes.put(matchedEntityData.identificator(), matchedEntityData)

        val leftNode = leftMatchedNodes.find(matchedEntityData)
        if (leftNode != null) {
          replaceMap[leftNode.createPid()] = matchedEntityData.createPid()
          if (leftNode.hasPersistentId() && leftNode != matchedEntityData) {
            val clonedEntity = matchedEntityData.clone()
            clonedEntity.id = leftNode.id
            this.entitiesByType.replaceById(clonedEntity as PEntityData<TypedEntity>, clonedEntity.createPid().clazz.java)
            // TODO: 15.04.2020 Children and parents?
            updateChangeLog { it.add(ChangeEntry.ReplaceEntity(clonedEntity, emptyMap(), emptyMap())) }
          }
          leftMatchedNodes.remove(leftNode.identificator(), leftNode)
        }
        else {
          val newEntity = this.entitiesByType.cloneAndAdd(matchedEntityData as PEntityData<TypedEntity>, clazz as Class<TypedEntity>)
          replaceMap[newEntity.createPid()] = matchedEntityData.createPid()
          // TODO: 15.04.2020 Children and parents?
          updateChangeLog { it.add(ChangeEntry.AddEntity(newEntity, newEntity.createPid().clazz.java, emptyMap(), emptyMap())) }
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

        this.refs.updateParentOfChild(connectionId as ConnectionId<TypedEntity, TypedEntity>, localChildId, localParentId)
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
    //updateChangeLog { it.add(createAddEntity(newData, id.clazz.java)) }
  }

/*
  private fun <E : TypedEntity, T : TypedEntity> createAddEntity(data: PEntityData<E>, clazz: Class<T>): ChangeEntry.AddEntity<E> {
    // Handle children and parent references
    return ChangeEntry.AddEntity(data, clazz as Class<E>, emptyMap(), emptyMap())
  }
*/

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
