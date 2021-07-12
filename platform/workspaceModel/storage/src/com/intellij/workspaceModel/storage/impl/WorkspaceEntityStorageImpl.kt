// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.Compressor
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.containers.getDiff
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.exceptions.PersistentIdAlreadyExistsException
import com.intellij.workspaceModel.storage.impl.external.EmptyExternalEntityMapping
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex.MutableVirtualFileIndex.Companion.VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.name
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal data class EntityReferenceImpl<E : WorkspaceEntity>(private val id: EntityId) : EntityReference<E>() {
  override fun resolve(storage: WorkspaceEntityStorage): E? {
    @Suppress("UNCHECKED_CAST")
    return (storage as AbstractEntityStorage).entityDataById(id)?.createEntity(storage) as? E
  }
}

internal class WorkspaceEntityStorageImpl constructor(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: StorageIndexes
) : AbstractEntityStorage() {

  // This cache should not be transferred to other versions of storage
  private val persistentIdCache = ConcurrentHashMap<PersistentEntityId<*>, WorkspaceEntity>()

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val entity = persistentIdCache.getOrPut(id) { super.resolve(id) ?: NULL_ENTITY }
    return if (entity !== NULL_ENTITY) entity as E else null
  }

  companion object {
    private val NULL_ENTITY = ObjectUtils.sentinel("null entity", WorkspaceEntity::class.java)
    val EMPTY = WorkspaceEntityStorageImpl(ImmutableEntitiesBarrel.EMPTY, RefsTable(), StorageIndexes.EMPTY
    )
  }
}

