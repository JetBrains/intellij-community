// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.JPS
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.cache.ChangeOnWorkspaceBuilderChangeLog
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCacheImpl
import com.intellij.platform.workspace.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import com.intellij.platform.workspace.storage.impl.external.AbstractExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.external.EmptyExternalEntityMapping
import com.intellij.platform.workspace.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex.MutableVirtualFileIndex.Companion.VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.url.MutableVirtualFileUrlIndex
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.Java11Shim
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ConcurrentLongObjectMap
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class EntityPointerImpl<E : WorkspaceEntity>(internal val id: EntityId) : EntityPointer<E> {
  override fun resolve(storage: EntityStorage): E? {
    storage as EntityStorageInstrumentation
    return storage.resolveReference(this)
  }

  override fun isPointerTo(entity: E): Boolean {
    return id == (entity as? WorkspaceEntityBase)?.id
  }

  override fun equals(other: Any?): Boolean {
    return id == (other as? EntityPointerImpl<*>)?.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}

// companion object in EntityStorageSnapshotImpl is initialized too late
private val entityStorageSnapshotImplInstancesCounter: AtomicLong = AtomicLong()

internal open class ImmutableEntityStorageImpl(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: ImmutableStorageIndexes,
  internal val snapshotCache: TracedSnapshotCache = TracedSnapshotCacheImpl(),
) : ImmutableEntityStorageInstrumentation, AbstractEntityStorage() {

  init {
    entityStorageSnapshotImplInstancesCounter.incrementAndGet()
  }

  // This cache should not be transferred to other versions of storage
  private val symbolicIdCache = ConcurrentHashMap<SymbolicEntityId<*>, WorkspaceEntity>()

  // I suppose that we can use some kind of array of arrays to get a quicker access (just two accesses by-index)
  // However, it's not implemented currently because I'm not sure about threading.
  private val entityCache: ConcurrentLongObjectMap<WorkspaceEntity> = Java11Shim.createConcurrentLongObjectMap()

  override fun <T> cached(query: StorageQuery<T>): T {
    return snapshotCache.cached(query, this, null).value
  }

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val entity = symbolicIdCache.getOrPut(id) { super.resolve(id) ?: NULL_ENTITY }
    return if (entity !== NULL_ENTITY) entity as E else null
  }

  override fun <T : WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T {
    val found = entityCache[entityId]

    if (found != null) {
      @Suppress("UNCHECKED_CAST")
      return found as T
    }

    val newData = newInstance()
    val inserted = entityCache.putIfAbsent(entityId, newData) ?: newData

    @Suppress("UNCHECKED_CAST")
    return inserted as T
  }

  companion object {
    private val NULL_ENTITY = ObjectUtils.sentinel("null entity", WorkspaceEntity::class.java)
    val EMPTY = ImmutableEntityStorageImpl(ImmutableEntitiesBarrel.EMPTY, RefsTable(), ImmutableStorageIndexes.EMPTY)

    private fun setupOpenTelemetryReporting(meter: Meter) {
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

internal class MutableEntityStorageImpl(
  private val originalSnapshot: ImmutableEntityStorageImpl,
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
   * This change log affects [applyChangesFrom] operation and [collectChanges] return result
   *
   * There is no particular reason not to store the list of "state" changes here. However, this will require a lot of work in order
   *   to record all changes on the storage and update [applyChangesFrom] method to work in the new way.
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
  internal var upgradeApplyChangesFromEngine: ((ApplyChangesFromOperation) -> Unit)? = null

  // --------------- Replace By Source stuff -----------

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> = getEntitiesTimeMs.addMeasuredTime {
    @Suppress("UNCHECKED_CAST")
    entitiesByType[entityClass.toClassId()]?.all()?.map { it.createEntity(this) } as? Sequence<E> ?: emptySequence()
  }

  override fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(
    id: SymbolicEntityId<E>,
    entityClass: Class<R>
  ): Sequence<R> = getReferrersTimeMs.addMeasuredTime {
    val classId = entityClass.toClassId()

    @Suppress("UNCHECKED_CAST")
    indexes.softLinks.getIdsByEntry(id).asSequence()
      .filter { it.clazz == classId }
      .map { entityDataByIdOrDie(it).createEntity(this) as R }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? = resolveTimeMs.addMeasuredTime {
    val entityIds = indexes.symbolicIdIndex.getIdsByEntry(id) ?: return@addMeasuredTime null
    val entityData: WorkspaceEntityData<WorkspaceEntity> = entityDataById(entityIds) as? WorkspaceEntityData<WorkspaceEntity>
                                                           ?: return@addMeasuredTime null
    return@addMeasuredTime entityData.createEntity(this) as E?
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Sequence<WorkspaceEntity> {
    return getEntitiesBySourceTimeMs.addMeasuredTime {
      val index = indexes.entitySourceIndex
      index.entries()
        .filter(sourceFilter)
        // Do not put conversion to sequence before filter. Otherwise, you'll get a ConcurrentModificationException what some code
        //   will iterate over the sequence and update the builder. The immutable entity storage doesn't have this problem as it's unmodifiable
        .asSequence()
        .flatMap { source ->
          val entityIds = index.getIdsByEntry(source) ?: error("Entity source $source expected to be in the index")
          entityIds.asSequence().map { this.entityDataByIdOrDie(it).createEntity(this) }
        }
    }
  }

  override fun <M : WorkspaceEntity.Builder<T>, T : WorkspaceEntity> addEntity(entity: M): T = addEntityTimeMs.addMeasuredTime {
    try {
      startWriting()
      val entityToAdd = entity as ModifiableWorkspaceEntityBase<*, *>

      entityToAdd.applyToBuilder(this)
      entityToAdd.changedProperty.clear()

      @Suppress("UNCHECKED_CAST")
      return@addMeasuredTime this.entityDataByIdOrDie(entityToAdd.id).createEntity(this) as T
    }
    finally {
      finishWriting()
    }
  }

  // This should be removed or not extracted into the interface
  fun <T : WorkspaceEntity, E : WorkspaceEntityData<T>, D : ModifiableWorkspaceEntityBase<T, E>> putEntity(entity: D) = putEntityTimeMs.addMeasuredTime {
    try {
      startWriting()

      val newEntityData = entity.getEntityData()
      val immutableEntity = newEntityData.createEntity(this)
      val symbolicId = (immutableEntity as? WorkspaceEntityWithSymbolicId)?.symbolicId

      // Check for persistent id uniqueness
      assertUniqueSymbolicId(newEntityData, symbolicId)

      entitiesByType.add(newEntityData, entity.getEntityClass().toClassId())

      // Add the change to changelog
      changeLog.addAddEvent(newEntityData.createEntityId(), newEntityData)

      // Update indexes
      indexes.entityAdded(newEntityData, symbolicId)
    }
    finally {
      finishWriting()
    }
  }

  private fun <T : WorkspaceEntity> assertUniqueSymbolicId(pEntityData: WorkspaceEntityData<T>, symbolicId: SymbolicEntityId<*>?) {
    if (symbolicId == null) return
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

  @Suppress("UNCHECKED_CAST")
  override fun <M : WorkspaceEntity.Builder<out T>, T : WorkspaceEntity> modifyEntity(
    clazz: Class<M>, e: T, change: M.() -> Unit
  ): T = modifyEntityTimeMs.addMeasuredTime {
    val updatedEntity: T = try {
      startWriting()
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
        val immutableEntity = modifiableEntity.getEntityData().createEntity(this)
        val newSymbolicId = if (immutableEntity is WorkspaceEntityWithSymbolicId) immutableEntity.symbolicId else null
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

      if (modifiableEntity.changedProperty.isNotEmpty()) {
        this.indexes.updateSymbolicIdIndexes(this, updatedEntity, beforeSymbolicId, copiedData, modifiableEntity)
      }

      updatedEntity
    }
    finally {
      finishWriting()
    }

    return@addMeasuredTime updatedEntity
  }

  override fun removeEntity(e: WorkspaceEntity): Boolean = removeEntityTimeMs.addMeasuredTime {
    val result = try {
      startWriting()
      if (e is ModifiableWorkspaceEntityBase<*, *> && e.diff !== this) error("Trying to remove entity from a different builder")

      LOG.debug { "Removing ${e.javaClass}..." }
      e as WorkspaceEntityBase
      removeEntityByEntityId(e.id)

      // NB: This method is called from `createEntity` inside persistent id checking. It's possible that after the method execution
      //  the store is in inconsistent state, so we can't call assertConsistency here.
    }
    finally {
      finishWriting()
    }
    return@addMeasuredTime result
  }

  /**
   *  TODO Special cases: when source filter returns true for all entity sources.
   */
  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage) = replaceBySourceTimeMs.addMeasuredTime {
    try {
      startWriting()
      replaceWith as AbstractEntityStorage
      val rbsEngine = ReplaceBySourceAsTree()
      if (keepLastRbsEngine) {
        engine = rbsEngine
      }
      upgradeEngine?.let { it(rbsEngine) }
      rbsEngine.replace(this, replaceWith, sourceFilter)
    }
    finally {
      finishWriting()
    }
  }

  override fun collectChanges(): Map<Class<*>, List<EntityChange<*>>> = collectChangesTimeMs.addMeasuredTime {
    if (changeLog.changeLog.isEmpty()) {
      return@addMeasuredTime emptyMap()
    }

    // We keep the Removed-Replaced-Added ordering of the events
    //
    // This implemented by adding Removed events at the start, Added events at the end, and Replaced before the Added events.
    // To know when the Added events are located, we keep track of the index of the first Added event.
    val firstAddedIndex: HashMap<Class<*>, Int> = hashMapOf()

    try {
      startWriting()

      val result = HashMap<Class<*>, MutableList<EntityChange<*>>>()
      for ((entityId, change) in changeLog.changeLog) {
        when (change) {
          is ChangeEntry.AddEntity -> {
            val addedEntity = change.entityData.createEntity(this).asBase()
            val entityInterface = entityId.clazz.findWorkspaceEntity()
            result.computeIfAbsent(entityInterface) { ArrayList() }.apply {
              this.add(EntityChange.Added(addedEntity))
              if (entityInterface !in firstAddedIndex) firstAddedIndex[entityInterface] = this.size - 1
            }
          }
          is ChangeEntry.RemoveEntity -> {
            val removedData = originalSnapshot.entityDataById(change.id) ?: continue
            val removedEntity = removedData.createEntity(originalSnapshot).asBase()
            val entityInterface = entityId.clazz.findWorkspaceEntity()
            result.computeIfAbsent(entityInterface) { ArrayList() }.apply {
              this.add(0, EntityChange.Removed(removedEntity)) // Add Removed at the start of the list
              if (entityInterface in firstAddedIndex) firstAddedIndex[entityInterface] = firstAddedIndex.getValue(entityInterface) + 1
            }
          }
          is ChangeEntry.ReplaceEntity -> {
            val oldData = originalSnapshot.entityDataById(entityId) ?: continue
            val replacedData = oldData.createEntity(originalSnapshot).asBase()
            val replaceToData = this.entityDataByIdOrDie(entityId).createEntity(this)
            val entityInterface = entityId.clazz.findWorkspaceEntity()
            result.computeIfAbsent(entityInterface) { ArrayList() }.apply {
              add(firstAddedIndex.getOrDefault(entityInterface, size), EntityChange.Replaced(replacedData, replaceToData))
              if (entityInterface in firstAddedIndex) firstAddedIndex[entityInterface] = firstAddedIndex.getValue(entityInterface) + 1
            }
          }
        }
      }
      result
    }
    finally {
      finishWriting()
    }
  }

  override fun hasSameEntities(): Boolean = hasSameEntitiesTimeMs.addMeasuredTime {
    if (changeLog.changeLog.isEmpty()) return@addMeasuredTime true

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
        value.getDataByEntityId(newEntityId) != null
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

    return@addMeasuredTime collapsibleChanges == changeLog.changeLog.keys
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

  override fun toSnapshot(): ImmutableEntityStorage = toSnapshotTimeMs.addMeasuredTime {
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    val cache = TracedSnapshotCacheImpl()
    val snapshot = ImmutableEntityStorageImpl(newEntities, newRefs, newIndexes, cache)
    val externalMappingChangelog = this.indexes.externalMappings.mapValues { it.value.indexLogBunches.changes.keys }
    val changes = ChangeOnWorkspaceBuilderChangeLog(this.changeLog, externalMappingChangelog)
    cache.pullCache(snapshot, this.originalSnapshot.snapshotCache, changes)
    return@addMeasuredTime snapshot
  }

  override fun replaceChildren(
    connectionId: ConnectionId,
    parent: WorkspaceEntity.Builder<out WorkspaceEntity>,
    newChildren: List<WorkspaceEntity.Builder<out WorkspaceEntity>>
  ) {
    try {
      startWriting()
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
          val childrenIds = newChildren.map { it.asBase().id.asChild() }
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
    finally {
      finishWriting()
    }
  }

  override fun addChild(connectionId: ConnectionId, parent: WorkspaceEntity.Builder<out WorkspaceEntity>?, child: WorkspaceEntity.Builder<out WorkspaceEntity>) {
    try {
      startWriting()
      when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_ONE -> {
          val parentId = parent?.asBase()?.id?.asParent()
          val childId = child.asBase().id
          if (!connectionId.isParentNullable && parentId != null) {
            // If we replace a field in one-to-one connection, the previous entity is automatically removed.
            val existingChild = getOneChildData(connectionId, parent.asBase().id)
            if (existingChild != null && existingChild.createEntityId() != child.asBase().id) {
              removeEntityByEntityId(existingChild.createEntityId())
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
            val existingChild = getOneChildData(connectionId, parent.asBase().id)
            if (existingChild != null && existingChild.createEntityId() != child.asBase().id) {
              removeEntityByEntityId(existingChild.createEntityId())
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
    finally {
      finishWriting()
    }
  }

  override fun getOneChildBuilder(connectionId: ConnectionId, parent: WorkspaceEntity.Builder<*>): WorkspaceEntity.Builder<*>? {
    return getOneChildData(connectionId, (parent as ModifiableWorkspaceEntityBase<*, *>).id)?.wrapAsModifiable(this)
  }

  override fun getManyChildrenBuilders(connectionId: ConnectionId, parent: WorkspaceEntity.Builder<*>): Sequence<WorkspaceEntity.Builder<*>> {
    return getManyChildrenData(connectionId, (parent as ModifiableWorkspaceEntityBase<*, *>).id).map { it.wrapAsModifiable(this) }
  }

  override fun getParentBuilder(connectionId: ConnectionId, child: WorkspaceEntity.Builder<*>): WorkspaceEntity.Builder<*>? {
    return getParentData(connectionId, (child as ModifiableWorkspaceEntityBase<*, *>).id)?.wrapAsModifiable(this)
  }

  override fun hasChanges(): Boolean = changeLog.changeLog.isNotEmpty()

  override fun applyChangesFrom(builder: MutableEntityStorage) = applyChangesFromTimeMs.addMeasuredTime {
    try {
      startWriting()
      builder as MutableEntityStorageImpl
      applyChangesFromProtection(builder)
      val applyChangesFromOperation = ApplyChangesFromOperation(this, builder)
      upgradeApplyChangesFromEngine?.invoke(applyChangesFromOperation)
      applyChangesFromOperation.applyChangesFrom()
    }
    finally {
      finishWriting()
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getMutableExternalMapping(identifier: ExternalMappingKey<T>): MutableExternalEntityMapping<T> {
    return getMutableExternalMappingTimeMs.addMeasuredTime {
      try {
        startWriting()
        val mapping = indexes.externalMappings
          .computeIfAbsent(identifier) { MutableExternalEntityMappingImpl<T>() } as MutableExternalEntityMappingImpl<T>
        mapping.setTypedEntityStorage(this)
        mapping
      }
      finally {
        finishWriting()
      }
    }
  }

  fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex = getMutableVFUrlIndexTimeMs.addMeasuredTime {
    try {
      startWriting()
      val virtualFileIndex = indexes.virtualFileIndex
      virtualFileIndex.setTypedEntityStorage(this)
      virtualFileIndex
    }
    finally {
      finishWriting()
    }
  }

  private fun applyChangesFromProtection(builder: AbstractEntityStorage) {
    LOG.trace { "Applying applyChangesFrom. Builder: $builder" }
    if (builder.storageIsAlreadyApplied) {
      LOG.error("Builder is already applied.\n Info: \n${builder.applyInfo}")
    }
    else {
      builder.storageIsAlreadyApplied = true
      if (LOG.isTraceEnabled) {
        builder.applyInfo = buildString {
          appendLine("Applying builder using applyChangesFrom. Previous stack trace >>>>")
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
      removeSingleEntity(
        id,
        updateChangelogForChildren = false, // We should not register "change in references" for the chilren because they'll be removed anyway
        updateChangelogForParents = id == idx, // Same for parents of removed children, except the root entity.
      )
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
  internal fun removeSingleEntity(
    id: EntityId,
    updateChangelogForChildren: Boolean,
    updateChangelogForParents: Boolean,
  ) {
    // Unlink children
    val originalEntityData = this.getOriginalEntityData(id) as WorkspaceEntityData<WorkspaceEntity>
    val children = refs.getChildrenRefsOfParentBy(id.asParent())
    children.keys.forEach { connectionId ->
      val modifications = refs.removeRefsByParent(connectionId, id.asParent())
      LOG.trace { "Perform modifications on children refs for $connectionId: $modifications" }
      if (updateChangelogForChildren) {
        this.createReplaceEventsForUpdates(modifications, connectionId)
      }
    }

    // Unlink parents
    val parents = refs.getParentRefsOfChild(id.asChild())
    parents.forEach { (connectionId, parentId) ->
      val modifications = refs.removeParentToChildRef(connectionId, parentId, id.asChild())
      LOG.trace { "Perform modifications on parent refs for $connectionId: $modifications" }
      if (updateChangelogForParents) {
        this.createReplaceEventsForUpdates(modifications, connectionId)
      }
    }

    // Update indexes and generate changelog entry
    val entityData = entityDataByIdOrDie(id)
    if (entityData is SoftLinkable) indexes.removeFromSoftLinksIndex(entityData)
    indexes.entityRemoved(id)
    this.changeLog.addRemoveEvent(id, originalEntityData)

    entitiesByType.remove(id.arrayId, id.clazz)
  }

  private fun startWriting() {
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

  private fun finishWriting() {
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
    private val getEntitiesTimeMs = MillisecondsMeasurer()
    private val getReferrersTimeMs = MillisecondsMeasurer()
    private val resolveTimeMs = MillisecondsMeasurer()
    private val getEntitiesBySourceTimeMs = MillisecondsMeasurer()
    private val addEntityTimeMs = MillisecondsMeasurer()
    private val putEntityTimeMs = MillisecondsMeasurer()
    private val modifyEntityTimeMs = MillisecondsMeasurer()
    private val removeEntityTimeMs = MillisecondsMeasurer()
    private val replaceBySourceTimeMs = MillisecondsMeasurer()
    private val collectChangesTimeMs = MillisecondsMeasurer()
    private val hasSameEntitiesTimeMs = MillisecondsMeasurer()
    private val toSnapshotTimeMs = MillisecondsMeasurer()
    private val applyChangesFromTimeMs = MillisecondsMeasurer()
    private val getMutableExternalMappingTimeMs = MillisecondsMeasurer()
    private val getMutableVFUrlIndexTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
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
      val applyChangesFromTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.apply.changes.from.ms").buildObserver()
      val getMutableExternalMappingTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.mutable.ext.mapping.ms").buildObserver()
      val getMutableVFUrlIndexTimeCounter = meter.counterBuilder("workspaceModel.mutableEntityStorage.mutable.vfurl.index.ms").buildObserver()

      meter.batchCallback(
        {
          instancesCountCounter.record(instancesCounter.get())
          getEntitiesTimeCounter.record(getEntitiesTimeMs.asMilliseconds())
          getReferrersTimeCounter.record(getReferrersTimeMs.asMilliseconds())
          resolveTimeCounter.record(resolveTimeMs.asMilliseconds())
          getEntitiesBySourceTimeCounter.record(getEntitiesBySourceTimeMs.asMilliseconds())
          addEntityTimeCounter.record(addEntityTimeMs.asMilliseconds())
          putEntityTimeCounter.record(putEntityTimeMs.asMilliseconds())
          modifyEntityTimeCounter.record(modifyEntityTimeMs.asMilliseconds())
          removeEntityTimeCounter.record(removeEntityTimeMs.asMilliseconds())
          replaceBySourceTimeCounter.record(replaceBySourceTimeMs.asMilliseconds())
          collectChangesTimeCounter.record(collectChangesTimeMs.asMilliseconds())
          hasSameEntitiesTimeCounter.record(hasSameEntitiesTimeMs.asMilliseconds())
          toSnapshotTimeCounter.record(toSnapshotTimeMs.asMilliseconds())
          applyChangesFromTimeCounter.record(applyChangesFromTimeMs.asMilliseconds())
          getMutableExternalMappingTimeCounter.record(getMutableExternalMappingTimeMs.asMilliseconds())
          getMutableVFUrlIndexTimeCounter.record(getMutableVFUrlIndexTimeMs.asMilliseconds())
        },
        instancesCountCounter, getEntitiesTimeCounter, getReferrersTimeCounter, resolveTimeCounter,
        getEntitiesBySourceTimeCounter, addEntityTimeCounter,
        putEntityTimeCounter, modifyEntityTimeCounter, removeEntityTimeCounter, replaceBySourceTimeCounter,
        collectChangesTimeCounter, hasSameEntitiesTimeCounter, toSnapshotTimeCounter, applyChangesFromTimeCounter,
        getMutableExternalMappingTimeCounter, getMutableVFUrlIndexTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(JPS))
    }
  }
}

internal sealed class AbstractEntityStorage : EntityStorageInstrumentation {

  internal abstract val entitiesByType: EntitiesBarrel
  internal abstract val refs: AbstractRefsTable
  internal abstract val indexes: AbstractStorageIndexes

  internal var brokenConsistency: Boolean = false

  internal var isEventHandling: Boolean = false
  private val reporterExecutor = ConcurrencyUtil.newSingleThreadExecutor("Workspace Model Reporter Pool")

  internal var storageIsAlreadyApplied = false
  internal var applyInfo: String? = null

  private val detectBridgesUsageInListeners = Registry.`is`("ide.workspace.model.assertions.bridges.usage", false)

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    @Suppress("UNCHECKED_CAST")
    return entitiesByType[entityClass.toClassId()]?.all()?.map { it.createEntity(this) } as? Sequence<E> ?: emptySequence()
  }

  override fun <E : WorkspaceEntity> entityCount(entityClass: Class<E>): Int {
    return entitiesByType[entityClass.toClassId()]?.size() ?: 0
  }

  override fun isEmpty(): Boolean = entitiesByType.size() == 0

  internal fun entityDataById(id: EntityId): WorkspaceEntityData<out WorkspaceEntity>? = entitiesByType[id.clazz]?.get(id.arrayId)

  internal fun entityDataByIdOrDie(id: EntityId): WorkspaceEntityData<out WorkspaceEntity> {
    val entityFamily = entitiesByType[id.clazz] ?: error(
      "Entity family doesn't exist or has no entities: ${id.clazz.findWorkspaceEntity()}")
    return entityFamily.getOrFail(id.arrayId) ?: error("Cannot find an entity by id ${id.asString()}")
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

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Sequence<WorkspaceEntity> {
    val index = indexes.entitySourceIndex
    return index.entries()
      .asSequence()
      .filter(sourceFilter)
      .flatMap { source ->
        val entityIds = index.getIdsByEntry(source) ?: error("Entity source $source expected to be in the index")
        entityIds.asSequence().map { this.entityDataByIdOrDie(it).createEntity(this) }
      }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExternalMapping(identifier: ExternalMappingKey<T>): ExternalEntityMapping<T> {
    if (detectBridgesUsageInListeners && isEventHandling) {
      // https://stackoverflow.com/a/26122232
      reporterExecutor.execute(BridgeAccessThreadAnalyzer(Exception(), identifier as ExternalMappingKey<Any>))
    }
    val index = indexes.externalMappings[identifier] as? AbstractExternalEntityMappingImpl<T>
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
  override fun <T : WorkspaceEntity> resolveReference(reference: EntityPointer<T>): T? {
    reference as EntityPointerImpl<T>
    return this.entityDataById(reference.id)?.createEntity(this) as? T
  }

  override fun getOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): WorkspaceEntity? {
    return getOneChildData(connectionId, parent.asBase().id)?.createEntity(this)
  }

  protected fun getOneChildData(connectionId: ConnectionId, parentEntityId: EntityId): WorkspaceEntityData<*>? {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        return refs.getOneToOneChild(connectionId, parentEntityId.arrayId) {
          entityDataByIdOrDie(createEntityId(it, connectionId.childClass))
        }
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val childId = refs.getChildrenByParent(connectionId, parentEntityId.asParent()).singleOrNull()?.id ?: return null
        return entityDataByIdOrDie(childId)
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, ConnectionId.ConnectionType.ONE_TO_MANY -> {
        error("This function works only with one-to-one connections")
      }
    }
  }

  override fun getManyChildren(connectionId: ConnectionId, parent: WorkspaceEntity): Sequence<WorkspaceEntity> {
    return getManyChildrenData(connectionId, parent.asBase().id).map { it.createEntity(this) }
  }

  protected fun getManyChildrenData(connectionId: ConnectionId, parentEntityId: EntityId): Sequence<WorkspaceEntityData<*>> {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        return refs.getChildrenByParent(connectionId, parentEntityId.asParent())
          .asSequence()
          .map { entityDataByIdOrDie(it.id) }
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        return refs.getChildrenByParent(connectionId, parentEntityId.asParent())
                 .asSequence()
                 .map { entityDataByIdOrDie(it.id) }
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, ConnectionId.ConnectionType.ONE_TO_ONE -> {
        error("This function works only with one-to-many connections")
      }
    }
  }

  override fun getParent(connectionId: ConnectionId, child: WorkspaceEntity): WorkspaceEntity? {
    return getParentData(connectionId, child.asBase().id)?.createEntity(this)
  }

  protected fun getParentData(connectionId: ConnectionId, childEntityId: EntityId): WorkspaceEntityData<*>? {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        return refs.getOneToOneParent(connectionId, childEntityId.arrayId) {
          val entityId = createEntityId(it, connectionId.parentClass)
          entityDataByIdOrDie(entityId)
        }
      }
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        return refs.getOneToManyParent(connectionId, childEntityId.arrayId) {
          val entityId = createEntityId(it, connectionId.parentClass)
          entityDataByIdOrDie(entityId)
        }
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        return refs.getOneToAbstractManyParent(connectionId, childEntityId.asChild())
          ?.let { entityDataByIdOrDie(it.id) }
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        return refs.getOneToAbstractOneParent(connectionId, childEntityId.asChild())
          ?.let { entityDataByIdOrDie(it.id) }
      }
    }
  }

  private class BridgeAccessThreadAnalyzer(private val exception: Exception, private val identifier: ExternalMappingKey<Any>) : Runnable {
    override fun run() {
      if (!predefinedBridges.any { identifier.toString().contains(it) }) return
      val stackTrace = exception.stackTrace
      //val stackTraceAsString = StringBuilder()
      for (stackTraceElement in stackTrace) {
        val elementAsString = stackTraceElement.toString()
        // Skipping access from GlobalWorkspaceModel
        if (elementAsString.contains("GlobalWorkspaceModel.onChanged")
            || elementAsString.contains("GlobalWorkspaceModel.onBeforeChanged")
            // Skip WorkspaceFileIndex contributors
            || elementAsString.contains("WorkspaceFileIndexDataImpl.onEntitiesChanged")) {
          return
        }
        //stackTraceAsString.append(stackTrace).append("\n")
      }
      LOG.error("Access to the bridge is prohibited during the event handling ", exception)
    }

    companion object {
      private val predefinedBridges = listOf("intellij.modules.bridge", "intellij.artifacts.bridge", "intellij.facets.bridge", "intellij.libraries.bridge")
    }
  }

  companion object {
    val LOG = logger<AbstractEntityStorage>()
  }
}


/** This function exposes `brokenConsistency` property to the outside and should be removed along with the property itself */
public val EntityStorage.isConsistent: Boolean
  get() = !(this as AbstractEntityStorage).brokenConsistency
