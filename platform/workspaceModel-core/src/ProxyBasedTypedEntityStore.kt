@file:Suppress("UNCHECKED_CAST")

package com.intellij.workspace.api

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Queues
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.MultiMap
import com.intellij.util.lang.JavaVersion
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

inline fun <reified M : ModifiableTypedEntity<T>, T : TypedEntity> TypedEntityStorageBuilder.addEntity(source: EntitySource, noinline initializer: M.() -> Unit): T
  = addEntity(M::class.java, source, initializer)

internal interface ProxyBasedEntity : TypedEntity {
  val storage: ProxyBasedEntityStorage
  val id: Long
  val data: EntityData
}

internal open class ProxyBasedEntityStorage(internal open val entitiesByType: Map<Class<out TypedEntity>, Set<EntityData>>,
                                            internal open val entitiesBySource: Map<EntitySource, Set<EntityData>>,
                                            internal open val entitiesByPersistentIdHash: Map<Int, Set<EntityData>>,
                                            internal open val entityById: Map<Long, EntityData>,
                                            internal open val referrers: Map<Long, List<Long>>,
                                            internal val metaDataRegistry: EntityMetaDataRegistry) : TypedEntityStorage {
  companion object {
    private val proxyClassConstructors = ConcurrentFactoryMap.createMap<Class<out TypedEntity>, Constructor<out ProxyBasedEntity>> {
      //todo replace Proxy class by a generated class which stores properties in separate fields to improve performance and memory usage
      val constructor = Proxy.getProxyClass(it.classLoader, it, ProxyBasedEntity::class.java).getConstructor(InvocationHandler::class.java)
      constructor.isAccessible = true
      constructor as Constructor<out ProxyBasedEntity>
    }

    @JvmStatic
    internal fun <E : TypedEntity> createProxy(clazz: Class<out E>, impl: EntityImpl) =
      proxyClassConstructors[clazz]!!.newInstance(impl) as E

    internal fun <T1, T2> classifyByEquals(c1: Iterable<T1>, c2: Iterable<T2>, hashFunc1: (T1) -> Int, hashFunc2: (T2) -> Int, equalsFunc: (T1, T2) -> Boolean): TypedEntityStorageBuilderImpl.EqualityResult<T1, T2> {
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
        } else {
          val ml1 = l1.toMutableList()
          val ml2 = l2.toMutableList()

          for (itemFrom1 in ml1) {
            val index2 = ml2.indexOfFirst { equalsFunc(itemFrom1, it) }
            if (index2 < 0) {
              onlyIn1.add(itemFrom1)
            } else {
              val itemFrom2 = ml2.removeAt(index2)
              equal.add(itemFrom1 to itemFrom2)
            }
          }

          for (itemFrom2 in ml2) {
            onlyIn2.add(itemFrom2)
          }
        }
      }

      return TypedEntityStorageBuilderImpl.EqualityResult(onlyIn1 = onlyIn1, onlyIn2 = onlyIn2, equal = equal)
    }
  }

  override fun <E : TypedEntity> entities(entityClass: KClass<E>): Sequence<E> {
    return entitiesByType[entityClass.java]?.asSequence()?.map { createEntityInstance(it) as E } ?: emptySequence()
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out TypedEntity>, List<TypedEntity>>> {
    return entitiesBySource.asSequence().filter { sourceFilter(it.key) && it.value.isNotEmpty() }
      .associateBy({ it.key }, { (_, listOfData) ->
        listOfData.groupBy({ it.unmodifiableEntityType }, { createEntityInstance(it) })
      })
  }

  internal fun createEntityInstance(it: EntityData) =
    createProxy(it.unmodifiableEntityType, EntityImpl(it, this))

  override fun <E : TypedEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    return entitiesByPersistentIdHash[id.hashCode()]?.asSequence()
      ?.map { (createEntityInstance(it) as E) }
      ?.find {it.persistentId() == id }
  }

  override fun <E : TypedEntity, R : TypedEntity> referrers(e: E,
                                                            entityClass: KClass<R>,
                                                            property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    //todo: do we need this?
    return entities(entityClass).filter { property.get(it).resolve(this) == e }
  }

  internal fun <R : TypedEntity> referrers(id: Long, entityClass: Class<R>, propertyName: String): Sequence<R> {
    val referrers = referrers[id] ?: return emptySequence()
    return referrers.asSequence().mapNotNull {
      val referrer = entityById.getValue(it)
      if (referrer.unmodifiableEntityType == entityClass && referrer.properties[propertyName] == id) {
        createEntityInstance(referrer) as R
      }
      else {
        null
      }
    }
  }

  fun applyDiff(diffBuilder: TypedEntityStorageDiffBuilder): TypedEntityStorage {
    val builder = TypedEntityStorageBuilder.from(this) as TypedEntityStorageBuilderImpl
    builder.addDiff(diffBuilder)
    return builder.toStorage()
  }
}

