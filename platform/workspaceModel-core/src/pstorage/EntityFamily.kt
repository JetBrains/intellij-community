// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import gnu.trove.TIntHashSet

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
  private var freezed: Boolean
) : EntityFamily<E>() {

  // This set contains empty slots at the moment of MutableEntityFamily creation
  //   New empty slots MUST NOT be added this this set.
  // TODO Fill the reason
  private val availableSlots: TIntHashSet = TIntHashSet().also {
    entities.mapIndexed { index, pEntityData -> if (pEntityData == null) it.add(index) }
  }

  private var amountOfGapsInEntities = availableSlots.size()

  private val copiedToModify: TIntHashSet = TIntHashSet()

  fun remove(id: Int) {
    if (id in availableSlots) error("id $id is already removed")
    startWrite()

    copiedToModify.remove(id)
    entities[id] = null
    amountOfGapsInEntities++
  }

  fun add(other: PEntityData<E>) {
    startWrite()

    if (availableSlots.isEmpty) {
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
    if (id in availableSlots) error("Nothing to replace")
    startWrite()

    entities[id] = entity
    copiedToModify.add(id)
  }

  fun getEntityDataForModification(id: PId<E>): PEntityData<E> {
    val entity = entities[id.arrayId] ?: error("Nothing to modify")
    if (id.arrayId in copiedToModify) return entity
    startWrite()

    val clonedEntity = entity.clone()
    entities[id.arrayId] = clonedEntity
    copiedToModify.add(id.arrayId)
    return clonedEntity
  }

  fun toImmutable(): ImmutableEntityFamily<E> {
    freezed = true
    copiedToModify.clear()
    return ImmutableEntityFamily(entities, amountOfGapsInEntities)
  }

  override fun size(): Int = entities.size - amountOfGapsInEntities

  private fun startWrite() {
    if (!freezed) return

    entities = ArrayList(entities)
    freezed = false
  }

  private fun TIntHashSet.pop(): Int {
    val iterator = this.iterator()
    if (!iterator.hasNext()) error("Set is empty")
    val res = iterator.next()
    iterator.remove()
    return res
  }

  companion object {
    fun <E : TypedEntity> createEmptyMutable() = MutableEntityFamily<E>(ArrayList(), false)
  }
}

internal sealed class EntityFamily<E : TypedEntity> {

  internal abstract val entities: List<PEntityData<E>?>

  operator fun get(idx: Int) = entities.getOrNull(idx)

  fun all() = entities.asSequence().filterNotNull()

  fun exists(id: Int) = get(id) != null

  fun isEmpty() = entities.isEmpty()

  abstract fun size(): Int
}