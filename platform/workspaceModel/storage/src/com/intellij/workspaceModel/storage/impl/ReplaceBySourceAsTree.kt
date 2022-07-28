// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.ReplaceBySourceOperation
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

  private lateinit var thisStorage: MutableEntityStorageImpl
  private lateinit var replaceWithStorage: AbstractEntityStorage
  private lateinit var entityFilter: (EntitySource) -> Boolean

  internal val operations = HashMap<EntityId, Operation>()
  internal val addOperations = HashSet<AddSubtree>()
  internal val thisState = HashMap<EntityId, ReplaceState>()
  internal val replaceWithState = HashMap<EntityId, ReplaceWithState>()


  override fun replace(
    thisStorage: MutableEntityStorageImpl,
    replaceWithStorage: AbstractEntityStorage,
    entityFilter: (EntitySource) -> Boolean,
  ) {
    this.thisStorage = thisStorage
    this.replaceWithStorage = replaceWithStorage
    this.entityFilter = entityFilter

    val thisEntitiesToReplace = thisStorage.entitiesBySource(entityFilter)
    val replaceWithEntitiesToReplace = replaceWithStorage.entitiesBySource(entityFilter)

    for (thisEntityToReplace in thisEntitiesToReplace.values.flatMap { it.values }.flatten()) {
      thisEntityToReplace as WorkspaceEntityBase

      val thisEntitiesTrack = ArrayList<EntityId>()
      val thisRootEntity = buildRootTrack(thisEntityToReplace.id, thisEntitiesTrack, this.thisStorage)
      processThisRoot(thisRootEntity, thisEntitiesTrack)
    }

    for (replaceWithEntityToReplace in replaceWithEntitiesToReplace.values.flatMap { it.values }.flatten()) {
      // TODO: 27.07.2022 Check state of replaceWith entities
      processReplaceWithEntity(replaceWithEntityToReplace)
    }

    applyOperations()
  }

  private fun processReplaceWithEntity(replaceWithEntity: WorkspaceEntity) {
    replaceWithEntity as WorkspaceEntityBase

    val replaceWithEntitiesTrack = ArrayList<EntityId>()
    val replaceWithRootEntity = buildRootTrack(replaceWithEntity.id, replaceWithEntitiesTrack, replaceWithStorage)

    processReplaceWithRootEntity(replaceWithRootEntity, replaceWithEntitiesTrack)
  }

  private fun processReplaceWithRootEntity(replaceWithRootEntity: WorkspaceEntityBase,
                                           replaceWithEntitiesTrack: ArrayList<EntityId>) {
    require(replaceWithRootEntity is WorkspaceEntityWithPersistentId) {
      "Root entities without persistent ids are not yet supported"
    }
    require(replaceWithEntitiesTrack.size < 3) {
      TODO("Not yet supported so deep chains")
    }

    val continueProcess = findAndReplaceReplaceWithRootEntity(replaceWithRootEntity)
    if (!continueProcess) return
    replaceWithEntitiesTrack.remove(replaceWithRootEntity.id).also { require(it) }

    val thisRoot = thisStorage.resolve(replaceWithRootEntity.persistentId)!!

    for (replaceWithEntity in replaceWithEntitiesTrack.reversed()) {
      val thisRootEntityId = (thisRoot as WorkspaceEntityBase).id
      val replaceWithCurrentState = replaceWithState[replaceWithEntity]
      when (replaceWithCurrentState) {
        ReplaceWithState.NoChange -> TODO()
        ReplaceWithState.Relabel -> continue
        ReplaceWithState.SubtreeMoved -> continue
        ReplaceWithState.Processed -> continue
        null -> {}
      }
      addOperations += AddSubtree(thisRootEntityId, replaceWithEntity)
      replaceWithEntity.addState(ReplaceWithState.SubtreeMoved)
    }
  }

  private fun applyOperations() {
    for ((id, operation) in operations) {
      when (operation) {
        is Operation.Relabel -> {
          // TODO: Terrible modification using reflection, but it's simpler to use it now
          val thisEntityData = thisStorage.entityDataByIdOrDie(id)
          val thisEntity = thisEntityData.createEntity(thisStorage)
          val replaceWithEntity = replaceWithStorage.entityDataByIdOrDie(operation.replaceWithEntityId).createEntity(replaceWithStorage)
          val fieldsToUpdate = thisEntityData::class.memberProperties.map { it.name }
          thisStorage.modifyEntity(ModifiableWorkspaceEntity::class.java, thisEntity) {
            val myInterface = this::class.java.interfaces.single()
            val myInterfaceSnapshot = thisEntity::class.java.interfaces.single()
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
          thisStorage.removeEntity(id)
        }
      }
    }

    for (addOperation in addOperations) {
      addSubtree(addOperation.thisParent, addOperation.replaceWithSource)
    }
  }

  private fun addSubtree(parent: EntityId?, replaceWithDataSource: EntityId) {
    val entityData = replaceWithStorage.entityDataByIdOrDie(replaceWithDataSource).clone()

    val thisNewCreatedEntityData = thisStorage.entitiesByType.cloneAndAdd(entityData, entityData.createEntityId().clazz)
    // TODO: 27.07.2022 UPDATE INDEXESSSS!!!
    thisStorage.indexes.entitySourceIndex.index(thisNewCreatedEntityData.createEntityId(), thisNewCreatedEntityData.entitySource)
    thisStorage.indexes.persistentIdIndex.index(thisNewCreatedEntityData.createEntityId(), thisNewCreatedEntityData.persistentId())
    // TODO: 27.07.2022 CreatE eEVENT!!

    if (parent != null) {
      connectChildToParent(parent, thisNewCreatedEntityData.createEntity(thisStorage))
    }

    replaceWithStorage.refs.getChildrenRefsOfParentBy(replaceWithDataSource.asParent()).values.flatten().forEach {
      val replaceWithChildEntityData = replaceWithStorage.entityDataByIdOrDie(it.id)
      if (!entityFilter(replaceWithChildEntityData.entitySource)) return@forEach
      addSubtree(thisNewCreatedEntityData.createEntityId(), it.id)
    }
  }

  private fun connectChildToParent(parentEntityId: EntityId, childEntity: WorkspaceEntity) {
    val thisParent = thisStorage.entityDataByIdOrDie(parentEntityId).createEntity(thisStorage)
    thisStorage.modifyEntity(ModifiableWorkspaceEntity::class.java, childEntity) {
      val property = this::class.memberProperties.single { it.name == "parentEntity" } as KMutableProperty1<WorkspaceEntity, Any>
      property.set(this, thisParent)
    }
  }

  private fun buildRootTrack(entity: EntityId,
                             entitiesTrack: MutableList<EntityId>,
                             storage: AbstractEntityStorage): WorkspaceEntityBase {
    val thisEntityId = entity
    val parents = storage.refs.getParentRefsOfChild(thisEntityId.asChild())
    if (parents.size > 1) {
      error("Multiple Parents are not yet supported")
    }
    else if (parents.size == 1) {
      entitiesTrack += entity
      val parentEntity = storage.entityDataByIdOrDie(parents.values.single().id)
      return buildRootTrack(parentEntity.createEntityId(), entitiesTrack, storage)
    }
    else {
      entitiesTrack += entity
      return storage.entityDataByIdOrDie(entity).createEntity(storage) as WorkspaceEntityBase
    }
  }

  private fun processThisRoot(thisRootEntity: WorkspaceEntityBase, thisRootTrack: MutableList<EntityId>) {
    require(thisRootEntity is WorkspaceEntityWithPersistentId) {
      "Root entities without persistent ids are not yet supported"
    }
    require(thisRootTrack.size < 3) {
      TODO("Not yet supported so deep chains")
    }

    val continueProcess = findAndReplaceThisRootEntity(thisRootEntity)
    thisRootTrack.remove(thisRootEntity.id).also { require(it) }

    if (!continueProcess) {
      if (thisState[thisRootEntity.id] == ReplaceState.Remove) {
        thisRootTrack.forEach { it.addState(ReplaceState.Remove) }
      }
      return
    }

    val replaceWithRoot = replaceWithStorage.resolve(thisRootEntity.persistentId)!!


    for (workspaceEntityBase in thisRootTrack.reversed()) {

      val rootState = thisState[thisRootEntity.id]
      if (rootState is ReplaceState.Relink && workspaceEntityBase.clazz.findWorkspaceEntity() in rootState.linkedChildren) {
        continue
      }

      val childState = thisState[workspaceEntityBase]
      if (childState != null) {
        when (childState) {
          ReplaceState.NoChange -> continue
          ReplaceState.Relabel -> continue
          is ReplaceState.Relink -> continue
          ReplaceState.Remove -> break
        }
      }

      processChildren(thisRootEntity, replaceWithRoot, workspaceEntityBase)

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
      //  - Split thisChildren into thisMatched & thisUnmatched
      //  - Split replaceWithChildren into replaceWithMatched & replaceWithUnmatched
      //  - For any replaceWithMatched:
      //    - Try to find a corresponding entity in thisChildren. If (a) - only in matched, if (b) in all.
      //    - If Found:
      //      - Run "Replace child process"
      //    - If Not Found:
      //      - Run "New subtree proceess"
      //    - Remove thisProcessedChild from thisChildren
      //    - For any child in thisChildren run "Remove child process"
      //  - ??? Mark this subtree as processed (How?)
      //  - ??? Should we stop on this level, or process the children deeper?


      /// New subtree process
      /// Input - entity from this store and replaceWith store. If (a), they may have different entity source (one matched and one unmatched),
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

  private fun processChildren(thisRootEntity: WorkspaceEntity, replaceWithRoot: WorkspaceEntity, childClassEntityId: EntityId) {
    val thisChildren = thisStorage.refs.getChildrenRefsOfParentBy((thisRootEntity as WorkspaceEntityBase).id.asParent())

    // TODO: This search won't work for abstract entities
    val thisFoundChildren = thisChildren.filterKeys { it.childClass == childClassEntityId.clazz }
    require(thisFoundChildren.size < 2) { "Got unexpected amount of children" }
    require(thisFoundChildren.isNotEmpty()) { "How this may happen? Because we have at least one child in our trace" }

    val (thisConnectionId, thisChildEntityIds) = thisFoundChildren.entries.single()

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

    val thisChildrenMap = buildMap {
      thisChildEntityIds.forEach { id ->
        val value = thisStorage.entityDataByIdOrDie(id.id)
        put(value, value)
      }
    }.toMutableMap()

    replaceWithEntityIds.map { replaceWithStorage.entityDataByIdOrDie(it.id) }.forEach { replaceWithEntityData ->
      val thisEntityData = thisChildrenMap.remove(replaceWithEntityData)
      val replaceWithSourceMatches = entityFilter(replaceWithEntityData.entitySource)
      if (thisEntityData != null) {
        val thisSourceMatches = entityFilter(thisEntityData.entitySource)
        @Suppress("KotlinConstantConditions")
        when {
          thisSourceMatches && replaceWithSourceMatches -> replaceWorkspaceData(thisEntityData, replaceWithEntityData)
          thisSourceMatches && !replaceWithSourceMatches -> removeWorkspaceData(thisEntityData)
          !thisSourceMatches && replaceWithSourceMatches -> replaceWorkspaceData(thisEntityData, replaceWithEntityData)
          !thisSourceMatches && !replaceWithSourceMatches -> doNothingOn(thisEntityData)
        }
      }
      else {
        @Suppress("KotlinConstantConditions")
        when {
          replaceWithSourceMatches -> addWorkspaceData(thisRootEntity, replaceWithEntityData)
          !replaceWithSourceMatches -> Unit // TODO Should we also track replaceWith store as we do with the local one using `state`?
        }
      }
    }

    thisChildrenMap.keys.forEach { thisEntityData -> removeWorkspaceData(thisEntityData) }

    thisRootEntity.id.updateStateLinkProcessed(childClassEntityId.clazz.findWorkspaceEntity())
  }

  private fun replaceWorkspaceData(thisEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                                   replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    thisEntityData.createEntityId() operation Operation.Relabel(replaceWithEntityData.createEntityId())
    thisEntityData.createEntityId().addState(ReplaceState.Relabel)
    replaceWithEntityData.createEntityId().addState(ReplaceWithState.Relabel)
  }

  private fun removeWorkspaceData(thisEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    thisEntityData.createEntityId() operation Operation.Remove
    thisEntityData.createEntityId().addState(ReplaceState.Remove)
  }

  private fun addWorkspaceData(thisParent: WorkspaceEntityBase, replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    addOperations.add(AddSubtree(thisParent.id, replaceWithEntityData.createEntityId()))
    replaceWithEntityData.createEntityId().addState(ReplaceWithState.SubtreeMoved)
  }

  private fun doNothingOn(thisEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    thisEntityData.createEntityId().addState(ReplaceState.NoChange)
  }

  private fun findAndReplaceThisRootEntity(thisRootEntity: WorkspaceEntityWithPersistentId): Boolean {

    val thisRootEntityId = (thisRootEntity as WorkspaceEntityBase).id
    val currentThisState = thisState[thisRootEntityId]
    if (currentThisState != null) {
      when (currentThisState) {
        ReplaceState.NoChange -> return true
        ReplaceState.Relabel -> return true
        is ReplaceState.Relink -> return true
        ReplaceState.Remove -> return false
      }
    }

    val persistentId = thisRootEntity.persistentId
    val replaceWithEntity = replaceWithStorage.resolve(persistentId)
    if (replaceWithEntity == null) {
      if (entityFilter(thisRootEntity.entitySource)) {
        thisRootEntityId operation Operation.Remove
        thisRootEntityId.addState(ReplaceState.Remove)
        return false
      }
      else {
        thisRootEntityId.addState(ReplaceState.NoChange)
        return false
      }
    }

    thisRootEntity as WorkspaceEntityBase
    replaceWithEntity as WorkspaceEntityBase
    when {
      entityFilter(thisRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
        thisRootEntity.id operation Operation.Relabel(replaceWithEntity.id)
        thisRootEntity.id.addState(ReplaceState.Relabel)
        replaceWithEntity.id.addState(ReplaceWithState.Relabel)
        return true
      }
      entityFilter(thisRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
        thisRootEntity.id operation Operation.Remove
        thisRootEntity.id.addState(ReplaceState.Remove)
        replaceWithEntity.id.addState(ReplaceWithState.Processed)
        return false
      }
      !entityFilter(thisRootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
        thisRootEntity.id operation Operation.Relabel(replaceWithEntity.id)
        thisRootEntity.id.addState(ReplaceState.Relabel)
        replaceWithEntity.id.addState(ReplaceWithState.Relabel)
        return true
      }
      !entityFilter(thisRootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
        thisRootEntity.id.addState(ReplaceState.NoChange)
        replaceWithEntity.id.addState(ReplaceWithState.NoChange)
        return true
      }
    }

    error("Unexpected branch")
  }

  private fun findAndReplaceReplaceWithRootEntity(replaceWithRootEntity: WorkspaceEntityWithPersistentId): Boolean {

    val currentState = replaceWithState[(replaceWithRootEntity as WorkspaceEntityBase).id]
    when (currentState) {
      is ReplaceWithState.SubtreeMoved -> return false
      ReplaceWithState.NoChange -> return true
      ReplaceWithState.Relabel -> return true
      ReplaceWithState.Processed -> return true
      null -> Unit
    }

    val persistentId = replaceWithRootEntity.persistentId
    val thisEntity = thisStorage.resolve(persistentId)
    if (thisEntity == null) {
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

    val thisCurrentState = thisState[(thisEntity as WorkspaceEntityBase).id]
    when (thisCurrentState) {
      ReplaceState.NoChange -> return true
      ReplaceState.Relabel -> return true
      is ReplaceState.Relink -> {
        when (thisCurrentState) {
          is ReplaceState.Relink.Relabel -> return true
          is ReplaceState.Relink.NoChange -> return true
        }
      }
      ReplaceState.Remove -> return false
      null -> Unit
    }

    when {
      entityFilter(replaceWithRootEntity.entitySource) && entityFilter(thisEntity.entitySource) -> {
        error("This branch should be already processed because we process 'this' entities first")
      }
      entityFilter(replaceWithRootEntity.entitySource) && !entityFilter(thisEntity.entitySource) -> {
        (thisEntity as WorkspaceEntityBase).id operation Operation.Relabel((replaceWithRootEntity as WorkspaceEntityBase).id)
        (replaceWithRootEntity as WorkspaceEntityBase).id.addState(ReplaceWithState.Relabel)
        return true
      }
      !entityFilter(replaceWithRootEntity.entitySource) && entityFilter(thisEntity.entitySource) -> {
        error("This branch should be already processed because we process 'this' entities first")
      }
      !entityFilter(replaceWithRootEntity.entitySource) && !entityFilter(thisEntity.entitySource) -> {
        (replaceWithRootEntity as WorkspaceEntityBase).id.addState(ReplaceWithState.NoChange)
        return true
      }
    }

    error("Unexpected branch")
  }

  private fun EntityId.addState(state: ReplaceState) {
    val currentState = thisState[this]
    require(currentState == null)
    thisState[this] = state
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
    val currentState = thisState[this]
    require(currentState != null)
    when (currentState) {
      ReplaceState.Relabel -> thisState[this] = ReplaceState.Relink.Relabel(listOf(clazz))
      is ReplaceState.Relink -> when (currentState) {
        is ReplaceState.Relink.Relabel -> thisState[this] = ReplaceState.Relink.Relabel(currentState.linkedChildren + listOf(clazz))
        is ReplaceState.Relink.NoChange -> thisState[this] = ReplaceState.Relink.NoChange(currentState.linkedChildren + listOf(clazz))
      }
      is ReplaceState.NoChange -> thisState[this] = ReplaceState.Relink.NoChange(listOf(clazz))
      else -> error("")
    }
  }
}

internal sealed interface Operation {
  object Remove : Operation
  class Relabel(val replaceWithEntityId: EntityId) : Operation
}
internal class AddSubtree(val thisParent: EntityId?, val replaceWithSource: EntityId)

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
