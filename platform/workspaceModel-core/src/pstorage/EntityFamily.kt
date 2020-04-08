// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import gnu.trove.TIntHashSet

internal open class EntityFamily<E : TypedEntity> internal constructor(
  override val entities: List<PEntityData<E>?>,
  protected val emptySlotsSize: Int
) : AbstractEntityFamily<E>() {

  fun copyToMutable() = MutableEntityFamily(entities.toMutableList())

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

  override fun size(): Int = entities.size - emptySlotsSize

  companion object {
    fun <E : TypedEntity> empty(): EntityFamily<E> = Empty as EntityFamily<E>

    private object Empty : EntityFamily<PTypedEntity>(emptyList(), 0)
  }
}

internal class MutableEntityFamily<E : TypedEntity>(
  override val entities: MutableList<PEntityData<E>?>
) : AbstractEntityFamily<E>() {

  // This set contains empty slots at the moment of MutableEntityFamily creation
  //   New empty slots MUST NOT be added this this set.
  // TODO Fill the reason
  private val emptySlots: TIntHashSet = TIntHashSet().also {
    entities.mapIndexed { index, pEntityData -> if (pEntityData == null) it.add(index) }
  }

  private val copiedToModify: TIntHashSet = TIntHashSet()

  fun remove(id: Int) {
    if (id in emptySlots) return

    copiedToModify.remove(id)
    entities[id] = null
  }

  fun add(other: PEntityData<E>) {
    if (emptySlots.isEmpty) {
      other.id = entities.size
      entities += other
    }
    else {
      val emptySlot = emptySlots.pop()
      other.id = emptySlot
      entities[emptySlot] = other
    }
    copiedToModify.add(other.id)
  }

  fun replaceById(entity: PEntityData<E>) {
    val id = entity.id
    if (id in emptySlots) error("Nothing to replace")
    entities[id] = entity
    copiedToModify.add(id)
  }

  fun getEntityDataForModification(id: PId<E>): PEntityData<E> {
    val entity = entities[id.arrayId] ?: error("Nothing to modify")
    if (id.arrayId in copiedToModify) return entity

    val clonedEntity = entity.clone()
    entities[id.arrayId] = clonedEntity
    copiedToModify.add(id.arrayId)
    return clonedEntity
  }

  private fun TIntHashSet.pop(): Int {
    val iterator = this.iterator()
    if (!iterator.hasNext()) error("Set is empty")
    val res = iterator.next()
    iterator.remove()
    return res
  }

  fun toImmutable(): EntityFamily<E>{
    copiedToModify.clear()
    return EntityFamily(entities.toList(), emptySlots.size())
  }

  override fun size(): Int = entities.size - emptySlots.size()

  companion object {
    fun <E : TypedEntity> createEmptyMutable() = MutableEntityFamily<E>(mutableListOf())
  }
}

internal sealed class AbstractEntityFamily<E : TypedEntity> {

  protected abstract val entities: List<PEntityData<E>?>

  operator fun get(idx: Int) = entities.getOrNull(idx)

  fun all() = entities.asSequence().filterNotNull()

  fun exists(id: Int) = get(id) != null

  abstract fun size(): Int
}