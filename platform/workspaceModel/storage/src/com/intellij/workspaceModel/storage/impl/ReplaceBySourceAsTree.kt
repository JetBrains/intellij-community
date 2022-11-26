// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.ReplaceBySourceAsTree.OperationsApplier
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.jetbrains.annotations.TestOnly
import java.util.*
import kotlin.collections.ArrayList

/**
 * # Replace By Source ~~as tree~~
 *
 * Replace By Source, or RBS for short.
 *
 * "As tree" is not a correct fact. It was previously processed as tree, before we committed to multiple parents.
 *   So, this is still a directed graph.
 *
 * During the RBS, we collect a set of operations. After the set is collected, we apply them on a target storage.
 * Theoretically, it's possible to create a dry run, if needed.
 * Operations are supposed to be isolated and complete. That means that target storage doesn't become in inconsistent state at
 *   any moment of the RBS.
 *
 * This RBS implementation doesn't have expected exception and should always succeed.
 * If this RBS doesn't have sense from the abstract point of view (for example, during the RBS we transfer some entity,
 *   but we can't find a parent for this entity in the target store), we still get into some consistent state. As for the example,
 *   this entity won't be transferred into the target storage.
 *
 *
 * # Implementation details
 *
 * The operations are collected in separate data structures: For adding, replacing and removing.
 * Relabel operation is also known as "Replace".
 * Add operations should be applied in order, while for other operations the order is not determined.
 *
 * During the RBS we maintain a separate state for each of processed entity to avoid processing the same entity twice.
 *   Two separate states are presented: for target and ReplaceWith storages.
 *
 * # Debugging
 *
 * You can use [OperationsApplier.dumpOperations] for listing the operations on the storage.
 *
 * # Future improvements
 *
 * - Make type graphs. Separate graphs into independent parts (how is it called correctly?)
 * - Work on separate graph parts as on independent items
 */
internal class ReplaceBySourceAsTree : ReplaceBySourceOperation {

  private lateinit var targetStorage: MutableEntityStorageImpl
  private lateinit var replaceWithStorage: AbstractEntityStorage
  private lateinit var entityFilter: (EntitySource) -> Boolean

  internal val replaceOperations = ArrayList<RelabelElement>()
  internal val removeOperations = ArrayList<RemoveElement>()
  internal val addOperations = ArrayList<AddElement>()
  internal val targetState = Long2ObjectOpenHashMap<ReplaceState>()
  internal val replaceWithState = Long2ObjectOpenHashMap<ReplaceWithState>()

  @set:TestOnly
  internal var shuffleEntities: Long = -1L

  private val replaceWithProcessingCache = HashMap<Pair<EntityId?, Int>, Pair<DataCache, MutableList<ChildEntityId>>>()

  override fun replace(
    targetStorage: MutableEntityStorageImpl,
    replaceWithStorage: AbstractEntityStorage,
    entityFilter: (EntitySource) -> Boolean,
  ) {
    this.targetStorage = targetStorage
    this.replaceWithStorage = replaceWithStorage
    this.entityFilter = entityFilter

    // Process entities from the target storage
    val targetEntitiesToReplace = targetStorage.entitiesBySource(entityFilter)
    val targetEntities = targetEntitiesToReplace.values.flatMap { it.values }.flatten().toMutableList()
    if (shuffleEntities != -1L && targetEntities.size > 1) {
      targetEntities.shuffle(Random(shuffleEntities))
    }
    for (targetEntityToReplace in targetEntities) {
      TargetProcessor().processEntity(targetEntityToReplace)
    }

    // Process entities from the replaceWith storage
    val replaceWithEntitiesToReplace = replaceWithStorage.entitiesBySource(entityFilter)
    val replaceWithEntities = replaceWithEntitiesToReplace.values.flatMap { it.values }.flatten().toMutableList()
    if (shuffleEntities != -1L && replaceWithEntities.size > 1) {
      replaceWithEntities.shuffle(Random(shuffleEntities))
    }
    for (replaceWithEntityToReplace in replaceWithEntities) {
      ReplaceWithProcessor().processEntity(replaceWithEntityToReplace)
    }

    // This method can be used for debugging
    // OperationsApplier().dumpOperations()

    // Apply collected operations on the target storage
    OperationsApplier().apply()
  }

