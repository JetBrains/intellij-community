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
  internal val addOperations = HashSet<AddSubtree>()
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
      for ((id, operation) in operations) {
        when (operation) {
          is Operation.Relabel -> {
            val targetEntity = targetStorage.entityDataByIdOrDie(id).createEntity(targetStorage)
            val replaceWithEntity = replaceWithStorage.entityDataByIdOrDie(operation.replaceWithEntityId).createEntity(replaceWithStorage)
            targetStorage.modifyEntity(ModifiableWorkspaceEntity::class.java, targetEntity) {
              (this as ModifiableWorkspaceEntityBase<*>).relabel(replaceWithEntity)
            }
            targetStorage.indexes.updateExternalMappingForEntityId(operation.replaceWithEntityId, id, replaceWithStorage.indexes)
          }
          Operation.Remove -> {
            targetStorage.removeEntity(id)
          }
        }
      }

      for (addOperation in addOperations) {
        addSubtree(addOperation.targetParent, addOperation.replaceWithSource)
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
        is ReplaceState.Relink -> {
          when (targetCurrentState) {
            is ReplaceState.Relink.Relabel -> return true to targetEntityId
            is ReplaceState.Relink.NoChange -> return true to targetEntityId
          }
        }
        ReplaceState.Remove -> return false to targetEntityId
        null -> Unit
      }

      when {
        entityFilter(replaceWithRootEntity.entitySource) && entityFilter(targetEntity.entitySource) -> {
          error("This branch should be already processed because we process 'target' entities first")
        }
        entityFilter(replaceWithRootEntity.entitySource) && !entityFilter(targetEntity.entitySource) -> {
          replaceWorkspaceData(targetEntity.id, replaceWithRootEntity.id)
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
      processRoot(trackToParents)
    }

    private fun processRoot(trackToParents: TrackToParents) {
      val rootTrack = trackToParents.singleRoot()
      var (continueProcess, replaceWithRootEntityId) = findAndReplaceRootEntity(
        targetStorage.entityDataByIdOrDie(rootTrack.entity).createEntity(targetStorage) as WorkspaceEntityBase)

      if (!continueProcess) {
        if (targetState[rootTrack.entity] == ReplaceState.Remove) {
          var trackItem = rootTrack
          while (trackItem.child != null) {
            trackItem = trackItem.child!!
            val currentState = targetState[trackItem.entity]
            if (currentState == null) {
              trackItem.entity.addState(ReplaceState.Remove)
            } else if (currentState !is ReplaceState.Remove) {
              error("Unexpected state: $currentState")
            }
          }
        }
        return
      }

      var targetRootEntityId = rootTrack.entity
      var markOtherToRemove = false
      var trackItem = rootTrack
      while (trackItem.child != null) {
        trackItem = trackItem.child!!
        val targetWorkspaceEntityBase = trackItem.entity
        if (markOtherToRemove) {
          targetWorkspaceEntityBase.addState(ReplaceState.Remove)
          continue
        }

        val rootState = targetState[targetRootEntityId]
        if (rootState is ReplaceState.Relink && targetWorkspaceEntityBase.clazz.findWorkspaceEntity() in rootState.linkedChildren) {
          targetRootEntityId = targetWorkspaceEntityBase
          replaceWithRootEntityId = targetState.getValue(targetWorkspaceEntityBase).oppositeId()
          continue
        }

        val childState = targetState[targetWorkspaceEntityBase]
        if (childState != null) {
          when (childState) {
            is ReplaceState.NoChange -> {
              targetRootEntityId = targetWorkspaceEntityBase
              replaceWithRootEntityId = childState.replaceWithEntityId
              continue
            }
            is ReplaceState.Relabel -> {
              targetRootEntityId = targetWorkspaceEntityBase
              replaceWithRootEntityId = childState.replaceWithEntityId
              continue
            }
            is ReplaceState.Relink -> {
              targetRootEntityId = targetWorkspaceEntityBase
              replaceWithRootEntityId = childState.replaceWithEntityId
              continue
            }
            ReplaceState.Remove -> {
              markOtherToRemove = true
              continue
            }
          }
        }

        val newReplaceWithRootEntityId = processChildren(targetRootEntityId, replaceWithRootEntityId, targetWorkspaceEntityBase)

        if (newReplaceWithRootEntityId != null) {
          replaceWithRootEntityId = newReplaceWithRootEntityId
        } else {
          // Target entity was removed, no sense to continue processing
          markOtherToRemove = true
        }
        targetRootEntityId = targetWorkspaceEntityBase

        // OPEN QUESTION: DO WE FORCE ONLY A SINGLE KEYED CHILDREN (Can we have two children with the same key)
        // (a) no
        // (b) yes

        // - Get direct parent
        // - Check if the parent is replacable
        //   - If no:
        //     - Detect children (only one type)
        //       - Run "Tree children processing"
        //       -
        //   - If yes:


        /// Tree children processing
        //  - Split targetChildren into targetMatched & targetUnmatched
        //  - Split replaceWithChildren into replaceWithMatched & replaceWithUnmatched
        //  - For any replaceWithMatched:
        //    - Try to find a corresponding entity in targetChildren. If (a) - only in matched, if (b) in all.
        //    - If Found:
        //      - Run "Replace child process"
        //    - If Not Found:
        //      - Run "New subtree proceess"
        //    - Remove targetProcessedChild from targetChildren
        //    - For any child in targetChildren run "Remove child process"
        //  - ??? Mark target subtree as processed (How?)
        //  - ??? Should we stop on this level, or process the children deeper?


        /// New subtree process
        /// Input - entity from target store and replaceWith store. If (a), they may have different entity source (one matched and one unmatched),
        /// if (b), they both have matched entity source.


        /// Replace Child process


        /// Remove Child process


        /**
         * 1 - | - 2                   Replacing [5]
         *     | - 3                    - Remove 4 - Drop all children of 4 and the full subtree
         *     | - 4 | - [5]            - Update 4 - Replace only data, start processing children?
         *           | - 6
         *           | - 7
         */
      }
    }

    fun findAndReplaceRootEntity(targetRootEntity: WorkspaceEntityBase): Pair<Boolean, EntityId?> {
      val targetRootEntityId = targetRootEntity.id
      val currentTargetState = targetState[targetRootEntityId]
      if (currentTargetState != null) {
        when (currentTargetState) {
          is ReplaceState.NoChange -> {
            return true to currentTargetState.replaceWithEntityId
          }
          is ReplaceState.Relabel -> {
            return true to currentTargetState.replaceWithEntityId
          }
          is ReplaceState.Relink -> {
            return true to currentTargetState.replaceWithEntityId
          }
          ReplaceState.Remove -> {
            return false to null
          }
        }
      }

      val replaceWithEntity = findEntityInReplaceWithStorage(targetRootEntity)
      if (replaceWithEntity == null) {
        if (entityFilter(targetRootEntity.entitySource)) {
          targetRootEntityId operation Operation.Remove
          targetRootEntityId.addState(ReplaceState.Remove)
          return false to null
        }
        else {
          targetRootEntityId.addState(ReplaceState.NoChange(null))
          return false to null
        }
      }

      replaceWithEntity as WorkspaceEntityBase
      when {
        entityFilter(targetRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
          replaceWorkspaceData(targetRootEntity.id, replaceWithEntity.id)
          return true to replaceWithEntity.id
        }
        entityFilter(targetRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
          removeWorkspaceData(targetRootEntity.id, replaceWithEntity.id)
          return false to replaceWithEntity.id
        }
        !entityFilter(targetRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
          replaceWorkspaceData(targetRootEntity.id, replaceWithEntity.id)
          return true to replaceWithEntity.id
        }
        !entityFilter(targetRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
          doNothingOn(targetRootEntity.id, replaceWithEntity.id)
          return true to replaceWithEntity.id
        }
      }

      error("Unexpected branch")
    }

    fun findEntityInReplaceWithStorage(targetRootEntity: WorkspaceEntityBase): WorkspaceEntity? {
      return if (targetRootEntity is WorkspaceEntityWithPersistentId) {
        val persistentId = targetRootEntity.persistentId
        replaceWithStorage.resolve(persistentId)
      } else {
        replaceWithStorage.entities(targetRootEntity.id.clazz.findWorkspaceEntity())
          .filter {
            replaceWithStorage.entityDataByIdOrDie((it as WorkspaceEntityBase).id) == targetStorage.entityDataByIdOrDie(targetRootEntity.id)
          }
          .firstOrNull()
      }
    }

    private fun processChildren(targetRootEntityId: EntityId,
                                replaceWithRootId: EntityId?,
                                childClassEntityId: EntityId): EntityId? {
      val targetChildren = targetStorage.refs.getChildrenRefsOfParentBy(targetRootEntityId.asParent())

      val targetFoundChildren = targetChildren.filterKeys { sameClass(it.childClass, childClassEntityId.clazz, it.connectionType) }
      require(targetFoundChildren.size < 2) { "Got unexpected amount of children" }
      require(targetFoundChildren.isNotEmpty()) { "How this may happen? Because we have at least one child in our trace" }

      val (_, targetChildEntityIds) = targetFoundChildren.entries.single()

      //----

      val replaceWithEntityIds = if (replaceWithRootId != null) {
        val replaceWithChildren = replaceWithStorage.refs.getChildrenRefsOfParentBy(replaceWithRootId.asParent())

        val replaceWithFoundChildren = replaceWithChildren.filterKeys { sameClass(it.childClass, childClassEntityId.clazz, it.connectionType) }
        require(replaceWithFoundChildren.size < 2) { "Got unexpected amount of children" }

        if (replaceWithFoundChildren.isEmpty()) {
          emptyList()
        }
        else {
          val (_, replaceWithChildEntityIds) = replaceWithFoundChildren.entries.single()
          replaceWithChildEntityIds
        }
      } else {
        emptyList()
      }

      //----

      val targetChildrenMap = Object2ObjectOpenCustomHashMap<WorkspaceEntityData<out WorkspaceEntity>, List<WorkspaceEntityData<out WorkspaceEntity>>>(EntityDataStrategy())
      targetChildEntityIds.forEach { id ->
        val value = targetStorage.entityDataByIdOrDie(id.id)
        val existingValue = targetChildrenMap[value]
        targetChildrenMap[value] = if (existingValue != null) existingValue + value else listOf(value)
      }

      var returnValue: EntityId? = null

      replaceWithEntityIds.map { replaceWithStorage.entityDataByIdOrDie(it.id) }.forEach { replaceWithEntityData ->
        val targetEntityData = targetChildrenMap.removeSome(replaceWithEntityData)
        val replaceWithSourceMatches = entityFilter(replaceWithEntityData.entitySource)
        if (targetEntityData != null) {
          val targetSourceMatches = entityFilter(targetEntityData.entitySource)
          @Suppress("KotlinConstantConditions")
          when {
            targetSourceMatches && replaceWithSourceMatches -> {
              replaceWorkspaceData(targetEntityData.createEntityId(), replaceWithEntityData.createEntityId())
              if (targetEntityData.createEntityId() == childClassEntityId) {
                returnValue = replaceWithEntityData.createEntityId()
              }
            }
            targetSourceMatches && !replaceWithSourceMatches -> removeWorkspaceData(targetEntityData.createEntityId(),
                                                                                    replaceWithEntityData.createEntityId())
            !targetSourceMatches && replaceWithSourceMatches -> {
              replaceWorkspaceData(targetEntityData.createEntityId(), replaceWithEntityData.createEntityId())
              if (targetEntityData.createEntityId() == childClassEntityId) {
                returnValue = replaceWithEntityData.createEntityId()
              }
            }
            !targetSourceMatches && !replaceWithSourceMatches -> {
              doNothingOn(targetEntityData.createEntityId(), replaceWithEntityData.createEntityId())
              if (targetEntityData.createEntityId() == childClassEntityId) {
                returnValue = replaceWithEntityData.createEntityId()
              }
            }
          }
        }
        else {
          @Suppress("KotlinConstantConditions")
          when {
            replaceWithSourceMatches -> addWorkspaceData(replaceWithEntityData, targetRootEntityId)
            !replaceWithSourceMatches -> Unit
          }
        }
      }

      targetChildrenMap.keys.forEach { targetEntityData ->
        val targetEntityId = targetEntityData.createEntityId()
        if (entityFilter(targetEntityData.entitySource)) {
          removeWorkspaceData(targetEntityId, null)
        } else {
          doNothingOn(targetEntityId, null)
        }
      }

      targetRootEntityId.updateStateLinkProcessed(childClassEntityId.clazz.findWorkspaceEntity())

      return returnValue
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

  private fun replaceWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId) {
    targetEntityId operation Operation.Relabel(replaceWithEntityId)
    targetEntityId.addState(ReplaceState.Relabel(replaceWithEntityId))
    replaceWithEntityId.addState(ReplaceWithState.Relabel(targetEntityId))
  }

  private fun removeWorkspaceData(targetEntityId: EntityId, replaceWithEntityId: EntityId?) {
    targetEntityId operation Operation.Remove
    targetEntityId.addState(ReplaceState.Remove)
    replaceWithEntityId?.addState(ReplaceWithState.NoChangeTraceLost)
  }

  private fun addWorkspaceData(replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>, targetParentId: EntityId) {
    addOperations.add(AddSubtree(targetParentId, replaceWithEntityData.createEntityId()))
    replaceWithEntityData.createEntityId().addState(ReplaceWithState.SubtreeMoved)
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

  private fun EntityId.updateStateLinkProcessed(clazz: Class<out WorkspaceEntity>) {
    val currentState = targetState[this]
    require(currentState != null)
    when (currentState) {
      is ReplaceState.Relabel -> targetState[this] = ReplaceState.Relink.Relabel(listOf(clazz), currentState.replaceWithEntityId)
      is ReplaceState.Relink -> when (currentState) {
        is ReplaceState.Relink.Relabel -> targetState[this] = ReplaceState.Relink.Relabel(currentState.linkedChildren + listOf(clazz),
                                                                                          currentState.replaceWithEntityId)
        is ReplaceState.Relink.NoChange -> targetState[this] = ReplaceState.Relink.NoChange(currentState.linkedChildren + listOf(clazz),
                                                                                            currentState.replaceWithEntityId)
      }
      is ReplaceState.NoChange -> targetState[this] = ReplaceState.Relink.NoChange(listOf(clazz), currentState.replaceWithEntityId)
      else -> error("")
    }
  }

  companion object {
    private fun buildRootTrack(entity: TrackToParents,
                               storage: AbstractEntityStorage) {
      val parents = storage.refs.getParentRefsOfChild(entity.entity.asChild())
      if (parents.size > 1) {
        error("Multiple Parents are not yet supported")
      }
      parents.values.forEach { parentEntityId ->
        val parentTrack = TrackToParents(parentEntityId.id)
        buildRootTrack(parentTrack, storage)
        entity.parents += parentTrack
        parentTrack.child = entity
      }
    }
  }
}

internal sealed interface Operation {
  object Remove : Operation
  class Relabel(val replaceWithEntityId: EntityId) : Operation
}

internal class AddSubtree(val targetParent: EntityId?, val replaceWithSource: EntityId)

internal sealed interface ReplaceState {
  data class Relabel(val replaceWithEntityId: EntityId) : ReplaceState
  data class NoChange(val replaceWithEntityId: EntityId?) : ReplaceState
  object Remove : ReplaceState
  abstract class Relink(open val linkedChildren: List<Class<out WorkspaceEntity>>, open val replaceWithEntityId: EntityId?) : ReplaceState {
    data class Relabel(override val linkedChildren: List<Class<out WorkspaceEntity>>, override val replaceWithEntityId: EntityId?)
      : Relink(linkedChildren, replaceWithEntityId)

    data class NoChange(override val linkedChildren: List<Class<out WorkspaceEntity>>, override val replaceWithEntityId: EntityId?)
      : Relink(linkedChildren, replaceWithEntityId)
  }

  fun oppositeId(): EntityId? {
    return when (this) {
      is NoChange -> replaceWithEntityId
      is Relabel -> replaceWithEntityId
      is Relink -> replaceWithEntityId
      Remove -> null
    }
  }
}

internal sealed interface ReplaceWithState {
  object SubtreeMoved : ReplaceWithState
  data class NoChange(val targetEntityId: EntityId) : ReplaceWithState
  data class Relabel(val targetEntityId: EntityId) : ReplaceWithState
  object NoChangeTraceLost : ReplaceWithState
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