internal class WorkspaceEntityStorageBuilderImpl(
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable,
  override val indexes: MutableStorageIndexes,
  @Volatile
  private var trackStackTrace: Boolean = false
) : WorkspaceEntityStorageBuilder, AbstractEntityStorage() {

  internal val changeLog = WorkspaceBuilderChangeLog()

  internal fun incModificationCount() {
    this.changeLog.modificationCount++
  }

  override val modificationCount: Long
    get() = this.changeLog.modificationCount

  private val writingFlag = AtomicBoolean()

  @Volatile
  private var stackTrace: String? = null

  @Volatile
  private var threadId: Long? = null

  @Volatile
  private var threadName: String? = null

  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> addEntity(clazz: Class<M>,
                                                                                 source: EntitySource,
                                                                                 initializer: M.() -> Unit): T {
    try {
      lockWrite()

      // Extract entity classes
      val unmodifiableEntityClass = ClassConversion.modifiableEntityToEntity(clazz.kotlin).java
      val entityDataClass = ClassConversion.entityToEntityData(unmodifiableEntityClass.kotlin)
      val unmodifiableEntityClassId = unmodifiableEntityClass.toClassId()

      // Construct entity data
      val pEntityData = entityDataClass.java.getDeclaredConstructor().newInstance()
      pEntityData.entitySource = source

      // Add entity data to the structure
      entitiesByType.add(pEntityData, unmodifiableEntityClassId)

      // Wrap it with modifiable and execute initialization code
      @Suppress("UNCHECKED_CAST")
      val modifiableEntity = pEntityData.wrapAsModifiable(this) as M // create modifiable after adding entity data to set
      (modifiableEntity as ModifiableWorkspaceEntityBase<*>).allowModifications {
        modifiableEntity.initializer()
      }

      // Check for persistent id uniqueness
      pEntityData.persistentId(this)?.let { persistentId ->
        val ids = indexes.persistentIdIndex.getIdsByEntry(persistentId)
        if (ids != null) {
          // Oh oh. This persistent id exists already
          // Fallback strategy: remove existing entity with all it's references
          val existingEntityData = entityDataByIdOrDie(ids)
          val existingEntity = existingEntityData.createEntity(this)
          removeEntity(existingEntity)
          LOG.error("""
            addEntity: persistent id already exists. Replacing entity with the new one.
            Persistent id: $persistentId
            
            Existing entity data: $existingEntityData
            New entity data: $pEntityData
            
            Broken consistency: $brokenConsistency
          """.trimIndent(), PersistentIdAlreadyExistsException(persistentId))
        }
      }

      // Add the change to changelog
      createAddEvent(pEntityData)

      // Update indexes
      indexes.entityAdded(pEntityData, this)

      LOG.debug { "New entity added: $clazz-${pEntityData.id}" }

      return pEntityData.createEntity(this)
    }
    finally {
      unlockWrite()
    }
  }

  override fun <M : ModifiableWorkspaceEntity<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    try {
      lockWrite()
      // Get entity data that will be modified
      @Suppress("UNCHECKED_CAST")
      val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>

      @Suppress("UNCHECKED_CAST")
      val modifiableEntity = copiedData.wrapAsModifiable(this) as M

      val beforePersistentId = if (e is WorkspaceEntityWithPersistentId) e.persistentId() else null

      val entityId = e.id

      val beforeParents = this.refs.getParentRefsOfChild(entityId.asChild())
      val beforeChildren = this.refs.getChildrenRefsOfParentBy(entityId.asParent()).flatMap { (key, value) -> value.map { key to it } }

      // Execute modification code
      (modifiableEntity as ModifiableWorkspaceEntityBase<*>).allowModifications {
        modifiableEntity.change()
      }

      // Check for persistent id uniqueness
      if (beforePersistentId != null) {
        val newPersistentId = copiedData.persistentId(this)
        if (newPersistentId != null) {
          val ids = indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
          if (beforePersistentId != newPersistentId && ids != null) {
            // Oh oh. This persistent id exists already.
            // Remove an existing entity and replace it with the new one.

            val existingEntityData = entityDataByIdOrDie(ids)
            val existingEntity = existingEntityData.createEntity(this)
            removeEntity(existingEntity)
            LOG.error("""
              modifyEntity: persistent id already exists. Replacing entity with the new one.
              Old entity: $existingEntityData
              Persistent id: $copiedData
              
              Broken consistency: $brokenConsistency
            """.trimIndent(), PersistentIdAlreadyExistsException(newPersistentId))
          }
        }
        else {
          LOG.error("Persistent id expected for entity: $copiedData")
        }
      }

      // Add an entry to changelog
      addReplaceEvent(this, entityId, beforeChildren, beforeParents, copiedData)

      val updatedEntity = copiedData.createEntity(this)

      updatePersistentIdIndexes(updatedEntity, beforePersistentId, copiedData, modifiableEntity)

      return updatedEntity
    }
    finally {
      unlockWrite()
    }
  }

  internal fun <T : WorkspaceEntity> updatePersistentIdIndexes(updatedEntity: WorkspaceEntity,
                                                               beforePersistentId: PersistentEntityId<*>?,
                                                               copiedData: WorkspaceEntityData<T>,
                                                               modifiableEntity: ModifiableWorkspaceEntityBase<*>? = null) {
    val entityId = (updatedEntity as WorkspaceEntityBase).id
    if (updatedEntity is WorkspaceEntityWithPersistentId) {
      val newPersistentId = updatedEntity.persistentId()
      if (beforePersistentId != null && beforePersistentId != newPersistentId) {
        indexes.persistentIdIndex.index(entityId, newPersistentId)
        updateComposedIds(beforePersistentId, newPersistentId)
      }
    }
    indexes.simpleUpdateSoftReferences(copiedData, modifiableEntity)
  }

  private fun updateComposedIds(beforePersistentId: PersistentEntityId<*>, newPersistentId: PersistentEntityId<*>) {
    val idsWithSoftRef = HashSet(indexes.softLinks.getIdsByEntry(beforePersistentId))
    for (entityId in idsWithSoftRef) {
      val entity = this.entitiesByType.getEntityDataForModification(entityId)
      val editingBeforePersistentId = entity.persistentId(this)
      (entity as SoftLinkable).updateLink(beforePersistentId, newPersistentId)

      // Add an entry to changelog
      this.changeLog.addReplaceEvent(entityId, entity, emptyList(), emptySet(), emptyMap())
      // TODO :: Avoid updating of all soft links for the dependent entity
      updatePersistentIdIndexes(entity.createEntity(this), editingBeforePersistentId, entity)
    }
  }

  override fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T {
    try {
      lockWrite()

      @Suppress("UNCHECKED_CAST")
      val copiedData = entitiesByType.getEntityDataForModification((e as WorkspaceEntityBase).id) as WorkspaceEntityData<T>
      copiedData.entitySource = newSource

      val entityId = copiedData.createEntityId()

      this.changeLog.addChangeSourceEvent(entityId, copiedData)

      indexes.entitySourceIndex.index(entityId, newSource)
      newSource.virtualFileUrl?.let { indexes.virtualFileIndex.index(entityId, VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY, it) }
      return copiedData.createEntity(this)
    }
    finally {
      unlockWrite()
    }
  }

  override fun removeEntity(e: WorkspaceEntity) {
    try {
      lockWrite()

      LOG.debug { "Removing ${e.javaClass}..." }
      e as WorkspaceEntityBase
      removeEntity(e.id)
    }
    finally {
      unlockWrite()
    }
  }

  /**
   *  TODO  Spacial cases: when source filter returns true for all entity sources.
   */
  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: WorkspaceEntityStorage) {
    try {
      lockWrite()
      ReplaceBySourceAsGraph.replaceBySourceAsGraph(this, replaceWith, sourceFilter)
    }
    finally {
      unlockWrite()
    }
  }

  internal fun addDiffAndReport(message: String,
                                left: WorkspaceEntityStorage?,
                                right: WorkspaceEntityStorage) {
    reportConsistencyIssue(message, AddDiffException(message), null, left, right, this)
  }

  sealed class EntityDataChange<T : WorkspaceEntityData<out WorkspaceEntity>> {
    data class Added<T : WorkspaceEntityData<out WorkspaceEntity>>(val entity: T) : EntityDataChange<T>()
    data class Removed<T : WorkspaceEntityData<out WorkspaceEntity>>(val entity: T) : EntityDataChange<T>()
    data class Replaced<T : WorkspaceEntityData<out WorkspaceEntity>>(val oldEntity: T, val newEntity: T) : EntityDataChange<T>()
  }

  override fun collectChanges(original: WorkspaceEntityStorage): Map<Class<*>, List<EntityChange<*>>> {
    try {
      lockWrite()
      val originalImpl = original as AbstractEntityStorage

      val res = HashMap<Class<*>, MutableList<EntityChange<*>>>()
      for ((entityId, change) in this.changeLog.changeLog) {
        when (change) {
          is ChangeEntry.AddEntity<*> -> {
            val addedEntity = change.entityData.createEntity(this) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }.add(EntityChange.Added(addedEntity))
          }
          is ChangeEntry.RemoveEntity -> {
            val removedData = originalImpl.entityDataById(change.id) ?: continue
            val removedEntity = removedData.createEntity(originalImpl) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }.add(EntityChange.Removed(removedEntity))
          }
          is ChangeEntry.ReplaceEntity<*> -> {
            val oldData = originalImpl.entityDataById(entityId) ?: continue
            val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
            val replaceToData = change.newData.createEntity(this) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
              .add(EntityChange.Replaced(replacedData, replaceToData))
          }
          is ChangeEntry.ChangeEntitySource<*> -> {
            val oldData = originalImpl.entityDataById(entityId) ?: continue
            val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
            val replaceToData = change.newData.createEntity(this) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
              .add(EntityChange.Replaced(replacedData, replaceToData))
          }
          is ChangeEntry.ReplaceAndChangeSource<*> -> {
            val oldData = originalImpl.entityDataById(entityId) ?: continue
            val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
            val replaceToData = change.dataChange.newData.createEntity(this) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
              .add(EntityChange.Replaced(replacedData, replaceToData))
          }
        }
      }

      return res
    }
    finally {
      unlockWrite()
    }
  }

  override fun toStorage(): WorkspaceEntityStorageImpl {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    return WorkspaceEntityStorageImpl(newEntities, newRefs, newIndexes)
  }

  override fun isEmpty(): Boolean = this.changeLog.changeLog.isEmpty()

  override fun addDiff(diff: WorkspaceEntityStorageDiffBuilder) {
    try {
      lockWrite()
      diff as WorkspaceEntityStorageBuilderImpl
      AddDiffOperation(this, diff).addDiff()
    }
    finally {
      unlockWrite()
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T> {
    try {
      lockWrite()
      val mapping = indexes.externalMappings.computeIfAbsent(
        identifier) { MutableExternalEntityMappingImpl<T>() } as MutableExternalEntityMappingImpl<T>
      mapping.setTypedEntityStorage(this)
      return mapping
    }
    finally {
      unlockWrite()
    }
  }

  override fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex {
    try {
      lockWrite()
      val virtualFileIndex = indexes.virtualFileIndex
      virtualFileIndex.setTypedEntityStorage(this)
      return virtualFileIndex
    }
    finally {
      unlockWrite()
    }
  }

  fun removeExternalMapping(identifier: String) {
    indexes.externalMappings[identifier]?.clearMapping()
  }

  // modificationCount is not incremented
  internal fun removeEntity(idx: EntityId, entityFilter: (EntityId) -> Boolean = { true }) {
    val accumulator: MutableSet<EntityId> = mutableSetOf(idx)

    accumulateEntitiesToRemove(idx, accumulator, entityFilter)

    for (id in accumulator) {
      val entityData = entityDataById(id)
      if (entityData is SoftLinkable) indexes.removeFromSoftLinksIndex(entityData)
      entitiesByType.remove(id.arrayId, id.clazz)
    }

    // Update index
    //   Please don't join it with the previous loop
    for (id in accumulator) indexes.removeFromIndices(id)

    accumulator.forEach {
      LOG.debug { "Cascade removing: ${ClassToIntConverter.getClassOrDie(it.clazz)}-${it.arrayId}" }
      this.changeLog.addRemoveEvent(it)
    }
  }

  private fun lockWrite() {
    val currentThread = Thread.currentThread()
    if (writingFlag.getAndSet(true)) {
      if (threadId != null && threadId != currentThread.id) {
        LOG.error("""
            Concurrent write to builder from the following threads
            First Thread: $threadName
            Second Thread: ${currentThread.name}
            Previous stack trace: $stackTrace
          """.trimIndent())
        trackStackTrace = true
      }
    }
    if (trackStackTrace) {
      stackTrace = ExceptionUtil.currentStackTrace()
    }
    threadId = currentThread.id
    threadName = currentThread.name
  }

  private fun unlockWrite() {
    writingFlag.set(false)
    stackTrace = null
    threadId = null
    threadName = null
  }

  internal fun <T : WorkspaceEntity> createAddEvent(pEntityData: WorkspaceEntityData<T>) {
    val entityId = pEntityData.createEntityId()
    this.changeLog.addAddEvent(entityId, pEntityData)
  }

  /**
   * Cleanup references and accumulate hard linked entities in [accumulator]
   */
  private fun accumulateEntitiesToRemove(id: EntityId, accumulator: MutableSet<EntityId>, entityFilter: (EntityId) -> Boolean) {
    val children = refs.getChildrenRefsOfParentBy(id.asParent())
    for ((connectionId, childrenIds) in children) {
      for (childId in childrenIds) {
        if (childId.id in accumulator) continue
        if (!entityFilter(childId.id)) continue
        accumulator.add(childId.id)
        accumulateEntitiesToRemove(childId.id, accumulator, entityFilter)
        refs.removeRefsByParent(connectionId, id.asParent())
      }
    }

    val parents = refs.getParentRefsOfChild(id.asChild())
    for ((connectionId, parent) in parents) {
      refs.removeParentToChildRef(connectionId, parent, id.asChild())
    }
  }

  companion object {

    private val LOG = logger<WorkspaceEntityStorageBuilderImpl>()

    fun create(): WorkspaceEntityStorageBuilderImpl {
      return from(WorkspaceEntityStorageImpl.EMPTY)
    }

    fun from(storage: WorkspaceEntityStorage): WorkspaceEntityStorageBuilderImpl {
      storage as AbstractEntityStorage
      return when (storage) {
        is WorkspaceEntityStorageImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
          val copiedRefs = MutableRefsTable.from(storage.refs)
          val copiedIndex = storage.indexes.toMutable()
          WorkspaceEntityStorageBuilderImpl(copiedBarrel, copiedRefs, copiedIndex)
        }
        is WorkspaceEntityStorageBuilderImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType.toImmutable())
          val copiedRefs = MutableRefsTable.from(storage.refs.toImmutable())
          val copiedIndexes = storage.indexes.toMutable()
          WorkspaceEntityStorageBuilderImpl(copiedBarrel, copiedRefs, copiedIndexes, storage.trackStackTrace)
        }
      }
    }

    internal fun <T : WorkspaceEntity> addReplaceEvent(builder: WorkspaceEntityStorageBuilderImpl,
                                                       entityId: EntityId,
                                                       beforeChildren: List<Pair<ConnectionId, ChildEntityId>>,
                                                       beforeParents: Map<ConnectionId, ParentEntityId>,
                                                       copiedData: WorkspaceEntityData<T>) {
      val parents = builder.refs.getParentRefsOfChild(entityId.asChild())
      val unmappedChildren = builder.refs.getChildrenRefsOfParentBy(entityId.asParent())
      val children = unmappedChildren.flatMap { (key, value) -> value.map { key to it } }

      // Collect children changes
      val beforeChildrenSet = beforeChildren.toMutableSet()
      val (removedChildren, addedChildren) = getDiff(beforeChildrenSet, children)

      // Collect parent changes
      val parentsMapRes: MutableMap<ConnectionId, ParentEntityId?> = beforeParents.toMutableMap()
      for ((connectionId, parentId) in parents) {
        val existingParent = parentsMapRes[connectionId]
        if (existingParent != null) {
          if (existingParent == parentId) {
            parentsMapRes.remove(connectionId, parentId)
          }
          else {
            parentsMapRes[connectionId] = parentId
          }
        }
        else {
          parentsMapRes[connectionId] = parentId
        }
      }
      val removedKeys = beforeParents.keys - parents.keys
      removedKeys.forEach { parentsMapRes[it] = null }

      builder.changeLog.addReplaceEvent(entityId, copiedData, addedChildren, removedChildren, parentsMapRes)
    }
  }
}

