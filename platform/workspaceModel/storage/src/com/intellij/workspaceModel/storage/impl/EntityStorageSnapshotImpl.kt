// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ExceptionUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
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
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal data class EntityReferenceImpl<E : WorkspaceEntity>(private val id: EntityId) : EntityReference<E>() {
  override fun resolve(storage: EntityStorage): E? {
    @Suppress("UNCHECKED_CAST")
    return (storage as AbstractEntityStorage).entityDataById(id)?.createEntity(storage) as? E
  }
}

internal class EntityStorageSnapshotImpl constructor(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: StorageIndexes
) : EntityStorageSnapshot, AbstractEntityStorage() {

  // This cache should not be transferred to other versions of storage
  private val persistentIdCache = ConcurrentHashMap<PersistentEntityId<*>, WorkspaceEntity>()

  // I suppose that we can use some kind of array of arrays to get a quicker access (just two accesses by-index)
  // However, it's not implemented currently because I'm not sure about threading.
  private val entitiesCache = ConcurrentHashMap<EntityId, WorkspaceEntity>()

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val entity = persistentIdCache.getOrPut(id) { super.resolve(id) ?: NULL_ENTITY }
    return if (entity !== NULL_ENTITY) entity as E else null
  }

  override fun toSnapshot(): EntityStorageSnapshot = this

  internal fun getCachedEntityById(entityId: EntityId, orPut: (() -> WorkspaceEntity)): WorkspaceEntity {
    val found = entitiesCache[entityId]
    if (found != null) {
      return found
    }
    val newData = orPut()
    entitiesCache[entityId] = newData
    return newData
  }

  companion object {
    private val NULL_ENTITY = ObjectUtils.sentinel("null entity", WorkspaceEntity::class.java)
    val EMPTY = EntityStorageSnapshotImpl(ImmutableEntitiesBarrel.EMPTY, RefsTable(), StorageIndexes.EMPTY)
  }
}

