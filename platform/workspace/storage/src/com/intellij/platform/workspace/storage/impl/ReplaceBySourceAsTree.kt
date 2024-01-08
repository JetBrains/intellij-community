// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.impl

import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ReplaceBySourceAsTree.OperationsApplier
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.util.*

/**
 * This is map ot entities to itself to get quick get by equals.
 * In the majority of cases the list should contain a single element - the key itself, but it may contain more elements
 *   if there are multiple entities are equal
 */
private typealias WorkspaceEntityDataCustomCollection = MutableMap<WorkspaceEntityData<out WorkspaceEntity>,
  MutableList<WorkspaceEntityData<out WorkspaceEntity>>>

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
internal class ReplaceBySourceAsTree {

  private lateinit var targetStorage: MutableEntityStorageImpl
  private lateinit var replaceWithStorage: AbstractEntityStorage
  private lateinit var entityFilter: (EntitySource) -> Boolean

  internal val replaceOperations = ArrayList<RelabelElement>()
  internal val removeOperations = ArrayList<RemoveElement>()
  internal val addOperations = ArrayList<AddElement>()
  internal val targetState = Long2ObjectOpenHashMap<ReplaceState>()
  internal val replaceWithState = Long2ObjectOpenHashMap<ReplaceWithState>()

  private val listOfParentsWithPotentiallyBrokenChildrenOrder = HashSet<ParentsRef>()

  @set:TestOnly
  internal var shuffleEntities: Long = -1L

  /**
   * This caches parents to children association. However, it's mutable, and we remove children while we find an associated entity
   */
  private val replaceWithParentToChildrenCache = HashMap<Pair<EntityId?, Int>, WorkspaceEntityDataCustomCollection>()

  fun replace(
    targetStorage: MutableEntityStorageImpl,
    replaceWithStorage: AbstractEntityStorage,
    entityFilter: (EntitySource) -> Boolean,
  ) {
    this.targetStorage = targetStorage
    this.replaceWithStorage = replaceWithStorage
    this.entityFilter = entityFilter

    // Process entities from the target storage
    val targetEntitiesToReplace = targetStorage.entitiesBySource(entityFilter).toList()
    val targetEntities = targetEntitiesToReplace.maybeShuffled()
    for (targetEntityToReplace in targetEntities) {
      TargetProcessor().processEntity(targetEntityToReplace)
    }

    // Process entities from the replaceWith storage
    val replaceWithEntitiesToReplace = replaceWithStorage.entitiesBySource(entityFilter)
    val replaceWithEntities = replaceWithEntitiesToReplace.toList().maybeShuffled()
    for (replaceWithEntityToReplace in replaceWithEntities) {
      ReplaceWithProcessor().processEntity(replaceWithEntityToReplace)
    }

    LOG.trace {
      // This method can be used for debugging
      OperationsApplier().dumpOperations()
    }

    // Apply collected operations on the target storage
    OperationsApplier().apply()
  }