internal sealed class AbstractEntityStorage : WorkspaceEntityStorage {

  internal abstract val entitiesByType: EntitiesBarrel
  internal abstract val refs: AbstractRefsTable
  internal abstract val indexes: StorageIndexes

  internal var brokenConsistency: Boolean = false

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    @Suppress("UNCHECKED_CAST")
    return entitiesByType[entityClass.toClassId()]?.all()?.map { it.createEntity(this) } as? Sequence<E> ?: emptySequence()
  }

  override fun <E : WorkspaceEntity> entitiesAmount(entityClass: Class<E>): Int {
    return entitiesByType[entityClass.toClassId()]?.size() ?: 0
  }

  internal fun entityDataById(id: EntityId): WorkspaceEntityData<out WorkspaceEntity>? = entitiesByType[id.clazz]?.get(id.arrayId)

  internal fun entityDataByIdOrDie(id: EntityId): WorkspaceEntityData<out WorkspaceEntity> {
    return entitiesByType[id.clazz]?.get(id.arrayId) ?: error("Cannot find an entity by id $id")
  }

  override fun <E : WorkspaceEntity, R : WorkspaceEntity> referrers(e: E, entityClass: KClass<R>,
                                                                    property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    TODO()
    //return entities(entityClass.java).filter { property.get(it).resolve(this) == e }
  }

  override fun <E : WorkspaceEntityWithPersistentId, R : WorkspaceEntity> referrers(id: PersistentEntityId<E>,
                                                                                    entityClass: Class<R>): Sequence<R> {
    val classId = entityClass.toClassId()

    @Suppress("UNCHECKED_CAST")
    return indexes.softLinks.getIdsByEntry(id).asSequence()
      .filter { it.clazz == classId }
      .map { entityDataByIdOrDie(it).createEntity(this) as R }
  }

  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val entityIds = indexes.persistentIdIndex.getIdsByEntry(id) ?: return null
    @Suppress("UNCHECKED_CAST")
    return entityDataById(entityIds)?.createEntity(this) as E?
  }

  // Do not remove cast to Class<out TypedEntity>. kotlin fails without it
  @Suppress("USELESS_CAST")
  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    return indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
      indexes.entitySourceIndex
        .getIdsByEntry(source)!!.map { this.entityDataByIdOrDie(it).createEntity(this) }
        .groupBy { it.javaClass as Class<out WorkspaceEntity> }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T> {
    val index = indexes.externalMappings[identifier] as? ExternalEntityMappingImpl<T>
    if (index == null) return EmptyExternalEntityMapping as ExternalEntityMapping<T>
    index.setTypedEntityStorage(this)
    return index
  }

  override fun getVirtualFileUrlIndex(): VirtualFileUrlIndex {
    indexes.virtualFileIndex.setTypedEntityStorage(this)
    return indexes.virtualFileIndex
  }

  override fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E> = EntityReferenceImpl((e as WorkspaceEntityBase).id)

  internal fun assertConsistency() {
    entitiesByType.assertConsistency(this)
    // Rules:
    //  1) Refs should not have links without a corresponding entity
    //    1.1) For abstract containers: EntityId has the class of ConnectionId
    //  2) There is no child without a parent under the hard reference

    refs.oneToManyContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY)

      //  1) Refs should not have links without a corresponding entity
      map.forEachKey { childId, parentId ->
        assertResolvable(connectionId.parentClass, parentId)
        assertResolvable(connectionId.childClass, childId)
      }

      //  2) All children should have a parent if the connection has a restriction for that
      if (!connectionId.isParentNullable) {
        checkStrongConnection(map.keys, connectionId.childClass, connectionId.parentClass, connectionId)
      }
    }

    refs.oneToOneContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ONE)

      //  1) Refs should not have links without a corresponding entity
      map.forEachKey { childId, parentId ->
        assertResolvable(connectionId.parentClass, parentId)
        assertResolvable(connectionId.childClass, childId)
      }

      //  2) Connections satisfy connectionId requirements
      if (!connectionId.isParentNullable) checkStrongConnection(map.keys, connectionId.childClass, connectionId.parentClass, connectionId)
    }

    refs.oneToAbstractManyContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY)

      map.forEach { (childId, parentId) ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(parentId.id.clazz, parentId.id.arrayId)
        assertResolvable(childId.id.clazz, childId.id.arrayId)

        //  1.1) For abstract containers: EntityId has the class of ConnectionId
        assertCorrectEntityClass(connectionId.parentClass, parentId.id)
        assertCorrectEntityClass(connectionId.childClass, childId.id)
      }

      //  2) Connections satisfy connectionId requirements
      if (!connectionId.isParentNullable) {
        checkStrongAbstractConnection(map.keys.map { it.id }.toMutableSet(), map.keys.map { it.id.clazz }.toSet(), connectionId.debugStr())
      }
    }

    refs.abstractOneToOneContainer.forEach { (connectionId, map) ->

      // Assert correctness of connection id
      assert(connectionId.connectionType == ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE)

      map.forEach { (childId, parentId) ->
        //  1) Refs should not have links without a corresponding entity
        assertResolvable(parentId.id.clazz, parentId.id.arrayId)
        assertResolvable(childId.id.clazz, childId.id.arrayId)

        //  1.1) For abstract containers: EntityId has the class of ConnectionId
        assertCorrectEntityClass(connectionId.parentClass, parentId.id)
        assertCorrectEntityClass(connectionId.childClass, childId.id)
      }

      //  2) Connections satisfy connectionId requirements
      if (!connectionId.isParentNullable) {
        checkStrongAbstractConnection(map.keys.mapTo(HashSet()) { it.id }, map.keys.toMutableSet().map { it.id.clazz }.toSet(),
                                      connectionId.debugStr())
      }
    }

    indexes.assertConsistency(this)
  }

  private fun checkStrongConnection(connectionKeys: IntSet, entityFamilyClass: Int, connectionTo: Int, connectionId: ConnectionId) {
    var counter = 0
    val entityFamily = entitiesByType[entityFamilyClass] ?: ImmutableEntityFamily()
    entityFamily.entities.forEachIndexed { i, entity ->
      if (entity == null) return@forEachIndexed
      assert(i in connectionKeys) {
        """
          |Storage inconsistency. Hard reference broken.
          |Existing entity $entity
          |Misses a reference to ${connectionTo.findWorkspaceEntity()}
          |Reference id: $i
          |ConnectionId: $connectionId
          """.trimMargin()
      }
      counter++
    }

    assert(counter == connectionKeys.size) { "Store is inconsistent" }
  }

  private fun checkStrongAbstractConnection(connectionKeys: Set<EntityId>, entityFamilyClasses: Set<Int>, debugInfo: String) {
    val keys = connectionKeys.toMutableSet()
    entityFamilyClasses.forEach { entityFamilyClass ->
      checkAllStrongConnections(entityFamilyClass, keys, debugInfo)
    }
    assert(keys.isEmpty()) { "Store is inconsistent. $debugInfo" }
  }

  private fun checkAllStrongConnections(entityFamilyClass: Int, keys: MutableSet<EntityId>, debugInfo: String) {
    val entityFamily = entitiesByType.entityFamilies[entityFamilyClass] ?: error("Entity family doesn't exist. $debugInfo")
    entityFamily.entities.forEach { entity ->
      if (entity == null) return@forEach
      val removed = keys.remove(entity.createEntityId())
      assert(removed) { "Entity $entity doesn't have a correct connection. $debugInfo" }
    }
  }

  internal fun assertConsistencyInStrictMode(message: String,
                                             sourceFilter: ((EntitySource) -> Boolean)?,
                                             left: WorkspaceEntityStorage?,
                                             right: WorkspaceEntityStorage?) {
    if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) {
      try {
        this.assertConsistency()
      }
      catch (e: Throwable) {
        brokenConsistency = true
        val storage = if (this is WorkspaceEntityStorageBuilder) this.toStorage() as AbstractEntityStorage else this
        val report = { reportConsistencyIssue(message, e, sourceFilter, left, right, storage) }
        if (ConsistencyCheckingMode.current == ConsistencyCheckingMode.ASYNCHRONOUS) {
          consistencyChecker.execute(report)
        }
        else {
          report()
        }
      }
    }
  }

  fun reportConsistencyIssue(message: String,
                             e: Throwable,
                             sourceFilter: ((EntitySource) -> Boolean)?,
                             left: WorkspaceEntityStorage?,
                             right: WorkspaceEntityStorage?,
                             resulting: WorkspaceEntityStorage) {
    val entitySourceFilter = if (sourceFilter != null) {
      val allEntitySources = (left as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries()?.toHashSet() ?: hashSetOf()
      allEntitySources.addAll((right as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries() ?: emptySet())
      allEntitySources.sortedBy { it.toString() }.fold("") { acc, source -> acc + if (sourceFilter(source)) "1" else "0" }
    }
    else null

    var _message = "$message\n\nEntity source filter: $entitySourceFilter"
    _message += "\n\nVersion: ${EntityStorageSerializerImpl.SERIALIZER_VERSION}"

    val zipFile = if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) {
      val dumpDirectory = getStoreDumpDirectory()
      _message += "\nSaving store content at: $dumpDirectory"
      serializeContentToFolder(dumpDirectory, left, right, resulting)
    }
    else null

    if (zipFile != null) {
      val attachment = Attachment("workspaceModelDump.zip", zipFile.readBytes(), "Zip of workspace model store")
      attachment.isIncluded = true
      LOG.error(_message, e, attachment)
    }
    else {
      LOG.error(_message, e)
    }
  }

  private fun serializeContentToFolder(contentFolder: Path,
                                       left: WorkspaceEntityStorage?,
                                       right: WorkspaceEntityStorage?,
                                       resulting: WorkspaceEntityStorage): File? {
    left?.let { serializeEntityStorage(contentFolder.resolve("Left_Store"), it) }
    right?.let { serializeEntityStorage(contentFolder.resolve("Right_Store"), it) }
    serializeEntityStorage(contentFolder.resolve("Res_Store"), resulting)
    serializeContent(contentFolder.resolve("ClassToIntConverter")) { serializer, stream -> serializer.serializeClassToIntConverter(stream) }

    if (right is WorkspaceEntityStorageBuilder) {
      serializeContent(contentFolder.resolve("Right_Diff_Log")) { serializer, stream ->
        right as WorkspaceEntityStorageBuilderImpl
        serializer.serializeDiffLog(stream, right.changeLog)
      }
    }

    return if (!executingOnTC()) {
      val zipFile = contentFolder.parent.resolve(contentFolder.name + ".zip").toFile()
      Compressor.Zip(zipFile).use { it.addDirectory(contentFolder.toFile()) }
      FileUtil.delete(contentFolder)
      zipFile
    }
    else null
  }

  private fun assertResolvable(clazz: Int, id: Int) {
    assert(entitiesByType[clazz]?.get(id) != null) {
      "Reference to ${clazz.findEntityClass<WorkspaceEntity>()}-:-$id cannot be resolved"
    }
  }

  private fun assertCorrectEntityClass(connectionClass: Int, entityId: EntityId) {
    assert(connectionClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(entityId.clazz.findEntityClass<WorkspaceEntity>())) {
      "Entity storage with connection class ${connectionClass.findEntityClass<WorkspaceEntity>()} contains entity data of wrong type $entityId"
    }
  }

  companion object {
    val LOG = logger<AbstractEntityStorage>()

    private val consistencyChecker = AppExecutorUtil.createBoundedApplicationPoolExecutor("Check workspace model consistency", 1)
  }
}

