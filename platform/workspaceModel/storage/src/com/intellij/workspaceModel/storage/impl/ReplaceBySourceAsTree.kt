// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.*
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap

/**
 * # Replace By Source as a tree
 *
 * - Make type graphs. Separate graphs into independent parts (how is it called correctly?)
 * - Work on separate graph parts as on independent items
 * -
 */

internal class ReplaceBySourceAsTree : ReplaceBySourceOperation {

  private lateinit var targetStorage: MutableEntityStorageImpl
  private lateinit var replaceWithStorage: AbstractEntityStorage
  private lateinit var entityFilter: (EntitySource) -> Boolean

  internal val operations = HashMap<EntityId, Operation>()
  internal val addOperations = ArrayList<AddSubtree>()
  internal val targetState = HashMap<EntityId, ReplaceState>()
  internal val replaceWithState = HashMap<EntityId, ReplaceWithState>()


  override fun replace(
    targetStorage: MutableEntityStorageImpl,
    replaceWithStorage: AbstractEntityStorage,
    entityFilter: (EntitySource) -> Boolean,
  ) {
    this.targetStorage = targetStorage
    this.replaceWithStorage = replaceWithStorage
    this.entityFilter = entityFilter

    val targetEntitiesToReplace = targetStorage.entitiesBySource(entityFilter)
    val replaceWithEntitiesToReplace = replaceWithStorage.entitiesBySource(entityFilter)

    for (targetEntityToReplace in targetEntitiesToReplace.values.flatMap { it.values }.flatten()) {
      TargetProcessor().processEntity(targetEntityToReplace)
    }

    for (replaceWithEntityToReplace in replaceWithEntitiesToReplace.values.flatMap { it.values }.flatten()) {
      ReplaceWithProcessor().processEntity(replaceWithEntityToReplace)
    }

    OperationsApplier().apply()
  }

