// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import gnu.trove.TIntHashSet
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

open class EntitiesStore private constructor(
  entities: Map<Class<out TypedEntity>, EntityFamily<out TypedEntity>>
) : Iterable<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> {

  constructor() : this(emptyMap())

  protected open val entitiesByType: Map<Class<out TypedEntity>, EntityFamily<out TypedEntity>> = HashMap(entities)

  @Suppress("UNCHECKED_CAST")
  open operator fun <T : TypedEntity> get(clazz: Class<T>): EntityFamily<T>? = entitiesByType[clazz] as EntityFamily<T>?

  fun all() = entitiesByType

  override fun iterator(): Iterator<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> {
    return entitiesByType.iterator()
  }

  fun copy(): EntitiesStore = EntitiesStore(this.entitiesByType)
  fun join(other: EntitiesStore): EntitiesStore = EntitiesStore(entitiesByType + other.entitiesByType)
}

class MutableEntitiesStore() : EntitiesStore() {
  override val entitiesByType: MutableMap<Class<out TypedEntity>, MutableEntityFamily<out TypedEntity>> = mutableMapOf()

  @Suppress("UNCHECKED_CAST")
  override operator fun <T : TypedEntity> get(clazz: Class<T>): MutableEntityFamily<T>? = entitiesByType[clazz] as MutableEntityFamily<T>?

  fun clear() = entitiesByType.clear()

  fun isEmpty() = entitiesByType.isEmpty()

  operator fun <T : TypedEntity> set(clazz: Class<T>, newFamily: MutableEntityFamily<T>) {
    entitiesByType[clazz] = newFamily
  }
}

