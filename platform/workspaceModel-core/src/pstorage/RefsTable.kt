// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.containers.IntIntBiMap
import com.intellij.workspace.api.pstorage.containers.IntIntMultiMap
import kotlin.reflect.KClass

data class ConnectionId<T : TypedEntity, SUBT : TypedEntity> private constructor(
  val toSingleClass: KClass<T>,
  val toSequenceClass: KClass<SUBT>,
  var isHard: Boolean
) {

  companion object {
    fun <T : TypedEntity, SUBT : TypedEntity> create(toSingle: KClass<T>,
                                                     toSequence: KClass<SUBT>,
                                                     isHard: Boolean): ConnectionId<T, SUBT> {
      return ConnectionId(toSingle, toSequence, isHard)
    }
  }
}

open class RefsTable private constructor(
  protected open val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, IntIntBiMap>
) {

  constructor() : this(HashMap())

  internal fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyTable(connectionId: ConnectionId<T, SUBT>): IntIntBiMap? {
    return oneToManyContainer[connectionId]
  }

  fun <T : TypedEntity> getHardReferencesOf(entityClass: Class<T>,
                                            localIndex: Int): Map<KClass<out TypedEntity>, IntIntMultiMap.IntSequence> {
    val filtered = oneToManyContainer.filterKeys { it.toSingleClass == entityClass && it.isHard }
    val res = HashMap<KClass<out TypedEntity>, IntIntMultiMap.IntSequence>()
    for ((connectionId, bimap) in filtered) {
      val keys = bimap.getKeys(localIndex)
      if (!keys.isEmpty()) {
        val klass = connectionId.toSequenceClass
        res[klass] = keys
      }
    }
    return res
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToMany(connectionId: ConnectionId<T, SUBT>,
                                                         localIndex: Int): IntIntMultiMap.IntSequence? {
    // TODO: 26.03.2020 What about missing values?
    return oneToManyContainer[connectionId]?.getKeys(localIndex)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getManyToOne(connectionId: ConnectionId<T, SUBT>,
                                                         localIndex: Int,
                                                         transformer: (Int) -> T?): T? {
    val res = oneToManyContainer[connectionId]?.get(localIndex) ?: return null
    if (res == -1) return null
    return transformer(res)
  }

  fun overlapBy(other: RefsTable): RefsTable = RefsTable(this.oneToManyContainer + other.oneToManyContainer)
}

class MutableRefsTable : RefsTable() {
  override val oneToManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, IntIntBiMap> = HashMap()

  fun <T : TypedEntity, SUBT : TypedEntity> removeManyToOne(connectionId: ConnectionId<T, SUBT>, id: Int) {
    oneToManyContainer[connectionId]?.removeKey(id)
  }

  fun <T : TypedEntity, SUBT : PTypedEntity<SUBT>> updateOneToMany(connectionId: ConnectionId<T, SUBT>, id: Int, updateTo: Sequence<SUBT>) {
    val table = if (connectionId in oneToManyContainer) {
      oneToManyContainer[connectionId]!!.also { it.removeValue(id) }
    }
    else {
      IntIntBiMap().also { oneToManyContainer[connectionId] = it }
    }
    updateTo.forEach { table.put(it.id.arrayId, id) }
  }

  fun <T : PTypedEntity<T>, SUBT : TypedEntity> updateManyToOne(connectionId: ConnectionId<T, SUBT>, id: Int, updateTo: T) {
    val table = if (connectionId in oneToManyContainer) {
      oneToManyContainer[connectionId]!!.also { it.removeKey(id) }
    }
    else {
      IntIntBiMap().also { oneToManyContainer[connectionId] = it }
    }
    table.put(id, updateTo.id.arrayId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> cloneTableFrom(connectionId: ConnectionId<T, SUBT>, other: RefsTable) {
    other.getOneToManyTable(connectionId)?.let {
      oneToManyContainer[connectionId] = it.copy()
    }
  }

  fun clear() = oneToManyContainer.clear()
}