internal class TypedEntityStorageBuilderImpl(override val entitiesByType: MutableMap<Class<out TypedEntity>, MutableSet<EntityData>>,
                                             override val entitiesBySource: MutableMap<EntitySource, MutableSet<EntityData>>,
                                             override val entitiesByPersistentIdHash: MutableMap<Int, MutableSet<EntityData>>,
                                             override val entityById: MutableMap<Long, EntityData>,
                                             override val referrers: MutableMap<Long, MutableList<Long>>,
                                             metaDataRegistry: EntityMetaDataRegistry)
  : ProxyBasedEntityStorage(entitiesByType, entitiesBySource, entitiesByPersistentIdHash, entityById, referrers, metaDataRegistry), TypedEntityStorageBuilder, TypedEntityStorageDiffBuilder {

  constructor(storage: ProxyBasedEntityStorage)
    : this(storage.entitiesByType.mapValuesTo(HashMap()) { it.value.toMutableSet() },
           storage.entitiesBySource.mapValuesTo(HashMap()) { it.value.toMutableSet() },
           storage.entitiesByPersistentIdHash.mapValuesTo(HashMap()) { it.value.toMutableSet() },
           storage.entityById.toMutableMap(),
           storage.referrers.mapValuesTo(HashMap()) { it.value.toMutableList() },
           storage.metaDataRegistry)

  private val changeLogImpl: MutableList<ChangeEntry> = mutableListOf()
  private var modificationCount: Long = 0

  private val changeLog: List<ChangeEntry>
    get() = changeLogImpl

  private inline fun updateChangeLog(updater: (MutableList<ChangeEntry>) -> Unit) {
    updater(changeLogImpl)
    modificationCount++
  }

  override fun isEmpty(): Boolean = changeLog.isEmpty()
  override fun getModificationCount(): Long = modificationCount

  override fun <E : TypedEntity> createReference(e: E): EntityReference<E> = ProxyBasedEntityReferenceImpl((e as ProxyBasedEntity).id)

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> addEntity(clazz: Class<M>, source: EntitySource,
                                                                         initializer: M.() -> Unit): T {
    val data = createEntityData(source, clazz)
    val entity = initializeEntityInstance(data, clazz, initializer)
    // Referrers are handled by proxy method invocations from initializer
    addEntity(data, entity, handleReferrers = false)
    updateChangeLog { it.add(ChangeEntry.AddEntity(data)) }
    return entity as T
  }

  private fun <M : ModifiableTypedEntity<T>, T : TypedEntity> createEntityData(source: EntitySource, clazz: Class<M>) =
    createEntityDataByUnmodifiableEntityClass(source, getUnmodifiableEntityClass(clazz))

  private fun <T : TypedEntity> createEntityDataByUnmodifiableEntityClass(source: EntitySource, unmodifiableEntityType: Class<out T>) =
    EntityData(source, NEXT_ID.getAndIncrement(), metaDataRegistry.getEntityMetaData(unmodifiableEntityType))

  private fun <M : ModifiableTypedEntity<T>, T : TypedEntity> initializeEntityInstance(data: EntityData, clazz: Class<M>,
                                                                                       initializer: M.() -> Unit): M {
    val impl = EntityImpl(data, this)
    val entity = createProxy(clazz, impl)
    impl.allowModifications {
      entity.initializer()
    }
    return entity
  }

  internal fun addEntity(entityData: EntityData, entityInstance: TypedEntity?, handleReferrers: Boolean) {
    entitiesByType.putValue(entityData.unmodifiableEntityType, entityData)
    entitiesBySource.putValue(entityData.entitySource, entityData)
    entityById[entityData.id] = entityData
    if (TypedEntityWithPersistentId::class.java.isAssignableFrom(entityData.unmodifiableEntityType)) {
      val persistentId = ((entityInstance ?: createEntityInstance(entityData)) as TypedEntityWithPersistentId).persistentId()
      entitiesByPersistentIdHash.putValue(persistentId.hashCode(), entityData)
    }
    if (handleReferrers) {
      addReferences(entityData)
    }
  }

  override fun <M : ModifiableTypedEntity<T>, T : TypedEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    val oldIdHash = (e as? TypedEntityWithPersistentId)?.persistentId()?.hashCode()
    val oldData = (e as ProxyBasedEntity).data
    val newData = oldData.createModifiableCopy()
    val newImpl = EntityImpl(newData, this)
    val newInstance = createProxy(clazz, newImpl)
    newImpl.allowModifications {
      newInstance.change()
    }
    // Referrers are updated in proxy method invocation
    replaceEntity(e.id, newData, newInstance, oldIdHash, handleReferrers = false)
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(e.id, newData)) }
    return newInstance as T
  }

  override fun <T : TypedEntity> changeSource(e: T, newSource: EntitySource): T {
    val oldData = (e as ProxyBasedEntity).data
    val newData = EntityData(newSource, oldData.id, oldData.metaData, oldData.properties)
    replaceEntity(e.id, newData, null, null, false)
    updateChangeLog { it.add(ChangeEntry.ReplaceEntity(e.id, newData)) }
    return createEntityInstance(newData) as T
  }

  private fun replaceEntity(id: Long, newData: EntityData, newInstance: TypedEntity?, oldIdHash: Int?, handleReferrers: Boolean) {
    if (id != newData.id) {
      error("new and old IDs must be equal. Trying to replace entity #$id with #${newData.id}")
    }

    val oldData = entityById[id] ?: error("Unknown id $id")
    if (oldData.entitySource == newData.entitySource) {
      val bySource = entitiesBySource[oldData.entitySource]!!
      bySource.removeIf { it.id == id }
      bySource.add(newData)
    }
    else {
      entitiesBySource.removeValue(oldData.entitySource, oldData)
      entitiesBySource.putValue(newData.entitySource, newData)
    }
    val byType = entitiesByType[oldData.unmodifiableEntityType]!!
    byType.removeIf { it.id == id }
    byType.add(newData)

    entityById[id] = newData
    if (TypedEntityWithPersistentId::class.java.isAssignableFrom(oldData.unmodifiableEntityType)) {
      entitiesByPersistentIdHash.removeValue(oldIdHash ?: (createEntityInstance(oldData) as TypedEntityWithPersistentId).persistentId().hashCode(), oldData)
      val persistentId = ((newInstance ?: createEntityInstance(newData)) as TypedEntityWithPersistentId).persistentId()
      entitiesByPersistentIdHash.putValue(persistentId.hashCode(), newData)
    }
    if (handleReferrers) {
      removeReferences(oldData)
      addReferences(newData)
    }
  }

  private fun removeReferences(data: EntityData) {
    data.collectReferences { referencesId ->
      val refs = referrers[referencesId] ?: error("Unable to find reference target id $referencesId")
      if (!refs.remove(data.id)) {
        error("Id ${data.id} was not in references with target id $referencesId")
      }

      if (refs.isEmpty()) referrers.remove(referencesId)
    }
  }

  private fun addReferences(data: EntityData) {
    data.collectReferences { referencesId ->
      val refs = referrers.getOrPut(referencesId) { mutableListOf() }

      // TODO Slow check
      if (refs.contains(data.id)) error("Id ${data.id} was already in references with target id $referencesId")

      refs.add(data.id)
    }
  }

  override fun removeEntity(e: TypedEntity) {
    val id = (e as ProxyBasedEntity).id
    updateChangeLog { it.add(ChangeEntry.RemoveEntity(id)) }
    removeEntity(id)
  }

  private fun removeEntity(id: Long) {
    val data = entityById.remove(id) ?: error("Unknown id $id")
    entitiesByType[data.unmodifiableEntityType]?.removeIf { it.id == id }
    entitiesBySource[data.entitySource]?.removeIf { it.id == id }
    if (TypedEntityWithPersistentId::class.java.isAssignableFrom(data.unmodifiableEntityType)) {
      //todo store hash in EntityData instead?
      val persistentId = (createEntityInstance(data) as TypedEntityWithPersistentId).persistentId()
      entitiesByPersistentIdHash.removeValue(persistentId.hashCode(), data)
    }
    removeReferences(data)

    val toRemove = referrers[id]
    while (true) {
      val idToRemove = toRemove?.firstOrNull() ?: break
      removeEntity(idToRemove)
    }

    if (referrers.containsKey(id)) {
      error("Referrers still have reference target id $id even after entity removal")
    }
  }

  override fun addDiff(diff: TypedEntityStorageDiffBuilder) {
    val diffLog = (diff as TypedEntityStorageBuilderImpl).changeLog
    updateChangeLog { it.addAll(diffLog) }
    for (change in diffLog) {
      when (change) {
        is ChangeEntry.AddEntity -> addEntity(change.entityData, null, handleReferrers = true)
        is ChangeEntry.RemoveEntity -> {
          if (change.id in entityById) {
            removeEntity(change.id)
          }
        }
        is ChangeEntry.ReplaceEntity -> {
          if (change.id in entityById) {
            replaceEntity(change.id, change.newData, null, null, handleReferrers = true)
          }
        }
      }
    }
  }

  private fun EntityData.persistentId() =
    (createEntityInstance(this) as TypedEntityWithPersistentId).persistentId()

  private fun calcBackReferrers(referrers: Map<Long, List<Long>>): MultiMap<Long, Long> {
    val map = MultiMap.create<Long, Long>()

    for ((id, children) in referrers) {
      for (child in children) {
        map.putValue(child, id)
      }
    }

    return map
  }

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: TypedEntityStorage) {
    replaceWith as ProxyBasedEntityStorage

    val replaceWithBackReferrers = calcBackReferrers(replaceWith.referrers)
    val backReferrers = calcBackReferrers(referrers)

    val replaceMap = HashBiMap.create<Long, Long>()
    val emptyBiMap = HashBiMap.create<Long, Long>()
    val entitiesToRemove = mutableSetOf<Long>()
    val entitiesToAdd = mutableSetOf<Long>()

    // TODO Cache sourceFilter result by entitySource to call it only once per entitySource

    // TODO The following code assumes eligible persistent id entities to be without parents
    // TODO  add some support for entities with parents. Like when they're all deep equal

    for ((idHash, oldEntities) in entitiesByPersistentIdHash.toList()) {
      val newEntities = replaceWith.entitiesByPersistentIdHash[idHash]?.toMutableList() ?: mutableListOf()
      for (oldData in oldEntities.toList()) {
        if (!sourceFilter(oldData.entitySource)) continue

        // Currently persistent id entities must not have any parents
        if (backReferrers.containsKey(oldData.id)) continue

        val persistentId = oldData.persistentId()

        val newData = newEntities.firstOrNull { it.persistentId() == persistentId && sourceFilter(it.entitySource) }
        if (newData != null) {
          replaceMap[oldData.id] = newData.id

          if (!shallowEquals(oldData, newData, emptyBiMap)) {
            val replaceWithData = EntityData(
              entitySource = oldData.entitySource,
              metaData = oldData.metaData,
              id = oldData.id,
              properties = newData.properties
            )

            replaceEntity(oldData.id, replaceWithData, newInstance = null, oldIdHash = null, handleReferrers = true)
            updateChangeLog { it.add(ChangeEntry.ReplaceEntity(oldData.id, replaceWithData)) }
          }

          newEntities.remove(newData)
        } else {
          // Remove right here?
          // TODO Don't forget to check sourceFilter
          entitiesToRemove.add(oldData.id)
        }
      }
    }

    for ((idHash, newEntities) in replaceWith.entitiesByPersistentIdHash.toList()) {
      val oldEntities = entitiesByPersistentIdHash[idHash] ?: mutableSetOf()
      for (newData in newEntities) {
        if (!sourceFilter(newData.entitySource)) continue
        // Currently persistent id entities must not have any parents
        if (replaceWithBackReferrers.containsKey(newData.id)) continue

        val persistentId = newData.persistentId()

        val oldData = oldEntities.firstOrNull { it.persistentId() == persistentId && sourceFilter(it.entitySource) }
        if (oldData == null) {
          // Add sub-graph right here?
          // TODO Don't forget to check sourceFilter
          entitiesToAdd.add(newData.id)
        }
      }
    }

    // TODO References to cold entities (not having persistent id as a root)
    // TODO Ref to cold entities from persistent id
    // TODO test entity refs couple of persistent ids with a different path length to each
    // TODO Test cold entities (not related to persistent ids)
    // TODO Compare cold entities by hash, probably pre-calculate this hash

    // assumes start nodes have no parents
    val queue: Queue<Pair<Long, Long>> = Queues.newArrayDeque(replaceMap.toList())
    while (queue.isNotEmpty()) {
      val (oldId, newId) = queue.remove()

      // new nodes - children

      // TODO hash
      val newChildren = replaceWith.referrers[newId]
                          ?.filter { !entitiesToAdd.contains(it) && !replaceMap.containsKey(it) }
                          ?.map { replaceWith.entityById.getValue(it) }
                        ?: emptyList()
      val oldChildren = referrers[oldId]
                          ?.filter { !entitiesToRemove.contains(it) && !replaceMap.containsValue(it) }
                          ?.map { entityById.getValue(it) }
                        ?: emptyList()

      val eq = classifyByEquals(
        c1 = oldChildren,
        c2 = newChildren,
        hashFunc1 = this::shallowHashCode, hashFunc2 = this::shallowHashCode,
        equalsFunc = { v1, v2 -> shallowEquals(v1, v2, replaceMap) })

      for ((oldChildData, newChildData) in eq.equal) {
        if (entitiesToAdd.contains(newChildData.id)) error("id=${newChildData.id} already exists in entriesToAdd")
        if (entitiesToRemove.contains(oldChildData.id)) error("id=${oldChildData.id} already exists in entitiesToRemove")

        queue.add(oldChildData.id to newChildData.id)
        replaceMap[oldChildData.id] = newChildData.id
      }

      // TODO Check we won't get any persistent id nodes?
      for (data in eq.onlyIn1) {
        traverseNodes(this, data.id) { id ->
          if (replaceMap.containsKey(id)) {
            error("Trying to remove node with id=$id: it's already marked for replacement")
          }

          entitiesToRemove.add(id)
        }
      }

      // TODO Check we won't get any persistent id nodes?
      for (data in eq.onlyIn2) {
        traverseNodes(replaceWith, data.id) { id ->
          if (replaceMap.containsValue(id)) {
            error("Trying to add node with id=$id: it's already marked for replacement")
          }

          entitiesToAdd.add(id)
        }
      }
    }

    // Process all non-persistent-id related nodes
    // TODO Check for external links, sourceFilter must filter out a connected component

    val destEntitiesToCompare = mutableSetOf<EntityData>()
    foreachNotProcessedEntity(this, sourceFilter, replaceMap, entitiesToRemove) { data ->
      destEntitiesToCompare.add(data)
    }

    val sourceEntitiesToCompare = mutableSetOf<EntityData>()
    foreachNotProcessedEntity(replaceWith, sourceFilter, replaceMap.inverse(), entitiesToAdd) { data ->
      sourceEntitiesToCompare.add(data)
    }

    val equalsCache = mutableMapOf<Pair<Long, Long>, Boolean>()
    val eq = classifyByEquals(
      destEntitiesToCompare, sourceEntitiesToCompare,
      this::shallowHashCode, this::shallowHashCode
    ) { e1, e2 ->
      deepEquals(
        data1 = e1,
        data2 = e2,
        replaceMap = replaceMap,
        storage1 = this,
        storage2 = replaceWith,
        backReferrers1 = backReferrers,
        backReferrers2 = replaceWithBackReferrers,
        equalsCache = equalsCache)
    }

    for (data in eq.onlyIn1) {
      entitiesToRemove.add(data.id)
    }

    for (data in eq.onlyIn2) {
      entitiesToAdd.add(data.id)
    }

    for ((oldChildData, newChildData) in eq.equal) {
      replaceMap[oldChildData.id] = newChildData.id
    }

    for (idToRemove in entitiesToRemove) {
      if (entityById.containsKey(idToRemove)) {
        removeEntity(idToRemove)
        updateChangeLog { it.add(ChangeEntry.RemoveEntity(idToRemove)) }
      }
    }

    for (idToAdd in entitiesToAdd) {
      if (!replaceMap.containsValue(idToAdd)) {
        recursiveAddEntity(idToAdd, replaceWithBackReferrers, replaceWith, replaceMap)
      }
    }
  }

  private fun deepEquals(data1: EntityData,
                         data2: EntityData,
                         replaceMap: Map<Long, Long>,
                         storage1: ProxyBasedEntityStorage,
                         storage2: ProxyBasedEntityStorage,
                         backReferrers1: MultiMap<Long, Long>,
                         backReferrers2: MultiMap<Long, Long>,
                         equalsCache: MutableMap<Pair<Long, Long>, Boolean>): Boolean {

    val cachedResult = equalsCache[data1.id to data2.id]
    if (cachedResult != null) return cachedResult

    if (replaceMap[data1.id] == data2.id) return true

    val data1parents = backReferrers1[data1.id].map { storage1.entityById.getValue(it) }
    val data2parents = backReferrers2[data2.id].map { storage2.entityById.getValue(it) }

    val eq = classifyByEquals(data1parents, data2parents, this::shallowHashCode, this::shallowHashCode) { e1, e2 ->
      deepEquals(e1, e2, replaceMap, storage1, storage2, backReferrers1, backReferrers2, equalsCache)
    }

    val result = eq.onlyIn1.isEmpty() && eq.onlyIn2.isEmpty()
    equalsCache[data1.id to data2.id] = result
    return result
  }

  private fun foreachNotProcessedEntity(storage: ProxyBasedEntityStorage,
                                        sourceFilter: (EntitySource) -> Boolean,
                                        replaceMap: Map<Long, Long>,
                                        otherProcessedSet: Set<Long>,
                                        block: (EntityData) -> Unit) {
    for ((oldSource, oldEntities) in storage.entitiesBySource) {
      if (sourceFilter(oldSource)) {
        oldEntities.toList().forEach { data ->
          if (!replaceMap.containsKey(data.id) && !otherProcessedSet.contains(data.id)) {
            block(data)
          }
        }
      }
    }
  }

  private fun shallowHashCode(data: EntityData): Int = data.properties.entries.sortedBy { it.key }.fold(data.unmodifiableEntityType.hashCode()) { acc: Int, (key: String, value: Any?) ->
      when (val kind = data.metaData.properties.getValue(key)) {
        is EntityPropertyKind.EntityReference -> kind.clazz.hashCode()
        is EntityPropertyKind.List -> when (val itemKind = kind.itemKind) {
          is EntityPropertyKind.EntityReference -> itemKind.clazz.hashCode()
          is EntityPropertyKind.List -> error("List of lists are not supported")
          // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> value.hashCode()
          is EntityPropertyKind.DataClass -> {
            assertDataClassIsWithoutReferences(itemKind)
            value.hashCode()
          }
          is EntityPropertyKind.EntityValue -> itemKind.clazz.hashCode()
          EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> value.hashCode()
        }
        // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> value.hashCode()
        is EntityPropertyKind.DataClass -> {
          assertDataClassIsWithoutReferences(kind)
          value.hashCode()
        }
        is EntityPropertyKind.EntityValue -> kind.clazz.hashCode()
        EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> value.hashCode()
      } * 7 + acc
    }

  private fun shallowEquals(data1: EntityData, data2: EntityData, replaceMap: Map<Long, Long>): Boolean {
    if (data1.unmodifiableEntityType != data2.unmodifiableEntityType) return false
    if (data1.properties.keys != data2.properties.keys) return false
    if (data1.entitySource != data2.entitySource) return false

    for (key in data1.properties.keys) {
      val v1 = data1.properties.getValue(key)
      val v2 = data2.properties.getValue(key)

      val rc = when (val kind = data1.metaData.properties.getValue(key)) {
        is EntityPropertyKind.EntityReference -> {
          replaceMap[(v1 as ProxyBasedEntityReferenceImpl<*>).id] == (v2 as ProxyBasedEntityReferenceImpl<*>).id
        }

        is EntityPropertyKind.List -> when (val itemKind = kind.itemKind) {
          is EntityPropertyKind.EntityReference -> {
            val list1 = (v1 as List<ProxyBasedEntityReferenceImpl<*>>).map { replaceMap[it.id] }
            val list2 = (v2 as List<ProxyBasedEntityReferenceImpl<*>>).map { it.id }

            list1 == list2
          }
          is EntityPropertyKind.List -> error("List of lists are not supported")
          // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> (v1 as List<Any?>) == (v2 as List<Any?>)
          is EntityPropertyKind.DataClass -> {
            assertDataClassIsWithoutReferences(itemKind)
            (v1 as List<Any?>) == (v2 as List<Any?>)
          }
          is EntityPropertyKind.EntityValue -> {
            val list1 = (v1 as List<Long>).map { replaceMap[it] }
            val list2 = v2 as List<Long>

            list1 == list2
          }
          EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> (v1 as List<Any?>) == (v2 as List<Any?>)
        }

        // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> v1 == v2
        is EntityPropertyKind.DataClass -> {
          assertDataClassIsWithoutReferences(kind)
          v1 == v2
        }
        is EntityPropertyKind.EntityValue -> replaceMap[v1 as Long] == v2
        EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> v1 == v2
      }

      if (!rc) return false
    }

    return true
  }

  private fun assertDataClassIsWithoutReferences(dataClassKind: EntityPropertyKind.DataClass) {
    if (dataClassKind.hasReferences) {
      TODO("DataClasses with references are not supported here yet: ${dataClassKind.dataClass}")
    }
  }

  private fun recursiveAddEntity(id: Long, backReferrers: MultiMap<Long, Long>, storage: ProxyBasedEntityStorage, replaceMap: BiMap<Long, Long>) {
    backReferrers[id].forEach { parentId ->
      if (!replaceMap.containsValue(parentId)) {
        recursiveAddEntity(parentId, backReferrers, storage, replaceMap)
      }
    }

    val data = storage.entityById.getValue(id)
    val newData = createEntityDataByUnmodifiableEntityClass(data.entitySource, data.unmodifiableEntityType)
    replaceMap[newData.id] = id
    copyEntityProperties(data, newData, replaceMap.inverse())
    addEntity(newData, null, handleReferrers = true)
    updateChangeLog { it.add(ChangeEntry.AddEntity(data)) }
  }

  private fun copyEntityProperties(source: EntityData, dest: EntityData, replaceMap: Map<Long, Long>) {
    dest.properties.clear()
    source.properties.mapValuesTo(dest.properties) { (key, value) ->
      when (val kind = source.metaData.properties.getValue(key)) {
        is EntityPropertyKind.EntityReference -> replaceMap.getValue((value as ProxyBasedEntityReferenceImpl<*>).id)
        is EntityPropertyKind.List -> when (val itemKind = kind.itemKind) {
          is EntityPropertyKind.EntityReference -> (value as List<ProxyBasedEntityReferenceImpl<*>>).map { replaceMap.getValue(it.id) }
          is EntityPropertyKind.List -> error("List of lists are not supported")
          // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> value
          is EntityPropertyKind.DataClass -> {
            assertDataClassIsWithoutReferences(itemKind)
            value
          }
          is EntityPropertyKind.EntityValue -> (value as List<Long>).map { replaceMap.getValue(it) }
          EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> value
        }
        // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> value
        is EntityPropertyKind.DataClass -> {
          assertDataClassIsWithoutReferences(kind)
          value
        }
        is EntityPropertyKind.EntityValue -> replaceMap.getValue(value as Long)
        EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> value
      }
    }
  }

  private fun traverseNodes(storage: ProxyBasedEntityStorage, startNode: Long, block: (Long) -> Unit) {
    val queue: Queue<Long> = Queues.newArrayDeque(listOf(startNode))
    while (queue.isNotEmpty()) {
      val id = queue.remove()
      block(id)

      val referrers = storage.referrers[id]
      if (referrers != null) {
        queue.addAll(referrers)
      }
    }
  }

  data class EqualityResult<T1, T2>(
    val onlyIn1: List<T1>,
    val onlyIn2: List<T2>,
    val equal: List<Pair<T1, T2>>
  )

  override fun collectChanges(original: TypedEntityStorage): Map<Class<*>, List<EntityChange<*>>> {
    val originalImpl = original as ProxyBasedEntityStorage
    //this can be optimized to avoid creation of entity instances which are thrown away and copying the results from map to list
    // LinkedHashMap<Long, EntityChange<T>>
    val changes = LinkedHashMap<Long, Pair<Class<*>, EntityChange<*>>>()
    for (change in changeLog) {
      when (change) {
        is ChangeEntry.AddEntity -> {
          changes[change.entityData.id] = change.entityData.unmodifiableEntityType to EntityChange.Added(createEntityInstance(change.entityData))
        }
        is ChangeEntry.RemoveEntity -> {
          val removedData = originalImpl.entityById[change.id]
          val oldChange = changes.remove(change.id)
          if (oldChange?.second !is EntityChange.Added && removedData != null) {
            changes[change.id] = removedData.unmodifiableEntityType to EntityChange.Removed(originalImpl.createEntityInstance(removedData))
          }
        }
        is ChangeEntry.ReplaceEntity -> {
          val oldChange = changes.remove(change.id)
          if (oldChange?.second is EntityChange.Added) {
            changes[change.id] = change.newData.unmodifiableEntityType to EntityChange.Added(createEntityInstance(change.newData))
          }
          else {
            val oldData = originalImpl.entityById[change.id]
            if (oldData != null) {
              changes[change.id] = oldData.unmodifiableEntityType to EntityChange.Replaced(originalImpl.createEntityInstance(oldData), createEntityInstance(change.newData))
            }
          }
        }
      }
    }
    return changes.values.groupBy { it.first }.mapValues { list -> list.value.map { it.second } }
  }

  override fun resetChanges() {
    updateChangeLog { changeLog -> changeLog.clear() }
  }

  override fun toStorage(): TypedEntityStorage = ProxyBasedEntityStorage(entitiesByType.mapValues { it.value.toSet() },
                                                                         entitiesBySource.mapValues { it.value.toSet() },
                                                                         entitiesByPersistentIdHash.mapValues { it.value.toSet() },
                                                                         entityById.toMap(),
                                                                         referrers.mapValues { it.value.toList() },
                                                                         metaDataRegistry)

  companion object {
    internal val NEXT_ID = AtomicLong(1)
  }

  sealed class ChangeEntry {
    data class AddEntity(val entityData: EntityData) : ChangeEntry()
    data class RemoveEntity(val id: Long) : ChangeEntry()
    data class ReplaceEntity(val id: Long, val newData: EntityData) : ChangeEntry()
  }
}