open class PEntityStorage constructor(
  open val entitiesByType: EntitiesStore,
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

  protected fun <E : TypedEntity> KProperty1<E, *>.declaringClass(): Class<E> {
    return this.javaField!!.declaringClass as Class<E>
  }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractRefs(local: KProperty1<T, Sequence<SUBT>>,
                                                             remote: KProperty1<SUBT, T?>,
                                                             id: PId<T>): Sequence<SUBT> {
    val entitiesList = entitiesByType[remote.declaringClass()] ?: return emptySequence()
    return getRefs(local, remote, id, entitiesList, refs) ?: emptySequence()
  }

  protected fun <T : TypedEntity, SUBT : TypedEntity> getRefs(local: KProperty1<T, Sequence<SUBT>>,
                                                              remote: KProperty1<SUBT, T?>,
                                                              index: PId<T>,
                                                              entitiesList: EntityFamily<SUBT>,
                                                              searchedTable: RefsTable
  ): Sequence<SUBT>? = searchedTable[local, remote, index.arrayId]?.map { entitiesList[it]!!.createEntity(this) }

  protected fun <T : TypedEntity, SUBT : TypedEntity> getBackRefs(local: KProperty1<SUBT, T?>,
                                                                  remote: KProperty1<T, Sequence<SUBT>>,
                                                                  index: Int,
                                                                  entitiesList: EntityFamily<T>,
                                                                  searchedTable: RefsTable
  ): T? = searchedTable[local, remote, index]?.first { entitiesList[it]!!.createEntity(this) }

  open fun <T : TypedEntity, SUBT : TypedEntity> extractBackRef(local: KProperty1<SUBT, T?>,
                                                                remote: KProperty1<T, Sequence<SUBT>>,
                                                                index: PId<SUBT>): T? {
    val entitiesList = entitiesByType[remote.declaringClass()] ?: return null
    return getBackRefs(local, remote, index.arrayId, entitiesList, refs)
  }

  override fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    return entitiesByType.all().asSequence().map { it.value.all() }.flatten().filterNotNull()
      .map { it.createEntity(this) }.filterIsInstance<TypedEntityWithPersistentId>().find { it.persistentId() == id } as E
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out TypedEntity>, List<TypedEntity>>> {
    val res = mutableMapOf<EntitySource, MutableMap<Class<out TypedEntity>, MutableList<TypedEntity>>>()
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

  constructor() : this(PEntityStorage(EntitiesStore(), RefsTable()))

  private val modified: MutableEntitiesStore = MutableEntitiesStore()
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

    val pEntityData = entityDataClass.primaryConstructor!!.call() as PEntityData<T>

    pEntityData.entitySource = source

    val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
    modifiableEntity.initializer()
    modificationCount++

    val unmodifiableEntityClass = (modifiableEntity as PModifiableTypedEntity<*>).id.clazz.java as Class<T>
    val entities = getEntitiesToModify(unmodifiableEntityClass)
    entities.add(pEntityData)

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

  override fun <T : TypedEntity, SUBT : TypedEntity> extractRefs(local: KProperty1<T, Sequence<SUBT>>,
                                                                 remote: KProperty1<SUBT, T?>,
                                                                 id: PId<T>): Sequence<SUBT> {

    // TODO: 24.03.2020 Check if already removed
    val entitiesList = getEntities(remote.declaringClass())
    return getRefs(local, remote, id, entitiesList, clonedRefs)
           ?: getRefs(local, remote, id, entitiesList, refs)
           ?: emptySequence()
  }

  override fun <T : TypedEntity, SUBT : TypedEntity> extractBackRef(local: KProperty1<SUBT, T?>,
                                                                    remote: KProperty1<T, Sequence<SUBT>>,
                                                                    index: PId<SUBT>): T? {
    val entitiesList = getEntities(remote.declaringClass())
    return getBackRefs(local, remote, index.arrayId, entitiesList, clonedRefs)
           ?: getBackRefs(local, remote, index.arrayId, entitiesList, refs)
  }

  private fun copyTable(local: KProperty1<*, *>, remote: KProperty1<*, *>) {
    clonedRefs.unorderedCloneTableFrom(local, remote, refs)
  }

  fun <T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> updateRef(left: KProperty1<T, Sequence<SUBT>>,
                                                                 right: KProperty1<SUBT, T?>,
                                                                 id: PId<T>,
                                                                 updateTo: Sequence<SUBT>) {
    copyTable(left, right)
    clonedRefs.updateRef(left, right, id.arrayId, updateTo)
  }

  fun <T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> updateBackRef(left: KProperty1<SUBT, T?>,
                                                                     right: KProperty1<T, Sequence<SUBT>>,
                                                                     id: PId<SUBT>,
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

open class EntityFamily<E : TypedEntity> protected constructor(
  protected open val entities: List<PEntityData<E>?>,
  protected val emptySlots: TIntHashSet
) {

  operator fun get(idx: Int) = entities[idx]

  fun copyToMutable() = MutableEntityFamily(entities.toMutableList())

  fun all() = entities.asSequence().filterNotNull()

  fun exists(id: Int) = entities[id] != null

  val size: Int
    get() = entities.size - emptySlots.size()

  companion object {
    fun <E : TypedEntity> empty(): EntityFamily<E> = Empty as EntityFamily<E>

    private object Empty : EntityFamily<PTypedEntity<*>>(emptyList(), TIntHashSet())
  }
}

class MutableEntityFamily<E : TypedEntity>(
  override val entities: MutableList<PEntityData<E>?>
) : EntityFamily<E>(
  entities,
  TIntHashSet().also { entities.mapIndexed { index, pEntityData -> if (pEntityData == null) it.add(index) } }
) {

  private val copiedToModify: TIntHashSet = TIntHashSet()

  fun remove(id: Int) {
    if (id in emptySlots) return

    emptySlots.add(id)
    copiedToModify.remove(id)
    entities[id] = null
  }

  fun add(other: PEntityData<E>) {
    if (emptySlots.isEmpty) {
      other.id = entities.size
      entities += other
    }
    else {
      val emptySlot = emptySlots.pop()
      other.id = emptySlot
      entities[emptySlot] = other
    }
    copiedToModify.add(other.id)
  }

  fun replaceById(entity: PEntityData<E>) {
    val id = entity.id
    emptySlots.remove(id)
    entities[id] = entity
    copiedToModify.add(id)
  }

  fun getEntityDataForModification(id: PId<E>): PEntityData<E> {
    val entity = entities[id.arrayId] ?: error("Nothing to modify")
    if (id.arrayId in copiedToModify) return entity

    val clonedEntity = entity.clone()
    entities[id.arrayId] = clonedEntity
    copiedToModify.add(id.arrayId)
    return clonedEntity
  }

  private fun TIntHashSet.pop(): Int {
    val iterator = this.iterator()
    if (!iterator.hasNext()) error("Set is empty")
    val res = iterator.next()
    iterator.remove()
    return res
  }

  companion object {
    fun <E : TypedEntity> createEmpty() = MutableEntityFamily<E>(mutableListOf())
  }
}

interface PTypedEntity<E : TypedEntity> : TypedEntity {
  val id: PId<E>

  fun asStr(): String = "${javaClass.simpleName}@$id"
}

interface PModifiableTypedEntity<T : PTypedEntity<T>> : PTypedEntity<T>, ModifiableTypedEntity<T>

data class PId<E : TypedEntity>(val arrayId: Int, val clazz: KClass<E>) {
  init {
    if (arrayId < 0) error("")
  }
}

interface PEntityData<E : TypedEntity> {
  var entitySource: EntitySource
  var id: Int
  fun createEntity(snapshot: PEntityStorage): E
  fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<E>
  fun clone(): PEntityData<E>
}

class PFolderEntityData : PEntityData<PFolderEntity> {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage) = PFolderEntity(entitySource, PId(id, PFolderEntity::class), data,
                                                                      snapshot)

  override fun wrapAsModifiable(diff: PEntityStorageBuilder) = PFolderModifiableEntity(this, diff)

  override fun clone() = PFolderEntityData().also {
    it.id = this.id
    it.entitySource = this.entitySource
    it.data = this.data
  }
}

class PSubFolderEntityData : PEntityData<PSubFolderEntity> {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage): PSubFolderEntity {
    return PSubFolderEntity(entitySource, PId(id, PSubFolderEntity::class), data, snapshot)
  }

  override fun wrapAsModifiable(diff: PEntityStorageBuilder): PSubFolderModifiableEntity {
    return PSubFolderModifiableEntity(this, diff)
  }

  override fun clone() = PSubFolderEntityData().also {
    it.id = id
    it.entitySource = entitySource
    it.data = data
  }
}

class PFolderEntity(
  override val entitySource: EntitySource,
  override val id: PId<PFolderEntity>,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity<PFolderEntity> {

  val children: Sequence<PSubFolderEntity> by Refs(snapshot, PSubFolderEntity::parent)

  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override fun toString(): String = asStr()
}

class Refs<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  private val snapshot: PEntityStorage,
  private val remote: KProperty1<SUBT, T?>
) : ReadOnlyProperty<T, Sequence<SUBT>> {
  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractRefs(property as KProperty1<T, Sequence<SUBT>>, remote, thisRef.id)
  }
}

