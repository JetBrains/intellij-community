// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.*
import com.intellij.workspace.api.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor

internal open class PEntityStorage constructor(
  open val entitiesByType: EntitiesBarrel,
  open val refs: RefsTable
) : TypedEntityStorage {
  override fun <E : TypedEntity> entities(entityClass: Class<E>): Sequence<E> {
    return entitiesByType[entityClass]?.all()?.map { it.createEntity(this) } ?: emptySequence()
  }

  internal fun <E : TypedEntity> entityDataById(id: PId<E>): PEntityData<E>? {
    return entitiesByType[id.clazz.java]?.get(id.arrayId)
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

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToManyRefs(connectionId: ConnectionId<T, SUBT>, id: PId<T>): Sequence<SUBT> {
    val entitiesList = entitiesByType[connectionId.toSequenceClass.java] ?: return emptySequence()
    return refs.getOneToMany(connectionId, id.arrayId)?.map { entitiesList[it]!!.createEntity(this) } ?: emptySequence()
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractManyToOneRef(connectionId: ConnectionId<T, SUBT>, index: PId<SUBT>): T? {
    val entitiesList = entitiesByType[connectionId.toSingleClass.java] ?: return null
    return refs.getManyToOne(connectionId, index.arrayId) { entitiesList[it]!!.createEntity(this) }
  }

  override fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    return entitiesByType.all().asSequence().map { it.value.all() }.flatten().filterNotNull()
      .map { it.createEntity(this) }.filterIsInstance<TypedEntityWithPersistentId>().find { it.persistentId() == id } as E
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

  companion object {
    fun create(): TypedEntityStorageBuilder = PEntityStorageBuilder()
  }
}

internal class PEntityStorageBuilder(
  private val origStorage: PEntityStorage,
  override var entitiesByType: MutableEntitiesBarrel,
  override var refs: MutableRefsTable
) : TypedEntityStorageBuilder, PEntityStorage(entitiesByType, refs) {

  private val changeLogImpl: MutableList<ChangeEntry> = mutableListOf()

  private val changeLog: List<ChangeEntry>
    get() = changeLogImpl

  private inline fun updateChangeLog(updater: (MutableList<ChangeEntry>) -> Unit) {
    updater(changeLogImpl)
    modificationCount++
  }

  constructor() : this(PEntityStorage(EntitiesBarrel(), RefsTable()), MutableEntitiesBarrel(), MutableRefsTable())

  private sealed class ChangeEntry {
    data class AddEntity<E : TypedEntity>(val entityData: PEntityData<E>, val clazz: Class<E>) : ChangeEntry()
    data class RemoveEntity(val id: PId<*>) : ChangeEntry()
    data class ReplaceEntity(val id: PId<*>, val newData: PEntityData<*>) : ChangeEntry()
  }

  override var modificationCount: Long = 0
    private set

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> addEntity(clazz: Class<M>,
                                                                         source: EntitySource,
                                                                         initializer: M.() -> Unit): T {
    val entityDataClass = clazz.kotlin.annotations.filterIsInstance<PEntityDataClass>().first().clazz
    val unmodifiableEntityClass = clazz.kotlin.annotations.filterIsInstance<PEntityClass>().first().clazz.java as Class<T>

    val pEntityData = entityDataClass.primaryConstructor!!.call() as PEntityData<T>

    pEntityData.entitySource = source

    entitiesByType.add(pEntityData, unmodifiableEntityClass)

    val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
    modifiableEntity.initializer()
    updateChangeLog { it.add(ChangeEntry.AddEntity(pEntityData, unmodifiableEntityClass)) }

    return pEntityData.createEntity(this)
  }

  // modificationCount is not incremented
  // TODO: 27.03.2020 T and E should be the same type. Looks like an error in kotlin inheritance algorithm
  private fun <T : TypedEntity, E : TypedEntity> addEntityWithRefs(entity: PEntityData<T>, clazz: Class<E>, storage: PEntityStorage) {
    clazz as Class<T>
    entitiesByType.add(entity, clazz)

    handleReferences(storage, entity, clazz)
  }

  // modificationCount is not incremented
  // TODO: 27.03.2020 T and E should be the same type. Looks like an error in kotlin inheritance algorithm
  private fun <T : TypedEntity, E : TypedEntity> replaceEntityWithRefs(newEntity: PEntityData<T>,
                                                                       clazz: Class<E>,
                                                                       storage: PEntityStorage) {
    clazz as Class<T>

    entitiesByType.replaceById(newEntity, clazz)

    handleReferences(storage, newEntity, clazz)
  }

  private fun <T : TypedEntity> handleReferences(storage: PEntityStorage,
                                                 newEntity: PEntityData<T>,
                                                 clazz: Class<T>) {
    val childrenRefs = storage.refs.getChildren(newEntity.id, clazz)
    for ((connection, newIds) in childrenRefs) {
      refs.updateOneToMany(connection, newEntity.id, newIds)
    }

    val parentRefs = storage.refs.getParents(newEntity.id, clazz)
    for ((connection, newId) in parentRefs) {
      refs.updateManyToOne(connection, newEntity.id, newId)
    }
  }

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    val copiedData = entitiesByType.getEntityDataForModification((e as PTypedEntity<T>).id)
    (copiedData.wrapAsModifiable(this) as M).change()
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(e.id, copiedData)) }
    return copiedData.createEntity(this)
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = entitiesByType.getEntityDataForModification((e as PTypedEntity<T>).id)
    copiedData.entitySource = newSource
    modificationCount++
    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: TypedEntity) {
    removeEntity((e as PTypedEntity<out TypedEntity>).id)
    updateChangeLog { it.add(ChangeEntry.RemoveEntity(e.id)) }
  }


  // modificationCount is not incremented
  private fun <E : TypedEntity> removeEntity(idx: PId<E>) {
    val accumulator = HashMap<Class<out TypedEntity>, MutableSet<Int>>()
    accumulator[idx.clazz.java] = mutableSetOf(idx.arrayId)

    accumulateEntitiesToRemove(idx.arrayId, idx.clazz.java, accumulator)

    for ((klass, ids) in accumulator) {
      ids.forEach { id -> entitiesByType.remove(id, klass) }
    }
  }

  private fun <T : TypedEntity> accumulateEntitiesToRemove(idx: Int,
                                                           entityClass: Class<T>,
                                                           accumulator: MutableMap<Class<out TypedEntity>, MutableSet<Int>>) {
    val hardRef = refs.getHardReferencesOf(entityClass, idx)
    for ((klass, ids) in hardRef) {
      ids.forEach { id ->
        if (id in accumulator.getOrPut(klass.java) { HashSet() }) return@forEach
        accumulator.getOrPut(klass.java) { HashSet() }.add(id)
        accumulateEntitiesToRemove(id, klass.java, accumulator)
      }
      refs.removeOneToMany(ConnectionId.create(entityClass.kotlin, klass, true), idx)
    }
  }

  fun <T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> updateOneToMany(connectionId: ConnectionId<T, SUBT>,
                                                                       id: PId<T>,
                                                                       updateTo: Sequence<SUBT>) {
    refs.updateOneToMany(connectionId, id.arrayId, updateTo)
  }

  fun <T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> updateManyToOne(connectionId: ConnectionId<T, SUBT>, id: PId<SUBT>, updateTo: T?) {
    if (updateTo != null) {
      refs.updateManyToOne(connectionId, id.arrayId, updateTo)
    }
    else {
      refs.removeManyToOne(connectionId, id.arrayId)
    }
  }

  override fun <E : TypedEntity> createReference(e: E): EntityReference<E> {
    TODO("Not yet implemented")
  }

  private fun PEntityData<*>.persistentId() =
    (this.createEntity(this@PEntityStorageBuilder) as TypedEntityWithPersistentId).persistentId()

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage) {

    val entitiesByPersistentIdHash = groupByPersistentIdHash(this)
    val replaceWithEntitiesByPersistentIdHash = groupByPersistentIdHash(replaceWith as PEntityStorage)


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
        if (refs.getParents(oldData.id, clazz).isNotEmpty()) continue

        val persistentId = oldData.persistentId()

        val newData = newEntities.firstOrNull { it.first.persistentId() == persistentId && sourceFilter(it.first.entitySource) }
        if (newData != null) {
          replaceMap[PId(oldData.id, clazz.kotlin)] = PId(newData.first.id, newData.second.kotlin)

          if (!shallowEquals(oldData, newData.first, emptyBiMap, replaceWith)) {
            val replaceWithData = newData.first.clone()

            replaceEntityWithRefs(replaceWithData, newData.second, this)
            updateChangeLog { it.add(ChangeEntry.ReplaceEntity((PId(oldData.id, clazz.kotlin)), replaceWithData)) }
          }

          newEntities.remove(newData)
        }
        else {
          // Remove right here?
          // TODO Don't forget to check sourceFilter
          entitiesToRemove.add(PId(oldData.id, clazz.kotlin))
        }
      }
    }

    for ((idHash, newEntities) in replaceWithEntitiesByPersistentIdHash.asMap()) {
      val oldEntities = entitiesByPersistentIdHash[idHash] ?: mutableSetOf()
      for ((newData, clazz) in newEntities) {
        if (!sourceFilter(newData.entitySource)) continue
        // Currently persistent id entities must not have any parents
        if (replaceWith.refs.getParents(newData.id, clazz).isNotEmpty()) continue

        val persistentId = newData.persistentId()

        val oldData = oldEntities.firstOrNull { it.first.persistentId() == persistentId && sourceFilter(it.first.entitySource) }
        if (oldData == null) {
          // Add sub-graph right here?
          // TODO Don't forget to check sourceFilter
          entitiesToAdd.add(PId(newData.id, clazz.kotlin))
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
      val newChildren = replaceWith.refs.getChildren(newId.arrayId, newId.clazz.java)
        .map { (cId, seq) -> seq.map { PId(it, cId.toSequenceClass) } }
        .flatMap { it.asIterable() }
        .filter { it !in entitiesToAdd && !replaceMap.containsKey(it) }
        .map { replaceWith.entityDataById(it)!! }
      val oldChildren = this.refs.getChildren(oldId.arrayId, oldId.clazz.java)
        .map { (cId, seq) -> seq.map { PId(it, cId.toSequenceClass) } }
        .flatMap { it.asIterable() }
        .filter { it !in entitiesToRemove && !replaceMap.containsKey(it) }
        .map { this.entityDataById(it)!! }

      val eq = classifyByEquals(
        c1 = oldChildren,
        c2 = newChildren,
        hashFunc1 = this::shallowHashCode, hashFunc2 = this::shallowHashCode,
        equalsFunc = { v1, v2 -> shallowEquals(v1, v2, replaceMap, replaceWith) })

      for ((oldChildData, newChildData) in eq.equal) {
        val newPId = (newChildData.createEntity(replaceWith) as PTypedEntity<*>).id
        val oldPId = (oldChildData.createEntity(this) as PTypedEntity<*>).id
        if (newPId in entitiesToAdd) error("id=${newChildData.id} already exists in entriesToAdd")
        if (oldPId in entitiesToRemove) error("id=${oldChildData.id} already exists in entitiesToRemove")

        queue.add(oldPId to newPId)
        replaceMap[oldPId] = newPId
      }

      // TODO Check we won't get any persistent id nodes?
      for (data in eq.onlyIn1) {
        traverseNodes(this, (data.createEntity(this) as PTypedEntity<out TypedEntity>).id) { id ->
          if (replaceMap.containsKey(id)) {
            error("Trying to remove node with id=$id: it's already marked for replacement")
          }

          entitiesToRemove.add(id)
        }
      }

      // TODO Check we won't get any persistent id nodes?
      for (data in eq.onlyIn2) {
        traverseNodes(replaceWith, (data.createEntity(this) as PTypedEntity<out TypedEntity>).id) { id ->
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
      val id = (data.createEntity(this) as PTypedEntity<out TypedEntity>).id
      entitiesToRemove.add(id)
    }

    for (data in eq.onlyIn2) {
      val id = (data.createEntity(this) as PTypedEntity<out TypedEntity>).id
      entitiesToAdd.add(id)
    }

    for ((oldChildData, newChildData) in eq.equal) {
      val oldId = (oldChildData.createEntity(this) as PTypedEntity<out TypedEntity>).id
      val newId = (newChildData.createEntity(this) as PTypedEntity<out TypedEntity>).id
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
                                 storage: PEntityStorage,
                                 replaceMap: BiMap<PId<out TypedEntity>, PId<out TypedEntity>>,
                                 sourceFilter: (EntitySource) -> Boolean) {
    for ((conId, thisId) in storage.refs.getParents(id.arrayId, id.clazz.java)) {
      val parentId = PId(thisId, conId.toSingleClass)
      if (!replaceMap.containsValue(parentId)) {
        if (sourceFilter(storage.entityDataById(parentId)!!.entitySource)) {
          recursiveAddEntity(parentId/*, backReferrers*/, storage, replaceMap, sourceFilter)
        }
        else {
          replaceMap[parentId] = parentId
        }
      }
    }

    val data = storage.entityDataById(id)!!
    val newData = data.clone()
    replaceMap[(newData.createEntity(this) as PTypedEntity<out TypedEntity>).id] = id
    //copyEntityProperties(data, newData, replaceMap.inverse())
    addEntityWithRefs(newData, id.clazz.java, storage)
    //addEntity(newData, null, handleReferrers = true)
    updateChangeLog { it.add(createAddEntity(newData, id.clazz.java)) }
  }

  private fun <E : TypedEntity, T : TypedEntity> createAddEntity(data: PEntityData<E>, clazz: Class<T>): ChangeEntry.AddEntity<E> {
    return ChangeEntry.AddEntity(data, clazz as Class<E>)
  }

  private fun deepEquals(data1: PEntityData<out TypedEntity>,
                         data2: PEntityData<out TypedEntity>,
                         replaceMap: Map<PId<*>, PId<*>>,
                         storage1: PEntityStorage,
                         storage2: PEntityStorage,
    /*
                             backReferrers1: MultiMap<Long, Long>,
                             backReferrers2: MultiMap<Long, Long>,
    */
                         equalsCache: MutableMap<Pair<PId<out TypedEntity>, PId<out TypedEntity>>, Boolean>): Boolean {

    val id1 = (data1.createEntity(this) as PTypedEntity<out TypedEntity>).id
    val id2 = (data2.createEntity(this) as PTypedEntity<out TypedEntity>).id
    val cachedResult = equalsCache[id1 to id2]
    if (cachedResult != null) return cachedResult

    if (replaceMap[id1] == id2) return true


    val data1parents = storage1.refs.getParents(id1.arrayId, id1.clazz.java).map { (conId, value) ->
      storage1.entityDataById(PId(value, conId.toSingleClass))!!
    }
    val data2parents = storage2.refs.getParents(id2.arrayId, id2.clazz.java).map { (conId, value) ->
      storage2.entityDataById(PId(value, conId.toSingleClass))!!
    }

    val eq = classifyByEquals(data1parents, data2parents, this::shallowHashCode, this::shallowHashCode) { e1, e2 ->
      deepEquals(e1, e2, replaceMap, storage1, storage2/*, backReferrers1, backReferrers2*/, equalsCache)
    }

    val result = eq.onlyIn1.isEmpty() && eq.onlyIn2.isEmpty()
    equalsCache[id1 to id2] = result
    return result
  }


  private fun foreachNotProcessedEntity(storage: PEntityStorage,
                                        sourceFilter: (EntitySource) -> Boolean,
                                        replaceMap: Map<PId<out TypedEntity>, PId<out TypedEntity>>,
                                        otherProcessedSet: Set<PId<out TypedEntity>>,
                                        block: (PEntityData<out TypedEntity>) -> Unit) {
    for (entityFamily in storage.entitiesByType.all().values) {
      entityFamily.all().filter { sourceFilter(it.entitySource) }.forEach {
        val id = (it.createEntity(this) as PTypedEntity<out TypedEntity>).id
        if (!replaceMap.containsKey(id) && !otherProcessedSet.contains(id)) {
          block(it)
        }
      }
    }
  }

  private fun traverseNodes(storage: PEntityStorage, startNode: PId<out TypedEntity>, block: (PId<out TypedEntity>) -> Unit) {
    val queue = Queues.newArrayDeque(listOf(startNode))
    while (queue.isNotEmpty()) {
      val id = queue.remove()
      block(id)

      for ((cid, seq) in storage.refs.getChildren(id.arrayId, id.clazz.java)) {
        seq.forEach {
          queue.add(PId(it, cid.toSequenceClass))
        }
      }
    }
  }

  private fun shallowHashCode(data: PEntityData<out TypedEntity>): Int {
    // TODO: 30.03.2020 Implement
    return data.hashCode()
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

  data class EqualityResult<T1, T2>(
    val onlyIn1: List<T1>,
    val onlyIn2: List<T2>,
    val equal: List<Pair<T1, T2>>
  )

  private fun shallowEquals(oldData: PEntityData<out TypedEntity>,
                            newData: PEntityData<out TypedEntity>,
                            emptyBiMap: HashBiMap<PId<*>, PId<*>>?,
                            newStorage: PEntityStorage): Boolean {
    return oldData.createEntity(this).hasEqualProperties(newData.createEntity(newStorage))
  }


  private fun groupByPersistentIdHash(storage: PEntityStorage): Multimap<Int, Pair<PEntityData<*>, Class<out TypedEntity>>> {
    val res = HashMultimap.create<Int, Pair<PEntityData<*>, Class<out TypedEntity>>>()
    for ((clazz, entityFamily) in storage.entitiesByType.all()) {
      for (pEntityData in entityFamily.all()) {
        if (clazz !is TypedEntityWithPersistentId) continue
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
    val changes = LinkedHashMap<Int, Pair<Class<*>, EntityChange<*>>>()
    for (change in changeLog) {
      when (change) {
        is ChangeEntry.AddEntity<*> -> {
          val addedEntity = change.entityData.createEntity(this) as PTypedEntity<*>
          changes[change.entityData.id] = addedEntity.id.clazz.java to EntityChange.Added(addedEntity)
        }
        is ChangeEntry.RemoveEntity -> {
          val removedData = originalImpl.entityDataById(change.id)
          val oldChange = changes.remove(change.id.arrayId)
          if (oldChange?.second !is EntityChange.Added && removedData != null) {
            val replacedEntity = removedData.createEntity(this) as PTypedEntity<*>
            changes[change.id.arrayId] = change.id.clazz.java to EntityChange.Removed(replacedEntity)
          }
        }
        is ChangeEntry.ReplaceEntity -> {
          val oldChange = changes.remove(change.id.arrayId)
          if (oldChange?.second is EntityChange.Added) {
            val addedEntity = change.newData.createEntity(originalImpl) as PTypedEntity<*>
            changes[change.id.arrayId] = addedEntity.id.clazz.java to EntityChange.Added(addedEntity)
          }
          else {
            val oldData = originalImpl.entityDataById(change.id)
            if (oldData != null) {
              val replacedData = oldData.createEntity(originalImpl) as PTypedEntity<*>
              val replaceToData = change.newData.createEntity(this) as PTypedEntity<*>
              changes[change.id.arrayId] = replacedData.id.clazz.java to EntityChange.Replaced(replacedData, replaceToData)
            }
          }
        }
      }
    }
    return changes.values.groupBy { it.first }.mapValues { list -> list.value.map { it.second } }
  }

  override fun resetChanges() {
    updateChangeLog { it.clear() }
    entitiesByType = MutableEntitiesBarrel.from(origStorage.entitiesByType)
    refs = MutableRefsTable.from(origStorage.refs)
  }

  override fun toStorage(): TypedEntityStorage {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    return PEntityStorage(newEntities, newRefs)
  }

  override fun isEmpty(): Boolean = changeLogImpl.isEmpty()

  override fun addDiff(diff: TypedEntityStorageDiffBuilder) {

    val diffLog = (diff as PEntityStorageBuilder).changeLog
    updateChangeLog { it.addAll(diffLog) }
    for (change in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity<*> -> addEntityWithRefs(change.entityData, change.clazz, diff)
        is ChangeEntry.RemoveEntity -> {
          if (this.entityDataById(change.id) != null) {
            removeEntity(change.id)
          }
        }
        is ChangeEntry.ReplaceEntity -> {
          replaceEntityWithRefs(change.newData, change.id.clazz.java, diff)
        }
      }
    }
    // TODO: 27.03.2020 Here should be consistency check
  }

  companion object {
    fun from(storage: TypedEntityStorage): PEntityStorageBuilder {
      storage as PEntityStorage
      val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
      val copiedRefs = MutableRefsTable.from(storage.refs)
      return PEntityStorageBuilder(storage, copiedBarrel, copiedRefs)
    }
  }
}

fun main() {
  val pStoreBuilder = PEntityStorage.create()
  val createdEntity = pStoreBuilder.addEntity(PFolderModifiableEntity::class.java, MySource) {
    this.data = "xxxx"
  }
  pStoreBuilder.addEntity(PSubFolderModifiableEntity::class.java, MySource) {
    this.data = "XYZ"
    this.parent = createdEntity
  }
  pStoreBuilder.addEntity(PSubFolderModifiableEntity::class.java, MySource) {
    this.data = "XYZ2"
    this.parent = createdEntity
  }
  pStoreBuilder.addEntity(PSoftSubFolderModifiableEntity::class.java, MySource) {
    this.parent = createdEntity
  }

  printStorage(pStoreBuilder)
  println("---------------")
  pStoreBuilder.removeEntity(pStoreBuilder.entities(PFolderEntity::class.java).first())
  printStorage(pStoreBuilder)
}

private fun printStorage(pStoreBuilder: TypedEntityStorageBuilder) {
  println(pStoreBuilder.entities(PFolderEntity::class.java).toList())
  println(pStoreBuilder.entities(PSubFolderEntity::class.java).toList())
  println(pStoreBuilder.entities(PSoftSubFolder::class.java).toList())

  println(pStoreBuilder.entities(PSubFolderEntity::class.java).firstOrNull()?.parent)
  println(pStoreBuilder.entities(PFolderEntity::class.java).firstOrNull()?.children?.toList())
  println(pStoreBuilder.entities(PFolderEntity::class.java).firstOrNull()?.softChildren?.toList())
}

