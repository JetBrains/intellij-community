// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor

open class PEntityStorage constructor(
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

class PEntityStorageBuilder(
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

    val entities = getMutableEntityFamily(unmodifiableEntityClass)
    entities.add(pEntityData)

    val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
    modifiableEntity.initializer()
    updateChangeLog { it.add(ChangeEntry.AddEntity(pEntityData, unmodifiableEntityClass)) }

    return pEntityData.createEntity(this)
  }

  // modificationCount is not incremented
  // TODO: 27.03.2020 T and E should be the same type. Looks like an error in kotlin inheritance algorithm
  private fun <T : TypedEntity, E : TypedEntity> addEntityWithRefs(entity: PEntityData<T>, clazz: Class<E>, storage: PEntityStorage) {
    clazz as Class<T>
    getMutableEntityFamily(clazz).add(entity)

    handleReferences(storage, entity, clazz)
  }

  // modificationCount is not incremented
  // TODO: 27.03.2020 T and E should be the same type. Looks like an error in kotlin inheritance algorithm
  private fun <T : TypedEntity, E : TypedEntity> replaceEntityWithRefs(newEntity: PEntityData<T>,
                                                                       clazz: Class<E>,
                                                                       storage: PEntityStorage) {
    clazz as Class<T>
    val family = getMutableEntityFamily(clazz)
    if (!family.exists(newEntity.id)) error("Nothing to replace")  // TODO: 25.03.2020 Or just call "add"?
    family.replaceById(newEntity)

    handleReferences<T>(storage, newEntity, clazz)
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
    val unmodifiableEntityClass = e.javaClass
    val copiedData = getMutableEntityFamily(unmodifiableEntityClass).getEntityDataForModification((e as PTypedEntity<T>).id)
    (copiedData.wrapAsModifiable(this) as M).change()
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(e.id, copiedData)) }
    return copiedData.createEntity(this)
  }

  private fun <T : TypedEntity> getMutableEntityFamily(unmodifiableEntityClass: Class<T>): MutableEntityFamily<T> {
    val entityFamily = entitiesByType[unmodifiableEntityClass]
    if (entityFamily == null) {
      val newMutable = MutableEntityFamily.createEmptyMutable<T>()
      entitiesByType[unmodifiableEntityClass] = newMutable
      return newMutable
    }
    else {
      if (entityFamily !is MutableEntityFamily<T> || !entityFamily.familyCopiedToModify) {
        val newMutable = entityFamily.copyToMutable()
        entitiesByType[unmodifiableEntityClass] = newMutable
        return newMutable
      }
      else {
        return entityFamily
      }
    }
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = getMutableEntityFamily(e.javaClass).getEntityDataForModification((e as PTypedEntity<T>).id)
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
      val modifiableEntityFamily = getMutableEntityFamily(klass)
      ids.forEach { id ->
        modifiableEntityFamily.remove(id)
      }
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

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage) {
    TODO("Not yet implemented")
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
    fun from(storage: PEntityStorage): PEntityStorageBuilder {
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