  // This class is just a wrapper to combine functions logically
  private inner class OperationsApplier {
    fun apply() {
      val replaceToTarget = HashBiMap.create<EntityId, EntityId>()
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
        // Here we get all parents which should be associated with the current entity in the target storage
        targetStorage.modifyEntity(WorkspaceEntity.Builder::class.java, targetEntity) {
          (this as ModifiableWorkspaceEntityBase<*, *>).relabel(replaceWithEntity, parents)
        }
        targetStorage.indexes.updateExternalMappingForEntityId(operation.replaceWithEntityId, operation.targetEntityId,
                                                               replaceWithStorage.indexes)
      }

      for (removeOperation in removeOperations) {
        targetStorage.removeEntityByEntityId(removeOperation.targetEntityId)
      }

      listOfParentsWithPotentiallyBrokenChildrenOrder.forEach { parentsRef ->
        when (parentsRef) {
          is ParentsRef.AddedElement -> {
            // todo this case should also be processed. Not necessary in this place
          }
          is ParentsRef.TargetRef -> {
            val targetParent = parentsRef.targetEntityId
            val replaceWithParent = replaceWithEntityFromState(targetParent) ?: return@forEach
            val children = targetStorage.refs.getChildrenRefsOfParentBy(targetParent.asParent())
            children.forEach inner@{ connectionId, childEntityIds ->
              if (childEntityIds.size < 2) return@inner

              // Here we use the following approach to fix ordering:
              // - Associate children with its indexes
              // - For the children we can find an associated entity in replaceWith storage, change index to the index of child in replaceWith
              // - Sort children by index
              // This gives more-or-less sensible order of children
              val replaceWithChildren = childrenInReplaceForOrdering(replaceWithParent, childEntityIds.first().id.clazz)
                .mapIndexed { index, childEntityId -> childEntityId.id to index }
                .toMap()
              val sortedChildren = childEntityIds.mapIndexed { index, childEntityId ->
                val replaceWithChildAssociatedWithCurrentElement = replaceToTarget.inverse()[childEntityId.id]
                                                                   ?: replaceWithEntityFromState(childEntityId.id)
                val myIndex = replaceWithChildAssociatedWithCurrentElement?.let { replaceWithChildren[it] } ?: index
                childEntityId to myIndex
              }.sortedBy { it.second }.map { it.first }
              val modifications = targetStorage.refs.replaceChildrenOfParent(connectionId, targetParent.asParent(), sortedChildren)
              targetStorage.createReplaceEventsForUpdates(modifications, connectionId)
            }
          }
        }
      }
    }

    private fun replaceWithEntityFromState(targetParent: EntityId): EntityId? {
      val replaceWithState = targetState[targetParent] ?: return null
      return when (replaceWithState) {
        is ReplaceState.NoChange -> replaceWithState.replaceWithEntityId ?: return null
        is ReplaceState.Relabel -> replaceWithState.replaceWithEntityId
        is ReplaceState.Remove -> return null
      }
    }