class PSubFolderEntity(
  override val entitySource: EntitySource,
  override val id: PId<PSubFolderEntity>,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity<PSubFolderEntity> {

  val parent: PFolderEntity? by BackRefs(snapshot, PFolderEntity::children)

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }

  override fun toString(): String = asStr()
}

class BackRefs<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  private val snapshot: PEntityStorage,
  private val remote: KProperty1<T, Sequence<SUBT>>
) : ReadOnlyProperty<SUBT, T?> {
  override fun getValue(thisRef: SUBT, property: KProperty<*>): T? {
    return snapshot.extractBackRef(property as KProperty1<SUBT, T?>, remote, thisRef.id)
  }
}

@PEntityDataClass(PFolderEntityData::class)
class PFolderModifiableEntity(val original: PFolderEntityData,
                              val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PFolderEntity> {
  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override var entitySource: EntitySource = original.entitySource

  var data: String
    get() = original.data
    set(value) {
      original.data = value
    }

  var children: Sequence<PSubFolderEntity> by RwRefs(diff, PFolderEntity::children, PSubFolderEntity::parent)

  override val id: PId<PFolderEntity> = PId(original.id, PFolderEntity::class)
}

class RwRefs<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
  private val snapshot: PEntityStorageBuilder,
  private val local: KProperty1<T, Sequence<SUBT>>,
  private val remote: KProperty1<SUBT, T?>
) : ReadWriteProperty<MODT, Sequence<SUBT>> {
  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractRefs(local, remote, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    snapshot.updateRef(local, remote, thisRef.id, value)
  }
}

@PEntityDataClass(PSubFolderEntityData::class)
class PSubFolderModifiableEntity(val original: PSubFolderEntityData,
                                 val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PSubFolderEntity> {
  override val id: PId<PSubFolderEntity> = PId(original.id, PSubFolderEntity::class)

  var data: String
    get() = original.data
    set(value) {
      original.data = value
    }

  var parent: PFolderEntity? by RwBackRefs(diff, PSubFolderEntity::parent, PFolderEntity::children)

  override val entitySource: EntitySource = original.entitySource

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }
}

class RwBackRefs<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
  private val snapshot: PEntityStorageBuilder,
  private val local: KProperty1<SUBT, T?>,
  private val remote: KProperty1<T, Sequence<SUBT>>
) : ReadWriteProperty<MODSUBT, T?> {
  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
    return snapshot.extractBackRef(local, remote, thisRef.id)
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
    return snapshot.updateBackRef(local, remote, thisRef.id, value)
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

annotation class PEntityDataClass(val clazz: KClass<out PEntityData<*>>)