internal class MutableEntityStorageImpl(
  override val entitiesByType: MutableEntitiesBarrel,
  override val refs: MutableRefsTable,
  override val indexes: MutableStorageIndexes,
  @Volatile
  private var trackStackTrace: Boolean = false
) : MutableEntityStorage, AbstractEntityStorage() {

  internal val changeLog = WorkspaceBuilderChangeLog()

  // Temporal solution for accessing error in deft project.
  internal var throwExceptionOnError = false

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

  // --------------- Replace By Source stuff -----------
  internal var useNewRbs = Registry.`is`("ide.workspace.model.rbs.as.tree", true)

  @TestOnly
  internal var keepLastRbsEngine = false
  internal var engine: ReplaceBySourceOperation? = null

  @set:TestOnly
  internal var upgradeEngine: ((ReplaceBySourceOperation) -> Unit)? = null

  // --------------- Replace By Source stuff -----------

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    @Suppress("UNCHECKED_CAST")
    return entitiesByType[entityClass.toClassId()]?.all()?.map { it.wrapAsModifiable(this) } as? Sequence<E> ?: emptySequence()
  }

  override fun <E : WorkspaceEntityWithPersistentId, R : WorkspaceEntity> referrers(id: PersistentEntityId<E>,
                                                                                    entityClass: Class<R>): Sequence<R> {
    val classId = entityClass.toClassId()

    @Suppress("UNCHECKED_CAST")
    return indexes.softLinks.getIdsByEntry(id).asSequence()
      .filter { it.clazz == classId }
      .map { entityDataByIdOrDie(it).wrapAsModifiable(this) as R }
  }

  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    val entityIds = indexes.persistentIdIndex.getIdsByEntry(id) ?: return null
    val entityData: WorkspaceEntityData<WorkspaceEntity> = entityDataById(entityIds) as? WorkspaceEntityData<WorkspaceEntity> ?: return null
    @Suppress("UNCHECKED_CAST")
    return entityData.wrapAsModifiable(this) as E?
  }

  // Do not remove cast to Class<out TypedEntity>. kotlin fails without it
  @Suppress("USELESS_CAST")
  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    return indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
      indexes.entitySourceIndex
        .getIdsByEntry(source)!!.map {
          val entityDataById: WorkspaceEntityData<WorkspaceEntity> = this.entityDataById(it) as? WorkspaceEntityData<WorkspaceEntity>
                                                                     ?: run {
                                                                       reportErrorAndAttachStorage("Cannot find an entity by id $it",
                                                                                                   this@MutableEntityStorageImpl)
                                                                       error("Cannot find an entity by id $it")
                                                                     }
          entityDataById.wrapAsModifiable(this)
        }
        .groupBy { (it as WorkspaceEntityBase).getEntityInterface() }
    }
  }

  override fun <T : WorkspaceEntity> addEntity(entity: T) {
    try {
      lockWrite()

      entity as ModifiableWorkspaceEntityBase<T>

      entity.applyToBuilder(this)
    }
    finally {
      unlockWrite()
    }
  }

  // This should be removed or not extracted into the interface
  fun <T : WorkspaceEntity, D: ModifiableWorkspaceEntityBase<T>> putEntity(entity: D) {
    try {
      lockWrite()

      val newEntityData = entity.getEntityData()

      // Check for persistent id uniqueness
      assertUniquePersistentId(newEntityData)

      entitiesByType.add(newEntityData, entity.getEntityClass().toClassId())

      // Add the change to changelog
      createAddEvent(newEntityData)

      // Update indexes
      indexes.entityAdded(newEntityData)
    }
    finally {
      unlockWrite()
    }
  }

  private fun <T : WorkspaceEntity> assertUniquePersistentId(pEntityData: WorkspaceEntityData<T>) {
    pEntityData.persistentId()?.let { persistentId ->
      val ids = indexes.persistentIdIndex.getIdsByEntry(persistentId)
      if (ids != null) {
        // Oh, oh. This persistent id exists already
        // Fallback strategy: remove existing entity with all it's references
        val existingEntityData = entityDataByIdOrDie(ids)
        val existingEntity = existingEntityData.createEntity(this)
        removeEntity(existingEntity)
        LOG.error(
          """
              addEntity: persistent id already exists. Replacing entity with the new one.
              Persistent id: $persistentId
              
              Existing entity data: $existingEntityData
              New entity data: $pEntityData
              
              Broken consistency: $brokenConsistency
            """.trimIndent(), PersistentIdAlreadyExistsException(persistentId)
        )
        if (throwExceptionOnError) {
          throw PersistentIdAlreadyExistsException(persistentId)
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <M : ModifiableWorkspaceEntity<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    try {
      lockWrite()
      val entityId = (e as WorkspaceEntityBase).id

      val originalEntityData = this.getOriginalEntityData(entityId) as WorkspaceEntityData<T>

      // Get entity data that will be modified
      val copiedData = entitiesByType.getEntityDataForModification(entityId) as WorkspaceEntityData<T>

      val modifiableEntity = copiedData.wrapAsModifiable(this) as M

      val beforePersistentId = if (e is WorkspaceEntityWithPersistentId) e.persistentId else null

      val originalParents = this.getOriginalParents(entityId.asChild())
      val beforeParents = this.refs.getParentRefsOfChild(entityId.asChild())
      val beforeChildren = this.refs.getChildrenRefsOfParentBy(entityId.asParent()).flatMap { (key, value) -> value.map { key to it } }

      // Execute modification code
      (modifiableEntity as ModifiableWorkspaceEntityBase<*>).allowModifications {
        modifiableEntity.change()
      }

      // Check for persistent id uniqueness
      if (beforePersistentId != null) {
        val newPersistentId = copiedData.persistentId()
        if (newPersistentId != null) {
          val ids = indexes.persistentIdIndex.getIdsByEntry(newPersistentId)
          if (beforePersistentId != newPersistentId && ids != null) {
            // Oh, oh. This persistent id exists already.
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
            if (throwExceptionOnError) {
              throw PersistentIdAlreadyExistsException(newPersistentId)
            }
          }
        }
        else {
          LOG.error("Persistent id expected for entity: $copiedData")
        }
      }

      if (!modifiableEntity.changedProperty.contains("entitySource") || modifiableEntity.changedProperty.size > 1) {
        // Add an entry to changelog
        addReplaceEvent(this, entityId, beforeChildren, beforeParents, copiedData, originalEntityData, originalParents)
      }
      if (modifiableEntity.changedProperty.contains("entitySource")) {
        updateEntitySource(entityId, originalEntityData, copiedData)
      }

      val updatedEntity = copiedData.createEntity(this)

      this.indexes.updatePersistentIdIndexes(this, updatedEntity, beforePersistentId, copiedData, modifiableEntity)

      return updatedEntity
    }
    finally {
      unlockWrite()
    }
  }

  private fun <T : WorkspaceEntity> updateEntitySource(entityId: EntityId, originalEntityData: WorkspaceEntityData<T>,
                                                       copiedEntityData: WorkspaceEntityData<T>) {
    val newSource = copiedEntityData.entitySource
    val originalSource = this.getOriginalSourceFromChangelog(entityId) ?: originalEntityData.entitySource

    this.changeLog.addChangeSourceEvent(entityId, copiedEntityData, originalSource)
    indexes.entitySourceIndex.index(entityId, newSource)
    newSource.virtualFileUrl?.let { indexes.virtualFileIndex.index(entityId, VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY, it) }
  }

  override fun removeEntity(e: WorkspaceEntity): Boolean {
    try {
      lockWrite()

      LOG.debug { "Removing ${e.javaClass}..." }
      e as WorkspaceEntityBase
      return removeEntityByEntityId(e.id)

      // NB: This method is called from `createEntity` inside persistent id checking. It's possible that after the method execution
      //  the store is in inconsistent state, so we can't call assertConsistency here.
    }
    finally {
      unlockWrite()
    }
  }

  private fun getRbsEngine(): ReplaceBySourceOperation {
    if (useNewRbs) {
      return ReplaceBySourceAsTree()
    }
    else {
      return ReplaceBySourceAsGraph()
    }
  }

  /**
   *  TODO  Spacial cases: when source filter returns true for all entity sources.
   */
  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage) {
    try {
      lockWrite()
      replaceWith as AbstractEntityStorage
      val rbsEngine = getRbsEngine()
      if (keepLastRbsEngine) {
        engine = rbsEngine
      }
      upgradeEngine?.let { it(rbsEngine) }
      rbsEngine.replace(this, replaceWith, sourceFilter)
    }
    finally {
      unlockWrite()
    }
  }

  override fun collectChanges(original: EntityStorage): Map<Class<*>, List<EntityChange<*>>> {
    try {
      lockWrite()
      val originalImpl = original as AbstractEntityStorage

      cleanupChanges(originalImpl)

      val res = HashMap<Class<*>, MutableList<EntityChange<*>>>()
      for ((entityId, change) in this.changeLog.changeLog) {
        when (change) {
          is ChangeEntry.AddEntity -> {
            val addedEntity = change.entityData.createEntity(this) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }.add(EntityChange.Added(addedEntity))
          }
          is ChangeEntry.RemoveEntity -> {
            val removedData = originalImpl.entityDataById(change.id) ?: continue
            val removedEntity = removedData.createEntity(originalImpl) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }.add(EntityChange.Removed(removedEntity))
          }
          is ChangeEntry.ReplaceEntity -> {
            @Suppress("DuplicatedCode")
            val oldData = originalImpl.entityDataById(entityId) ?: continue
            val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
            val replaceToData = change.newData.createEntity(this) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
              .add(EntityChange.Replaced(replacedData, replaceToData))
          }
          is ChangeEntry.ChangeEntitySource -> {
            val oldData = originalImpl.entityDataById(entityId) ?: continue
            val replacedData = oldData.createEntity(originalImpl) as WorkspaceEntityBase
            val replaceToData = change.newData.createEntity(this) as WorkspaceEntityBase
            res.getOrPut(entityId.clazz.findEntityClass<WorkspaceEntity>()) { ArrayList() }
              .add(EntityChange.Replaced(replacedData, replaceToData))
          }
          is ChangeEntry.ReplaceAndChangeSource -> {
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

  /**
   * Here we eliminate remove + add changes if the entities are the same and they don't have external mappings.
   * We don't join events if the entities have external mappings just because it's safer.
   */
  internal fun cleanupChanges(originalImpl: AbstractEntityStorage) {
    val adds = ArrayList<WorkspaceEntityData<*>>()
    val removes = CollectionFactory.createSmallMemoryFootprintMap<WorkspaceEntityData<out WorkspaceEntity>, MutableList<WorkspaceEntityData<out WorkspaceEntity>>>()
    changeLog.changeLog.forEach { _, value ->
      if (value is ChangeEntry.AddEntity) {
        adds.add(value.entityData)
      }
      else if (value is ChangeEntry.RemoveEntity) {
        val existingValue = removes[value.oldData]
        removes[value.oldData] = if (existingValue != null) ArrayList(existingValue + value.oldData) else mutableListOf(value.oldData)
      }
    }
    val idsToRemove = ArrayList<EntityId>()
    adds.forEach { addedEntityData ->
      if (removes.isEmpty()) return@forEach
      val possibleRemovedSameEntity = removes[addedEntityData]
      val addedEntityId = addedEntityData.createEntityId()
      val hasMapping = this.indexes.externalMappings.any { (_, value) ->
        (value as ExternalEntityMappingImpl<*>).getDataByEntityId(addedEntityId) != null
      }
      if (hasMapping) return@forEach
      val found = possibleRemovedSameEntity?.firstOrNull { possibleRemovedSame ->
        same(originalImpl, addedEntityId, possibleRemovedSame.createEntityId())
      }
      if (found != null) {
        val foundEntityId = found.createEntityId()
        val hasRemovedMapping = originalImpl.indexes.externalMappings.any { (_, value) ->
          value.getDataByEntityId(foundEntityId) != null
        }
        if (hasRemovedMapping) return@forEach
        possibleRemovedSameEntity.remove(found)
        if (possibleRemovedSameEntity.isEmpty()) removes.remove(addedEntityData)
        idsToRemove += addedEntityId
        idsToRemove += foundEntityId
      }
    }
    idsToRemove.forEach { changeLog.changeLog.remove(it) }
  }

  private fun same(originalImpl: AbstractEntityStorage,
                   addedEntityId: EntityId,
                   removedEntityId: EntityId): Boolean {
    if (addedEntityId == removedEntityId) return true
    if (addedEntityId.clazz != removedEntityId.clazz) return false

    val addedParents = this.refs.getParentRefsOfChild(addedEntityId.asChild())
    val removeParents = originalImpl.refs.getParentRefsOfChild(removedEntityId.asChild())
    if (addedParents.keys != removeParents.keys) return false
    return addedParents.entries.all { (connectionId, addedParentEntityId) ->
      val removedParentEntityId = removeParents[connectionId]!!
      if (addedParentEntityId == removedParentEntityId) return@all true
      val addedParentInfo = changeLog.changeLog[addedParentEntityId.id]
      val removedParentInfo = changeLog.changeLog[removedParentEntityId.id]
      if (addedParentInfo is ChangeEntry.AddEntity && removedParentInfo is ChangeEntry.RemoveEntity) {
        same(originalImpl, addedParentEntityId.id, removedParentEntityId.id)
      } else false
    }
  }


  override fun toSnapshot(): EntityStorageSnapshot {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    return EntityStorageSnapshotImpl(newEntities, newRefs, newIndexes)
  }

  override fun isEmpty(): Boolean = this.changeLog.changeLog.isEmpty()

  override fun addDiff(diff: MutableEntityStorage) {
    try {
      lockWrite()
      diff as MutableEntityStorageImpl
      applyDiffProtection(diff, "addDiff")
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

  internal fun addDiffAndReport(message: String, left: EntityStorage?, right: EntityStorage) {
    reportConsistencyIssue(message, AddDiffException(message), null, left, right, this)
  }

  private fun applyDiffProtection(diff: AbstractEntityStorage, method: String) {
    LOG.trace { "Applying $method. Builder: $diff" }
    if (diff.storageIsAlreadyApplied) {
      LOG.error("Builder is already applied.\n Info: \n${diff.applyInfo}")
    }
    else {
      diff.storageIsAlreadyApplied = true
      var info = "Applying builder using $method. Previous stack trace >>>>\n"
      if (LOG.isTraceEnabled) {
        val currentStackTrace = ExceptionUtil.currentStackTrace()
        info += "\n$currentStackTrace"
      }
      info += "<<<<"
      diff.applyInfo = info
    }
  }

  // modificationCount is not incremented
  internal fun removeEntityByEntityId(idx: EntityId, entityFilter: (EntityId) -> Boolean = { true }): Boolean {
    val accumulator: MutableSet<EntityId> = mutableSetOf(idx)

    if (!entitiesByType.exists(idx)) {
      return false
    }

    accumulateEntitiesToRemove(idx, accumulator, entityFilter)

    val originals = accumulator.associateWith {
      this.getOriginalEntityData(it) as WorkspaceEntityData<WorkspaceEntity> to this.getOriginalParents(it.asChild())
    }

    for (id in accumulator) {
      val entityData = entityDataById(id)
      if (entityData is SoftLinkable) indexes.removeFromSoftLinksIndex(entityData)
      entitiesByType.remove(id.arrayId, id.clazz)
    }

    // Update index
    //   Please don't join it with the previous loop
    for (id in accumulator) indexes.entityRemoved(id)

    accumulator.forEach {
      LOG.debug { "Cascade removing: ${ClassToIntConverter.INSTANCE.getClassOrDie(it.clazz)}-${it.arrayId}" }
      this.changeLog.addRemoveEvent(it, originals[it]!!.first, originals[it]!!.second)
    }
    return true
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
    if (trackStackTrace || LOG.isTraceEnabled) {
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

    private val LOG = logger<MutableEntityStorageImpl>()

    fun create(): MutableEntityStorageImpl {
      return from(EntityStorageSnapshotImpl.EMPTY)
    }

    fun from(storage: EntityStorage): MutableEntityStorageImpl {
      storage as AbstractEntityStorage
      val newBuilder = when (storage) {
        is EntityStorageSnapshotImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType)
          val copiedRefs = MutableRefsTable.from(storage.refs)
          val copiedIndex = storage.indexes.toMutable()
          MutableEntityStorageImpl(copiedBarrel, copiedRefs, copiedIndex)
        }
        is MutableEntityStorageImpl -> {
          val copiedBarrel = MutableEntitiesBarrel.from(storage.entitiesByType.toImmutable())
          val copiedRefs = MutableRefsTable.from(storage.refs.toImmutable())
          val copiedIndexes = storage.indexes.toMutable()
          MutableEntityStorageImpl(copiedBarrel, copiedRefs, copiedIndexes, storage.trackStackTrace)
        }
      }
      LOG.trace { "Create new builder $newBuilder from $storage.\n${currentStackTrace(10)}" }
      return newBuilder
    }

    internal fun addReplaceEvent(
      builder: MutableEntityStorageImpl,
      entityId: EntityId,
      beforeChildren: List<Pair<ConnectionId, ChildEntityId>>,
      beforeParents: Map<ConnectionId, ParentEntityId>,
      copiedData: WorkspaceEntityData<out WorkspaceEntity>,
      originalEntity: WorkspaceEntityData<out WorkspaceEntity>,
      originalParents: Map<ConnectionId, ParentEntityId>,
    ) {
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

      builder.changeLog.addReplaceEvent(entityId, copiedData, originalEntity, originalParents, addedChildren, removedChildren, parentsMapRes)
    }
  }
}

internal sealed class AbstractEntityStorage : EntityStorage {

  internal abstract val entitiesByType: EntitiesBarrel
  internal abstract val refs: AbstractRefsTable
  internal abstract val indexes: StorageIndexes

  internal var brokenConsistency: Boolean = false

  internal var storageIsAlreadyApplied = false
  internal var applyInfo: String? = null

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    @Suppress("UNCHECKED_CAST")
    return entitiesByType[entityClass.toClassId()]?.all()?.map { it.createEntity(this) } as? Sequence<E> ?: emptySequence()
  }

  override fun <E : WorkspaceEntity> entitiesAmount(entityClass: Class<E>): Int {
    return entitiesByType[entityClass.toClassId()]?.size() ?: 0
  }

  internal fun entityDataById(id: EntityId): WorkspaceEntityData<out WorkspaceEntity>? = entitiesByType[id.clazz]?.get(id.arrayId)

  internal fun entityDataByIdOrDie(id: EntityId): WorkspaceEntityData<out WorkspaceEntity> {
    val entityFamily = entitiesByType[id.clazz] ?: error(
      "Entity family doesn't exist or has no entities: ${id.clazz.findWorkspaceEntity()}")
    return entityFamily.get(id.arrayId) ?: error("Cannot find an entity by id $id")
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

  operator override fun <E : WorkspaceEntityWithPersistentId> contains(id: PersistentEntityId<E>): Boolean {
    return indexes.persistentIdIndex.getIdsByEntry(id) != null
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    return indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
      indexes.entitySourceIndex
        .getIdsByEntry(source)!!.map {
          this.entityDataById(it)?.createEntity(this) ?: run {
            reportErrorAndAttachStorage("Cannot find an entity by id $it", this@AbstractEntityStorage)
            error("Cannot find an entity by id $it")
          }
        }
        .groupBy { (it as WorkspaceEntityBase).getEntityInterface() }
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

  internal fun assertConsistencyInStrictMode(message: String,
                                             sourceFilter: ((EntitySource) -> Boolean)?,
                                             left: EntityStorage?,
                                             right: EntityStorage?) {
    if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) {
      try {
        this.assertConsistency()
      }
      catch (e: Throwable) {
        brokenConsistency = true
        val storage = if (this is MutableEntityStorage) this.toSnapshot() as AbstractEntityStorage else this
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

  companion object {
    val LOG = logger<AbstractEntityStorage>()

    private val consistencyChecker = AppExecutorUtil.createBoundedApplicationPoolExecutor("Check workspace model consistency", 1)
  }
}

/** This function exposes `brokenConsistency` property to the outside and should be removed along with the property itself */
val EntityStorage.isConsistent: Boolean
  get() = !(this as AbstractEntityStorage).brokenConsistency