/** This function exposes `brokenConsistency` property to the outside and should be removed along with the property itself */
val WorkspaceEntityStorage.isConsistent: Boolean
  get() = !(this as AbstractEntityStorage).brokenConsistency

internal object ClassConversion {

  private val modifiableToEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val entityToEntityDataCache = HashMap<KClass<*>, KClass<*>>()
  private val entityDataToEntityCache = HashMap<Class<*>, Class<*>>()
  private val entityDataToModifiableEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val packageCache = HashMap<KClass<*>, String>()

  fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifiableEntityToEntity(clazz: KClass<out M>): KClass<T> {
    @Suppress("UNCHECKED_CAST")
    return modifiableToEntityCache.getOrPut(clazz) {
      try {
        Class.forName(getPackage(clazz) + clazz.java.simpleName.drop(10), true, clazz.java.classLoader).kotlin
      }
      catch (e: ClassNotFoundException) {
        error("Cannot get modifiable class for $clazz")
      }
    } as KClass<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkspaceEntity> entityToEntityData(clazz: KClass<out T>): KClass<WorkspaceEntityData<T>> {
    return entityToEntityDataCache.getOrPut(clazz) {
      (Class.forName(clazz.java.name + "Data", true, clazz.java.classLoader) as Class<WorkspaceEntityData<T>>).kotlin
    } as KClass<WorkspaceEntityData<T>>
  }

  @Suppress("UNCHECKED_CAST")
  fun <M : WorkspaceEntityData<out T>, T : WorkspaceEntity> entityDataToEntity(clazz: Class<out M>): Class<T> {
    return entityDataToEntityCache.getOrPut(clazz) {
      (Class.forName(clazz.name.dropLast(4), true, clazz.classLoader) as Class<T>)
    } as Class<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <D : WorkspaceEntityData<T>, T : WorkspaceEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableWorkspaceEntity<T>> {
    return entityDataToModifiableEntityCache.getOrPut(clazz) {
      Class.forName(getPackage(clazz) + "Modifiable" + clazz.java.simpleName.dropLast(4), true,
                    clazz.java.classLoader).kotlin as KClass<ModifiableWorkspaceEntity<T>>
    } as KClass<ModifiableWorkspaceEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String = packageCache.getOrPut(clazz) { clazz.java.name.dropLastWhile { it != '.' } }
}

// TODO: 28.05.2021 Make this value class since kt 1.5
// Just a wrapper for entity id in THIS store
internal data class ThisEntityId(val id: EntityId)

// Just a wrapper for entity id in some other store
internal data class NotThisEntityId(val id: EntityId)

internal fun EntityId.asThis(): ThisEntityId = ThisEntityId(this)
internal fun EntityId.notThis(): NotThisEntityId = NotThisEntityId(this)
