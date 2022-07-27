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

      val thisEntitiesTrack = ArrayList<WorkspaceEntityBase>()
      val thisRootEntity = buildRootTrack(thisEntityToReplace, thisEntitiesTrack)
      processParent(thisRootEntity, thisEntitiesTrack)
    }

    applyOperations()
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
  }

  private fun buildRootTrack(entity: WorkspaceEntityBase,
                             entitiesTrack: MutableList<WorkspaceEntityBase>): WorkspaceEntityBase {
    val thisEntityId = entity.id
    val parents = thisStorage.refs.getParentRefsOfChild(thisEntityId.asChild())
    if (parents.size > 1) {
      error("Multiple Parents are not yet supported")
    }
    else if (parents.size == 1) {
      entitiesTrack += entity
      val parentEntity = thisStorage.entityDataByIdOrDie(parents.values.single().id).createEntity(thisStorage) as WorkspaceEntityBase
      return buildRootTrack(parentEntity, entitiesTrack)
    }
    else {
      entitiesTrack += entity
      return entity
    }
  }

  private fun processParent(rootEntity: WorkspaceEntityBase, rootTrack: MutableList<WorkspaceEntityBase>) {
    require(rootEntity is WorkspaceEntityWithPersistentId) {
      "Root entities without persistent ids are not yet supported"
    }

    val continueProcess = findAndReplaceRootEntity(rootEntity)
    rootTrack.remove(rootEntity).also { require(it) }

    if (!continueProcess) return

    val replaceWithRoot = replaceWithStorage.resolve(rootEntity.persistentId)!!

    for (workspaceEntityBase in rootTrack.reversed()) {

      processChildren(rootEntity, replaceWithRoot, workspaceEntityBase)

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

  private fun processChildren(thisRootEntity: WorkspaceEntity, replaceWithRoot: WorkspaceEntity, childExample: WorkspaceEntity) {
    val thisChildren = thisStorage.refs.getChildrenRefsOfParentBy((thisRootEntity as WorkspaceEntityBase).id.asParent())

    // TODO: This search won't work for abstract entities
    val thisFoundChildren = thisChildren.filterKeys { it.childClass == (childExample as WorkspaceEntityBase).id.clazz }
    require(thisFoundChildren.size < 2) { "Got unexpected amount of children" }
    require(thisFoundChildren.isNotEmpty()) { "How this may happen? Because we have at least one child in our trace" }

    val (thisConnectionId, thisChildEntityIds) = thisFoundChildren.entries.single()

    //----

    val replaceWothChildren = replaceWithStorage.refs.getChildrenRefsOfParentBy((replaceWithRoot as WorkspaceEntityBase).id.asParent())

    // TODO: This search won't work for abstract entities
    val replaceWithFoundChildren = replaceWothChildren.filterKeys { it.childClass == (childExample as WorkspaceEntityBase).id.clazz }
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
      if (thisEntityData != null) {
        when {
          entityFilter(thisEntityData.entitySource) && entityFilter(replaceWithEntityData.entitySource) -> replaceWorkspaceData(
            thisEntityData, replaceWithEntityData)
          entityFilter(thisEntityData.entitySource) && !entityFilter(replaceWithEntityData.entitySource) -> removeWorkspaceData(
            thisEntityData)
          !entityFilter(thisEntityData.entitySource) && entityFilter(replaceWithEntityData.entitySource) -> replaceWorkspaceData(
            thisEntityData, replaceWithEntityData)
          !entityFilter(thisEntityData.entitySource) && !entityFilter(replaceWithEntityData.entitySource) -> doNothingOn(thisEntityData)
        }
      }
      else {
        when {
          entityFilter(replaceWithEntityData.entitySource) -> addWorkspaceData(replaceWithEntityData)
          !entityFilter(
            replaceWithEntityData.entitySource) -> Unit // TODO Should we also track replaceWith store as we do with the local one using `state`?
        }
      }
    }

    thisChildrenMap.keys.forEach { thisEntityData -> removeWorkspaceData(thisEntityData) }
  }

  private fun replaceWorkspaceData(thisEntityData: WorkspaceEntityData<out WorkspaceEntity>,
                                   replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    operations[thisEntityData.createEntityId()] = Operation.Relabel(replaceWithEntityData.createEntityId())
    state[thisEntityData.createEntityId()] = ReplaceState.Relabel
  }

  private fun removeWorkspaceData(thisEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    operations[thisEntityData.createEntityId()] = Operation.Remove
    state[thisEntityData.createEntityId()] = ReplaceState.Remove
  }

  private fun addWorkspaceData(replaceWithEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    TODO()
  }

  private fun doNothingOn(thisEntityData: WorkspaceEntityData<out WorkspaceEntity>) {
    state[thisEntityData.createEntityId()] = ReplaceState.NoChange
  }

  private fun findAndReplaceRootEntity(rootEntity: WorkspaceEntityWithPersistentId): Boolean {
    val persistentId = rootEntity.persistentId
    val replaceWithEntity = replaceWithStorage.resolve(persistentId)
    if (replaceWithEntity == null) {
      if (entityFilter(rootEntity.entitySource)) {
        operations[(rootEntity as WorkspaceEntityBase).id] = Operation.Remove
        state[(rootEntity as WorkspaceEntityBase).id] = ReplaceState.Remove
        return false
      }
      else {
        state[(rootEntity as WorkspaceEntityBase).id] = ReplaceState.NoChange
        return false
      }
    }

    when {
      entityFilter(rootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
        operations[(rootEntity as WorkspaceEntityBase).id] = Operation.Relabel((replaceWithEntity as WorkspaceEntityBase).id)
        state[(rootEntity as WorkspaceEntityBase).id] = ReplaceState.Relabel
        return true
      }
      entityFilter(rootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
        operations[(rootEntity as WorkspaceEntityBase).id] = Operation.Remove
        state[(rootEntity as WorkspaceEntityBase).id] = ReplaceState.Remove
        return false
      }
      !entityFilter(rootEntity.entitySource) && entityFilter(replaceWithEntity.entitySource) -> {
        operations[(rootEntity as WorkspaceEntityBase).id] = Operation.Relabel((replaceWithEntity as WorkspaceEntityBase).id)
        state[(rootEntity as WorkspaceEntityBase).id] = ReplaceState.Relabel
        return true
      }
      !entityFilter(rootEntity.entitySource) && !entityFilter(replaceWithEntity.entitySource) -> {
        state[(rootEntity as WorkspaceEntityBase).id] = ReplaceState.NoChange
        return true
      }
    }

    error("Unexpected branch")
  }

  val operations = HashMap<EntityId, Operation>()
  val state = HashMap<EntityId, ReplaceState>()
}

sealed interface Operation {
  object Remove : Operation
  class Relabel(val replaceWithEntityId: EntityId) : Operation
}

sealed interface ReplaceState {
  object Relabel : ReplaceState
  object NoChange : ReplaceState
  object Remove : ReplaceState
  class Relink(val data: String) : ReplaceState
}