    private fun addElement(parents: Set<EntityId>?, replaceWithDataSource: EntityId, replaceToTarget: HashBiMap<EntityId, EntityId>) {
      val targetParents = mutableListOf<WorkspaceEntity>()
      parents?.forEach { parent ->
        targetParents += targetStorage.entityDataByIdOrDie(parent).createEntity(targetStorage)
      }

      val modifiableEntity = replaceWithStorage.entityDataByIdOrDie(replaceWithDataSource).createDetachedEntity(targetParents)
      modifiableEntity as ModifiableWorkspaceEntityBase<out WorkspaceEntity, out WorkspaceEntityData<*>>

      // We actually bind parents in [createDetachedEntity], but we can't do it for external entities (that are defined in a separate module)
      // Here we bind them again, so I guess we can remove "parents binding" from [createDetachedEntity], but let's do it twice for now.
      // Actually, I hope to get rid of [createDetachedEntity] at some moment.
      targetParents.groupBy { it::class }.forEach { (_, entities) ->
        modifiableEntity.updateReferenceToEntity(entities.first().getEntityInterface(), false, entities)
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
          appendLine(infoOf(addOperation.replaceWithSource, replaceWithStorage, true, addOperation.debugMsg))
          replaceWithEntities += addOperation.replaceWithSource
          if (addOperation.parents == null) {
            appendLine("No parent entities")
          }
          else {
            appendLine("Parents:")
            addOperation.parents.forEach { parent ->
              when (parent) {
                is ParentsRef.AddedElement -> {
                  appendLine(
                    "   - ${infoOf(parent.replaceWithEntityId, replaceWithStorage, true, addOperation.debugMsg)} <--- New Added Entity")
                  replaceWithEntities += parent.replaceWithEntityId
                }
                is ParentsRef.TargetRef -> {
                  appendLine("   - ${infoOf(parent.targetEntityId, targetStorage, true, addOperation.debugMsg)} <--- Existing Entity")
                  targetEntities += parent.targetEntityId
                }
              }
            }
          }
          appendLine()
        }

        appendLine("---- No More New Entities -------")
        appendLine("---- Removes -------")

        removeOperations.map { it.targetEntityId to it.debugMsg }.forEach { (entityId, debugMsg) ->
          appendLine(infoOf(entityId, targetStorage, true, debugMsg))
          targetEntities += entityId
        }

        appendLine("---- No More Removes -------")
        appendLine()
        appendLine("---- Replaces -------")

        replaceOperations.forEach { operation ->
          appendLine(
            infoOf(operation.targetEntityId, targetStorage, true)
            + " -> "
            + infoOf(operation.replaceWithEntityId, replaceWithStorage, true)
            + " | "
            + "Count of parents: ${operation.parents?.size}"
            + " | msg: ${operation.debugMsg}"
          )
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

    private fun infoOf(entityId: EntityId, store: AbstractEntityStorage, short: Boolean, debugMsg: String? = null): String {
      val entityData = store.entityDataByIdOrDie(entityId)
      val entity = entityData.createEntity(store)
      val debugMessage = if (debugMsg != null) " | msg: $debugMsg" else ""
      return if (entity is WorkspaceEntityWithSymbolicId) entity.symbolicId.toString() + debugMessage else if (short) "$entity$debugMessage" else "$entity | $entityData$debugMessage"
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class ReplaceWithProcessor {
    fun processEntity(replaceWithEntity: WorkspaceEntity) {
      replaceWithEntity as WorkspaceEntityBase

      if (replaceWithState[replaceWithEntity.id] != null) return
      processEntity(TrackToParents(replaceWithEntity.id, replaceWithStorage))
    }

    @Suppress("MoveVariableDeclarationIntoWhen")
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
          return if (parentsAssociation.isNotEmpty()) {
            val targetEntityData = parentsAssociation.filterIsInstance<ParentsRef.TargetRef>().firstNotNullOfOrNull { parent ->
              findEntityInTargetStore(replaceWithEntityData, parent.targetEntityId, replaceWithEntityId.clazz, emptySet())
            }
            val targetEntity = targetEntityData?.createEntity(targetStorage) as? WorkspaceEntityBase

            processExactEntity(targetEntity, replaceWithEntity, parentsAssociation)
          }
          else {
            replaceWithEntityId.addState(ReplaceWithState.NoChangeTraceLost)
            null
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
        return if (entityFilter(replaceWithEntity.entitySource)) {
          addSubtree(parents, replaceWithEntityId)
          ParentsRef.AddedElement(replaceWithEntityId)
        }
        else {
          replaceWithEntityId.addState(ReplaceWithState.NoChangeTraceLost)
          null
        }
      }
      else {

        val targetEntityId = (targetEntity as WorkspaceEntityBase).id

        @Suppress("MoveVariableDeclarationIntoWhen")
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
              replaceWorkspaceData(targetEntity.id, replaceWithEntity.id, parents,
                                   "ReplaceWithProcessor, process exact entity, both entities match filter")
            }
            else {
              doNothingOn(targetEntityId, replaceWithEntityId)
            }
            return ParentsRef.TargetRef(targetEntityId)
          }
          entityFilter(targetEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
            removeWorkspaceData(targetEntity.id, replaceWithEntity.id,
                                "ReplaceWithProcessor, Process exact entity, replaceWith entity doesn't match filter")
            return null
          }
          !entityFilter(targetEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
            return if (targetEntity is WorkspaceEntityWithSymbolicId) {
              if (replaceWithEntity.entitySource !is DummyParentEntitySource) {
                replaceWorkspaceData(targetEntityId, replaceWithEntityId, parents,
                                     "ReplaceWithProcessor, Process exact entity, target entity doesn't match filter")
              }
              else {
                doNothingOn(targetEntityId, replaceWithEntityId)
              }
              ParentsRef.TargetRef(targetEntityId)
            }
            else {
              addSubtree(parents, replaceWithEntityId)
              ParentsRef.AddedElement(replaceWithEntityId)
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

    @Suppress("MoveVariableDeclarationIntoWhen")
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
     * This is a very similar thing as [TargetProcessor.findSameEntity]. But it finds an entity in the target storage (or the entity that will be added)
     *
     * In [exceptTargetIds] you can define a set of ids where fill be filtered while searching.
     */
    @Suppress("MoveVariableDeclarationIntoWhen")
    fun findSameEntityInTargetStore(replaceWithTrack: TrackToParents, exceptTargetIds: Set<EntityId> = emptySet()): ParentsRef? {

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
        return findAndReplaceRootEntityInTargetStore(replaceWithEntityData.createEntity(replaceWithStorage) as WorkspaceEntityBase)
      }
      else {
        val parentsAssociation = replaceWithTrack.parents.associateWith { findSameEntityInTargetStore(it) }
        val entriesList = parentsAssociation.entries.toList()

        var isNewParent = false
        // Going through the parents of searchable entity and looking for it in target storage via parent's refs
        // to detect is it a new entity or existing one
        for (i in entriesList.indices) {
          val value = entriesList[i].value
          if (value is ParentsRef.TargetRef) {
            val targetEntityData = findEntityInTargetStore(replaceWithEntityData, value.targetEntityId, replaceWithTrack.entity.clazz,
                                                           exceptTargetIds)
            if (targetEntityData != null) {
              return targetEntityData.createEntityId().let { ParentsRef.TargetRef(it) }
            }
            else {
              // If target parent didn't change, but we can't find desired existing child, we suppose it's new child
              isNewParent = true
            }
          }
          else if (value is ParentsRef.AddedElement) {
            // The simplest case if the entity was already marked as new
            isNewParent = true
          }
        }
        return if (isNewParent) ParentsRef.AddedElement(replaceWithTrack.entity) else null
      }
    }

    private fun addSubtree(parents: Set<ParentsRef>?, replaceWithEntityId: EntityId) {
      @Suppress("MoveVariableDeclarationIntoWhen")
      val currentState = replaceWithState[replaceWithEntityId]
      when (currentState) {
        ReplaceWithState.ElementMoved -> return
        is ReplaceWithState.NoChange -> error("Unexpected state")
        ReplaceWithState.NoChangeTraceLost -> error("Unexpected state")
        is ReplaceWithState.Relabel -> error("Unexpected state")
        null -> Unit
      }

      addElementOperation(parents, replaceWithEntityId, "Adding a subtree for ${replaceWithEntityId.asString()}")

      replaceWithStorage.refs.getChildrenRefsOfParentBy(replaceWithEntityId.asParent()).values.flatten().forEach { childEntityId ->
        val replaceWithChildEntityData = replaceWithStorage.entityDataByIdOrDie(childEntityId.id)
        if (!entityFilter(replaceWithChildEntityData.entitySource)) return@forEach
        val trackToParents = TrackToParents(childEntityId.id, replaceWithStorage)
        val sameEntity = findSameEntityInTargetStore(trackToParents)
        if (sameEntity is ParentsRef.TargetRef) {
          return@forEach
        }
        val otherParents = trackToParents.parents.mapNotNull { findSameEntityInTargetStore(it) }
        addSubtree((otherParents + ParentsRef.AddedElement(replaceWithEntityId)).toSet(), childEntityId.id)
      }
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class TargetProcessor {
    fun processEntity(targetEntityToReplace: WorkspaceEntity) {
      targetEntityToReplace as WorkspaceEntityBase

      // This method not only finds the same entity in the ReplaceWith storage, but also processes all entities it meets.
      // So, for processing an entity, it's enough to call this method on the entity.
      findSameEntity(TrackToParents(targetEntityToReplace.id, targetStorage))
    }


    /**
     * This method searched for the "associated" entity of [targetEntityTrack] in the replaceWith storage
     * Here, let's use "associated" terming to define what we're looking for. If the entity have a [SymbolicEntityId],
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
     *    - Based on found parents in replaceWith storage, find an associated entity for our currently searched entity
     */
    private fun findSameEntity(targetEntityTrack: TrackToParents): EntityId? {

      // Check if this entity was already processed
      val targetEntityState = targetState[targetEntityTrack.entity]
      if (targetEntityState != null) {
        return when (targetEntityState) {
          is ReplaceState.NoChange -> targetEntityState.replaceWithEntityId
          is ReplaceState.Relabel -> targetEntityState.replaceWithEntityId
          ReplaceState.Remove -> null
        }
      }

      val targetEntityData = targetStorage.entityDataByIdOrDie(targetEntityTrack.entity)
      return if (targetEntityTrack.parents.isEmpty()) {
        // If the entity doesn't have parents, it's a root entity for this subtree (subgraph?)
        findAndReplaceRootEntity(targetEntityData)
      }
      else {
        val (targetParents, replaceWithEntity) = processParentsFromReplaceWithStorage(targetEntityTrack, targetEntityData)

        processExactEntity(targetParents, targetEntityData, replaceWithEntity)
      }
    }

    private fun processExactEntity(targetParents: MutableSet<ParentsRef>?,
                                   targetEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                                   replaceWithEntity: WorkspaceEntityBase?): EntityId? {
      // Here we check if any of the required parents is missing in our new parents
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
        return when (entityFilter(targetEntity.entitySource)) {
          true -> {
            removeWorkspaceData(targetEntity.id, null, "Remove entity, can't find entity in replaceWith")
            null
          }
          false -> {
            doNothingOn(targetEntity.id, null)
            null
          }
        }
      }
      else {
        when {
          entityFilter(targetEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
            if (replaceWithEntity.entitySource !is DummyParentEntitySource) {
              replaceWorkspaceData(targetEntity.id, replaceWithEntity.id, targetParents,
                                   "TargetProcessor, process exact entity, both entities match filter")
            }
            else {
              doNothingOn(targetEntity.id, replaceWithEntity.id)
            }
            return replaceWithEntity.id
          }
          entityFilter(targetEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
            removeWorkspaceData(targetEntity.id, replaceWithEntity.id,
                                "TargetProcessor, process exact entity, replaceWith entity doesn't match filter")
            return null
          }
          !entityFilter(targetEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
            if (replaceWithEntity.entitySource !is DummyParentEntitySource) {
              replaceWorkspaceData(targetEntity.id, replaceWithEntity.id, targetParents,
                                   "TargetProcessor, process exact entity, target entity doesn't match filter")
            }
            else {
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
        // What do we have at this moment:
        // - Child entity in target storage
        // - Parents of this entity in target storage
        // - To every parent in target storage we have an associated parent in replaceWith storage
        // What we need: find child in replaceWith storage that is the same as our child from target storage
        //
        // Approach used here:
        // - For every parent in replaceWith storage we get a list of children and select ones that are equal to our child
        // In the most basic scenario we'll get a single child for every parent that is our "associated" child
        // However, it may happen that two children of parent are equals to each other or two separate entities from two separate parents
        //   are also equals.
        // So, we select one of these children and say "now you're associated child in replaceWith storage"
        // The one child is selected that is presented in most amount of parents. So, the "associated" child and our target child have
        //   the most common parents.

        val replaceWithParentsCounter = HashMap<EntityId, Int>() // replaceWith child id to amount of parents where it's presented

        val parentAssociationsWithChildren = targetEntityTrack
          .parents.associateWith { findSameEntity(it) } // Find a parent in replaceWith storage to each of our parents
          .map { (targetParent, replaceWithParent) ->

            // The map contains entity data to itself association. See the doc for the map for explanation
            val replaceWithChildrenOfParent = replaceWithParentToChildrenCache.getOrPut(
              replaceWithParent to targetEntityTrack.entity.clazz) {
              childrenInReplaceWith(replaceWithParent, targetEntityTrack.entity.clazz)
            }

            val mutableListOfEqualEntities = replaceWithChildrenOfParent[targetEntityData]
            val replaceWithChildrenOptions = mutableListOfEqualEntities
                                               ?.filter { replaceWithState[it.createEntityId()] == null } // Filter entities that are already processed
                                               ?.map { it.createEntityId() }
                                               ?.toSet() ?: emptySet()

            // For every child count in what amount of parents it's presented
            replaceWithChildrenOptions.forEach { replaceWithChildOption ->
              replaceWithParentsCounter[replaceWithChildOption] = (replaceWithParentsCounter[replaceWithChildOption] ?: 0) + 1
            }
            Triple(targetParent, replaceWithChildrenOptions, mutableListOfEqualEntities)
          }

        val mostCommonReplaceWithChildId = replaceWithParentsCounter.withMaxValue()?.maybeShuffled()?.firstOrNull()?.key
        if (mostCommonReplaceWithChildId != null) {
          parentAssociationsWithChildren.forEach { (targetParent, replaceWithChildren, replaceWithSimilarEntityData) ->
            if (mostCommonReplaceWithChildId in replaceWithChildren) {
              targetParents += ParentsRef.TargetRef(targetParent.entity)
              replaceWithSimilarEntityData?.removeIf { it.id == mostCommonReplaceWithChildId.arrayId }
            }
          }
          replaceWithEntity = replaceWithStorage.entityDataByIdOrDie(mostCommonReplaceWithChildId)
            .createEntity(replaceWithStorage) as WorkspaceEntityBase
        }
      }

      // And here we get other parent' of the associated entity.
      // This is actually a complicated operation because it's not enough just to find parents in replaceWith storage.
      //   We should also understand if this parent is a new entity, or it already exists in the target storage.
      if (replaceWithEntity != null) {
        val alsoTargetParents = TrackToParents(replaceWithEntity.id, replaceWithStorage).parents
          .map { ReplaceWithProcessor().findSameEntityInTargetStore(it, setOf(targetEntityData.createEntityId())) }
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

      val replaceWithEntity = findRootEntityInStorage(targetEntityData.createEntity(targetStorage) as WorkspaceEntityBase,
                                                      replaceWithStorage,
                                                      targetStorage, replaceWithState) as? WorkspaceEntityBase

      return processExactEntity(null, targetEntityData, replaceWithEntity)
    }
  }

  private fun addElementOperation(targetParentEntity: Set<ParentsRef>?, replaceWithEntity: EntityId, debugMsg: String) {
    targetParentEntity?.let { listOfParentsWithPotentiallyBrokenChildrenOrder += it }
    addOperations += AddElement(targetParentEntity, replaceWithEntity, debugMsg)
    replaceWithEntity.addState(ReplaceWithState.ElementMoved)
  }

  private fun replaceWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId, parents: Set<ParentsRef>?, debugMsg: String) {
    parents?.let { listOfParentsWithPotentiallyBrokenChildrenOrder += it }
    replaceOperations.add(RelabelElement(targetEntityId, replaceWithEntityId, parents, debugMsg))
    targetEntityId.addState(ReplaceState.Relabel(replaceWithEntityId, parents))
    replaceWithEntityId.addState(ReplaceWithState.Relabel(targetEntityId))
  }

  private fun removeWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId?, debugMsg: String) {
    removeOperations.add(RemoveElement(targetEntityId, debugMsg))
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

  private fun findEntityInTargetStore(
    replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>,
    targetParentEntityId: EntityId,
    childClazz: Int,
    exceptTargetIds: Set<EntityId>,
  ): WorkspaceEntityData<out WorkspaceEntity>? {
    val childrenInTargetEntityDataCollection = childrenInTarget(targetParentEntityId, childClazz)
    if (childrenInTargetEntityDataCollection.isEmpty()) return null
    var targetEntityData = childrenInTargetEntityDataCollection.removeSome(replaceWithEntityData, exceptTargetIds)
    while (targetEntityData != null && targetState[targetEntityData.createEntityId()] != null) {
      targetEntityData = childrenInTargetEntityDataCollection.removeSome(replaceWithEntityData, exceptTargetIds)
    }
    return targetEntityData
  }

  private fun WorkspaceEntityDataCustomCollection.removeSome(
    entityData: WorkspaceEntityData<out WorkspaceEntity>,
    exceptTargetIds: Set<EntityId>,
  ): WorkspaceEntityData<out WorkspaceEntity>? {
    val entityDataListWithSameHash = this[entityData] ?: return null
    if (entityDataListWithSameHash.isEmpty()) return null
    entityDataListWithSameHash.maybeShuffle()
    return entityDataListWithSameHash.removeFirst { it.createEntityId() !in exceptTargetIds }
  }

  private fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean): T? {
    if (isEmpty()) return null
    val index = indexOfFirst { predicate(it) }
    if (index == -1) return null
    return removeAt(index)
  }

  private val targetChildrenEntityDataCache = HashMap<EntityId, Map<ConnectionId, WorkspaceEntityDataCustomCollection>>()
  private val replaceWithChildrenEntityDataCache = HashMap<EntityId, Map<ConnectionId, WorkspaceEntityDataCustomCollection>>()

  private fun childrenInReplaceWith(entityId: EntityId?, childClazz: Int): WorkspaceEntityDataCustomCollection {
    return childrenInStorage(entityId, childClazz, replaceWithStorage, replaceWithChildrenEntityDataCache)
  }

  private fun childrenInTarget(entityId: EntityId, childClazz: Int): WorkspaceEntityDataCustomCollection {
    return childrenInStorage(entityId, childClazz, targetStorage, targetChildrenEntityDataCache)
  }

  private val replaceWithChildrenEntityIdCache = HashMap<EntityId, Map<ConnectionId, List<ChildEntityId>>>()

  private fun childrenInReplaceForOrdering(entityId: EntityId, childClazz: Int): List<ChildEntityId> {
    return childrenInStorageForOrdering(entityId, childClazz, replaceWithStorage, replaceWithChildrenEntityIdCache)
  }

  companion object {
    private val LOG = logger<ReplaceBySourceAsTree>()
    private val hashingStrategy = object : HashingStrategy<WorkspaceEntityData<out WorkspaceEntity>> {
      override fun hashCode(entityData: WorkspaceEntityData<out WorkspaceEntity>?): Int {
        return entityData?.hashCodeByKey() ?: 0
      }

      override fun equals(entityData1: WorkspaceEntityData<out WorkspaceEntity>?,
                          entityData2: WorkspaceEntityData<out WorkspaceEntity>?): Boolean {
        if (entityData1 === entityData2) return true
        if (entityData1 == null || entityData2 == null) return false
        return entityData1.equalsByKey(entityData2)
      }
    }

    private fun childrenInStorageForOrdering(entityId: EntityId, childrenClass: Int, storage: AbstractEntityStorage,
                                             childrenCache: HashMap<EntityId, Map<ConnectionId, List<ChildEntityId>>>): List<ChildEntityId> {
      val targetChildren = childrenCache.getOrPut(entityId) { storage.refs.getChildrenRefsOfParentBy(entityId.asParent()) }

      val targetFoundChildren = targetChildren.filterKeys {
        sameClass(it.childClass, childrenClass, it.connectionType)
      }
      require(targetFoundChildren.size < 2) { "Got unexpected amount of children" }

      if (targetFoundChildren.isEmpty()) return emptyList()
      return targetFoundChildren.entries.single().value
    }

    private fun childrenInStorage(entityId: EntityId?, childrenClass: Int, storage: AbstractEntityStorage,
                                  childrenCache: HashMap<EntityId, Map<ConnectionId, WorkspaceEntityDataCustomCollection>>): WorkspaceEntityDataCustomCollection {
      if (entityId == null) return mutableMapOf()

      val targetChildren = childrenCache.getOrPut(entityId) {
        storage.refs.getChildrenRefsOfParentBy(entityId.asParent()).mapValues { mapEntry ->
          val entityDataCollection = CollectionFactory.createCustomHashingStrategyMap<WorkspaceEntityData<*>, MutableList<WorkspaceEntityData<*>>>(
            hashingStrategy)
          mapEntry.value.forEach { childEntityId ->
            val entityData = storage.entityDataByIdOrDie(childEntityId.id)
            entityDataCollection.getOrPut(entityData) { mutableListOf() }.apply { add(entityData) }
          }
          entityDataCollection
        }
      }

      val targetFoundChildren = targetChildren.filterKeys {
        sameClass(it.childClass, childrenClass, it.connectionType)
      }
      require(targetFoundChildren.size < 2) { "Got unexpected amount of children" }

      if (targetFoundChildren.isEmpty()) return mutableMapOf()
      return targetFoundChildren.entries.single().value
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

  /**
   * Shuffle collection if the field [shuffleEntities] is not -1 (set in tests)
   */
  private fun <E> List<E>.maybeShuffled(): List<E> {
    if (shuffleEntities != -1L && this.size > 1) {
      return this.shuffled(Random(shuffleEntities))
    }
    return this
  }

  private fun <E> MutableList<E>.maybeShuffle() {
    if (shuffleEntities != -1L && this.size > 1) {
      this.shuffle(Random(shuffleEntities))
    }
  }
}

internal data class RelabelElement(val targetEntityId: EntityId,
                                   val replaceWithEntityId: EntityId,
                                   val parents: Set<ParentsRef>?,
                                   val debugMsg: String) {
  override fun toString(): String {
    return "RelabelElement(targetEntityId=${targetEntityId.asString()}, replaceWithEntityId=${replaceWithEntityId.asString()}, parents=$parents, debugMsg=$debugMsg)"
  }
}

internal data class RemoveElement(val targetEntityId: EntityId, val debugMsg: String) {
  override fun toString(): String {
    return "RemoveElement(targetEntityId=${targetEntityId.asString()}, debugMsg=$debugMsg)"
  }
}

internal data class AddElement(val parents: Set<ParentsRef>?, val replaceWithSource: EntityId, val debugMsg: String) {
  override fun toString(): String {
    return "AddElement(parents=$parents, replaceWithSource=${replaceWithSource.asString()}, debugMsg=$debugMsg)"
  }
}

internal sealed interface ReplaceState {
  data class Relabel(val replaceWithEntityId: EntityId, val parents: Set<ParentsRef>? = null) : ReplaceState
  data class NoChange(val replaceWithEntityId: EntityId?) : ReplaceState
  object Remove : ReplaceState
}

internal sealed interface ReplaceWithState {
  object ElementMoved : ReplaceWithState
  data class NoChange(val targetEntityId: EntityId) : ReplaceWithState {
    override fun toString(): String {
      return "NoChange(targetEntityId=${targetEntityId.asString()})"
    }
  }

  data class Relabel(val targetEntityId: EntityId) : ReplaceWithState {
    override fun toString(): String {
      return "Relabel(targetEntityId=${targetEntityId.asString()})"
    }
  }

  object NoChangeTraceLost : ReplaceWithState
}

internal sealed interface ParentsRef {
  data class TargetRef(val targetEntityId: EntityId) : ParentsRef {
    override fun toString(): String {
      return "TargetRef(targetEntityId=${targetEntityId.asString()})"
    }
  }

  data class AddedElement(val replaceWithEntityId: EntityId) : ParentsRef {
    override fun toString(): String {
      return "AddedElement(replaceWithEntityId=${replaceWithEntityId.asString()})"
    }
  }
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

private fun <K, R : Comparable<R>> Map<out K, R>.withMaxValue(): List<Map.Entry<K, R>>? {
  val maxValue = values.maxOrNull() ?: return null
  return entries.filter { it.value == maxValue }
}
