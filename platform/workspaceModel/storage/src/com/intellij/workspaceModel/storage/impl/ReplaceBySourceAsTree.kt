// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

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
            // TODO: Terrible modification using reflection, but it's simpler to use it now
            val targetEntityData = targetStorage.entityDataByIdOrDie(id)
            val targetEntity = targetEntityData.createEntity(targetStorage)
            val replaceWithEntity = replaceWithStorage.entityDataByIdOrDie(operation.replaceWithEntityId).createEntity(replaceWithStorage)
            val fieldsToUpdate = targetEntityData::class.memberProperties.map { it.name }
            targetStorage.modifyEntity(ModifiableWorkspaceEntity::class.java, targetEntity) {
              val myInterface = this::class.java.interfaces.single()
              val myInterfaceSnapshot = targetEntity::class.java.interfaces.single()
              myInterface.kotlin.memberProperties.filter { it.name in fieldsToUpdate }
                .filterIsInstance<KMutableProperty1<WorkspaceEntity, Any>>()
                .forEach {
                  val newValue = myInterfaceSnapshot.kotlin.memberProperties
                    .filterIsInstance<KProperty1<WorkspaceEntity, Any>>().single { sn -> sn.name == it.name }.get(replaceWithEntity)
                  it.set(this, newValue)
                }
            }
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
      val entityData = replaceWithStorage.entityDataByIdOrDie(replaceWithDataSource).clone()

      val targetNewCreatedEntityData = targetStorage.entitiesByType.cloneAndAdd(entityData, entityData.createEntityId().clazz)
      // TODO: 27.07.2022 UPDATE INDEXESSSS!!!
      targetStorage.indexes.entitySourceIndex.index(targetNewCreatedEntityData.createEntityId(), targetNewCreatedEntityData.entitySource)
      targetStorage.indexes.persistentIdIndex.index(targetNewCreatedEntityData.createEntityId(), targetNewCreatedEntityData.persistentId())
      // TODO: 27.07.2022 CreatE eEVENT!!

      if (parent != null) {
        connectChildToParent(parent, targetNewCreatedEntityData.createEntity(targetStorage))
      }

      replaceWithStorage.refs.getChildrenRefsOfParentBy(replaceWithDataSource.asParent()).values.flatten().forEach {
        val replaceWithChildEntityData = replaceWithStorage.entityDataByIdOrDie(it.id)
        if (!entityFilter(replaceWithChildEntityData.entitySource)) return@forEach
        addSubtree(targetNewCreatedEntityData.createEntityId(), it.id)
      }
    }

    private fun connectChildToParent(parentEntityId: EntityId, childEntity: WorkspaceEntity) {
      val targetParent = targetStorage.entityDataByIdOrDie(parentEntityId).createEntity(targetStorage)
      targetStorage.modifyEntity(ModifiableWorkspaceEntity::class.java, childEntity) {
        val property = this::class.memberProperties.single { it.name == "parentEntity" } as KMutableProperty1<WorkspaceEntity, Any>
        property.set(this, targetParent)
      }
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class ReplaceWithProcessor {
    fun processEntity(replaceWithEntity: WorkspaceEntity) {
      replaceWithEntity as WorkspaceEntityBase

      val (replaceWithRootEntity, replaceWithPathToRoot) = buildRootTrack(replaceWithEntity.id, replaceWithStorage)

      processReplaceWithRootEntity(replaceWithRootEntity, replaceWithPathToRoot)
    }

    private fun processReplaceWithRootEntity(replaceWithRootEntity: WorkspaceEntityBase,
                                             replaceWithEntitiesTrack: ArrayList<EntityId>) {
      require(replaceWithRootEntity is WorkspaceEntityWithPersistentId) {
        "Root entities without persistent ids are not yet supported"
      }
      require(replaceWithEntitiesTrack.size < 3) {
        TODO("Not yet supported so deep chains")
      }

      val continueProcess = findAndReplaceRootEntity(replaceWithRootEntity)
      if (!continueProcess) return
      replaceWithEntitiesTrack.remove(replaceWithRootEntity.id).also { require(it) }

      val targetRoot = targetStorage.resolve(replaceWithRootEntity.persistentId)!!

      for (replaceWithEntity in replaceWithEntitiesTrack.reversed()) {
        val targetRootEntityId = (targetRoot as WorkspaceEntityBase).id
        val replaceWithCurrentState = replaceWithState[replaceWithEntity]
        when (replaceWithCurrentState) {
          ReplaceWithState.NoChange -> TODO()
          ReplaceWithState.Relabel -> continue
          ReplaceWithState.SubtreeMoved -> continue
          ReplaceWithState.Processed -> continue
          null -> {}
        }
        addOperations += AddSubtree(targetRootEntityId, replaceWithEntity)
        replaceWithEntity.addState(ReplaceWithState.SubtreeMoved)
      }
    }

    private fun findAndReplaceRootEntity(replaceWithRootEntity: WorkspaceEntityWithPersistentId): Boolean {

      val currentState = replaceWithState[(replaceWithRootEntity as WorkspaceEntityBase).id]
      when (currentState) {
        is ReplaceWithState.SubtreeMoved -> return false
        ReplaceWithState.NoChange -> return true
        ReplaceWithState.Relabel -> return true
        ReplaceWithState.Processed -> return true
        null -> Unit
      }

      val persistentId = replaceWithRootEntity.persistentId
      val targetEntity = targetStorage.resolve(persistentId)
      if (targetEntity == null) {
        if (entityFilter(replaceWithRootEntity.entitySource)) {
          addOperations += AddSubtree(null, (replaceWithRootEntity as WorkspaceEntityBase).id)
          (replaceWithRootEntity as WorkspaceEntityBase).id.addState(ReplaceWithState.SubtreeMoved)
          return false
        }
        else {
          (replaceWithRootEntity as WorkspaceEntityBase).id.addState(ReplaceWithState.NoChange)
          return false
        }
      }

      val targetCurrentState = targetState[(targetEntity as WorkspaceEntityBase).id]
      when (targetCurrentState) {
        ReplaceState.NoChange -> return true
        ReplaceState.Relabel -> return true
        is ReplaceState.Relink -> {
          when (targetCurrentState) {
            is ReplaceState.Relink.Relabel -> return true
            is ReplaceState.Relink.NoChange -> return true
          }
        }
        ReplaceState.Remove -> return false
        null -> Unit
      }

      when {
        entityFilter(replaceWithRootEntity.entitySource) && entityFilter(targetEntity.entitySource) -> {
          error("This branch should be already processed because we process 'target' entities first")
        }
        entityFilter(replaceWithRootEntity.entitySource) && !entityFilter(targetEntity.entitySource) -> {
          (targetEntity as WorkspaceEntityBase).id operation Operation.Relabel((replaceWithRootEntity as WorkspaceEntityBase).id)
          (replaceWithRootEntity as WorkspaceEntityBase).id.addState(ReplaceWithState.Relabel)
          return true
        }
        !entityFilter(replaceWithRootEntity.entitySource) && entityFilter(targetEntity.entitySource) -> {
          error("This branch should be already processed because we process 'target' entities first")
        }
        !entityFilter(replaceWithRootEntity.entitySource) && !entityFilter(targetEntity.entitySource) -> {
          (replaceWithRootEntity as WorkspaceEntityBase).id.addState(ReplaceWithState.NoChange)
          return true
        }
      }

      error("Unexpected branch")
    }
  }

  // This class is just a wrapper to combine functions logically
  private inner class TargetProcessor {
    fun processEntity(targetEntityToReplace: WorkspaceEntity) {
      targetEntityToReplace as WorkspaceEntityBase

      val (targetRootEntity, targetPathToRoot) = buildRootTrack(targetEntityToReplace.id, targetStorage)
      processRoot(targetRootEntity, targetPathToRoot)
    }

    private fun processRoot(targetRootEntity: WorkspaceEntityBase, targetRootTrack: MutableList<EntityId>) {
      require(targetRootEntity is WorkspaceEntityWithPersistentId) {
        "Root entities without persistent ids are not yet supported"
      }
      require(targetRootTrack.size < 3) {
        TODO("Not yet supported so deep chains")
      }

      val continueProcess = findAndReplaceRootEntity(targetRootEntity)
      targetRootTrack.remove(targetRootEntity.id).also { require(it) }

      if (!continueProcess) {
        if (targetState[targetRootEntity.id] == ReplaceState.Remove) {
          targetRootTrack.forEach { it.addState(ReplaceState.Remove) }
        }
        return
      }

      val replaceWithRoot = replaceWithStorage.resolve(targetRootEntity.persistentId)!!


      for (workspaceEntityBase in targetRootTrack.reversed()) {

        val rootState = targetState[targetRootEntity.id]
        if (rootState is ReplaceState.Relink && workspaceEntityBase.clazz.findWorkspaceEntity() in rootState.linkedChildren) {
          continue
        }

        val childState = targetState[workspaceEntityBase]
        if (childState != null) {
          when (childState) {
            ReplaceState.NoChange -> continue
            ReplaceState.Relabel -> continue
            is ReplaceState.Relink -> continue
            ReplaceState.Remove -> break
          }
        }

        processChildren(targetRootEntity, replaceWithRoot, workspaceEntityBase)

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

    fun findAndReplaceRootEntity(targetRootEntity: WorkspaceEntityWithPersistentId): Boolean {
      val targetRootEntityId = (targetRootEntity as WorkspaceEntityBase).id
      val currentTargetState = targetState[targetRootEntityId]
      if (currentTargetState != null) {
        when (currentTargetState) {
          ReplaceState.NoChange -> return true
          ReplaceState.Relabel -> return true
          is ReplaceState.Relink -> return true
          ReplaceState.Remove -> return false
        }
      }

      val persistentId = targetRootEntity.persistentId
      val replaceWithEntity = replaceWithStorage.resolve(persistentId)
      if (replaceWithEntity == null) {
        if (entityFilter(targetRootEntity.entitySource)) {
          targetRootEntityId operation Operation.Remove
          targetRootEntityId.addState(ReplaceState.Remove)
          return false
        }
        else {
          targetRootEntityId.addState(ReplaceState.NoChange)
          return false
        }
      }

      replaceWithEntity as WorkspaceEntityBase
      when {
        entityFilter(targetRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
          targetRootEntity.id operation Operation.Relabel(replaceWithEntity.id)
          targetRootEntity.id.addState(ReplaceState.Relabel)
          replaceWithEntity.id.addState(ReplaceWithState.Relabel)
          return true
        }
        entityFilter(targetRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
          targetRootEntity.id operation Operation.Remove
          targetRootEntity.id.addState(ReplaceState.Remove)
          replaceWithEntity.id.addState(ReplaceWithState.Processed)
          return false
        }
        !entityFilter(targetRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
          targetRootEntity.id operation Operation.Relabel(replaceWithEntity.id)
          targetRootEntity.id.addState(ReplaceState.Relabel)
          replaceWithEntity.id.addState(ReplaceWithState.Relabel)
          return true
        }
        !entityFilter(targetRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
          targetRootEntity.id.addState(ReplaceState.NoChange)
          replaceWithEntity.id.addState(ReplaceWithState.NoChange)
          return true
        }
      }

      error("Unexpected branch")
    }

    private fun processChildren(targetRootEntity: WorkspaceEntity, replaceWithRoot: WorkspaceEntity, childClassEntityId: EntityId) {
      val targetChildren = targetStorage.refs.getChildrenRefsOfParentBy((targetRootEntity as WorkspaceEntityBase).id.asParent())

      // TODO: This search won't work for abstract entities
      val targetFoundChildren = targetChildren.filterKeys { it.childClass == childClassEntityId.clazz }
      require(targetFoundChildren.size < 2) { "Got unexpected amount of children" }
      require(targetFoundChildren.isNotEmpty()) { "How this may happen? Because we have at least one child in our trace" }

      val (_, targetChildEntityIds) = targetFoundChildren.entries.single()

      //----

      val replaceWithChildren = replaceWithStorage.refs.getChildrenRefsOfParentBy((replaceWithRoot as WorkspaceEntityBase).id.asParent())

      // TODO: This search won't work for abstract entities
      val replaceWithFoundChildren = replaceWithChildren.filterKeys { it.childClass == childClassEntityId.clazz }
      require(replaceWithFoundChildren.size < 2) { "Got unexpected amount of children" }

      val replaceWithEntityIds = if (replaceWithFoundChildren.isEmpty()) {
        emptyList()
      }
      else {
        val (_, replaceWithChildEntityIds) = replaceWithFoundChildren.entries.single()
        replaceWithChildEntityIds
      }

      //----

      val targetChildrenMap = buildMap {
        targetChildEntityIds.forEach { id ->
          val value = targetStorage.entityDataByIdOrDie(id.id)
          put(value, value)
        }
      }.toMutableMap()

      replaceWithEntityIds.map { replaceWithStorage.entityDataByIdOrDie(it.id) }.forEach { replaceWithEntityData ->
        val targetEntityData = targetChildrenMap.remove(replaceWithEntityData)
        val replaceWithSourceMatches = entityFilter(replaceWithEntityData.entitySource)
        if (targetEntityData != null) {
          val targetSourceMatches = entityFilter(targetEntityData.entitySource)
          @Suppress("KotlinConstantConditions")
          when {
            targetSourceMatches && replaceWithSourceMatches -> replaceWorkspaceData(targetEntityData, replaceWithEntityData)
            targetSourceMatches && !replaceWithSourceMatches -> removeWorkspaceData(targetEntityData)
            !targetSourceMatches && replaceWithSourceMatches -> replaceWorkspaceData(targetEntityData, replaceWithEntityData)
            !targetSourceMatches && !replaceWithSourceMatches -> doNothingOn(targetEntityData)
          }
        }
        else {
          @Suppress("KotlinConstantConditions")
          when {
            replaceWithSourceMatches -> addWorkspaceData(targetRootEntity, replaceWithEntityData)
            !replaceWithSourceMatches -> Unit // TODO Should we also track replaceWith store as we do with the local one using `state`?
          }
        }
      }

      targetChildrenMap.keys.forEach { targetEntityData -> removeWorkspaceData(targetEntityData) }

      targetRootEntity.id.updateStateLinkProcessed(childClassEntityId.clazz.findWorkspaceEntity())
    }

    private fun replaceWorkspaceData(targetEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                                     replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
      targetEntityData.createEntityId() operation Operation.Relabel(replaceWithEntityData.createEntityId())
      targetEntityData.createEntityId().addState(ReplaceState.Relabel)
      replaceWithEntityData.createEntityId().addState(ReplaceWithState.Relabel)
    }

    private fun removeWorkspaceData(targetEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
      targetEntityData.createEntityId() operation Operation.Remove
      targetEntityData.createEntityId().addState(ReplaceState.Remove)
    }

    private fun addWorkspaceData(targetParent: WorkspaceEntityBase, replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
      addOperations.add(AddSubtree(targetParent.id, replaceWithEntityData.createEntityId()))
      replaceWithEntityData.createEntityId().addState(ReplaceWithState.SubtreeMoved)
    }

    private fun doNothingOn(targetEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
      targetEntityData.createEntityId().addState(ReplaceState.NoChange)
    }
  }

  private fun EntityId.addState(state: ReplaceState) {
    val currentState = targetState[this]
    require(currentState == null)
    targetState[this] = state
  }

  private fun EntityId.addState(state: ReplaceWithState) {
    val currentState = replaceWithState[this]
    require(currentState == null)
    replaceWithState[this] = state
  }

  private infix fun EntityId.operation(state: Operation) {
    val currentState = operations[this]
    require(currentState == null)
    operations[this] = state
  }

  private fun EntityId.updateStateLinkProcessed(clazz: Class<out WorkspaceEntity>) {
    val currentState = targetState[this]
    require(currentState != null)
    when (currentState) {
      ReplaceState.Relabel -> targetState[this] = ReplaceState.Relink.Relabel(listOf(clazz))
      is ReplaceState.Relink -> when (currentState) {
        is ReplaceState.Relink.Relabel -> targetState[this] = ReplaceState.Relink.Relabel(currentState.linkedChildren + listOf(clazz))
        is ReplaceState.Relink.NoChange -> targetState[this] = ReplaceState.Relink.NoChange(currentState.linkedChildren + listOf(clazz))
      }
      is ReplaceState.NoChange -> targetState[this] = ReplaceState.Relink.NoChange(listOf(clazz))
      else -> error("")
    }
  }

  companion object {
    private fun buildRootTrack(entity: EntityId,
                               storage: AbstractEntityStorage): Pair<WorkspaceEntityBase, ArrayList<EntityId>> {
      val pathToRoot = ArrayList<EntityId>()
      val parents = storage.refs.getParentRefsOfChild(entity.asChild())
      if (parents.size > 1) {
        error("Multiple Parents are not yet supported")
      }
      else if (parents.size == 1) {
        pathToRoot += entity
        val parentEntity = storage.entityDataByIdOrDie(parents.values.single().id)
        val deeperResult = buildRootTrack(parentEntity.createEntityId(), storage)
        pathToRoot.addAll(deeperResult.second)
        return deeperResult.first to pathToRoot
      }
      else {
        pathToRoot += entity
        return storage.entityDataByIdOrDie(entity).createEntity(storage) as WorkspaceEntityBase to pathToRoot
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
  object Relabel : ReplaceState
  object NoChange : ReplaceState
  object Remove : ReplaceState
  abstract class Relink(val linkedChildren: List<Class<out WorkspaceEntity>>) : ReplaceState {
    class Relabel(linkedChildren: List<Class<out WorkspaceEntity>>) : Relink(linkedChildren)
    class NoChange(linkedChildren: List<Class<out WorkspaceEntity>>) : Relink(linkedChildren)
  }
}

internal sealed interface ReplaceWithState {
  object SubtreeMoved : ReplaceWithState
  object NoChange : ReplaceWithState
  object Relabel : ReplaceWithState
  object Processed : ReplaceWithState
}
