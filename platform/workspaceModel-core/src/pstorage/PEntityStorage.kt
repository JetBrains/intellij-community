// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor

open class PEntityStorage constructor(
  open val entitiesByType: EntitiesBarrel,
  val refs: RefsTable
) : TypedEntityStorage {
  override fun <E : TypedEntity> entities(entityClass: Class<E>): Sequence<E> {
    return getEntities(entitiesByType[entityClass])
  }

  protected fun <E : TypedEntity> getEntities(listToSearch: EntityFamily<E>?) =
    listToSearch?.all()?.map { it.createEntity(this) } ?: emptySequence()

  override fun <E : TypedEntity, R : TypedEntity> referrers(e: E,
                                                            entityClass: KClass<R>,
                                                            property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    return entities(entityClass.java).filter { property.get(it).resolve(this) == e }
  }

  override fun <E : TypedEntityWithPersistentId, R : TypedEntity> referrers(id: PersistentEntityId<E>, entityClass: Class<R>): Sequence<R> {
    TODO("Not yet implemented")
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractOneToManyRefs(connectionId: ConnectionId,
                                                                      remoteClass: Class<SUBT>,
                                                                      id: PId<T>): Sequence<SUBT> {
    val entitiesList = entitiesByType[remoteClass] ?: return emptySequence()
    return getOneToManyRefs(connectionId, id, entitiesList, refs) ?: emptySequence()
  }

  protected fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyRefs(connectionId: ConnectionId,
                                                                       index: PId<T>,
                                                                       entitiesList: EntityFamily<SUBT>,
                                                                       searchedTable: RefsTable
  ): Sequence<SUBT>? = searchedTable.getOneToMany(connectionId, index.arrayId)?.map { entitiesList[it]!!.createEntity(this) }

  protected fun <T : TypedEntity> getManyToOneRef(connectionId: ConnectionId, index: Int,
                                                  entitiesList: EntityFamily<T>, searchedTable: RefsTable): T? {
    return searchedTable.getManyToOne(connectionId, index) { entitiesList[it]!!.createEntity(this) }
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractManyToOneRef(connectionId: ConnectionId,
                                                                     remoteClass: Class<T>,
                                                                     index: PId<SUBT>): T? {
    val entitiesList = entitiesByType[remoteClass] ?: return null
    return getManyToOneRef(connectionId, index.arrayId, entitiesList, refs)
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
  private val origStorage: PEntityStorage
) : TypedEntityStorageBuilder, PEntityStorage(origStorage.entitiesByType, origStorage.refs) {

  constructor() : this(PEntityStorage(EntitiesBarrel(), RefsTable()))

  private val modified: MutableEntitiesBarrel = MutableEntitiesBarrel()
  private val clonedRefs: MutableRefsTable = MutableRefsTable()

  override fun <E : TypedEntity> entities(entityClass: Class<E>): Sequence<E> {
    val listToSearch = modified[entityClass] ?: entitiesByType[entityClass]
    return getEntities(listToSearch)
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

    val entities = getEntitiesToModify(unmodifiableEntityClass)
    entities.add(pEntityData)

    val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
    modifiableEntity.initializer()
    modificationCount++

    return pEntityData.createEntity(this)
  }

  // modificationCount is not incremented
  private fun <T : TypedEntity> addEntity(entity: PEntityData<T>, clazz: Class<T>) {
    getEntitiesToModify(clazz).add(entity)
  }

  // modificationCount is not incremented
  private fun <T : TypedEntity> replaceEntity(newEntity: PEntityData<T>, clazz: Class<T>) {
    val family = getEntitiesToModify(clazz)
    if (!family.exists(newEntity.id)) error("Nothing to replace")  // TODO: 25.03.2020 Or just call "add"?
    family.replaceById(newEntity)
  }

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    val unmodifiableEntityClass = e.javaClass
    val copiedData = getEntitiesToModify(unmodifiableEntityClass).getEntityDataForModification((e as PTypedEntity<T>).id)
    (copiedData.wrapAsModifiable(this) as M).change()
    modificationCount++
    return copiedData.createEntity(this)
  }

  private fun <T : TypedEntity> getEntities(unmodifiableEntityClass: Class<T>): EntityFamily<T> {
    return modified[unmodifiableEntityClass] ?: entitiesByType[unmodifiableEntityClass] ?: EntityFamily.empty()
  }

  private fun <T : TypedEntity> getEntitiesToModify(unmodifiableEntityClass: Class<T>): MutableEntityFamily<T> {
    return modified[unmodifiableEntityClass] ?: let {
      val origSet = entitiesByType[unmodifiableEntityClass]?.copyToMutable() ?: MutableEntityFamily.createEmpty()
      modified[unmodifiableEntityClass] = origSet
      origSet
    }
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val copiedData = getEntitiesToModify(e.javaClass).getEntityDataForModification((e as PTypedEntity<T>).id)
    copiedData.entitySource = newSource
    modificationCount++
    return copiedData.createEntity(this)
  }

  override fun removeEntity(e: TypedEntity) {
    modificationCount++
    removeEntityX((e as PTypedEntity<out TypedEntity>).id)
  }


  // modificationCount is not incremented
  private fun <E : TypedEntity> removeEntityX(idx: PId<E>) {
    removeEntity(idx.arrayId, idx.clazz.java)
  }

  private fun <T : TypedEntity> removeEntity(idx: Int, entityClass: Class<T>) {
    getEntitiesToModify(entityClass).remove(idx)
  }

  override fun <T : TypedEntity, SUBT : TypedEntity> extractOneToManyRefs(connectionId: ConnectionId,
                                                                          remoteClass: Class<SUBT>,
                                                                          id: PId<T>): Sequence<SUBT> {

    // TODO: 24.03.2020 Check if already removed
    val entitiesList = getEntities(remoteClass)
    return getOneToManyRefs(connectionId, id, entitiesList, clonedRefs)
           ?: getOneToManyRefs(connectionId, id, entitiesList, refs)
           ?: emptySequence()
  }

  override fun <T : TypedEntity, SUBT : TypedEntity> extractManyToOneRef(connectionId: ConnectionId,
                                                                         remoteClass: Class<T>,
                                                                         index: PId<SUBT>): T? {
    val entitiesList = getEntities(remoteClass)
    return getManyToOneRef(connectionId, index.arrayId, entitiesList, clonedRefs)
           ?: getManyToOneRef(connectionId, index.arrayId, entitiesList, refs)
  }

  private fun copyTable(connectionId: ConnectionId) {
    clonedRefs.cloneTableFrom(connectionId, refs)
  }

  fun <T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> updateOneToMany(connectionId: ConnectionId, id: PId<T>, updateTo: Sequence<SUBT>) {
    copyTable(connectionId)
    clonedRefs.updateOneToMany(connectionId, id.arrayId, updateTo)
  }

  fun <T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> updateManyToOne(connectionId: ConnectionId, id: PId<SUBT>, updateTo: T?) {
    copyTable(connectionId)
    if (updateTo != null) {
      clonedRefs.updateManyToOne(connectionId, id.arrayId, updateTo)
    }
    else {
      clonedRefs.removeManyToOne(connectionId, id.arrayId)
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

  private fun collectDataChanges(): Map<Class<*>, List<EntityDataChange<*>>> {
    // TODO: 23.03.2020 This method doesn't take an argument into account. And should it actually have an argument?
    if (isEmpty()) return emptyMap()

    val res = mutableMapOf<Class<*>, List<EntityDataChange<*>>>()
    for ((key, entitiesList) in modified) {
      val originalEntitiesList = entitiesByType[key]
      if (originalEntitiesList == null) {
        res += key to entitiesList.all().map { EntityDataChange.Added(it) }.toList()
      }
      else {
        val localChanges = mutableListOf<EntityDataChange<*>>()
        val sizeDiff = entitiesList.size - originalEntitiesList.size
        val smallerSize = if (sizeDiff > 0) originalEntitiesList.size else entitiesList.size
        for (i in 0 until smallerSize) {
          val entity = entitiesList[i]
          val origEntity = originalEntitiesList[i]
          if (entity != null && origEntity == null) {
            localChanges += EntityDataChange.Added(entity)
          }
          else if (entity == null && origEntity != null) {
            localChanges += EntityDataChange.Removed(origEntity)
          }
          else if (entity != null && origEntity != null) {
            val origImmutableEntity = origEntity.createEntity(origStorage)
            val immutableEntity = entity.createEntity(this)
            if (origImmutableEntity != immutableEntity) {
              localChanges += EntityDataChange.Replaced(origEntity, entity)
            }
          }
        }
        if (sizeDiff > 0) {
          for (i in smallerSize until smallerSize + sizeDiff) {
            val entity = entitiesList[i]
            if (entity != null) {
              localChanges += EntityDataChange.Added(entity)
            }
          }
        }
        else if (sizeDiff < 0) {
          for (i in smallerSize until smallerSize - sizeDiff) {
            val origEntity = originalEntitiesList[i]
            if (origEntity != null) {
              localChanges += EntityDataChange.Removed(origEntity)
            }
          }
        }

        res += key to localChanges
      }
    }
    return res
  }

  override fun collectChanges(original: TypedEntityStorage): Map<Class<*>, List<EntityChange<*>>> {
    return collectDataChanges().mapValues { (_, value) ->
      value.map { dataChange ->
        when (dataChange) {
          is EntityDataChange.Added<*> -> EntityChange.Added(dataChange.entity.createEntity(this))
          is EntityDataChange.Removed<*> -> EntityChange.Removed(dataChange.entity.createEntity(origStorage))
          is EntityDataChange.Replaced<*> -> EntityChange.Replaced(dataChange.oldEntity.createEntity(origStorage),
                                                                   dataChange.oldEntity.createEntity(this))
        }
      }
    }
  }

  override fun resetChanges() {
    modified.clear()
    clonedRefs.clear()
    modificationCount++
  }

  override fun toStorage(): TypedEntityStorage {
    val newEntities = origStorage.entitiesByType.join(modified)
    val newRefs = origStorage.refs.overlapBy(clonedRefs)
    return PEntityStorage(newEntities, newRefs)
  }

  override fun isEmpty(): Boolean = modified.isEmpty()

  override fun addDiff(diff: TypedEntityStorageDiffBuilder) {
    val changes = (diff as PEntityStorageBuilder).collectDataChanges()
    modificationCount++
    for ((entityClass, changeLog) in changes) {
      for (change in changeLog) {
        when (change) {
          is EntityDataChange.Removed<*> -> this.removeEntity(change.entity.id, entityClass as Class<out TypedEntity>)
          is EntityDataChange.Added<*> -> this.addEntity(change.entity as PEntityData<TypedEntity>, entityClass as Class<TypedEntity>)
          is EntityDataChange.Replaced<*> -> {
            this.replaceEntity(change.newEntity as PEntityData<TypedEntity>, entityClass as Class<TypedEntity>)
          }
        }
      }
    }
  }

  companion object {
    fun from(storage: PEntityStorage) = PEntityStorageBuilder(storage)
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
  println(pStoreBuilder.entities(PFolderEntity::class.java).toList())
  println(pStoreBuilder.entities(PSubFolderEntity::class.java).toList())
  println(pStoreBuilder.entities(PSubFolderEntity::class.java).first().parent)
  println(pStoreBuilder.entities(PFolderEntity::class.java).first().children.toList())
}