internal class ProxyBasedEntityReferenceImpl<E : TypedEntity>(val id: Long): EntityReference<E>() {
  override fun resolve(storage: TypedEntityStorage): E = with(storage as ProxyBasedEntityStorage) {
    createEntityInstance(entityById.getValue(id)) as E
  }
}

internal class EntityData(val entitySource: EntitySource, val id: Long, val metaData: EntityMetaData, val properties: MutableMap<String, Any?> = HashMap()) {
  val unmodifiableEntityType: Class<out TypedEntity>
    get() = metaData.unmodifiableEntityType

  fun createModifiableCopy(): EntityData {
    val propertiesCopy = properties.mapValuesTo(HashMap()) { (it.value as? List<*>)?.toMutableList() ?: it.value }
    return EntityData(entitySource, id, metaData, propertiesCopy)
  }

  fun collectReferences(collector: (Long) -> Unit) = metaData.collectReferences(properties, collector)

  override fun toString() = "${unmodifiableEntityType.simpleName}@$id"
}

internal class EntityImpl(override val data: EntityData,
                          override val storage: ProxyBasedEntityStorage) : InvocationHandler, ProxyBasedEntity, ReferableTypedEntity {
  private val modifiable = ThreadLocal.withInitial { false }

  internal inline fun allowModifications(action: () -> Unit) {
    modifiable.set(true)
    try {
      action()
    }
    finally {
      modifiable.remove()
    }
  }

  override val entitySource: EntitySource
    get() = data.entitySource

  override val id: Long
    get() = data.id

  override fun <R : TypedEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> {
    return storage.referrers(id, entityClass, propertyName)
  }

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    return e is ProxyBasedEntity && e.data.unmodifiableEntityType == data.unmodifiableEntityType && e.data.properties == data.properties
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
    if (method.declaringClass == ProxyBasedEntity::class.java || method.declaringClass == ReferableTypedEntity::class.java
        || method.declaringClass == TypedEntity::class.java) {
      return method.invoke(this, *(args ?: ArrayUtil.EMPTY_OBJECT_ARRAY))
    }
    val name = method.name
    when (name) {
      "equals" -> return (args?.firstOrNull() as? ProxyBasedEntity)?.id == id
      "hashCode" -> return id.toInt()
      "toString" -> return data.toString()
    }
    if (method.isDefault) {
      if (JavaVersion.current().isAtLeast(9)) {
        val methodHandle = MethodHandles.lookup().findSpecial(method.declaringClass, method.name,
                                                              MethodType.methodType(method.returnType, method.parameterTypes),
                                                              method.declaringClass)
        return methodHandle.bindTo(proxy).invokeWithArguments(*(args ?: ArrayUtil.EMPTY_OBJECT_ARRAY))
      }
      else {
        //this hack is needed because in Java 8 the way above won't work (see https://bugs.openjdk.java.net/browse/JDK-8130227)
        val constructor = MethodHandles.Lookup::class.java.getDeclaredConstructor(Class::class.java, Int::class.java)
        constructor.isAccessible = true
        val lookup = constructor.newInstance(method.declaringClass, MethodHandles.Lookup.PRIVATE)
        return lookup.unreflectSpecial(method, method.declaringClass).bindTo(proxy).invokeWithArguments(*(args ?: ArrayUtil.EMPTY_OBJECT_ARRAY))
      }
    }
    if (name.startsWith("get")) {
      val propertyName = name.removePrefix("get").decapitalize()
      val value = data.properties[propertyName]
      return when (val propertyKind = data.metaData.properties.getValue(propertyName)) {
        is EntityPropertyKind.EntityValue -> storage.createEntityInstance(storage.entityById.getValue(value as Long))
        is EntityPropertyKind.List -> when (propertyKind.itemKind) {
          is EntityPropertyKind.EntityValue -> {
            (value as List<Long>).map { id -> storage.createEntityInstance(storage.entityById.getValue(id)) }
          }
          else -> value
        }
        else -> value
      }
    }
    if (name.startsWith("set")) {
      if (!modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }

      val newValue = args!![0]
      val propertyName = name.removePrefix("set").decapitalize()

      val referrers = (storage as TypedEntityStorageBuilderImpl).referrers

      when (val propertyKind = data.metaData.properties[propertyName]) {
        is EntityPropertyKind.EntityValue -> {
          val oldStorageValue = data.properties[propertyName]
          data.properties[propertyName] = newValue?.let { it as ProxyBasedEntity }?.id

          if (oldStorageValue != null) {
            referrers.listRemoveValue(oldStorageValue as Long, id)
          }
          if (newValue != null) {
            referrers.listPutValue((newValue as ProxyBasedEntity).id, id)
          }
        }
        is EntityPropertyKind.List -> when (val itemKind = propertyKind.itemKind) {
          is EntityPropertyKind.EntityValue -> {
            val oldValues = data.properties[propertyName]
            data.properties[propertyName] = newValue?.let { it as List<ProxyBasedEntity> }?.map { it.id }

            if (oldValues != null) {
              (oldValues as List<Long>).forEach { referrers.listRemoveValue(it, id) }
            }

            if (newValue != null) {
              (newValue as List<ProxyBasedEntity>).forEach { referrers.listPutValue(it.id, id) }
            }

            Unit
          }
          is EntityPropertyKind.DataClass -> {
            val oldValues = data.properties[propertyName]
            data.properties[propertyName] = newValue

            if (oldValues != null) {
              for (oldValue in oldValues as List<*>) {
                itemKind.collectReferences(oldValue) { referrers.listRemoveValue(it, id) }
              }
            }

            if (newValue != null) {
              for (value in newValue as List<*>) {
                itemKind.collectReferences(newValue) { referrers.listPutValue(it, id) }
              }
            }

            Unit
          }

          is EntityPropertyKind.EntityReference, is EntityPropertyKind.List -> error("List item kind is unsupported: $itemKind")
          // TODO EntityReferences are unsupported in SealedKotlinDataClassHierarchy, asserted in EntityMetaDataRegistry.getPropertyKind
          is EntityPropertyKind.SealedKotlinDataClassHierarchy, is EntityPropertyKind.Primitive,
          is EntityPropertyKind.PersistentId, EntityPropertyKind.FileUrl -> data.properties[propertyName] = newValue
        }.let {  } // exhaustive when
        is EntityPropertyKind.DataClass -> {
          val oldValue = data.properties[propertyName]
          data.properties[propertyName] = newValue

          propertyKind.collectReferences(oldValue) { referrers.listRemoveValue(it, id) }
          propertyKind.collectReferences(newValue) { referrers.listPutValue(it, id) }
        }
        else -> data.properties[propertyName] = newValue
      }
      return null
    }

    throw IllegalArgumentException("Unexpected method $method")
  }
}

