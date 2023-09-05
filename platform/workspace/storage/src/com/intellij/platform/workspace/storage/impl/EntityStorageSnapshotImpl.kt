// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.diagnostic.telemetry.JPS
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCacheImpl
import com.intellij.platform.workspace.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import com.intellij.platform.workspace.storage.impl.external.EmptyExternalEntityMapping
import com.intellij.platform.workspace.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex.MutableVirtualFileIndex.Companion.VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageSnapshotInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.url.MutableVirtualFileUrlIndex
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex
import com.intellij.util.ExceptionUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.CollectionFactory
import io.opentelemetry.api.metrics.Meter
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@OptIn(EntityStorageInstrumentationApi::class)
internal data class EntityReferenceImpl<E : WorkspaceEntity>(internal val id: EntityId) : EntityReference<E>() {
  override fun resolve(storage: EntityStorage): E? {
    storage as EntityStorageInstrumentation
    return storage.resolveReference(this)
  }

  override fun isReferenceTo(entity: WorkspaceEntity): Boolean {
    return id == (entity as? WorkspaceEntityBase)?.id
  }

  override fun equals(other: Any?): Boolean {
    return id == (other as? EntityReferenceImpl<*>)?.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}

// companion object in EntityStorageSnapshotImpl is initialized too late
private val entityStorageSnapshotImplInstancesCounter: AtomicLong = AtomicLong()

@OptIn(EntityStorageInstrumentationApi::class)
internal open class EntityStorageSnapshotImpl(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: StorageIndexes,
  internal val snapshotCache: TracedSnapshotCache = TracedSnapshotCacheImpl(),
) : EntityStorageSnapshotInstrumentation, AbstractEntityStorage() {

  init {
    entityStorageSnapshotImplInstancesCounter.incrementAndGet()
  }

  // This cache should not be transferred to other versions of storage
  private val symbolicIdCache = ConcurrentHashMap<SymbolicEntityId<*>, WorkspaceEntity>()

  // I suppose that we can use some kind of array of arrays to get a quicker access (just two accesses by-index)
  // However, it's not implemented currently because I'm not sure about threading.
  //
  // ConcurrentLongObjectHashMap can be used here, but it's not accessible from this module
  private val entityCache: Long2ObjectMap<WorkspaceEntity> = Long2ObjectOpenHashMap() // guarded by entityCache

  override fun <T> cached(query: StorageQuery<T>): T {
    return snapshotCache.cached(query, this)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val entity = symbolicIdCache.getOrPut(id) { super.resolve(id) ?: NULL_ENTITY }
    return if (entity !== NULL_ENTITY) entity as E else null
  }

  override fun toSnapshot(): EntityStorageSnapshot = this

  override fun <T: WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T {
    val found = synchronized(entityCache) { entityCache[entityId] }
    if (found != null) {
      @Suppress("UNCHECKED_CAST")
      return found as T
    }
    val newData = newInstance()
    synchronized(entityCache) {
      entityCache.put(entityId, newData)
    }
    return newData
  }

  companion object {
    private val NULL_ENTITY = ObjectUtils.sentinel("null entity", WorkspaceEntity::class.java)
    val EMPTY = EntityStorageSnapshotImpl(ImmutableEntitiesBarrel.EMPTY, RefsTable(), StorageIndexes.EMPTY)

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val instancesCountCounter = meter.counterBuilder("workspaceModel.entityStorageSnapshotImpl.instances.count").buildObserver()

      meter.batchCallback(
        {
          instancesCountCounter.record(entityStorageSnapshotImplInstancesCounter.get())
        },
        instancesCountCounter,
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(WorkspaceModel))
    }
  }
}


