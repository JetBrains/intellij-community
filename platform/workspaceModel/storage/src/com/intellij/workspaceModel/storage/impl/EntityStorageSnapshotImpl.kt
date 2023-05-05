// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.platform.diagnostic.telemetry.JPS
import com.intellij.platform.diagnostic.telemetry.TelemetryTracer
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.ExceptionUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.containers.getDiff
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import com.intellij.workspaceModel.storage.impl.external.EmptyExternalEntityMapping
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex.MutableVirtualFileIndex.Companion.VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY
import com.intellij.workspaceModel.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.workspaceModel.storage.instrumentation.EntityStorageSnapshotInstrumentation
import com.intellij.workspaceModel.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal data class EntityReferenceImpl<E : WorkspaceEntity>(private val id: EntityId) : EntityReference<E>() {
  override fun resolve(storage: EntityStorage): E? {
    @Suppress("UNCHECKED_CAST")
    return (storage as AbstractEntityStorage).entityDataById(id)?.createEntity(storage) as? E
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

internal class EntityStorageSnapshotImpl(
  override val entitiesByType: ImmutableEntitiesBarrel,
  override val refs: RefsTable,
  override val indexes: StorageIndexes
) : EntityStorageSnapshotInstrumentation, AbstractEntityStorage() {

  // This cache should not be transferred to other versions of storage
  private val symbolicIdCache = ConcurrentHashMap<SymbolicEntityId<*>, WorkspaceEntity>()

  // I suppose that we can use some kind of array of arrays to get a quicker access (just two accesses by-index)
  // However, it's not implemented currently because I'm not sure about threading.
  private val entitiesCache = ConcurrentHashMap<EntityId, WorkspaceEntity>()

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val entity = symbolicIdCache.getOrPut(id) { super.resolve(id) ?: NULL_ENTITY }
    return if (entity !== NULL_ENTITY) entity as E else null
  }

  override fun toSnapshot(): EntityStorageSnapshot = this

  override fun <T: WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T {
    val found = entitiesCache[entityId]
    if (found != null) {
      @Suppress("UNCHECKED_CAST")
      return found as T
    }
    val newData = newInstance()
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
) : MutableEntityStorageInstrumentation, AbstractEntityStorage() {

  /**
   * This log collects the log of operations, not the log of state changes.
   * This means, that if we remove child entity, we'll record only remove event without "modify" event for its parent.
   *
   * This change log affects addDiff operation and [collectChanges] return result
   *
   * There is no particular reason not to store the list of "state" changes here. However, this will require a lot of work in order
   *   to record all changes on the storage and update [addDiff] method to work in the new way.
   */
  internal val changeLog = WorkspaceBuilderChangeLog()

  // Temporal solution for accessing error in deft project.
  private var throwExceptionOnError = false

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

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    val start = System.currentTimeMillis()

    @Suppress("UNCHECKED_CAST")
    val entities = entitiesByType[entityClass.toClassId()]?.all()?.map { it.wrapAsModifiable(this) } as? Sequence<E> ?: emptySequence()

    getEntitiesTimeMs.addAndGet(System.currentTimeMillis() - start)
    return entities
  }

  override fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(id: SymbolicEntityId<E>,
                                                                                  entityClass: Class<R>): Sequence<R> {
    val start = System.currentTimeMillis()
    val classId = entityClass.toClassId()

    @Suppress("UNCHECKED_CAST")
    val referrers = indexes.softLinks.getIdsByEntry(id).asSequence()
      .filter { it.clazz == classId }
      .map { entityDataByIdOrDie(it).wrapAsModifiable(this) as R }

    getReferrersTimeMs.addAndGet(System.currentTimeMillis() - start)
    return referrers
  }

  @Suppress("UNCHECKED_CAST")
  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val start = System.currentTimeMillis()

    val entityIds = indexes.symbolicIdIndex.getIdsByEntry(id) ?: return null
    val entityData: WorkspaceEntityData<WorkspaceEntity> = entityDataById(entityIds) as? WorkspaceEntityData<WorkspaceEntity> ?: return null
    val asModifiable = entityData.wrapAsModifiable(this) as E?
    resolveTimeMs.addAndGet(System.currentTimeMillis() - start)
    return asModifiable
  }

  // Do not remove cast to Class<out TypedEntity>. kotlin fails without it
  @Suppress("USELESS_CAST", "UNCHECKED_CAST")
  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    val start = System.currentTimeMillis()
    val groupedBySource = indexes.entitySourceIndex.entries().asSequence().filter { sourceFilter(it) }.associateWith { source ->
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
    getEntitiesBySourceTimeMs.addAndGet(System.currentTimeMillis() - start)
    return groupedBySource
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : WorkspaceEntity> addEntity(entity: T): T {
    val start = System.currentTimeMillis()
    try {
      lockWrite()
      val entityToAdd = if (entity is ModifiableWorkspaceEntityBase<*, *>) {
        entity as ModifiableWorkspaceEntityBase<T, *>
      }
      else {
        @Suppress("USELESS_CAST") //this is needed to work around a bug in Kotlin compiler (KT-55555)
        entity.createEntityTreeCopy(true) as ModifiableWorkspaceEntityBase<T, *>
      }

      entityToAdd.applyToBuilder(this)
      entityToAdd.changedProperty.clear()
    }
    finally {
      unlockWrite()
    }

    addEntityTimeMs.addAndGet(System.currentTimeMillis() - start)
    return entity
  }

  // This should be removed or not extracted into the interface
  fun <T : WorkspaceEntity, E : WorkspaceEntityData<T>, D : ModifiableWorkspaceEntityBase<T, E>> putEntity(entity: D) {
    val start = System.currentTimeMillis()
    try {
      lockWrite()

      val newEntityData = entity.getEntityData()

      // Check for persistent id uniqueness
      assertUniqueSymbolicId(newEntityData)

      entitiesByType.add(newEntityData, entity.getEntityClass().toClassId())

      // Add the change to changelog
      createAddEvent(newEntityData)

      // Update indexes
      indexes.entityAdded(newEntityData)
    }
    finally {
      unlockWrite()
    }
    putEntityTimeMs.addAndGet(System.currentTimeMillis() - start)
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
        if (throwExceptionOnError) {
          throw SymbolicIdAlreadyExistsException(symbolicId)
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <M : WorkspaceEntity.Builder<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    val start = System.currentTimeMillis()

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

      val originalParents = this.getOriginalParents(entityId.asChild())
      val beforeParents = this.refs.getParentRefsOfChild(entityId.asChild())
      val beforeChildren = this.refs.getChildrenRefsOfParentBy(entityId.asParent()).flatMap { (key, value) -> value.map { key to it } }

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
            if (throwExceptionOnError) {
              throw SymbolicIdAlreadyExistsException(newSymbolicId)
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

      this.indexes.updateSymbolicIdIndexes(this, updatedEntity, beforeSymbolicId, copiedData, modifiableEntity)

      updatedEntity
    }
    finally {
      unlockWrite()
    }

    modifyEntityTimeMs.addAndGet(System.currentTimeMillis() - start)
    return updatedEntity
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
    val start = System.currentTimeMillis()

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

    removeEntityTimeMs.addAndGet(System.currentTimeMillis() - start)
    return result
  }

  /**
   *  TODO Special cases: when source filter returns true for all entity sources.
   */
  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage) {
    val start = System.currentTimeMillis()
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

    replaceBySourceTimeMs.addAndGet(System.currentTimeMillis() - start)
  }

  /**
   * Implementation note: [changeLog] contains the information about modified entities, but don't contain the info
   *   regarding the entities that are affected by this change.
   * For example, if we remove the child entity, we should generate two events: remove of child and modification of parent.
   *
   * --- Ideal behaviour - that is currently not implemented --------------
   *
   * The [EntityChange.Added] and [EntityChange.Removed] events are straightforward and generated in case of added or removed entities.
   *
   * The [EntityChange.Replaced] is generated in case if any of the fields of the entity will return a different value in the newer
   *   version of storage.
   * This means, that this event is generated for case of "primitive" field change (Int, String, data class, etc.) or in case of
   *   changes of the references to other entity.
   * So, for example, if we remove child entity, we'll generate two events: remove event for child and replace event for parent.
   *
   * # Examples
   *
   * Assuming the following structure of entities: A --> B --> C
   * Where A is the root entity and B and C are the children.
   *
   * - If we modify primitive field of C: [Replace(C)]
   * - If we remove C: [Replace(B), Remove(C)]
   * - If we remove reference between B and C: [Replace(B), Replace(C)]
   * - If we remove B: [Replace(A), Remove(B), Remove(C)]
   *
   * Another example:
   * Before: A --> B  C, After A  C --> B
   * We have an entity `A` that has a child `B` and we move this child from `A` to `C`
   *
   * Produced events: [Replace(A), Replace(B), Replace(C)]
   *
   * ----------------------------------------------------------------
   *
   * ^^^ The behaviour above is not yet implemented as it breaks some current behaviour of IDE.
   * For example, adding of facets should not cause roots changed event. But since we add the facet to some module,
   *   the module itself gets "replace" event and this causes roots changed event.
   *
   * The code in this implementation contains commented out code that reverts the ideal behaviour that is described above.
   *
   * The initial refactoring of this method was made for IDEA-313747.
   * It was aimed to fix the case of "adding reference between two existing entities".
   *   It described the problem that `A.link = B` and `B.link = A` cause two different events while they create the same state change.
   *
   * However, during investigation, I've found another case with entities creation:
   * `new B(link = A)` and `A.link = new B()` also generate different events while modifying storage in the same way.
   *
   * The proper fix for the case above is to create two events: modify A and create B. However, at the moment this will cause more
   *   roots changed events and at least will break some tests.
   */
  override fun collectChanges(original: EntityStorage): Map<Class<*>, Set<EntityChange<*>>> {
    val start = System.currentTimeMillis()
    val res = HashMap<Class<*>, MutableSet<EntityChange<*>>>()

    try {
      lockWrite()
      val originalImpl = original as AbstractEntityStorage
      val changedEntityIds = HashSet<Long>()

      for ((entityId, change) in this.changeLog.changeLog) {
        when (change) {
          is ChangeEntry.AddEntity -> changedEntityIds += entityId
          is ChangeEntry.RemoveEntity -> changedEntityIds += entityId
          is ChangeEntry.ReplaceEntity -> changedEntityIds += entityId
          is ChangeEntry.ChangeEntitySource -> changedEntityIds += entityId
          is ChangeEntry.ReplaceAndChangeSource -> changedEntityIds += entityId
        }
      }

      changedEntityIds.forEach { id ->
        val entityClass = id.clazz.findWorkspaceEntity()
        val oldData = originalImpl.entityDataById(id)?.createEntity(originalImpl)
        val newData = this.entityDataById(id)?.createEntity(this)
        val event = when {
          oldData != null && newData != null -> EntityChange.Replaced(oldData, newData)
          oldData == null && newData != null -> EntityChange.Added(newData)
          oldData != null && newData == null -> EntityChange.Removed(oldData)
          else -> null
        }
        if (event != null) {
          res.getOrPut(entityClass) { HashSet() }.add(event)
        }
      }
    }
    finally {
      unlockWrite()
    }

    collectChangesTimeMs.addAndGet(System.currentTimeMillis() - start)
    return res
  }

  override fun hasSameEntities(original: EntityStorage): Boolean {
    val start = System.currentTimeMillis()
    if (changeLog.changeLog.isEmpty()) return true

    original as AbstractEntityStorage
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
              && changedParent.references?.modifiedParents?.isEmpty() == true
              && changedParent.references.removedChildren.singleOrNull()?.takeIf { it.first == connection && it.second.id == initial } != null
              && changedParent.references.newChildren.singleOrNull()?.takeIf { it.first == connection && it.second.id == new } != null) {
            collapsibleChanges.add(parent.id)
          }
        }
      }
    }

    val isEqual = collapsibleChanges == changeLog.changeLog.keys
    hasSameEntitiesTimeMs.addAndGet(System.currentTimeMillis() - start)
    return isEqual
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

  override fun toSnapshot(): EntityStorageSnapshot {
    val start = System.currentTimeMillis()
    val newEntities = entitiesByType.toImmutable()
    val newRefs = refs.toImmutable()
    val newIndexes = indexes.toImmutable()
    val snapshot = EntityStorageSnapshotImpl(newEntities, newRefs, newIndexes)
    toSnapshotTimeMs.addAndGet(System.currentTimeMillis() - start)
    return snapshot
  }

  override fun hasChanges(): Boolean = changeLog.changeLog.isNotEmpty()

  override fun addDiff(diff: MutableEntityStorage) {
    val start = System.currentTimeMillis()
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
    addDiffTimeMs.addAndGet(System.currentTimeMillis() - start)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getMutableExternalMapping(identifier: @NonNls String): MutableExternalEntityMapping<T> {
    val start = System.currentTimeMillis()
    val mapping: MutableExternalEntityMappingImpl<T> = try {
      lockWrite()
      val mapping = indexes.externalMappings.computeIfAbsent(
        identifier) { MutableExternalEntityMappingImpl<T>() } as MutableExternalEntityMappingImpl<T>
      mapping.setTypedEntityStorage(this)
      mapping
    }
    finally {
      unlockWrite()
    }

    getMutableExternalMappingTimeMs.addAndGet(System.currentTimeMillis() - start)
    return mapping
  }

  override fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex {
    val start = System.currentTimeMillis()
    val virtualFileIndex: MutableVirtualFileUrlIndex = try {
      lockWrite()
      val virtualFileIndex = indexes.virtualFileIndex
      virtualFileIndex.setTypedEntityStorage(this)
      virtualFileIndex
    }
    finally {
      unlockWrite()
    }
    getMutableVFUrlIndexTimeMs.addAndGet(System.currentTimeMillis() - start)
    return virtualFileIndex
  }

  internal fun addDiffAndReport(message: String, left: EntityStorage?, right: EntityStorage) {
    this.reportConsistencyIssue(message, AddDiffException(message), null, left, right,
                                ConsistencyCheckingMode.current == ConsistencyCheckingMode.ASYNCHRONOUS)
  }

  private fun applyDiffProtection(diff: AbstractEntityStorage) {
    LOG.trace { "Applying addDiff. Builder: $diff" }
    if (diff.storageIsAlreadyApplied) {
      LOG.error("Builder is already applied.\n Info: \n${diff.applyInfo}")
    }
    else {
      diff.storageIsAlreadyApplied = true
      var info = "Applying builder using addDiff. Previous stack trace >>>>\n"
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

    @Suppress("UNCHECKED_CAST")
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
      val children = builder.refs
        .getChildrenRefsOfParentBy(entityId.asParent())
        .flatMap { (key, value) -> value.map { key to it } }

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

      builder.changeLog.addReplaceEvent(entityId, copiedData, originalEntity, originalParents, addedChildren, removedChildren,
                                        parentsMapRes)
    }

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
      val getEntitiesTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.entities.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val getReferrersTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.referrers.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val resolveTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.resolve.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val getEntitiesBySourceTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.entities.by.source.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val addEntityTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.add.entity.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val putEntityTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.put.entity.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val modifyEntityTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.modify.entity.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val removeEntityTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.remove.entity.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val replaceBySourceTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.replace.by.source.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val collectChangesTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.collect.changes.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val hasSameEntitiesTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.has.same.entities.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val toSnapshotTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.to.snapshot.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val addDiffTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.add.diff.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val getMutableExternalMappingTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.mutable.ext.mapping.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val getMutableVFUrlIndexTimeGauge = meter.gaugeBuilder("workspaceModel.mutableEntityStorage.mutable.vfurl.index.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          getEntitiesTimeGauge.record(getEntitiesTimeMs.get())
          getReferrersTimeGauge.record(getReferrersTimeMs.get())
          resolveTimeGauge.record(resolveTimeMs.get())
          getEntitiesBySourceTimeGauge.record(getEntitiesBySourceTimeMs.get())
          addEntityTimeGauge.record(addEntityTimeMs.get())
          putEntityTimeGauge.record(putEntityTimeMs.get())
          modifyEntityTimeGauge.record(modifyEntityTimeMs.get())
          removeEntityTimeGauge.record(removeEntityTimeMs.get())
          replaceBySourceTimeGauge.record(replaceBySourceTimeMs.get())
          collectChangesTimeGauge.record(collectChangesTimeMs.get())
          hasSameEntitiesTimeGauge.record(hasSameEntitiesTimeMs.get())
          toSnapshotTimeGauge.record(toSnapshotTimeMs.get())
          addDiffTimeGauge.record(addDiffTimeMs.get())
          getMutableExternalMappingTimeGauge.record(getMutableExternalMappingTimeMs.get())
          getMutableVFUrlIndexTimeGauge.record(getMutableVFUrlIndexTimeMs.get())
        },
        getEntitiesTimeGauge, getReferrersTimeGauge, resolveTimeGauge, getEntitiesBySourceTimeGauge, addEntityTimeGauge,
        putEntityTimeGauge, modifyEntityTimeGauge, removeEntityTimeGauge, replaceBySourceTimeGauge,
        collectChangesTimeGauge, hasSameEntitiesTimeGauge, toSnapshotTimeGauge, addDiffTimeGauge,
        getMutableExternalMappingTimeGauge, getMutableVFUrlIndexTimeGauge
      )
    }

    init {
      // See also [org.jetbrains.jps.diagnostic.JpsMetrics] and [org.jetbrains.jps.diagnostic.Metrics].
      // If tracking of spans are needed it makes sense to extract them into separate module and depend on it.
      setupOpenTelemetryReporting(TelemetryTracer.getMeter(JPS))
    }
  }
}

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
            reportErrorAndAttachStorage("Cannot find an entity by id $it", this@AbstractEntityStorage)
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

  override fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E> = EntityReferenceImpl((e as WorkspaceEntityBase).id)


  override fun <T: WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T = newInstance()

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
        reportConsistencyIssue(message, e, sourceFilter, left, right,
                               ConsistencyCheckingMode.current == ConsistencyCheckingMode.ASYNCHRONOUS)
      }
    }
  }

  internal fun reportConsistencyIssue(message: String,
                                      e: Throwable,
                                      sourceFilter: ((EntitySource) -> Boolean)?,
                                      left: EntityStorage?,
                                      right: EntityStorage?,
                                      reportInBackgroundThread: Boolean) {
    val storage = if (this is MutableEntityStorage) this.toSnapshot() as AbstractEntityStorage else this
    val report = { reportConsistencyIssue(message, e, sourceFilter, left, right, storage) }
    if (reportInBackgroundThread) {
      consistencyChecker.execute(report)
    }
    else {
      report()
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