private fun <M : ModifiableTypedEntity<T>, T : TypedEntity> getUnmodifiableEntityClass(clazz: Class<out M>): Class<T> {
  val modifiableType = clazz.genericInterfaces.filterIsInstance(ParameterizedType::class.java).find { it.rawType == ModifiableTypedEntity::class.java }
                       ?: throw IllegalArgumentException("$clazz doesn't implement ModifiableTypedEntity")
  val unmodifiableEntityClass = modifiableType.actualTypeArguments.firstOrNull() as? Class<T>
                               ?: throw IllegalArgumentException("$clazz doesn't specify type argument for ModifiableTypedEntity")
  if (!unmodifiableEntityClass.isAssignableFrom(clazz)) {
    throw IllegalArgumentException("$clazz is not subclass of its unmodifiable version $unmodifiableEntityClass")
  }
  return unmodifiableEntityClass
}

private fun <K, V> MutableMap<K, MutableSet<V>>.putValue(k: K, v: V) {
  computeIfAbsent(k) {HashSet()}.add(v)
}

private fun <K, V> MutableMap<K, MutableSet<V>>.removeValue(k: K, v: V) {
  val set = get(k)
  if (set != null) {
    set.remove(v)
    if (set.isEmpty()) {
      remove(k)
    }
  }
}

private fun <K, V> MutableMap<K, MutableList<V>>.listPutValue(k: K, v: V) {
  computeIfAbsent(k) {ArrayList()}.add(v)
}

private fun <K, V> MutableMap<K, MutableList<V>>.listRemoveValue(k: K, v: V) {
  val set = get(k)
  if (set != null) {
    set.remove(v)
    if (set.isEmpty()) {
      remove(k)
    }
  }
}