@OptIn(EntityStorageInstrumentationApi::class)
internal class MutableEntityStorageImpl(
  private val originalSnapshot: EntityStorageSnapshotImpl,
) : MutableEntityStorageInstrumentation, AbstractEntityStorage() {

  override val entitiesByType: MutableEntitiesBarrel = MutableEntitiesBarrel.from(originalSnapshot.entitiesByType)
  override val refs: MutableRefsTable = MutableRefsTable.from(originalSnapshot.refs)
  override val indexes: MutableStorageIndexes = originalSnapshot.indexes.toMutable()

  @Volatile
  private var trackStackTrace: Boolean = false

  init {
    instancesCounter.incrementAndGet()
  }

  /**
   *
   * This log collects the log of operations, not the log of state changes.
   * This means, that if we remove child entity, we'll record only remove event without "modify" event for its parent.
   *
   * This change log affects addDiff operation and [collectChanges] return result
   *
   * There is no particular reason not to store the list of "state" changes here. However, this will require a lot of work in order
   *   to record all changes on the storage and update [addDiff] method to work in the new way.
   */
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

  // --------------- Replace By Source stuff -----------
  @TestOnly
  internal var keepLastRbsEngine = false
  internal var engine: ReplaceBySourceAsTree? = null

  @set:TestOnly
  internal var upgradeEngine: ((ReplaceBySourceAsTree) -> Unit)? = null

  @set:TestOnly
  internal var upgradeAddDiffEngine: ((AddDiffOperation) -> Unit)? = null

  // --------------- Replace By Source stuff -----------

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> = getEntitiesTimeMs.addMeasuredTimeMillis {
    @Suppress("UNCHECKED_CAST")
    entitiesByType[entityClass.toClassId()]?.all()?.map { it.wrapAsModifiable(this) } as? Sequence<E> ?: emptySequence()
  }

  override fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(
    id: SymbolicEntityId<E>,
    entityClass: Class<R>
  ): Sequence<R> = getReferrersTimeMs.addMeasuredTimeMillis {
    val classId = entityClass.toClassId()

    @Suppress("UNCHECKED_CAST")
    indexes.softLinks.getIdsByEntry(id).asSequence()
      .filter { it.clazz == classId }
      .map { entityDataByIdOrDie(it).wrapAsModifiable(this) as R }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? = resolveTimeMs.addMeasuredTimeMillis {
    val entityIds = indexes.symbolicIdIndex.getIdsByEntry(id) ?: return@addMeasuredTimeMillis null
    val entityData: WorkspaceEntityData<WorkspaceEntity> = entityDataById(entityIds) as? WorkspaceEntityData<WorkspaceEntity>
                                                           ?: return@addMeasuredTimeMillis null
    return@addMeasuredTimeMillis entityData.wrapAsModifiable(this) as E?
  }

  // Do not remove cast to Class<out TypedEntity>. kotlin fails without it
  @Suppress("USELESS_CAST", "UNCHECKED_CAST")
  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean)
    : Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> = getEntitiesBySourceTimeMs.addMeasuredTimeMillis {
    indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
      indexes.entitySourceIndex
        .getIdsByEntry(source)!!.map {
          val entityDataById: WorkspaceEntityData<WorkspaceEntity> = this.entityDataById(it) as? WorkspaceEntityData<WorkspaceEntity>
                                                                     ?: run {
                                                                       error("Cannot find an entity by id $it")
                                                                     }
          entityDataById.wrapAsModifiable(this)
        }
        .groupBy { (it as WorkspaceEntityBase).getEntityInterface() }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : WorkspaceEntity> addEntity(entity: T): T = addEntityTimeMs.addMeasuredTimeMillis {
    try {
      lockWrite()
      val entityToAdd = if (entity is ModifiableWorkspaceEntityBase<*, *>) {
        entity as ModifiableWorkspaceEntityBase<T, *>
      }
      else {
        if ((entity as WorkspaceEntityBase).snapshot === this) { // We don't re-add entity from the same store
          return@addMeasuredTimeMillis entity
        }

        @Suppress("USELESS_CAST") //this is needed to work around a bug in Kotlin compiler (KT-55555)
        entity.createEntityTreeCopy(true) as ModifiableWorkspaceEntityBase<T, *>
      }

      entityToAdd.applyToBuilder(this)
      entityToAdd.changedProperty.clear()
    }
    finally {
      unlockWrite()
    }

    return@addMeasuredTimeMillis entity
  }

  // This should be removed or not extracted into the interface
  fun <T : WorkspaceEntity, E : WorkspaceEntityData<T>, D : ModifiableWorkspaceEntityBase<T, E>> putEntity(entity: D) = putEntityTimeMs.addMeasuredTimeMillis {
    try {
      lockWrite()

      val newEntityData = entity.getEntityData()

      // Check for persistent id uniqueness
      assertUniqueSymbolicId(newEntityData)

      entitiesByType.add(newEntityData, entity.getEntityClass().toClassId())

      // Add the change to changelog
      changeLog.addAddEvent(newEntityData.createEntityId(), newEntityData)

      // Update indexes
      indexes.entityAdded(newEntityData)
    }
    finally {
      unlockWrite()
    }
  }

  private fun <T : WorkspaceEntity> assertUniqueSymbolicId(pEntityData: WorkspaceEntityData<T>) {
    pEntityData.symbolicId()?.let { symbolicId ->
      val ids = indexes.symbolicIdIndex.getIdsByEntry(symbolicId)
      if (ids != null) {
        // Oh, oh. This symbolic id exists already
        // Fallback strategy: remove existing entity with all it's references
        val existingEntityData = entityDataByIdOrDie(ids)
        val existingEntity = existingEntityData.createEntity(this)
        removeEntity(existingEntity)
        LOG.error(
          """
              addEntity: symbolic id already exists. Replacing entity with the new one.
              Symbolic id: $symbolicId
              
              Existing entity data: $existingEntityData
              New entity data: $pEntityData
              
              Broken consistency: $brokenConsistency
            """.trimIndent(), SymbolicIdAlreadyExistsException(symbolicId)
        )
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <M : WorkspaceEntity.Builder<out T>, T : WorkspaceEntity> modifyEntity(
    clazz: Class<M>, e: T, change: M.() -> Unit
  ): T = modifyEntityTimeMs.addMeasuredTimeMillis {
    val updatedEntity: T = try {
      lockWrite()
      if (e is ModifiableWorkspaceEntityBase<*, *> && e.diff !== this) error("Trying to modify entity from a different builder")
      val entityId = (e as WorkspaceEntityBase).id

      val originalEntityData = this.getOriginalEntityData(entityId) as WorkspaceEntityData<T>

      // Get entity data that will be modified
      val copiedData = entitiesByType.getEntityDataForModification(entityId) as WorkspaceEntityData<T>

      val modifiableEntity = (if (e is WorkspaceEntity.Builder<*>) e else copiedData.wrapAsModifiable(this)) as M
      modifiableEntity as ModifiableWorkspaceEntityBase<*, *>
      modifiableEntity.changedProperty.clear()

      val beforeSymbolicId = if (e is WorkspaceEntityWithSymbolicId) e.symbolicId else null

      // Execute modification code
      modifiableEntity.allowModifications {
        modifiableEntity.change()
        modifiableEntity.afterModification()
      }

      // Check for persistent id uniqueness
      if (beforeSymbolicId != null) {
        val newSymbolicId = copiedData.symbolicId()
        if (newSymbolicId != null) {
          val ids = indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId)
          if (beforeSymbolicId != newSymbolicId && ids != null) {
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
            """.trimIndent(), SymbolicIdAlreadyExistsException(newSymbolicId))
          }
        }
        else {
          LOG.error("Persistent id expected for entity: $copiedData")
        }
      }

      changeLog.addReplaceDataEvent(entityId, copiedData, originalEntityData, true)
      if (modifiableEntity.changedProperty.contains("entitySource")) {
        val newSource = copiedData.entitySource
        indexes.entitySourceIndex.index(entityId, newSource)
        newSource.virtualFileUrl?.let { indexes.virtualFileIndex.index(entityId, VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY, it) }
      }

      val updatedEntity = copiedData.createEntity(this)

      this.indexes.updateSymbolicIdIndexes(this, updatedEntity, beforeSymbolicId, copiedData, modifiableEntity)

      updatedEntity
    }
    finally {
      unlockWrite()
    }

    return@addMeasuredTimeMillis updatedEntity
  }

  override fun removeEntity(e: WorkspaceEntity): Boolean = removeEntityTimeMs.addMeasuredTimeMillis {
    val result = try {
      lockWrite()
      if (e is ModifiableWorkspaceEntityBase<*, *> && e.diff !== this) error("Trying to remove entity from a different builder")

      LOG.debug { "Removing ${e.javaClass}..." }
      e as WorkspaceEntityBase
      removeEntityByEntityId(e.id)

      // NB: This method is called from `createEntity` inside persistent id checking. It's possible that after the method execution
      //  the store is in inconsistent state, so we can't call assertConsistency here.
    }
    finally {
      unlockWrite()
    }
    return@addMeasuredTimeMillis result
  }

  /**
   *  TODO Special cases: when source filter returns true for all entity sources.
   */
  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage) = replaceBySourceTimeMs.addMeasuredTimeMillis {
    try {
      lockWrite()
      replaceWith as AbstractEntityStorage
      val rbsEngine = ReplaceBySourceAsTree()
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

  override fun collectChanges(): Map<Class<*>, List<EntityChange<*>>> = collectChangesTimeMs.addMeasuredTimeMillis {
    val res = HashMap<Class<*>, MutableList<EntityChange<*>>>()

    try {
      lockWrite()

      for ((entityId, change) in this.changeLog.changeLog) {
        when (change) {
          is ChangeEntry.AddEntity -> {
            val addedEntity = change.entityData.createEntity(this).asBase()
            res.getOrPut(entityId.clazz.findWorkspaceEntity()) { ArrayList() }.add(EntityChange.Added(addedEntity))
          }
          is ChangeEntry.RemoveEntity -> {
            val removedData = originalSnapshot.entityDataById(change.id) ?: continue
            val removedEntity = removedData.createEntity(originalSnapshot).asBase()
            res.getOrPut(entityId.clazz.findWorkspaceEntity()) { ArrayList() }.add(EntityChange.Removed(removedEntity))
          }
          is ChangeEntry.ReplaceEntity -> {
            val oldData = originalSnapshot.entityDataById(entityId) ?: continue
            val replacedData = oldData.createEntity(originalSnapshot).asBase()
            val replaceToData = this.entityDataByIdOrDie(entityId).createEntity(this)
            res.getOrPut(entityId.clazz.findWorkspaceEntity()) { ArrayList() }.add(EntityChange.Replaced(replacedData, replaceToData))
          }
        }
      }
    }
    finally {
      unlockWrite()
    }

    return@addMeasuredTimeMillis res
  }

  override fun hasSameEntities(): Boolean = hasSameEntitiesTimeMs.addMeasuredTimeMillis {
    if (changeLog.changeLog.isEmpty()) return@addMeasuredTimeMillis true

    val original = originalSnapshot
    val adds = ArrayList<WorkspaceEntityData<*>>()
    val removes = CollectionFactory.createSmallMemoryFootprintMap<WorkspaceEntityData<out WorkspaceEntity>, MutableList<WorkspaceEntityData<out WorkspaceEntity>>>()
    changeLog.changeLog.forEach { (_, value) ->
      if (value is ChangeEntry.AddEntity) {
        adds.add(value.entityData)
      }
      else if (value is ChangeEntry.RemoveEntity) {
        val existingValue = removes[value.oldData]
        removes[value.oldData] = if (existingValue != null) ArrayList(existingValue + value.oldData) else mutableListOf(value.oldData)
      }
    }
    val idsToRemove = ArrayList<Pair<EntityId, EntityId>>()
    adds.forEach { addedEntityData ->
      if (removes.isEmpty()) return@forEach
      val possibleRemovedSameEntity = removes[addedEntityData]
      val newEntityId = addedEntityData.createEntityId()
      val hasMapping = this.indexes.externalMappings.any { (_, value) ->
        (value as ExternalEntityMappingImpl<*>).getDataByEntityId(newEntityId) != null
      }
      if (hasMapping) return@forEach
      val found = possibleRemovedSameEntity?.firstOrNull { possibleRemovedSame ->
        same(original, newEntityId, possibleRemovedSame.createEntityId())
      }
      if (found != null) {
        val initialEntityId = found.createEntityId()
        val hasRemovedMapping = original.indexes.externalMappings.any { (_, value) ->
          value.getDataByEntityId(initialEntityId) != null
        }
        if (hasRemovedMapping) return@forEach
        possibleRemovedSameEntity.remove(found)
        if (possibleRemovedSameEntity.isEmpty()) removes.remove(addedEntityData)
        idsToRemove += newEntityId to initialEntityId
      }
    }
    val collapsibleChanges = HashSet<EntityId>()
    idsToRemove.forEach { (new, initial) ->
      collapsibleChanges.add(new)
      collapsibleChanges.add(initial)


      this.refs.getParentRefsOfChild(new.asChild()).forEach { (connection, parent) ->
        val changedParent = changeLog.changeLog[parent.id]
        if (changedParent is ChangeEntry.ReplaceEntity) {
          if (changedParent.data?.newData == changedParent.data?.oldData
              && changedParent.references?.newParents?.isEmpty() == true
              && changedParent.references.removedParents.isEmpty()
              && changedParent.references.removedChildren.singleOrNull()?.takeIf { it.first == connection && it.second.id == initial } != null
              && changedParent.references.newChildren.singleOrNull()?.takeIf { it.first == connection && it.second.id == new } != null) {
            collapsibleChanges.add(parent.id)
          }
        }
      }
    }

    return@addMeasuredTimeMillis collapsibleChanges == changeLog.changeLog.keys
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
      }
      else false
    }
  }

  override fun toSnapshot(): EntityStorageSnapshot = toSnapshotTimeMs.addMeasuredTimeMillis {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    val cache = TracedSnapshotCacheImpl()
    val snapshot = EntityStorageSnapshotImpl(newEntities, newRefs, newIndexes, cache)
    val externalMappingChangelog = this.indexes.externalMappings.mapValues { it.value.indexLogBunches.changes.keys }
    cache.pullCache(snapshot, this.originalSnapshot.snapshotCache, this.changeLog, externalMappingChangelog)
    return@addMeasuredTimeMillis snapshot
  }

  override fun replaceChildren(connectionId: ConnectionId, parent: WorkspaceEntity, newChildren: List<WorkspaceEntity>) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        val parentId = parent.asBase().id
        check(newChildren.size <= 1) { "ONE_TO_ONE connection may have only one child" }
        val childId = newChildren.singleOrNull()?.asBase()?.id?.asChild()
        val existingChildId = refs.getChildrenByParent(connectionId, parentId.asParent()).singleOrNull()?.id
        if (!connectionId.isParentNullable && existingChildId != null && (childId == null || childId.id != existingChildId)) {
          removeEntityByEntityId(existingChildId)
        }
        if (childId != null) {
          checkCircularDependency(connectionId, childId.id.arrayId, parentId.arrayId, this)
          val modifications = refs.replaceOneToOneChildOfParent(connectionId, parentId, childId)
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
        else {
          val modifications = refs.removeRefsByParent(connectionId, parentId.asParent())
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
      }
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        val parentId = parent.asBase().id
        val childrenIds = newChildren.map { it.asBase().id.asChild() }
        if (!connectionId.isParentNullable) {
          val existingChildren = refs.getChildrenByParent(connectionId, parentId.asParent()).toMutableSet()
          childrenIds.forEach {
            existingChildren.remove(it)
          }
          existingChildren.forEach { removeEntityByEntityId(it.id) }
        }

        childrenIds.forEach { checkCircularDependency(connectionId, it.id.arrayId, parentId.arrayId, this) }
        val modifications = refs.replaceOneToManyChildrenOfParent(connectionId, parentId, childrenIds)
        this.createReplaceEventsForUpdates(modifications, connectionId)
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        // TODO Why we don't remove old children like in [EntityStorage.updateOneToManyChildrenOfParent]? IDEA-327863
        //    This is probably a bug.
        val parentId = parent.asBase().id.asParent()
        val childrenIds = newChildren.asSequence().map { it.asBase().id.asChild() }
        childrenIds.forEach { checkCircularDependency(it.id, parentId.id, this) }
        val modifications = refs.replaceOneToAbstractManyChildrenOfParent(connectionId, parentId, childrenIds)
        this.createReplaceEventsForUpdates(modifications, connectionId)
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        // TODO Why we don't remove old children like in [EntityStorage.updateOneToManyChildrenOfParent]? IDEA-327863
        //    This is probably a bug.
        val parentId = parent.asBase().id.asParent()
        check(newChildren.size <= 1) { "ABSTRACT_ONE_TO_ONE connection may have only one child" }
        val childId = newChildren.singleOrNull()?.asBase()?.id?.asChild()
        if (childId != null) {
          val modifications = refs.replaceOneToAbstractOneChildOfParent(connectionId, parentId, childId)
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
        else {
          val operation = refs.removeRefsByParent(connectionId, parentId)
          this.createReplaceEventsForUpdates(operation, connectionId)
        }
      }
    }
  }

  override fun addChild(connectionId: ConnectionId, parent: WorkspaceEntity?, child: WorkspaceEntity) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        val parentId = parent?.asBase()?.id?.asParent()
        val childId = child.asBase().id
        if (!connectionId.isParentNullable && parentId != null) {
          // If we replace a field in one-to-one connection, the previous entity is automatically removed.
          val existingChild = getOneChild(connectionId, parent)
          if (existingChild != null && existingChild != child) {
            removeEntity(existingChild)
          }
        }
        if (parentId != null) {
          checkCircularDependency(connectionId, childId.arrayId, parentId.id.arrayId, this)
          val modifications = refs.replaceOneToOneParentOfChild(connectionId, childId, parentId.id)
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
        else {
          // TODO: Why don't we check if the reference to parent is not null? See IDEA-327863
          val modifications = refs.removeOneToOneRefByChild(connectionId, childId)
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
      }
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        val childId = child.asBase().id.asChild()
        val parentId = parent?.asBase()?.id?.asParent()
        if (parentId != null) {
          checkCircularDependency(connectionId, childId.id.arrayId, parentId.id.arrayId, this)
          val modifications = refs.replaceOneToManyParentOfChild(connectionId, childId.id, parentId)
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
        else {
          // TODO: Why don't we check if the reference to parent is not null? See IDEA-327863
          val modification = refs.removeOneToManyRefsByChild(connectionId, childId)
          if (modification != null) this.createReplaceEventsForUpdates(listOf(modification), connectionId)
        }
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val childId = child.asBase().id.asChild()
        val parentId = parent?.asBase()?.id?.asParent()
        if (parentId != null) {
          checkCircularDependency(childId.id, parentId.id, this)
          val modifications = refs.replaceOneToAbstractManyParentOfChild(connectionId, childId, parentId)
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
        else {
          // TODO: Why don't we check if the reference to parent is not null? See IDEA-327863
          val modification = refs.removeOneToAbstractManyRefsByChild(connectionId, childId)
          if (modification != null) this.createReplaceEventsForUpdates(listOf(modification), connectionId)
        }
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val parentId = parent?.asBase()?.id?.asParent()
        val childId = child.asBase().id.asChild()
        if (!connectionId.isParentNullable && parentId != null) {
          // If we replace a field in one-to-one connection, the previous entity is automatically removed.
          val existingChild = getOneChild(connectionId, parent)
          if (existingChild != null && existingChild != child) {
            removeEntity(existingChild)
          }
        }
        if (parentId != null) {
          val modifications = refs.replaceOneToAbstractOneParentOfChild(connectionId, childId, parentId)
          this.createReplaceEventsForUpdates(modifications, connectionId)
        }
        else {
          // TODO: Why don't we check if the reference to parent is not null? See IDEA-327863
          val modification = refs.removeOneToAbstractOneRefByChild(connectionId, childId)
          if (modification != null) this.createReplaceEventsForUpdates(listOf(modification), connectionId)
        }
      }
    }
  }

  override fun hasChanges(): Boolean = changeLog.changeLog.isNotEmpty()

  override fun addDiff(diff: MutableEntityStorage) = addDiffTimeMs.addMeasuredTimeMillis {
    try {
      lockWrite()
      diff as MutableEntityStorageImpl
      applyDiffProtection(diff)
      val addDiffOperation = AddDiffOperation(this, diff)
      upgradeAddDiffEngine?.invoke(addDiffOperation)
      addDiffOperation.addDiff()
    }
    finally {
      unlockWrite()
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getMutableExternalMapping(identifier: @NonNls String)
    : MutableExternalEntityMapping<T> = getMutableExternalMappingTimeMs.addMeasuredTimeMillis {
    try {
      lockWrite()
      val mapping = indexes.externalMappings.computeIfAbsent(
        identifier) { MutableExternalEntityMappingImpl<T>() } as MutableExternalEntityMappingImpl<T>
      mapping.setTypedEntityStorage(this)
      mapping
    }
    finally {
      unlockWrite()
    }
  }

  fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex = getMutableVFUrlIndexTimeMs.addMeasuredTimeMillis {
    try {
      lockWrite()
      val virtualFileIndex = indexes.virtualFileIndex
      virtualFileIndex.setTypedEntityStorage(this)
      virtualFileIndex
    }
    finally {
      unlockWrite()
    }
  }

  private fun applyDiffProtection(diff: AbstractEntityStorage) {
    LOG.trace { "Applying addDiff. Builder: $diff" }
    if (diff.storageIsAlreadyApplied) {
      LOG.error("Builder is already applied.\n Info: \n${diff.applyInfo}")
    }
    else {
      diff.storageIsAlreadyApplied = true
      if (LOG.isTraceEnabled) {
        diff.applyInfo = buildString {
          appendLine("Applying builder using addDiff. Previous stack trace >>>>")
          appendLine(ExceptionUtil.currentStackTrace())
          appendLine("<<<<")
        }
      }
    }
  }

  // modificationCount is not incremented
  internal fun removeEntityByEntityId(idx: EntityId, entityFilter: (EntityId) -> Boolean = { true }): Boolean {
    val accumulator: MutableSet<EntityId> = mutableSetOf(idx)

    if (!entitiesByType.exists(idx)) {
      return false
    }

    accumulateEntitiesToRemove(idx, accumulator, entityFilter)

    accumulator.forEach { id ->
      removeSingleEntity(id)
    }

    return true
  }

  /**
   * Remove single entity, update indexes, and generate remove event
   *
   * This operation does not perform cascade removal of children. The children must be removed separately,
   *   to avoid leaving storage in an inconsistent state.
   */
  @Suppress("UNCHECKED_CAST")
  internal fun removeSingleEntity(id: EntityId) {
    // Unlink children
    val originalEntityData = this.getOriginalEntityData(id) as WorkspaceEntityData<WorkspaceEntity>
    val children = refs.getChildrenRefsOfParentBy(id.asParent())
    children.keys.forEach { connectionId ->
      val modifications = refs.removeRefsByParent(connectionId, id.asParent())
      this.createReplaceEventsForUpdates(modifications, connectionId)
    }

    // Unlink parents
    val parents = refs.getParentRefsOfChild(id.asChild())
    parents.forEach { (connectionId, parentId) ->
      val modifications = refs.removeParentToChildRef(connectionId, parentId, id.asChild())
      this.createReplaceEventsForUpdates(modifications, connectionId)
    }

    // Update indexes and generate changelog entry
    val entityData = entityDataByIdOrDie(id)
    if (entityData is SoftLinkable) indexes.removeFromSoftLinksIndex(entityData)
    indexes.entityRemoved(id)
    this.changeLog.addRemoveEvent(id, originalEntityData)

    entitiesByType.remove(id.arrayId, id.clazz)
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

  /**
   * Accumulate hard linked entities in [accumulator].
   *
   * The builder is not modified
   */
  private fun accumulateEntitiesToRemove(id: EntityId,
                                         accumulator: MutableSet<EntityId>,
                                         entityFilter: (EntityId) -> Boolean) {
    val children = refs.getChildrenRefsOfParentBy(id.asParent())
    for ((_, childrenIds) in children) {
      for (childId in childrenIds) {
        if (childId.id in accumulator) continue
        if (!entityFilter(childId.id)) continue
        // Let's keep the recursive call above adding an entity to the accumulator.
        //   In this way, the acc will be populated from children to parents direction, and we'll be able to easily remove
        //   entities by iterating from the start.
        accumulateEntitiesToRemove(childId.id, accumulator, entityFilter)
        accumulator.add(childId.id)
      }
    }
  }

  companion object {

    private val LOG = logger<MutableEntityStorageImpl>()

    private val instancesCounter: AtomicLong = AtomicLong()
    private val getEntitiesTimeMs: AtomicLong = AtomicLong()
    private val getReferrersTimeMs: AtomicLong = AtomicLong()
    private val resolveTimeMs: AtomicLong = AtomicLong()
    private val getEntitiesBySourceTimeMs: AtomicLong = AtomicLong()
    private val addEntityTimeMs: AtomicLong = AtomicLong()
    private val putEntityTimeMs: AtomicLong = AtomicLong()
    private val modifyEntityTimeMs: AtomicLong = AtomicLong()
    private val removeEntityTimeMs: AtomicLong = AtomicLong()
    private val replaceBySourceTimeMs: AtomicLong = AtomicLong()
    private val collectChangesTimeMs: AtomicLong = AtomicLong()
    private val hasSameEntitiesTimeMs: AtomicLong = AtomicLong()
    private val toSnapshotTimeMs: AtomicLong = AtomicLong()
    private val addDiffTimeMs: AtomicLong = AtomicLong()
    private val getMutableExternalMappingTimeMs: AtomicLong = AtomicLong()
    private val getMutableVFUrlIndexTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val instancesCountCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.instances.count").buildObserver()
      val getEntitiesTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.entities.ms").buildObserver()
      val getReferrersTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.referrers.ms").buildObserver()
      val resolveTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.resolve.ms").buildObserver()
      val getEntitiesBySourceTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.entities.by.source.ms").buildObserver()
      val addEntityTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.add.entity.ms").buildObserver()
      val putEntityTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.put.entity.ms").buildObserver()
      val modifyEntityTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.modify.entity.ms").buildObserver()
      val removeEntityTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.remove.entity.ms").buildObserver()
      val replaceBySourceTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.replace.by.source.ms").buildObserver()
      val collectChangesTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.collect.changes.ms").buildObserver()
      val hasSameEntitiesTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.has.same.entities.ms").buildObserver()
      val toSnapshotTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.to.snapshot.ms").buildObserver()
      val addDiffTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.add.diff.ms").buildObserver()
      val getMutableExternalMappingTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.mutable.ext.mapping.ms").buildObserver()
      val getMutableVFUrlIndexTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.mutable.vfurl.index.ms").buildObserver()

      meter.batchCallback(
        {
          instancesCountCounter.record(instancesCounter.get())
          getEntitiesTimeCounter.record(getEntitiesTimeMs.get())
          getReferrersTimeCounter.record(getReferrersTimeMs.get())
          resolveTimeCounter.record(resolveTimeMs.get())
          getEntitiesBySourceTimeCounter.record(getEntitiesBySourceTimeMs.get())
          addEntityTimeCounter.record(addEntityTimeMs.get())
          putEntityTimeCounter.record(putEntityTimeMs.get())
          modifyEntityTimeCounter.record(modifyEntityTimeMs.get())
          removeEntityTimeCounter.record(removeEntityTimeMs.get())
          replaceBySourceTimeCounter.record(replaceBySourceTimeMs.get())
          collectChangesTimeCounter.record(collectChangesTimeMs.get())
          hasSameEntitiesTimeCounter.record(hasSameEntitiesTimeMs.get())
          toSnapshotTimeCounter.record(toSnapshotTimeMs.get())
          addDiffTimeCounter.record(addDiffTimeMs.get())
          getMutableExternalMappingTimeCounter.record(getMutableExternalMappingTimeMs.get())
          getMutableVFUrlIndexTimeCounter.record(getMutableVFUrlIndexTimeMs.get())
        },
        instancesCountCounter, getEntitiesTimeCounter, getReferrersTimeCounter, resolveTimeCounter,
        getEntitiesBySourceTimeCounter, addEntityTimeCounter,
        putEntityTimeCounter, modifyEntityTimeCounter, removeEntityTimeCounter, replaceBySourceTimeCounter,
        collectChangesTimeCounter, hasSameEntitiesTimeCounter, toSnapshotTimeCounter, addDiffTimeCounter,
        getMutableExternalMappingTimeCounter, getMutableVFUrlIndexTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(JPS))
    }
  }
}

@OptIn(EntityStorageInstrumentationApi::class)
internal sealed class AbstractEntityStorage : EntityStorageInstrumentation {

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
    return entityFamily.getOrFail(id.arrayId) ?: error("Cannot find an entity by id $id")
  }

  override fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(id: SymbolicEntityId<E>,
                                                                                  entityClass: Class<R>): Sequence<R> {
    val classId = entityClass.toClassId()

    @Suppress("UNCHECKED_CAST")
    return indexes.softLinks.getIdsByEntry(id).asSequence()
      .filter { it.clazz == classId }
      .map { entityDataByIdOrDie(it).createEntity(this) as R }
  }

  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val entityIds = indexes.symbolicIdIndex.getIdsByEntry(id) ?: return null
    @Suppress("UNCHECKED_CAST")
    return entityDataById(entityIds)?.createEntity(this) as E?
  }

  override operator fun <E : WorkspaceEntityWithSymbolicId> contains(id: SymbolicEntityId<E>): Boolean {
    return indexes.symbolicIdIndex.getIdsByEntry(id) != null
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    return indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
      indexes.entitySourceIndex
        .getIdsByEntry(source)!!.map {
          this.entityDataById(it)?.createEntity(this) ?: run {
            error("Cannot find an entity by id $it")
          }
        }
        .groupBy { (it as WorkspaceEntityBase).getEntityInterface() }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExternalMapping(identifier: @NonNls String): ExternalEntityMapping<T> {
    val index = indexes.externalMappings[identifier] as? ExternalEntityMappingImpl<T>
    if (index == null) return EmptyExternalEntityMapping as ExternalEntityMapping<T>
    index.setTypedEntityStorage(this)
    return index
  }

  override fun getVirtualFileUrlIndex(): VirtualFileUrlIndex {
    indexes.virtualFileIndex.setTypedEntityStorage(this)
    return indexes.virtualFileIndex
  }

  override fun <T: WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T = newInstance()

  internal fun assertConsistencyInStrictMode(message: String) {
    if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED && !ConsistencyCheckingDisabler.isDisabled()) {
      try {
        this.assertConsistency()
      }
      catch (e: Throwable) {
        brokenConsistency = true
        LOG.error(message, e)
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : WorkspaceEntity> resolveReference(reference: EntityReference<T>): T? {
    reference as EntityReferenceImpl<T>
    return this.entityDataById(reference.id)?.createEntity(this) as? T
  }

  override fun getOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): WorkspaceEntity? {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        return refs.getOneToOneChild(connectionId, parent.asBase().id.arrayId) {
          entityDataByIdOrDie(createEntityId(it, connectionId.childClass)).createEntity(this)
        }
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val parentId = parent.asBase().id
        val childId = refs.getChildrenByParent(connectionId, parentId.asParent()).singleOrNull()?.id ?: return null
        return entityDataByIdOrDie(childId).createEntity(this)
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, ConnectionId.ConnectionType.ONE_TO_MANY -> {
        error("This function works only with one-to-one connections")
      }
    }
  }

  override fun getManyChildren(connectionId: ConnectionId, parent: WorkspaceEntity): Sequence<WorkspaceEntity> {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        val parentId = parent.asBase().id
        return refs.getChildrenByParent(connectionId, parentId.asParent())
          .asSequence()
          .map { entityDataByIdOrDie(it.id) }
          .map { it.createEntity(this) }
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        return refs.getChildrenByParent(connectionId, parent.asBase().id.asParent())
                 .asSequence()
                 .map { entityDataByIdOrDie(it.id) }
                 .map { it.createEntity(this) }
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, ConnectionId.ConnectionType.ONE_TO_ONE -> {
        error("This function works only with one-to-many connections")
      }
    }
  }

  override fun getParent(connectionId: ConnectionId, child: WorkspaceEntity): WorkspaceEntity? {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        return refs.getOneToOneParent(connectionId, child.asBase().id.arrayId) {
          val entityId = createEntityId(it, connectionId.parentClass)
          entityDataByIdOrDie(entityId).createEntity(this)
        }
      }
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        return refs.getOneToManyParent(connectionId, child.asBase().id.arrayId) {
          val entityId = createEntityId(it, connectionId.parentClass)
          entityDataByIdOrDie(entityId).createEntity(this)
        }
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        return refs.getOneToAbstractManyParent(connectionId, child.asBase().id.asChild())
          ?.let { entityDataByIdOrDie(it.id).createEntity(this) }
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        return refs.getOneToAbstractOneParent(connectionId, child.asBase().id.asChild())
          ?.let { entityDataByIdOrDie(it.id).createEntity(this) }
      }
    }
  }

  companion object {
    val LOG = logger<AbstractEntityStorage>()
  }
}

/** This function exposes `brokenConsistency` property to the outside and should be removed along with the property itself */
public val EntityStorage.isConsistent: Boolean
  get() = !(this as AbstractEntityStorage).brokenConsistency