  // This class is just a wrapper to combine functions logically
  private inner class OperationsApplier {
    fun apply() {
      for (addOperation in addOperations) {
        addSubtree(addOperation.targetParent, addOperation.replaceWithSource)
      }

      for ((id, operation) in operations) {
        when (operation) {
          is Operation.Relabel -> {
            val targetEntity = targetStorage.entityDataByIdOrDie(id).createEntity(targetStorage)
            val replaceWithEntity = replaceWithStorage.entityDataByIdOrDie(operation.replaceWithEntityId).createEntity(replaceWithStorage)
            val parents = operation.parents?.mapTo(HashSet()) { targetStorage.entityDataByIdOrDie((it as ParentsRef.TargetRef).targetEntityId).createEntity(targetStorage) }
            targetStorage.modifyEntity(ModifiableWorkspaceEntity::class.java, targetEntity) {
              (this as ModifiableWorkspaceEntityBase<*>).relabel(replaceWithEntity, parents)
            }
            targetStorage.indexes.updateExternalMappingForEntityId(operation.replaceWithEntityId, id, replaceWithStorage.indexes)
          }
          Operation.Remove -> {
            targetStorage.removeEntityByEntityId(id)
          }
        }
      }
    }

    private fun addSubtree(parent: EntityId?, replaceWithDataSource: EntityId) {
      val targetParents = mutableListOf<WorkspaceEntity>()
      if (parent != null) {
        targetParents += targetStorage.entityDataByIdOrDie(parent).createEntity(targetStorage)
      }

      val entityData = replaceWithStorage.entityDataByIdOrDie(replaceWithDataSource).createDetachedEntity(targetParents)
      targetStorage.addEntity(entityData)
      targetStorage.indexes.updateExternalMappingForEntityId(replaceWithDataSource, (entityData as WorkspaceEntityBase).id,
                                                             replaceWithStorage.indexes)

      replaceWithStorage.refs.getChildrenRefsOfParentBy(replaceWithDataSource.asParent()).values.flatten().forEach {
        val replaceWithChildEntityData = replaceWithStorage.entityDataByIdOrDie(it.id)
        if (!entityFilter(replaceWithChildEntityData.entitySource)) return@forEach
        addSubtree(entityData.id, it.id)
      }
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class ReplaceWithProcessor {
    fun processEntity(replaceWithEntity: WorkspaceEntity) {
      replaceWithEntity as WorkspaceEntityBase

      if (replaceWithState[replaceWithEntity.id] != null) return

      val trackToParents = TrackToParents(replaceWithEntity.id)
      buildRootTrack(trackToParents, replaceWithStorage)

      processReplaceWithRootEntity(trackToParents)
    }

    @Suppress("MoveVariableDeclarationIntoWhen")
    private fun processReplaceWithRootEntity(trackToParents: TrackToParents) {
      val trackRoot = trackToParents.singleRoot()
      val replaceWithRootEntity = replaceWithStorage
        .entityDataByIdOrDie(trackRoot.entity)
        .createEntity(replaceWithStorage) as WorkspaceEntityBase
      val (continueProcess, targetRootEntityIdN) = findAndReplaceRootEntity(replaceWithRootEntity)
      if (!continueProcess) return

      assert(targetRootEntityIdN != null)
      var targetRootEntityId: EntityId = targetRootEntityIdN!!

      var replaceWithEntityTrack = trackRoot
      while (replaceWithEntityTrack.child != null) {
        replaceWithEntityTrack = replaceWithEntityTrack.child!!
        val replaceWithEntity = replaceWithEntityTrack.entity

        val replaceWithCurrentState = replaceWithState[replaceWithEntity]
        when (replaceWithCurrentState) {
          ReplaceWithState.SubtreeMoved -> break
          is ReplaceWithState.NoChange -> {
            targetRootEntityId = replaceWithCurrentState.targetEntityId
            continue
          }
          ReplaceWithState.NoChangeTraceLost -> break
          is ReplaceWithState.Relabel -> {
            targetRootEntityId = replaceWithCurrentState.targetEntityId
            continue
          }
          null -> {}
        }
        val replaceWithEntityData = replaceWithStorage.entityDataByIdOrDie(replaceWithEntity)
        if (entityFilter(replaceWithEntityData.entitySource)) {
          addOperations += AddSubtree(targetRootEntityId, replaceWithEntity)
          replaceWithEntity.addState(ReplaceWithState.SubtreeMoved)
          break
        } else {
          // Searching for the associated entity
          val children = targetStorage.refs.getChildrenRefsOfParentBy(targetRootEntityId.asParent())
          val targetChildrenIds = children.filterKeys { sameClass(it.childClass, replaceWithEntity.clazz, it.connectionType) }
          require(targetChildrenIds.size < 2) { "Unexpected amount of children" }

          val ids = if (targetChildrenIds.isEmpty()) {
            emptyList()
          } else {
            targetChildrenIds.entries.single().value
          }

          val targetChildrenMap = Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>>(EntityDataStrategy())
          ids.forEach { id ->
            targetStorage.entityDataByIdOrDie(id.id).also {
              val existing = targetChildrenMap[it]
              targetChildrenMap[it] = if (existing != null) existing + it else listOf(it)
            }
          }

          val targetRelatedEntity = targetChildrenMap.removeSome(replaceWithEntityData)

          if (targetRelatedEntity == null) {
            replaceWithEntity.addState(ReplaceWithState.NoChangeTraceLost)
            break
          } else {
            val newTargetRootEntityId = targetRelatedEntity.createEntityId()
            replaceWithEntity.addState(ReplaceWithState.NoChange(newTargetRootEntityId))
            targetRootEntityId = newTargetRootEntityId
          }
        }
      }
    }

    @Suppress("MoveVariableDeclarationIntoWhen")
    private fun findAndReplaceRootEntity(replaceWithRootEntity: WorkspaceEntityBase): Pair<Boolean, EntityId?> {
      val currentState = replaceWithState[replaceWithRootEntity.id]
      when (currentState) {
        ReplaceWithState.SubtreeMoved -> return false to null
        is ReplaceWithState.NoChange -> return true to currentState.targetEntityId
        ReplaceWithState.NoChangeTraceLost -> return false to null
        is ReplaceWithState.Relabel -> return true to currentState.targetEntityId
        null -> Unit
      }

      val targetEntity = findEntityInTargetStorage(replaceWithRootEntity)
      if (targetEntity == null) {
        if (entityFilter(replaceWithRootEntity.entitySource)) {
          addOperations += AddSubtree(null, replaceWithRootEntity.id)
          replaceWithRootEntity.id.addState(ReplaceWithState.SubtreeMoved)
          return false to null
        }
        else {
          replaceWithRootEntity.id.addState(ReplaceWithState.NoChangeTraceLost)
          return false to null
        }
      }

      val targetEntityId = (targetEntity as WorkspaceEntityBase).id
      val targetCurrentState = targetState[targetEntityId]
      when (targetCurrentState) {
        is ReplaceState.NoChange -> return true to targetEntityId
        is ReplaceState.Relabel -> return true to targetEntityId
        ReplaceState.Remove -> return false to targetEntityId
        null -> Unit
      }

      when {
        entityFilter(replaceWithRootEntity.entitySource) && entityFilter(targetEntity.entitySource) -> {
          error("This branch should be already processed because we process 'target' entities first")
        }
        entityFilter(replaceWithRootEntity.entitySource) && !entityFilter(targetEntity.entitySource) -> {
          replaceWorkspaceData(targetEntity.id, replaceWithRootEntity.id, null)
          return true to targetEntityId
        }
        !entityFilter(replaceWithRootEntity.entitySource) && entityFilter(targetEntity.entitySource) -> {
          error("This branch should be already processed because we process 'target' entities first")
        }
        !entityFilter(replaceWithRootEntity.entitySource) && !entityFilter(targetEntity.entitySource) -> {
          doNothingOn(targetEntity.id, replaceWithRootEntity.id)
          return true to targetEntityId
        }
      }

      error("Unexpected branch")
    }

    fun findEntityInTargetStorage(replaceWithRootEntity: WorkspaceEntityBase): WorkspaceEntity? {
      return if (replaceWithRootEntity is WorkspaceEntityWithPersistentId) {
        val persistentId = replaceWithRootEntity.persistentId
        targetStorage.resolve(persistentId)
      } else {
        targetStorage.entities(replaceWithRootEntity.id.clazz.findWorkspaceEntity())
          .filter {
            targetStorage.entityDataByIdOrDie((it as WorkspaceEntityBase).id) == replaceWithStorage.entityDataByIdOrDie(replaceWithRootEntity.id)
          }
          .firstOrNull()
      }
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class TargetProcessor {
    fun processEntity(targetEntityToReplace: WorkspaceEntity) {
      targetEntityToReplace as WorkspaceEntityBase

      val trackToParents = TrackToParents(targetEntityToReplace.id)
      buildRootTrack(trackToParents, targetStorage)
      findSameEntity(trackToParents)
    }

    private fun findSameEntityInTargetStore(replaceWithTrack: TrackToParents): EntityId? {
      val parentsAssociation = replaceWithTrack.parents.associateWith { findSameEntityInTargetStore(it) }
      val replaceWithEntityData = replaceWithStorage.entityDataByIdOrDie(replaceWithTrack.entity)

      val replaceWithCurrentState = replaceWithState[replaceWithTrack.entity]
      when (replaceWithCurrentState) {
        is ReplaceWithState.NoChange -> return replaceWithCurrentState.targetEntityId
        ReplaceWithState.NoChangeTraceLost -> return null
        is ReplaceWithState.Relabel -> return replaceWithCurrentState.targetEntityId
        ReplaceWithState.SubtreeMoved -> TODO()
        null -> Unit
      }

      if (replaceWithTrack.parents.isEmpty()) {
        val targetRootEntityId = findAndReplaceRootEntityInTargetStore(
          replaceWithEntityData.createEntity(replaceWithStorage) as WorkspaceEntityBase)
        return targetRootEntityId
      } else {
        val entriesList = parentsAssociation.entries.toList()

        val targetParents = mutableSetOf<EntityId>()
        var targetEntityData: WorkspaceEntityData<out WorkspaceEntity>? = null
        for (i in entriesList.indices) {
          val targetEntityIds = childrenInStorage(entriesList[i].value, replaceWithTrack.entity.clazz, targetStorage)
          val targetChildrenMap = makeEntityDataCollection(targetEntityIds, targetStorage)
          targetEntityData = targetChildrenMap.removeSome(replaceWithEntityData)
          while (targetEntityData != null && replaceWithState[targetEntityData.createEntityId()] != null) {
            targetEntityData = targetChildrenMap.removeSome(replaceWithEntityData)
          }
          if (targetEntityData != null) {
            targetParents += entriesList[i].key.entity
            break
          }
        }
        return targetEntityData?.createEntityId()
      }
    }

    private fun findSameEntity(targetEntityTrack: TrackToParents): EntityId? {
      val parentsAssociation = targetEntityTrack.parents.associateWith { findSameEntity(it) }
      val targetEntityData = targetStorage.entityDataByIdOrDie(targetEntityTrack.entity)

      val targetEntityState = targetState[targetEntityTrack.entity]
      if (targetEntityState != null) {
        when (targetEntityState) {
          is ReplaceState.NoChange -> return targetEntityState.replaceWithEntityId
          is ReplaceState.Relabel -> return targetEntityState.replaceWithEntityId
          ReplaceState.Remove -> return null
        }
      }

      if (targetEntityTrack.parents.isEmpty()) {
        val replaceWithRootEntityId = findAndReplaceRootEntity(targetEntityData.createEntity(targetStorage) as WorkspaceEntityBase)
        return replaceWithRootEntityId
      }
      else {
        val entriesList = parentsAssociation.entries.toList()

        val targetParents = mutableSetOf<EntityId>()
        var index = 0
        var replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>? = null
        for (i in entriesList.indices) {
          index = i
          val replaceWithEntityIds = childrenInStorage(entriesList[i].value, targetEntityTrack.entity.clazz, replaceWithStorage)
          val replaceWithChildrenMap = makeEntityDataCollection(replaceWithEntityIds, replaceWithStorage)
          replaceWithEntityData = replaceWithChildrenMap.removeSome(targetEntityData)
          while (replaceWithEntityData != null && replaceWithState[replaceWithEntityData.createEntityId()] != null) {
            replaceWithEntityData = replaceWithChildrenMap.removeSome(targetEntityData)
          }
          if (replaceWithEntityData != null) {
            targetParents += entriesList[i].key.entity
            break
          }
        }

        entriesList.drop(index).forEach { tailItem ->
          val replaceWithEntityIds = childrenInStorage(tailItem.value, targetEntityTrack.entity.clazz, replaceWithStorage)
          val replaceWithChildrenMap = makeEntityDataCollection(replaceWithEntityIds, replaceWithStorage)
          var replaceWithMyEntityData = replaceWithChildrenMap.removeSome(targetEntityData)
          while (replaceWithMyEntityData != null && replaceWithEntityData!!.createEntityId() != replaceWithMyEntityData.createEntityId()) {
            replaceWithMyEntityData = replaceWithChildrenMap.removeSome(targetEntityData)
          }
          if (replaceWithMyEntityData != null) {
            targetParents += tailItem.key.entity
          }
        }

        if (replaceWithEntityData != null) {
          val replaceWithTrackToParents = TrackToParents(replaceWithEntityData.createEntityId())
          buildRootTrack(replaceWithTrackToParents, replaceWithStorage)
          val alsoTargetParents = replaceWithTrackToParents.parents.map { findSameEntityInTargetStore(it) }
          targetParents.addAll(alsoTargetParents.filterNotNull())
        }

        val targetParentClazzes = targetParents.map { it.clazz }
        val requiredParentMissing = targetEntityData.getRequiredParents().any { it.toClassId() !in targetParentClazzes }

        val targetSourceMatches = entityFilter(targetEntityData.entitySource)
        if (replaceWithEntityData == null || requiredParentMissing) {
          when (targetSourceMatches) {
            true -> removeWorkspaceData(targetEntityTrack.entity, null)
            false -> doNothingOn(targetEntityTrack.entity, null)
          }
        }
        else {
          val replaceWithSourceMatches = entityFilter(replaceWithEntityData.entitySource)
          @Suppress("KotlinConstantConditions")
          when {
            targetSourceMatches && replaceWithSourceMatches -> {
              replaceWorkspaceData(targetEntityTrack.entity, replaceWithEntityData.createEntityId(), targetParents.mapTo(HashSet()) { ParentsRef.TargetRef(it) })
            }
            targetSourceMatches && !replaceWithSourceMatches -> {
              removeWorkspaceData(targetEntityTrack.entity, replaceWithEntityData.createEntityId())
              return null
            }
            !targetSourceMatches && replaceWithSourceMatches -> {
              replaceWorkspaceData(targetEntityTrack.entity, replaceWithEntityData.createEntityId(), targetParents.mapTo(HashSet()) { ParentsRef.TargetRef(it) })
            }
            !targetSourceMatches && !replaceWithSourceMatches -> {
              doNothingOn(targetEntityTrack.entity, replaceWithEntityData.createEntityId())
            }
          }
        }
        return replaceWithEntityData?.createEntityId()
      }
    }

    fun findAndReplaceRootEntityInTargetStore(replaceWithRootEntity: WorkspaceEntityBase): EntityId? {
      val replaceRootEntityId = replaceWithRootEntity.id
      val replaceWithCurrentState = replaceWithState[replaceRootEntityId]
      when (replaceWithCurrentState) {
        is ReplaceWithState.NoChange -> return replaceWithCurrentState.targetEntityId
        ReplaceWithState.NoChangeTraceLost -> return null
        is ReplaceWithState.Relabel -> return replaceWithCurrentState.targetEntityId
        ReplaceWithState.SubtreeMoved -> TODO()
        null -> Unit
      }

      val targetEntity = findEntityInStorage(replaceWithRootEntity, targetStorage, replaceWithStorage)
      if (targetEntity == null) {
        if (entityFilter(replaceWithRootEntity.entitySource)) {
          TODO()
          return null
        } else {
          replaceRootEntityId.addState(ReplaceWithState.NoChangeTraceLost)
        }
      }

      targetEntity as WorkspaceEntityBase
      when {
        !entityFilter(targetEntity.entitySource) && entityFilter(replaceWithRootEntity.entitySource) -> {
          TODO()
          return targetEntity.id
        }
        !entityFilter(targetEntity.entitySource) && !entityFilter(replaceWithRootEntity.entitySource) -> {
          TODO()
          return targetEntity.id
        }
        else -> error("Unexpected branch")
      }
    }

    fun findAndReplaceRootEntity(targetRootEntity: WorkspaceEntityBase): EntityId? {
      val targetRootEntityId = targetRootEntity.id
      val currentTargetState = targetState[targetRootEntityId]
      if (currentTargetState != null) {
        when (currentTargetState) {
          is ReplaceState.NoChange -> {
            return currentTargetState.replaceWithEntityId
          }
          is ReplaceState.Relabel -> {
            return currentTargetState.replaceWithEntityId
          }
          ReplaceState.Remove -> {
            return null
          }
        }
      }

      val replaceWithEntity = findEntityInStorage(targetRootEntity, replaceWithStorage, targetStorage)
      if (replaceWithEntity == null) {
        if (entityFilter(targetRootEntity.entitySource)) {
          targetRootEntityId operation Operation.Remove
          targetRootEntityId.addState(ReplaceState.Remove)
          return null
        }
        else {
          targetRootEntityId.addState(ReplaceState.NoChange(null))
          return null
        }
      }

      replaceWithEntity as WorkspaceEntityBase
      when {
        entityFilter(targetRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
          replaceWorkspaceData(targetRootEntity.id, replaceWithEntity.id, null)
          return replaceWithEntity.id
        }
        entityFilter(targetRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
          removeWorkspaceData(targetRootEntity.id, replaceWithEntity.id)
          return null
        }
        !entityFilter(targetRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
          replaceWorkspaceData(targetRootEntity.id, replaceWithEntity.id, null)
          return replaceWithEntity.id
        }
        !entityFilter(targetRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
          doNothingOn(targetRootEntity.id, replaceWithEntity.id)
          return replaceWithEntity.id
        }
      }

      error("Unexpected branch")
    }

    private fun makeEntityDataCollection(targetChildEntityIds: List<ChildEntityId>, storage: AbstractEntityStorage): Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>> {
      val targetChildrenMap = Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>>(
        EntityDataStrategy())
      targetChildEntityIds.forEach { id ->
        val value = storage.entityDataByIdOrDie(id.id)
        val existingValue = targetChildrenMap[value]
        targetChildrenMap[value] = if (existingValue != null) existingValue + value else listOf(value)
      }
      return targetChildrenMap
    }
  }

  private class EntityDataStrategy : Hash.Strategy<WorkspaceEntityData<out WorkspaceEntity>> {
    override fun equals(a: WorkspaceEntityData<out WorkspaceEntity>?, b: WorkspaceEntityData<out WorkspaceEntity>?): Boolean {
      if (a == null || b == null) {
        return false
      }
      return a.equalsIgnoringEntitySource(b)
    }

    override fun hashCode(o: WorkspaceEntityData<out WorkspaceEntity>?): Int {
      return o?.hashCodeIgnoringEntitySource() ?: 0
    }
  }

  private fun <K, V> Object2ObjectOpenCustomHashMap<K, List<V>>.removeSome(key: K): V? {
    val existingValue = this[key] ?: return null
    return if (existingValue.size == 1) {
      this.remove(key)
      existingValue.single()
    } else {
      val firstElement = existingValue[0]
      this[key] = existingValue.drop(1)
      firstElement
    }
  }

  private fun replaceWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId, parents: Set<ParentsRef.TargetRef>?) {
    targetEntityId operation Operation.Relabel(replaceWithEntityId, parents)
    targetEntityId.addState(ReplaceState.Relabel(replaceWithEntityId, parents))
    replaceWithEntityId.addState(ReplaceWithState.Relabel(targetEntityId))
  }

  private fun removeWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId?) {
    targetEntityId operation Operation.Remove
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

  private infix fun EntityId.operation(state: Operation) {
    val currentState = operations[this]
    require(currentState == null) {
      "Unexpected existing state for ${this.asString()}: $currentState"
    }
    operations[this] = state
  }

  companion object {
    private fun buildRootTrack(entity: TrackToParents,
                               storage: AbstractEntityStorage) {
      val parents = storage.refs.getParentRefsOfChild(entity.entity.asChild())
      parents.values.forEach { parentEntityId ->
        val parentTrack = TrackToParents(parentEntityId.id)
        buildRootTrack(parentTrack, storage)
        entity.parents += parentTrack
        parentTrack.child = entity
      }
    }

    private fun childrenInStorage(entityId: EntityId?, childrenClass: Int, storage: AbstractEntityStorage): List<ChildEntityId> {
      val targetEntityIds = if (entityId != null) {
        val targetChildren = storage.refs.getChildrenRefsOfParentBy(entityId.asParent())

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

    private fun findEntityInStorage(rootEntity: WorkspaceEntityBase, goalStorage: AbstractEntityStorage, oppositeStorage: AbstractEntityStorage): WorkspaceEntity? {
      return if (rootEntity is WorkspaceEntityWithPersistentId) {
        val persistentId = rootEntity.persistentId
        goalStorage.resolve(persistentId)
      } else {
        goalStorage.entities(rootEntity.id.clazz.findWorkspaceEntity())
          .filter {
            goalStorage.entityDataByIdOrDie((it as WorkspaceEntityBase).id) == oppositeStorage.entityDataByIdOrDie(rootEntity.id)
          }
          .firstOrNull()
      }
    }
  }
}

internal sealed interface Operation {
  object Remove : Operation
  class Relabel(val replaceWithEntityId: EntityId, val parents: Set<ParentsRef>?) : Operation
}

internal class AddSubtree(val targetParent: EntityId?, val replaceWithSource: EntityId)

internal sealed interface ReplaceState {
  data class Relabel(val replaceWithEntityId: EntityId, val parents: Set<ParentsRef>? = null) : ReplaceState
  data class NoChange(val replaceWithEntityId: EntityId?) : ReplaceState
  object Remove : ReplaceState
}

internal sealed interface ReplaceWithState {
  object SubtreeMoved : ReplaceWithState
  data class NoChange(val targetEntityId: EntityId) : ReplaceWithState
  data class Relabel(val targetEntityId: EntityId) : ReplaceWithState
  object NoChangeTraceLost : ReplaceWithState
}

sealed interface ParentsRef {
  data class TargetRef(val targetEntityId: EntityId): ParentsRef
  data class AddedElement(val replaceWithEntityId: EntityId): ParentsRef
}

class TrackToParents(
  val entity: EntityId,
  var child: TrackToParents? = null,
  val parents: MutableList<TrackToParents> = ArrayList(),
) {
  fun singleRoot(): TrackToParents {
    if (parents.isEmpty()) return this
    return parents.single().singleRoot()
  }
}
