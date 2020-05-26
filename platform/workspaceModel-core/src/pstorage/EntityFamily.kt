// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

internal class ImmutableEntityFamily<E : TypedEntity>(
  override val entities: ArrayList<PEntityData<E>?>,
  private val emptySlotsSize: Int
) : EntityFamily<E>() {

  fun toMutable() = MutableEntityFamily(entities, true)

  override fun size(): Int = entities.size - emptySlotsSize

  inline fun assertConsistency(entityAssertion: (PEntityData<E>) -> Unit = {}) {
    var emptySlotsCounter = 0

    entities.forEachIndexed { idx, entity ->
      if (entity == null) {
        emptySlotsCounter++
      }
      else {
        assert(idx == entity.id) { "Entity with id ${entity.id} is placed at index $idx" }
        entityAssertion(entity)
      }
    }
    assert(emptySlotsCounter == emptySlotsSize) { "EntityFamily has unregistered gaps" }
  }
}

internal class MutableEntityFamily<E : TypedEntity>(
  override var entities: ArrayList<PEntityData<E>?>,

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
    if (availableSlots.contains(id)) error("id $id is already removed")
    startWrite()

    copiedToModify.remove(id)
    entities[id] = null
    amountOfGapsInEntities++
  }

  /**
   * This method adds entityData and changes it's id to the actual one
   */
  fun add(other: PEntityData<E>) {
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

  fun replaceById(entity: PEntityData<E>) {
    val id = entity.id
    if (availableSlots.contains(id)) error("Nothing to replace")
    startWrite()

    entities[id] = entity
    copiedToModify.add(id)
  }

  /**
   * Get entity data that can be modified in a save manne
   */
  fun getEntityDataForModification(arrayId: Int): PEntityData<E> {
    val entity = entities[arrayId] ?: error("Nothing to modify")
    if (arrayId in copiedToModify) return entity
    startWrite()

    val clonedEntity = entity.clone()
    entities[arrayId] = clonedEntity
    copiedToModify.add(arrayId)
    return clonedEntity
  }

  fun toImmutable(): ImmutableEntityFamily<E> {
    freezed = true
    copiedToModify.clear()
    return ImmutableEntityFamily(entities, amountOfGapsInEntities)
  }

  override fun size(): Int = entities.size - amountOfGapsInEntities

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
    @Suppress("RemoveExplicitTypeArguments")
    fun createEmptyMutable() = MutableEntityFamily<TypedEntity>(ArrayList(), false)
  }
}

internal sealed class EntityFamily<E : TypedEntity> {
  internal abstract val entities: List<PEntityData<E>?>

  operator fun get(idx: Int) = entities.getOrNull(idx)
  fun exists(id: Int) = get(id) != null
  fun all() = entities.asSequence().filterNotNull()
  fun isEmpty(): Boolean = entities.isEmpty()
  abstract fun size(): Int
}