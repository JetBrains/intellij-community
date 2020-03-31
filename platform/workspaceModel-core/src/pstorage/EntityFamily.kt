// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import gnu.trove.TIntHashSet

internal open class EntityFamily<E : TypedEntity> internal constructor(
  protected open val entities: List<PEntityData<E>?>,
  protected val emptySlots: TIntHashSet
) {

  operator fun get(idx: Int) = entities[idx]

  fun copyToMutable() = MutableEntityFamily(entities.toMutableList(), true)

  fun all() = entities.asSequence().filterNotNull()

  fun exists(id: Int) = entities[id] != null

  val size: Int
    get() = entities.size - emptySlots.size()

  companion object {
    fun <E : TypedEntity> empty(): EntityFamily<E> = Empty as EntityFamily<E>

    private object Empty : EntityFamily<PTypedEntity<*>>(emptyList(),
                                                         TIntHashSet())
  }
}

internal class MutableEntityFamily<E : TypedEntity>(
  override val entities: MutableList<PEntityData<E>?>,
  var familyCopiedToModify: Boolean
) : EntityFamily<E>(
  entities,
  TIntHashSet().also { entities.mapIndexed { index, pEntityData -> if (pEntityData == null) it.add(index) } }
) {

  private val copiedToModify: TIntHashSet = TIntHashSet()

  fun remove(id: Int) {
    if (id in emptySlots) return

    emptySlots.add(id)
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
    emptySlots.remove(id)
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

  fun freeze() {
    this.familyCopiedToModify = false
    this.copiedToModify.clear()
  }

  companion object {
    fun <E : TypedEntity> createEmptyMutable() = MutableEntityFamily<E>(mutableListOf(), true)
  }
}