  // This class is just a wrapper to combine functions logically
  private inner class OperationsApplier {
    fun apply() {
      val replaceToTarget = HashMap<EntityId, EntityId>()
      for (addOperation in addOperations) {
        val parents = addOperation.parents?.mapTo(HashSet()) {
          when (it) {
            is ParentsRef.AddedElement -> replaceToTarget.getValue(it.replaceWithEntityId)
            is ParentsRef.TargetRef -> it.targetEntityId
          }
        }
        addElement(parents, addOperation.replaceWithSource, replaceToTarget)
      }

      for (operation in replaceOperations) {
        val targetEntity = targetStorage.entityDataByIdOrDie(operation.targetEntityId).createEntity(targetStorage)
        val replaceWithEntity = replaceWithStorage.entityDataByIdOrDie(operation.replaceWithEntityId).createEntity(replaceWithStorage)
        val parents = operation.parents?.mapTo(HashSet()) {
          val targetEntityId = when (it) {
            is ParentsRef.AddedElement -> replaceToTarget.getValue(it.replaceWithEntityId)
            is ParentsRef.TargetRef -> it.targetEntityId
          }
          targetStorage.entityDataByIdOrDie(targetEntityId).createEntity(targetStorage)
        }
        targetStorage.modifyEntity(WorkspaceEntity.Builder::class.java, targetEntity) {
          (this as ModifiableWorkspaceEntityBase<*, *>).relabel(replaceWithEntity, parents)
        }
        targetStorage.indexes.updateExternalMappingForEntityId(operation.replaceWithEntityId, operation.targetEntityId, replaceWithStorage.indexes)
      }

      for (removeOperation in removeOperations) {
        targetStorage.removeEntityByEntityId(removeOperation.targetEntityId)
      }
    }

    private fun addElement(parents: Set<EntityId>?, replaceWithDataSource: EntityId, replaceToTarget: HashMap<EntityId, EntityId>) {
      val targetParents = mutableListOf<WorkspaceEntity>()
      parents?.forEach { parent ->
        targetParents += targetStorage.entityDataByIdOrDie(parent).createEntity(targetStorage)
      }

      val modifiableEntity = replaceWithStorage.entityDataByIdOrDie(replaceWithDataSource).createDetachedEntity(targetParents)
      modifiableEntity as ModifiableWorkspaceEntityBase<out WorkspaceEntity, out WorkspaceEntityData<*>>

      // We actually bind parents in [createDetachedEntity], but we can't do it for external entities (that are defined in a separate module)
      // Here we bind them again, so I guess we can remove "parents binding" from [createDetachedEntity], but let's do it twice for now.
      // Actually, I hope to get rid of [createDetachedEntity] at some moment.
      targetParents.groupBy { it::class }.forEach { (_, ents) ->
        modifiableEntity.linkExternalEntity(ents.first().getEntityInterface().kotlin, false, ents)
      }
      targetStorage.addEntity(modifiableEntity)
      targetStorage.indexes.updateExternalMappingForEntityId(replaceWithDataSource, modifiableEntity.id, replaceWithStorage.indexes)
      replaceToTarget[replaceWithDataSource] = modifiableEntity.id
    }

    /**
     * First print the operations, then print the information about entities
     */
    fun dumpOperations(): String {
      val targetEntities: MutableSet<EntityId> = mutableSetOf()
      val replaceWithEntities: MutableSet<EntityId> = mutableSetOf()
      return buildString {
        appendLine("---- New entities -------")
        for (addOperation in addOperations) {
          appendLine(infoOf(addOperation.replaceWithSource, replaceWithStorage, true))
          replaceWithEntities += addOperation.replaceWithSource
          if (addOperation.parents == null) {
            appendLine("No parent entities")
          }
          else {
            appendLine("Parents:")
            addOperation.parents.forEach { parent ->
              when (parent) {
                is ParentsRef.AddedElement -> {
                  appendLine("   - ${infoOf(parent.replaceWithEntityId, replaceWithStorage, true)} <--- New Added Entity")
                  replaceWithEntities += parent.replaceWithEntityId
                }
                is ParentsRef.TargetRef -> {
                  appendLine("   - ${infoOf(parent.targetEntityId, targetStorage, true)} <--- Existing Entity")
                  targetEntities += parent.targetEntityId
                }
              }
            }
          }
          appendLine()
        }

        appendLine("---- No More New Entities -------")
        appendLine("---- Removes -------")

        removeOperations.map { it.targetEntityId }.forEach { entityId ->
          appendLine(infoOf(entityId, targetStorage, true))
          targetEntities += entityId
        }

        appendLine("---- No More Removes -------")
        appendLine()
        appendLine("---- Replaces -------")

        replaceOperations.forEach { operation ->
          appendLine(
            infoOf(operation.targetEntityId, targetStorage, true) + " -> " + infoOf(operation.replaceWithEntityId, replaceWithStorage,
                                                                                    true) + " | " + "Count of parents: ${operation.parents?.size}")
          targetEntities += operation.targetEntityId
          replaceWithEntities += operation.replaceWithEntityId
        }

        appendLine("---- No More Replaces -------")
        appendLine()
        appendLine("---- Entities -------")
        appendLine()
        appendLine("---- Target Storage -------")
        targetEntities.forEach {
          appendLine(infoOf(it, targetStorage, false))
          appendLine()
        }

        appendLine()
        appendLine("---- Replace With Storage -------")
        replaceWithEntities.forEach {
          appendLine(infoOf(it, replaceWithStorage, false))
          appendLine()
        }
      }
    }

    private fun infoOf(entityId: EntityId, store: AbstractEntityStorage, short: Boolean): String {
      val entityData = store.entityDataByIdOrDie(entityId)
      val entity = entityData.createEntity(store)
      return if (entity is WorkspaceEntityWithSymbolicId) entity.symbolicId.toString() else if (short) "$entity" else "$entity | $entityData"
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class ReplaceWithProcessor {
    fun processEntity(replaceWithEntity: WorkspaceEntity) {
      replaceWithEntity as WorkspaceEntityBase

      if (replaceWithState[replaceWithEntity.id] != null) return
      processEntity(TrackToParents(replaceWithEntity.id, replaceWithStorage))
    }

    private fun processEntity(replaceWithTrack: TrackToParents): ParentsRef? {

      val replaceWithEntityId = replaceWithTrack.entity
      val replaceWithEntityState = replaceWithState[replaceWithEntityId]
      when (replaceWithEntityState) {
        ReplaceWithState.ElementMoved -> return ParentsRef.AddedElement(replaceWithEntityId)
        is ReplaceWithState.NoChange -> return ParentsRef.TargetRef(replaceWithEntityState.targetEntityId)
        ReplaceWithState.NoChangeTraceLost -> return null
        is ReplaceWithState.Relabel -> return ParentsRef.TargetRef(replaceWithEntityState.targetEntityId)
        null -> Unit
      }

      val replaceWithEntityData = replaceWithStorage.entityDataByIdOrDie(replaceWithEntityId)
      val replaceWithEntity = replaceWithEntityData.createEntity(replaceWithStorage) as WorkspaceEntityBase
      if (replaceWithTrack.parents.isEmpty()) {
        return findAndReplaceRootEntity(replaceWithEntity)
      }
      else {
        if (replaceWithEntity is WorkspaceEntityWithSymbolicId) {
          val targetEntity = targetStorage.resolve(replaceWithEntity.symbolicId)
          val parentsAssociation = replaceWithTrack.parents.mapNotNullTo(HashSet()) { processEntity(it) }
          return processExactEntity(targetEntity, replaceWithEntity, parentsAssociation)
        }
        else {
          val parentsAssociation = replaceWithTrack.parents.mapNotNullTo(HashSet()) { processEntity(it) }
          if (parentsAssociation.isNotEmpty()) {
            val targetEntityData = parentsAssociation.filterIsInstance<ParentsRef.TargetRef>().firstNotNullOfOrNull { parent ->
              findEntityInTargetStore(replaceWithEntityData, parent.targetEntityId, replaceWithEntityId.clazz)
            }
            val targetEntity = targetEntityData?.createEntity(targetStorage) as? WorkspaceEntityBase

            return processExactEntity(targetEntity, replaceWithEntity, parentsAssociation)
          }
          else {
            replaceWithEntityId.addState(ReplaceWithState.NoChangeTraceLost)
            return null
          }
        }
      }
    }

    private fun findAndReplaceRootEntity(replaceWithEntity: WorkspaceEntityBase): ParentsRef? {
      val replaceWithEntityId = replaceWithEntity.id
      val currentState = replaceWithState[replaceWithEntityId]
      // This was just checked before this call
      assert(currentState == null)

      val targetEntity = findRootEntityInStorage(replaceWithEntity, targetStorage, replaceWithStorage, targetState)
      val parents = null

      return processExactEntity(targetEntity, replaceWithEntity, parents)
    }

    private fun processExactEntity(targetEntity: WorkspaceEntity?,
                                   replaceWithEntity: WorkspaceEntityBase,
                                   parents: Set<ParentsRef>?): ParentsRef? {
      val replaceWithEntityId = replaceWithEntity.id
      if (targetEntity == null) {
        if (entityFilter(replaceWithEntity.entitySource)) {
          addSubtree(parents, replaceWithEntityId)
          return ParentsRef.AddedElement(replaceWithEntityId)
        }
        else {
          replaceWithEntityId.addState(ReplaceWithState.NoChangeTraceLost)
          return null
        }
      }
      else {

        val targetEntityId = (targetEntity as WorkspaceEntityBase).id
        val targetCurrentState = targetState[targetEntityId]
        when (targetCurrentState) {
          is ReplaceState.NoChange -> return ParentsRef.TargetRef(targetEntityId)
          is ReplaceState.Relabel -> return ParentsRef.TargetRef(targetEntityId)
          ReplaceState.Remove -> return null
          null -> Unit
        }

        when {
          entityFilter(targetEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
            if (replaceWithEntity.entitySource !is DummyParentEntitySource) {
              replaceWorkspaceData(targetEntity.id, replaceWithEntity.id, parents)
            } else {
              doNothingOn(targetEntityId, replaceWithEntityId)
            }
            return ParentsRef.TargetRef(targetEntityId)
          }
          entityFilter(targetEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
            removeWorkspaceData(targetEntity.id, replaceWithEntity.id)
            return null
          }
          !entityFilter(targetEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
            if (targetEntity is WorkspaceEntityWithSymbolicId) {
              if (replaceWithEntity.entitySource !is DummyParentEntitySource) {
                replaceWorkspaceData(targetEntityId, replaceWithEntityId, parents)
              } else {
                doNothingOn(targetEntityId, replaceWithEntityId)
              }
              return ParentsRef.TargetRef(targetEntityId)
            }
            else {
              addSubtree(parents, replaceWithEntityId)
              return ParentsRef.AddedElement(replaceWithEntityId)
            }
          }
          !entityFilter(targetEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
            doNothingOn(targetEntity.id, replaceWithEntityId)
            return ParentsRef.TargetRef(targetEntityId)
          }
          else -> error("Unexpected branch")
        }
      }
    }

    private fun findAndReplaceRootEntityInTargetStore(replaceWithRootEntity: WorkspaceEntityBase): ParentsRef? {
      val replaceRootEntityId = replaceWithRootEntity.id
      val replaceWithCurrentState = replaceWithState[replaceRootEntityId]
      when (replaceWithCurrentState) {
        is ReplaceWithState.NoChange -> return ParentsRef.TargetRef(replaceWithCurrentState.targetEntityId)
        ReplaceWithState.NoChangeTraceLost -> return null
        is ReplaceWithState.Relabel -> return ParentsRef.TargetRef(replaceWithCurrentState.targetEntityId)
        ReplaceWithState.ElementMoved -> TODO()
        null -> Unit
      }

      val targetEntity = findRootEntityInStorage(replaceWithRootEntity, targetStorage, replaceWithStorage, targetState)

      return processExactEntity(targetEntity, replaceWithRootEntity, null)
    }

    /**
     * This is a very similar thing as [findSameEntity]. But it finds an entity in the target storage (or the entity that will be added)
     */
    fun findSameEntityInTargetStore(replaceWithTrack: TrackToParents): ParentsRef? {

      // Check if this entity was already processed
      val replaceWithCurrentState = replaceWithState[replaceWithTrack.entity]
      when (replaceWithCurrentState) {
        is ReplaceWithState.NoChange -> return ParentsRef.TargetRef(replaceWithCurrentState.targetEntityId)
        ReplaceWithState.NoChangeTraceLost -> return null
        is ReplaceWithState.Relabel -> return ParentsRef.TargetRef(replaceWithCurrentState.targetEntityId)
        ReplaceWithState.ElementMoved -> return ParentsRef.AddedElement(replaceWithTrack.entity)
        null -> Unit
      }

      val replaceWithEntityData = replaceWithStorage.entityDataByIdOrDie(replaceWithTrack.entity)
      if (replaceWithTrack.parents.isEmpty()) {
        val targetRootEntityId = findAndReplaceRootEntityInTargetStore(
          replaceWithEntityData.createEntity(replaceWithStorage) as WorkspaceEntityBase)
        return targetRootEntityId
      }
      else {
        val parentsAssociation = replaceWithTrack.parents.associateWith { findSameEntityInTargetStore(it) }
        val entriesList = parentsAssociation.entries.toList()

        val targetParents = mutableSetOf<EntityId>()
        var targetEntityData: WorkspaceEntityData<out WorkspaceEntity>? = null
        for (i in entriesList.indices) {
          val value = entriesList[i].value
          if (value is ParentsRef.TargetRef) {
            targetEntityData = findEntityInTargetStore(replaceWithEntityData, value.targetEntityId, replaceWithTrack.entity.clazz)
            if (targetEntityData != null) {
              targetParents += entriesList[i].key.entity
              break
            }
          }
        }
        if (targetEntityData == null) {
          for (entry in entriesList) {
            val value = entry.value
            if (value is ParentsRef.AddedElement) {
              return ParentsRef.AddedElement(replaceWithTrack.entity)
            }
          }
        }
        return targetEntityData?.createEntityId()?.let { ParentsRef.TargetRef(it) }
      }
    }

    private fun addSubtree(parents: Set<ParentsRef>?, replaceWithEntityId: EntityId) {
      val currentState = replaceWithState[replaceWithEntityId]
      when (currentState) {
        ReplaceWithState.ElementMoved -> return
        is ReplaceWithState.NoChange -> error("Unexpected state")
        ReplaceWithState.NoChangeTraceLost -> error("Unexpected state")
        is ReplaceWithState.Relabel -> error("Unexpected state")
        null -> Unit
      }

      addElementOperation(parents, replaceWithEntityId)

      replaceWithStorage.refs.getChildrenRefsOfParentBy(replaceWithEntityId.asParent()).values.flatten().forEach {
        val replaceWithChildEntityData = replaceWithStorage.entityDataByIdOrDie(it.id)
        if (!entityFilter(replaceWithChildEntityData.entitySource)) return@forEach
        val trackToParents = TrackToParents(it.id, replaceWithStorage)
        val sameEntity = findSameEntityInTargetStore(trackToParents)
        if (sameEntity is ParentsRef.TargetRef) {
          return@forEach
        }
        val otherParents = trackToParents.parents.mapNotNull { findSameEntityInTargetStore(it) }
        addSubtree((otherParents + ParentsRef.AddedElement(replaceWithEntityId)).toSet(), it.id)
      }
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class TargetProcessor {
    fun processEntity(targetEntityToReplace: WorkspaceEntity) {
      targetEntityToReplace as WorkspaceEntityBase

      // This method not only finds the same entity in the ReplaceWith storage, but also processes all entities it meets.
      // So, for processing an entity, it's enough to call this methos on the entity.
      findSameEntity(TrackToParents(targetEntityToReplace.id, targetStorage))
    }


    /**
     * This method searched for the "associated" entity of [targetEntityTrack] in the repalceWith storage
     * Here, let's use "associated" termin to define what we're looking for. If the entity have a [SymbolicEntityId],
     *   this is super simple. "associated" entity is just an entity from the different storage with the same SymbolicId.
     *
     *   Things go complicated if there is no SymbolicId. In this case we build a track to the root entities in the graph, trying
     *     to find same roots in the replaceWith storage and building a "track" to the entity in the replaceWith storage. This
     *     traced entity is an "associated" entity for our current entity.
     *
     * This is a recursive algorithm
     * - Get all parents of the entity
     * - if there are NO parents:
     *    - Try to find associated entity in replaceWith storage (by SymbolicId in most cases)
     * - if there are parents:
     *    - Run this algorithm on all parents to find associated parents in the replaceWith storage
     *    - Based on found parents in replaceWith storage, find an associated entity for our currenly searched entity
     */
    private fun findSameEntity(targetEntityTrack: TrackToParents): EntityId? {

      // Check if this entity was already processed
      val targetEntityState = targetState[targetEntityTrack.entity]
      if (targetEntityState != null) {
        when (targetEntityState) {
          is ReplaceState.NoChange -> return targetEntityState.replaceWithEntityId
          is ReplaceState.Relabel -> return targetEntityState.replaceWithEntityId
          ReplaceState.Remove -> return null
        }
      }

      val targetEntityData = targetStorage.entityDataByIdOrDie(targetEntityTrack.entity)
      if (targetEntityTrack.parents.isEmpty()) {
        // If the entity doesn't have parents, it's a root entity for this subtree (subgraph?)
        return findAndReplaceRootEntity(targetEntityData)
      }
      else {
        val (targetParents, replaceWithEntity) = processParentsFromReplaceWithStorage(targetEntityTrack, targetEntityData)

        return processExactEntity(targetParents, targetEntityData, replaceWithEntity)
      }
    }

    private fun processExactEntity(targetParents: MutableSet<ParentsRef>?,
                                   targetEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                                   replaceWithEntity: WorkspaceEntityBase?): EntityId? {
      // Here we check if any of the required parents is missing the our new parents
      val requiredParentMissing = if (targetParents != null) {
        val targetParentClazzes = targetParents.map {
          when (it) {
            is ParentsRef.AddedElement -> it.replaceWithEntityId.clazz
            is ParentsRef.TargetRef -> it.targetEntityId.clazz
          }
        }
        targetEntityData.getRequiredParents().any { it.toClassId() !in targetParentClazzes }
      }
      else false

      val targetEntity = targetEntityData.createEntity(targetStorage) as WorkspaceEntityBase

      if (replaceWithEntity == null || requiredParentMissing) {
        // Here we don't have an associated entity in the replaceWith storage. Decide if we remove our entity or just keep it
        when (entityFilter(targetEntity.entitySource)) {
          true -> {
            removeWorkspaceData(targetEntity.id, null)
            return null
          }
          false -> {
            doNothingOn(targetEntity.id, null)
            return null
          }
        }
      }
      else {
        when {
          entityFilter(targetEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
            if (replaceWithEntity.entitySource !is DummyParentEntitySource) {
              replaceWorkspaceData(targetEntity.id, replaceWithEntity.id, targetParents)
            } else {
              doNothingOn(targetEntity.id, replaceWithEntity.id)
            }
            return replaceWithEntity.id
          }
          entityFilter(targetEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
            removeWorkspaceData(targetEntity.id, replaceWithEntity.id)
            return null
          }
          !entityFilter(targetEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
            if (replaceWithEntity.entitySource !is DummyParentEntitySource) {
              replaceWorkspaceData(targetEntity.id, replaceWithEntity.id, targetParents)
            } else {
              doNothingOn(targetEntity.id, replaceWithEntity.id)
            }
            return replaceWithEntity.id
          }
          !entityFilter(targetEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
            doNothingOn(targetEntity.id, replaceWithEntity.id)
            return replaceWithEntity.id
          }
          else -> error("Unexpected branch")
        }
      }
    }

    /**
     * An interesting logic here. We've found associated parents for the target entity.
     * Now, among these parents we have to find a child, that will be similar to "our" entity in target storage.
     *
     * In addition, we're currently missing "new" parents in the replaceWith storage.
     * So, the return type is a set of parents (existing and new) and an "associated" entity in the replaceWith storage.
     */
    private fun processParentsFromReplaceWithStorage(
      targetEntityTrack: TrackToParents,
      targetEntityData: WorkspaceEntityData<out WorkspaceEntity>
    ): Pair<MutableSet<ParentsRef>, WorkspaceEntityBase?> {

      // Our future set of parents
      val targetParents = mutableSetOf<ParentsRef>()

      val targetEntity = targetEntityData.createEntity(targetStorage)
      var replaceWithEntity: WorkspaceEntityBase? = null
      if (targetEntity is WorkspaceEntityWithSymbolicId) {
        replaceWithEntity = replaceWithStorage.resolve(targetEntity.symbolicId) as? WorkspaceEntityBase
      }
      else {
        // Here we're just traversing parents. If we find a parent that does have a child entity that is equal to our entity, stop and save
        //   this "equaled" entity as our "associated" entity.
        // After that we're checking that other parents does have this child among their children. If not, we do not register this parent as
        //   "new" parent for our entity.
        //
        // Another approach would be finding the "most common" child among of all parents. But currently we use this approach
        //   because I think that it's "good enough" and the "most common child" may be not what we're looking for.

        // So, here we search for the first equal entity
        val parentsAssociation = targetEntityTrack.parents.associateWith { findSameEntity(it) }
        val entriesList = parentsAssociation.entries.toList()
        var index = 0
        for (i in entriesList.indices) {
          index = i

          val (caching, replaceWithEntityIds) =
            replaceWithProcessingCache.getOrPut(entriesList[i].value to targetEntityTrack.entity.clazz) {
              val ids = LinkedList(childrenInReplaceWith(entriesList[i].value, targetEntityTrack.entity.clazz))
              DataCache(ids.size, EntityDataStrategy()) to ids
            }

          replaceWithEntity = replaceWithEntityIds.removeSomeWithCaching(targetEntityData, caching, replaceWithStorage)
            ?.createEntity(replaceWithStorage) as? WorkspaceEntityBase
          if (replaceWithEntity != null) {
            assert(replaceWithState[replaceWithEntity.id] == null)
            targetParents += ParentsRef.TargetRef(entriesList[i].key.entity)
            break
          }
        }

        // Here we know our "associated" entity, so we just check what parents remain with it.
        entriesList.drop(index + 1).forEach { tailItem ->
          // Should we use cache as in above?
          val replaceWithEntityIds = childrenInReplaceWith(tailItem.value, targetEntityTrack.entity.clazz).toMutableList()
          val caching = DataCache(replaceWithEntityIds.size, EntityDataStrategy())
          var replaceWithMyEntityData = replaceWithEntityIds.removeSomeWithCaching(targetEntityData, caching, replaceWithStorage)
          while (replaceWithMyEntityData != null && replaceWithEntity!!.id != replaceWithMyEntityData.createEntityId()) {
            replaceWithMyEntityData = replaceWithEntityIds.removeSomeWithCaching(targetEntityData, caching, replaceWithStorage)
          }
          if (replaceWithMyEntityData != null) {
            targetParents += ParentsRef.TargetRef(tailItem.key.entity)
          }
        }

      }

      // And here we get other parent' of the associated entity.
      // This is actually a complicated operation because it's not enough just to find parents in replaceWith storage.
      //   We should also understand if this parent is a new entity or it already exists in the target storage.
      if (replaceWithEntity != null) {
        val alsoTargetParents = TrackToParents(replaceWithEntity.id, replaceWithStorage).parents
          .map { ReplaceWithProcessor().findSameEntityInTargetStore(it) }
        targetParents.addAll(alsoTargetParents.filterNotNull())
      }
      return Pair(targetParents, replaceWithEntity)
    }

    /**
     * Process root entity of the storage
     */
    fun findAndReplaceRootEntity(targetEntityData: WorkspaceEntityData<out WorkspaceEntity>): EntityId? {
      val targetRootEntityId = targetEntityData.createEntityId()
      val currentTargetState = targetState[targetRootEntityId]
      assert(currentTargetState == null) { "This state was already checked before this function" }

      val replaceWithEntity = findRootEntityInStorage(targetEntityData.createEntity(targetStorage) as WorkspaceEntityBase, replaceWithStorage,
                                                      targetStorage, replaceWithState) as? WorkspaceEntityBase

      return processExactEntity(null, targetEntityData, replaceWithEntity)
    }
  }

  private class EntityDataStrategy : Hash.Strategy<WorkspaceEntityData<out WorkspaceEntity>> {
    override fun equals(a: WorkspaceEntityData<out WorkspaceEntity>?, b: WorkspaceEntityData<out WorkspaceEntity>?): Boolean {
      if (a == null || b == null) {
        return false
      }
      return a.equalsByKey(b)
    }

    override fun hashCode(o: WorkspaceEntityData<out WorkspaceEntity>?): Int {
      return o?.hashCodeByKey() ?: 0
    }
  }

  private fun MutableList<ChildEntityId>.removeSomeWithCaching(key: WorkspaceEntityData<out WorkspaceEntity>,
                                                               cache: Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>>,
                                                               storage: AbstractEntityStorage): WorkspaceEntityData<out WorkspaceEntity>? {
    val foundInCache = cache.removeSome(key)
    if (foundInCache != null) return foundInCache

    val thisIterator = this.iterator()
    while (thisIterator.hasNext()) {
      val id = thisIterator.next()
      val value = storage.entityDataByIdOrDie(id.id)
      if (value.equalsByKey(key)) {
        thisIterator.remove()
        return value
      }
      thisIterator.remove()
      addValueToMap(cache, value)
    }
    return null
  }

  private fun <K, V> Object2ObjectOpenCustomHashMap<K, List<V>>.removeSome(key: K): V? {
    val existingValue = this[key] ?: return null
    return if (existingValue.size == 1) {
      this.remove(key)
      existingValue.single()
    }
    else {
      val firstElement = existingValue[0]
      this[key] = existingValue.drop(1)
      firstElement
    }
  }

  private fun addElementOperation(targetParentEntity: Set<ParentsRef>?, replaceWithEntity: EntityId) {
    addOperations += AddElement(targetParentEntity, replaceWithEntity)
    replaceWithEntity.addState(ReplaceWithState.ElementMoved)
  }

  private fun replaceWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId, parents: Set<ParentsRef>?) {
    replaceOperations.add(RelabelElement(targetEntityId, replaceWithEntityId, parents))
    targetEntityId.addState(ReplaceState.Relabel(replaceWithEntityId, parents))
    replaceWithEntityId.addState(ReplaceWithState.Relabel(targetEntityId))
  }

  private fun removeWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId?) {
    removeOperations.add(RemoveElement(targetEntityId))
    targetEntityId.addState(ReplaceState.Remove)
    replaceWithEntityId?.addState(ReplaceWithState.NoChangeTraceLost)
  }

  private fun doNothingOn(targetEntityId: EntityId, replaceWithEntityId: EntityId?) {
    targetEntityId.addState(ReplaceState.NoChange(replaceWithEntityId))
    replaceWithEntityId?.addState(ReplaceWithState.NoChange(targetEntityId))
  }

  private fun EntityId.addState(state: ReplaceState) {
    val currentState = targetState[this]
    require(currentState == null) {
      "Unexpected existing state for $this: $currentState"
    }
    targetState[this] = state
  }

  private fun EntityId.addState(state: ReplaceWithState) {
    val currentState = replaceWithState[this]
    require(currentState == null)
    replaceWithState[this] = state
  }

  private fun findEntityInTargetStore(replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                                      targetParentEntityId: EntityId,
                                      childClazz: Int): WorkspaceEntityData<out WorkspaceEntity>? {
    var targetEntityData1: WorkspaceEntityData<out WorkspaceEntity>?
    val targetEntityIds = childrenInTarget(targetParentEntityId, childClazz)
    val targetChildrenMap = makeEntityDataCollection(targetEntityIds, targetStorage)
    targetEntityData1 = targetChildrenMap.removeSome(replaceWithEntityData)
    while (targetEntityData1 != null && targetState[targetEntityData1.createEntityId()] != null) {
      targetEntityData1 = targetChildrenMap.removeSome(replaceWithEntityData)
    }
    return targetEntityData1
  }


  private fun makeEntityDataCollection(targetChildEntityIds: List<ChildEntityId>,
                                       storage: AbstractEntityStorage): Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>> {
    val targetChildrenMap = Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>>(
      targetChildEntityIds.size,
      EntityDataStrategy())
    targetChildEntityIds.forEach { id ->
      val value = storage.entityDataByIdOrDie(id.id)
      addValueToMap(targetChildrenMap, value)
    }
    return targetChildrenMap
  }

  private fun addValueToMap(targetChildrenMap: Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>>,
                            value: WorkspaceEntityData<out WorkspaceEntity>) {
    val existingValue = targetChildrenMap[value]
    targetChildrenMap[value] = if (existingValue != null) existingValue + value else listOf(value)
  }

  private val targetChildrenCache = HashMap<EntityId, Map<ConnectionId, List<ChildEntityId>>>()
  private val replaceWithChildrenCache = HashMap<EntityId, Map<ConnectionId, List<ChildEntityId>>>()

  private fun childrenInReplaceWith(entityId: EntityId?, childClazz: Int): List<ChildEntityId> {
    return childrenInStorage(entityId, childClazz, replaceWithStorage, replaceWithChildrenCache)
  }

  private fun childrenInTarget(entityId: EntityId?, childClazz: Int): List<ChildEntityId> {
    return childrenInStorage(entityId, childClazz, targetStorage, targetChildrenCache)
  }

  companion object {
    private fun childrenInStorage(entityId: EntityId?,
                                  childrenClass: Int,
                                  storage: AbstractEntityStorage,
                                  childrenCache: HashMap<EntityId, Map<ConnectionId, List<ChildEntityId>>>): List<ChildEntityId> {
      val targetEntityIds = if (entityId != null) {
        val targetChildren = childrenCache.getOrPut(entityId) { storage.refs.getChildrenRefsOfParentBy(entityId.asParent()) }

        val targetFoundChildren = targetChildren.filterKeys {
          sameClass(it.childClass, childrenClass, it.connectionType)
        }
        require(targetFoundChildren.size < 2) { "Got unexpected amount of children" }

        if (targetFoundChildren.isEmpty()) {
          emptyList()
        }
        else {
          val (_, targetChildEntityIds) = targetFoundChildren.entries.single()
          targetChildEntityIds
        }
      }
      else {
        emptyList()
      }
      return targetEntityIds
    }

    /**
     * Search entity from [oppositeStorage] in [goalStorage]
     */
    private fun findRootEntityInStorage(rootEntity: WorkspaceEntityBase,
                                        goalStorage: AbstractEntityStorage,
                                        oppositeStorage: AbstractEntityStorage,
                                        goalState: Long2ObjectMap<out Any>): WorkspaceEntity? {
      return if (rootEntity is WorkspaceEntityWithSymbolicId) {
        val symbolicId = rootEntity.symbolicId
        goalStorage.resolve(symbolicId)
      }
      else {
        val oppositeEntityData = oppositeStorage.entityDataByIdOrDie(rootEntity.id)
        goalStorage.entities(rootEntity.id.clazz.findWorkspaceEntity())
          .filter {
            val itId = (it as WorkspaceEntityBase).id
            if (goalState[itId] != null) return@filter false
            goalStorage.entityDataByIdOrDie(itId).equalsByKey(oppositeEntityData) && goalStorage.refs.getParentRefsOfChild(itId.asChild())
              .isEmpty()
          }
          .firstOrNull()
      }
    }
  }
}

typealias DataCache = Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>>

internal data class RelabelElement(val targetEntityId: EntityId, val replaceWithEntityId: EntityId, val parents: Set<ParentsRef>?)
internal data class RemoveElement(val targetEntityId: EntityId)
internal data class AddElement(val parents: Set<ParentsRef>?, val replaceWithSource: EntityId)

internal sealed interface ReplaceState {
  data class Relabel(val replaceWithEntityId: EntityId, val parents: Set<ParentsRef>? = null) : ReplaceState
  data class NoChange(val replaceWithEntityId: EntityId?) : ReplaceState
  object Remove : ReplaceState
}

internal sealed interface ReplaceWithState {
  object ElementMoved : ReplaceWithState
  data class NoChange(val targetEntityId: EntityId) : ReplaceWithState
  data class Relabel(val targetEntityId: EntityId) : ReplaceWithState
  object NoChangeTraceLost : ReplaceWithState
}

sealed interface ParentsRef {
  data class TargetRef(val targetEntityId: EntityId) : ParentsRef
  data class AddedElement(val replaceWithEntityId: EntityId) : ParentsRef
}

private class TrackToParents(
  val entity: EntityId,
  private val storage: AbstractEntityStorage
) {
  private var cachedParents: List<TrackToParents>? = null

  val parents: List<TrackToParents>
    get() {
      if (cachedParents == null) {
        cachedParents = storage.refs.getParentRefsOfChild(entity.asChild()).values.map { parentEntityId ->
          TrackToParents(parentEntityId.id, storage)
        }
      }
      return cachedParents!!
    }
}
