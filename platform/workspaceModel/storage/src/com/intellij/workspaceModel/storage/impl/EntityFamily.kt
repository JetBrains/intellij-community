// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.workspaceModel.storage.WorkspaceEntity
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

internal class ImmutableEntityFamily<E : WorkspaceEntity>(
  override val entities: ArrayList<WorkspaceEntityData<E>?>,
  private val emptySlotsSize: Int
) : EntityFamily<E>() {

  constructor(): this(ArrayList(), 0)

  fun toMutable() = MutableEntityFamily(entities, true)

  override fun size(): Int = entities.size - emptySlotsSize

  override fun familyCheck() {
    val emptySlotsCounter = entities.count { it == null }
    assert(emptySlotsCounter == emptySlotsSize) { "EntityFamily has unregistered gaps" }
  }
}

internal class MutableEntityFamily<E : WorkspaceEntity>(
  override var entities: ArrayList<WorkspaceEntityData<E>?>,

  // if [freezed] is true, [entities] array MUST BE copied before modifying it.
  private var freezed: Boolean
) : EntityFamily<E>() {

  // This set contains empty slots at the moment of MutableEntityFamily creation
  //   New empty slots MUST NOT be added this this set, otherwise it would be impossible to distinguish (remove + add) and (replace) events
  private val availableSlots: IntSet = IntOpenHashSet().also {
    entities.mapIndexed { index, pEntityData -> if (pEntityData == null) it.add(index) }
  }

  // Current amount of nulls in entities
  private var amountOfGapsInEntities = availableSlots.size

  // Indexes of entity data that are copied for modification. These entities can be safely modified.
  private val copiedToModify: IntSet = IntOpenHashSet()

  fun remove(id: Int) {
    if (availableSlots.contains(id) || entities[id] == null) {
      return
    }

    startWrite()

    copiedToModify.remove(id)
    entities[id] = null
    amountOfGapsInEntities++
  }

  /**
   * This method adds entityData and changes it's id to the actual one
   */
  fun add(other: WorkspaceEntityData<E>) {
    startWrite()

    if (availableSlots.isEmpty()) {
      other.id = entities.size
      entities.add(other)
    }
    else {
      val emptySlot = availableSlots.pop()
      other.id = emptySlot
      entities[emptySlot] = other
      amountOfGapsInEntities--
    }
    copiedToModify.add(other.id)
  }

  fun book(): Int {
    startWrite()

    val bookedId = if (availableSlots.isEmpty()) {
      entities.add(null)
      amountOfGapsInEntities++
      entities.lastIndex
    }
    else {
      val emptySlot = availableSlots.pop()
      entities[emptySlot] = null
      emptySlot
    }
    copiedToModify.add(bookedId)
    return bookedId
  }

  fun insertAtId(data: WorkspaceEntityData<E>) {
    startWrite()

    val prevValue = entities[data.id]
    entities[data.id] = data
    availableSlots.remove(data.id)
    if (prevValue == null) amountOfGapsInEntities--

    copiedToModify.add(data.id)
  }

  fun replaceById(entity: WorkspaceEntityData<E>) {
    val id = entity.id
    if (entities[id] == null) {
      thisLogger().error("Nothing to replace. EntityData: $entity")
      return
    }

    startWrite()

    entities[id] = entity
    copiedToModify.add(id)
  }

  /**
   * Get entity data that can be modified in a save manne
   */
  fun getEntityDataForModificationOrNull(arrayId: Int): WorkspaceEntityData<E>? {
    val entity = entities.getOrNull(arrayId) ?: return null
    if (arrayId in copiedToModify) return entity
    startWrite()

    val clonedEntity = entity.clone()
    entities[arrayId] = clonedEntity
    copiedToModify.add(arrayId)
    return clonedEntity
  }

  fun getEntityDataForModification(arrayId: Int): WorkspaceEntityData<E> {
    return getEntityDataForModificationOrNull(arrayId) ?: error("Nothing to modify")
  }

  fun set(position: Int, value: WorkspaceEntityData<E>) {
    startWrite()
    entities[position] = value
  }

  fun toImmutable(): ImmutableEntityFamily<E> {
    freezed = true
    copiedToModify.clear()
    return ImmutableEntityFamily(entities, amountOfGapsInEntities)
  }

  override fun size(): Int = entities.size - amountOfGapsInEntities

  override fun familyCheck() {}

  internal fun isEmpty() = entities.size == amountOfGapsInEntities

  /** This method should always be called before any modification */
  private fun startWrite() {
    if (!freezed) return

    entities = ArrayList(entities)
    freezed = false
  }

  private fun IntSet.pop(): Int {
    val iterator = this.iterator()
    if (!iterator.hasNext()) error("Set is empty")
    val res = iterator.nextInt()
    iterator.remove()
    return res
  }

  companion object {
    // Do not remove parameter. Kotlin fails with compilation without it
    fun <T: WorkspaceEntity> createEmptyMutable() = MutableEntityFamily<T>(ArrayList(), false)
  }
}

internal sealed class EntityFamily<E : WorkspaceEntity> {
  internal abstract val entities: List<WorkspaceEntityData<E>?>

  operator fun get(idx: Int) = entities.getOrNull(idx)
  fun getOrFail(idx: Int) = entities[idx]
  fun exists(id: Int) = get(id) != null
  fun all() = entities.asSequence().filterNotNull()
  abstract fun size(): Int

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EntityFamily<*>) return false

    if (entities != other.entities) return false

    return true
  }

  override fun hashCode(): Int = entities.hashCode()

  override fun toString(): String {
    return "EntityFamily(entities=$entities)"
  }

  protected abstract fun familyCheck()

  inline fun assertConsistency(entityAssertion: (WorkspaceEntityData<E>) -> Unit = {}) {
    entities.forEachIndexed { idx, entity ->
      if (entity != null) {
        assert(idx == entity.id) { "Entity with id ${entity.id} is placed at index $idx" }
        entityAssertion(entity)
      }
    }
    familyCheck()
  }
}
