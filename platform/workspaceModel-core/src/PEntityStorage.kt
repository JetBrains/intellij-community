// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

open class PEntityStorage constructor(
  open val entitiesByType: Map<Class<out TypedEntity>, EntityFamily>,
  val refs: RefsTable
) : TypedEntityStorage {
  override fun <E : TypedEntity> entities(entityClass: Class<E>): Sequence<E> {
    return getEntities(entitiesByType[entityClass])
  }

  protected fun <E : TypedEntity> getEntities(listToSearch: EntityFamily?) =
    listToSearch?.entities?.asSequence()?.filterNotNull()?.map { it.createEntity(this) as E } ?: emptySequence()

  override fun <E : TypedEntity, R : TypedEntity> referrers(e: E,
                                                            entityClass: KClass<R>,
                                                            property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    return entities(entityClass.java).filter { property.get(it).resolve(this) == e }
  }

  override fun <E : TypedEntityWithPersistentId, R : TypedEntity> referrers(id: PersistentEntityId<E>, entityClass: Class<R>): Sequence<R> {
    TODO("Not yet implemented")
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractRefs(local: KProperty1<T, Sequence<SUBT>>,
                                                             remote: KProperty1<SUBT, T?>,
                                                             id: PId): Sequence<SUBT> {
    val entitiesList = entitiesByType[remote.javaField!!.declaringClass] ?: return emptySequence()
    return getRefs(local, remote, id.arrayId, entitiesList, refs) ?: emptySequence()
  }

  protected fun <T : TypedEntity, SUBT : TypedEntity> getRefs(local: KProperty1<T, Sequence<SUBT>>,
                                                              remote: KProperty1<SUBT, T?>,
                                                              index: Int,
                                                              entitiesList: EntityFamily,
                                                              searchedTable: RefsTable
  ): Sequence<SUBT>? = searchedTable[local, remote, index]?.map { entitiesList[it]!!.createEntity(this) as SUBT }

  protected fun <T : TypedEntity, SUBT : TypedEntity> getBackRefs(local: KProperty1<SUBT, T?>,
                                                                  remote: KProperty1<T, Sequence<SUBT>>,
                                                                  index: Int,
                                                                  entitiesList: EntityFamily,
                                                                  searchedTable: RefsTable
  ): T? = searchedTable[local, remote, index]?.first { entitiesList[it]!!.createEntity(this) as T }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractBackRef(local: KProperty1<SUBT, T?>,
                                                                remote: KProperty1<T, Sequence<SUBT>>,
                                                                index: PId): T? {
    val entitiesList = entitiesByType[remote.javaField!!.declaringClass as Class<T>] ?: return null
    return getBackRefs(local, remote, index.arrayId, entitiesList, refs)
  }

  override fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    return entitiesByType.asSequence().map { it.value.entities }.flatten().filterNotNull()
      .map { it.createEntity(this) }.filterIsInstance<TypedEntityWithPersistentId>().find { it.persistentId() == id } as E
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out TypedEntity>, List<TypedEntity>>> {
    val res = mutableMapOf<EntitySource, MutableMap<Class<out TypedEntity>, MutableList<TypedEntity>>>()
    entitiesByType.forEach { (type, entities) ->
      entities.entities.asSequence().filterNotNull().forEach {
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

  constructor() : this(PEntityStorage(HashMap(), RefsTable()))

  private val modified: MutableMap<Class<out TypedEntity>, MutableEntityFamily> = mutableMapOf()
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
    val unmodifiableEntityClass = clazz.kotlin.annotations.filterIsInstance<PEntityClass>().first().clazz
    val entities = getEntitiesToModify(unmodifiableEntityClass.java).entities
    val pEntityData = entityDataClass.primaryConstructor!!.call()

    pEntityData.entitySource = source
    pEntityData.id = entities.size // set.size - index of the next inserted entity
    entities += pEntityData

    val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
    modifiableEntity.initializer()
    modificationCount++

    return pEntityData.createEntity(this) as T
  }

  // modificationCount is not incremented
  private fun addEntity(entity: PEntityData, clazz: Class<out PTypedEntity>) {
    val entities = getEntitiesToModify(clazz).entities
    entity.id = entities.size // set.size - index of the next inserted entity
    entities += entity
  }

  // modificationCount is not incremented
  private fun replaceEntity(newEntity: PEntityData, clazz: Class<out PTypedEntity>) {
    val entities = getEntitiesToModify(clazz).entities
    entities[newEntity.id] = newEntity
  }

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    val unmodifiableEntityClass = clazz.kotlin.annotations.filterIsInstance<PEntityClass>().first().clazz
    val list = getEntitiesToModify(unmodifiableEntityClass.java).entities
    val idx = (e as PTypedEntity).id.arrayId
    val copiedData = list[idx]!!.clone()
    (copiedData.wrapAsModifiable(this) as M).change()
    list[idx] = copiedData
    modificationCount++
    return copiedData.createEntity(this) as T
  }

  private fun <T : TypedEntity> getEntities(unmodifiableEntityClass: Class<T>): EntityFamily {
    return modified[unmodifiableEntityClass] ?: entitiesByType[unmodifiableEntityClass] ?: EntityFamily.empty()
  }

  private fun <T : TypedEntity> getEntitiesToModify(unmodifiableEntityClass: Class<T>): MutableEntityFamily {
    return modified[unmodifiableEntityClass] ?: let {
      val origSet = entitiesByType[unmodifiableEntityClass]?.copyToMutable() ?: MutableEntityFamily.createEmpty()
      modified[unmodifiableEntityClass] = origSet
      origSet
    }
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val list = getEntitiesToModify(e.javaClass).entities
    val idx = (e as PTypedEntity).id.arrayId
    val copiedData = list[idx]!!.clone()
    copiedData.entitySource = newSource
    list[idx] = copiedData
    modificationCount++
    return copiedData.createEntity(this) as T
  }

  override fun removeEntity(e: TypedEntity) {
    modificationCount++
    removeEntity((e as PTypedEntity).id)
  }


  // modificationCount is not incremented
  private fun removeEntity(idx: PId) {
    val list = getEntitiesToModify(idx.clazz.java).entities
    list[idx.arrayId] = null
  }

  override fun <T : TypedEntity, SUBT : TypedEntity> extractRefs(local: KProperty1<T, Sequence<SUBT>>,
                                                                 remote: KProperty1<SUBT, T?>,
                                                                 id: PId): Sequence<SUBT> {

    // TODO: 24.03.2020 Check if already removed
    val entitiesList = getEntities(remote.javaField!!.declaringClass as Class<SUBT>)
    return getRefs(local, remote, id.arrayId, entitiesList, clonedRefs)
           ?: getRefs(local, remote, id.arrayId, entitiesList, refs)
           ?: emptySequence()
  }

  override fun <T : TypedEntity, SUBT : TypedEntity> extractBackRef(local: KProperty1<SUBT, T?>,
                                                                    remote: KProperty1<T, Sequence<SUBT>>,
                                                                    index: PId): T? {
    val entitiesList = getEntities(remote.javaField!!.declaringClass as Class<T>)
    return getBackRefs(local, remote, index.arrayId, entitiesList, clonedRefs)
           ?: getBackRefs(local, remote, index.arrayId, entitiesList, refs)
  }

  private fun copyTable(local: KProperty1<*, *>, remote: KProperty1<*, *>) {
    clonedRefs.unorderedCloneTableFrom(local, remote, refs)
  }

  fun <T : TypedEntity, SUBT : PTypedEntity> updateRef(left: KProperty1<T, Sequence<SUBT>>,
                                                       right: KProperty1<SUBT, T?>,
                                                       id: PId,
                                                       updateTo: Sequence<SUBT>) {
    copyTable(left, right)
    clonedRefs.updateRef(left, right, id.arrayId, updateTo)
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateBackRef(left: KProperty1<SUBT, T?>,
                                                            right: KProperty1<T, Sequence<SUBT>>,
                                                            id: PId,
                                                            updateTo: T?) {
    copyTable(left, right)
    if (updateTo != null) {
      clonedRefs.updateRef(left, right, id.arrayId, sequenceOf(updateTo))
    }
    else {
      clonedRefs.remove(left, right, id.arrayId)
    }
  }

  override fun <E : TypedEntity> createReference(e: E): EntityReference<E> {
    TODO("Not yet implemented")
  }

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage) {
    TODO("Not yet implemented")
  }

  sealed class EntityDataChange<T : PEntityData> {
    data class Added<T : PEntityData>(val entity: T) : EntityDataChange<T>()
    data class Removed<T : PEntityData>(val entity: T) : EntityDataChange<T>()
    data class Replaced<T : PEntityData>(val oldEntity: T, val newEntity: T) : EntityDataChange<T>()
  }

  private fun collectDataChanges(): Map<Class<*>, List<EntityDataChange<*>>> {
    // TODO: 23.03.2020 This method doesn't take an argument into account. And should it actually have an argument?
    if (isEmpty()) return emptyMap()

    val res = mutableMapOf<Class<*>, List<EntityDataChange<*>>>()
    for ((key, entitiesList) in modified) {
      val originalEntitiesList = entitiesByType[key]
      if (originalEntitiesList == null) {
        res += key to entitiesList.entities.asSequence().filterNotNull().map { EntityDataChange.Added(it) }.toList()
      }
      else {
        val localChanges = mutableListOf<EntityDataChange<*>>()
        val sizeDiff = entitiesList.entities.size - originalEntitiesList.entities.size
        val smallerSize = if (sizeDiff > 0) originalEntitiesList.entities.size else entitiesList.entities.size
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
    val newEntities = HashMap(origStorage.entitiesByType)
    newEntities.putAll(modified)
    val newRefs = origStorage.refs.joinWith(clonedRefs)
    return PEntityStorage(newEntities, newRefs)
  }

  override fun isEmpty(): Boolean = modified.isEmpty()

  override fun addDiff(diff: TypedEntityStorageDiffBuilder) {
    val changes = (diff as PEntityStorageBuilder).collectDataChanges()
    modificationCount++
    for ((entityClass, changeLog) in changes) {
      for (change in changeLog) {
        when (change) {
          is EntityDataChange.Removed<*> -> this.removeEntity(PId(change.entity.id, entityClass.kotlin as KClass<out PTypedEntity>))
          is EntityDataChange.Added<*> -> this.addEntity(change.entity, entityClass as Class<out PTypedEntity>)
          is EntityDataChange.Replaced<*> -> this.replaceEntity(change.newEntity, entityClass as Class<out PTypedEntity>)
        }
      }
    }
  }

  companion object {
    fun from(storage: PEntityStorage) = PEntityStorageBuilder(storage)
  }
}

open class EntityFamily(open val entities: List<PEntityData?>) {
  operator fun get(idx: Int) = entities[idx]

  fun copyToMutable() = MutableEntityFamily(entities.toMutableList())

  companion object {
    fun empty(): EntityFamily = Empty

    private object Empty : EntityFamily(emptyList())
  }
}

class MutableEntityFamily(override val entities: MutableList<PEntityData?>) : EntityFamily(entities) {
  companion object {
    fun createEmpty() = MutableEntityFamily(mutableListOf())
  }
}

interface PTypedEntity : TypedEntity {
  val id: PId

  fun asStr(): String = "${javaClass.simpleName}@$id"
}

interface PModifiableTypedEntity<T : PTypedEntity> : PTypedEntity, ModifiableTypedEntity<T>

data class PId(val arrayId: Int, val clazz: KClass<out PTypedEntity>) {
  init {
    if (arrayId < 0) error("")
  }
}

interface PEntityData {
  var entitySource: EntitySource
  var id: Int
  fun createEntity(snapshot: PEntityStorage): PTypedEntity
  fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<*>
  fun clone(): PEntityData
}

class PFolderEntityData : PEntityData {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage): PTypedEntity = PFolderEntity(entitySource, PId(id, PFolderEntity::class), data,
                                                                                    snapshot)

  override fun wrapAsModifiable(diff: PEntityStorageBuilder) = PFolderModifiableEntity(this, diff)

  override fun clone(): PEntityData = PFolderEntityData().also {
    it.id = this.id
    it.entitySource = this.entitySource
    it.data = this.data
  }
}

class PSubFolderEntityData : PEntityData {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage): PTypedEntity {
    return PSubFolderEntity(entitySource, PId(id, PSubFolderEntity::class), data, snapshot)
  }

  override fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<*> {
    return PSubFolderModifiableEntity(this, diff)
  }

  override fun clone(): PEntityData = PSubFolderEntityData().also {
    it.id = id
    it.entitySource = entitySource
    it.data = data
  }
}

class PFolderEntity(
  override val entitySource: EntitySource,
  override val id: PId,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity {

  val children: Sequence<PSubFolderEntity> by Refs(snapshot, PSubFolderEntity::parent)

  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override fun toString(): String = asStr()
}

class Refs<T : PTypedEntity, SUBT : PTypedEntity>(
  private val snapshot: PEntityStorage,
  private val remote: KProperty1<SUBT, T?>
) : ReadOnlyProperty<T, Sequence<SUBT>> {
  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractRefs(property as KProperty1<T, Sequence<SUBT>>, remote, thisRef.id)
  }
}

class PSubFolderEntity(
  override val entitySource: EntitySource,
  override val id: PId,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity {

  val parent: PFolderEntity? by lazy {
    snapshot.extractBackRef(PSubFolderEntity::parent, PFolderEntity::children, id)
  }

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }

  override fun toString(): String = asStr()
}

@PEntityDataClass(PFolderEntityData::class)
@PEntityClass(PFolderEntity::class)
class PFolderModifiableEntity(val original: PFolderEntityData,
                              val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PFolderEntity> {
  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override var entitySource: EntitySource = original.entitySource

  var data: String
    get() = original.data
    set(value) {
      original.data = value
    }

  var children: Sequence<PSubFolderEntity>
    get() = diff.extractRefs(PFolderEntity::children, PSubFolderEntity::parent, id)
    set(value) {
      diff.updateRef(PFolderEntity::children, PSubFolderEntity::parent, id, value)
    }

  override val id: PId = PId(original.id, PFolderEntity::class)
}

@PEntityDataClass(PSubFolderEntityData::class)
@PEntityClass(PSubFolderEntity::class)
class PSubFolderModifiableEntity(val original: PSubFolderEntityData,
                                 val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PSubFolderEntity> {
  override val id: PId = PId(original.id, PSubFolderEntity::class)

  var data: String
    get() = original.data
    set(value) {
      original.data = value
    }

  var parent: PFolderEntity?
    get() = diff.extractBackRef(PSubFolderEntity::parent, PFolderEntity::children, id)
    set(value) {
      diff.updateBackRef(PSubFolderEntity::parent, PFolderEntity::children, id, value)
    }

  override val entitySource: EntitySource = original.entitySource

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }
}

object MySource : EntitySource

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

annotation class PEntityDataClass(val clazz: KClass<out PEntityData>)
annotation class PEntityClass(val clazz: KClass<out PTypedEntity>)
