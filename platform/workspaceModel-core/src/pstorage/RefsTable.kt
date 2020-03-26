// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.containers.IntIntBiMap
import com.intellij.workspace.api.pstorage.containers.IntIntMultiMap
import gnu.trove.TIntObjectHashMap

inline class ConnectionId(val id: Int)

open class RefsTable private constructor(
  protected open val oneToManyContainer: Map<ConnectionId, IntIntBiMap>,
  protected val isHardLink: TIntObjectHashMap<Boolean>
) {

  fun checkConnectionId(connectionId: ConnectionId, isHard: Boolean) {
    if (connectionId.id in isHardLink) return
    isHardLink.put(connectionId.id, isHard)
  }

  constructor() : this(HashMap(), TIntObjectHashMap())

  internal fun getOneToManyTable(connectionId: ConnectionId): IntIntBiMap? {
    return oneToManyContainer[connectionId]
  }

  fun getOneToMany(connectionId: ConnectionId, localIndex: Int): IntIntMultiMap.IntSequence? {
    // TODO: 26.03.2020 What about missing values?
    return oneToManyContainer[connectionId]?.getKeys(localIndex)
  }

  fun <T : TypedEntity> getManyToOne(connectionId: ConnectionId, localIndex: Int, transformer: (Int) -> T?): T? {
    val res = oneToManyContainer[connectionId]?.get(localIndex) ?: return null
    if (res == -1) return null
    return transformer(res)
  }

  fun overlapBy(other: RefsTable): RefsTable = RefsTable(
    this.oneToManyContainer + other.oneToManyContainer,
    TIntObjectHashMap<Boolean>().also { map ->
      this.isHardLink.forEachEntry { k, v -> map.put(k, v) }
      other.isHardLink.forEachEntry { k, v -> map.put(k, v) }
    }
  )
}

class MutableRefsTable : RefsTable() {
  override val oneToManyContainer: MutableMap<ConnectionId, IntIntBiMap> = HashMap()

  fun removeManyToOne(connectionId: ConnectionId, id: Int) {
    oneToManyContainer[connectionId]?.removeKey(id)
  }

  fun <SUBT : PTypedEntity<SUBT>> updateOneToMany(connectionId: ConnectionId, id: Int, updateTo: Sequence<SUBT>) {
    val table = if (connectionId in oneToManyContainer) {
      oneToManyContainer[connectionId]!!.also { it.removeValue(id) }
    }
    else {
      IntIntBiMap().also { oneToManyContainer[connectionId] = it }
    }
    updateTo.forEach { table.put(it.id.arrayId, id) }
  }

  fun <T : PTypedEntity<T>> updateManyToOne(connectionId: ConnectionId, id: Int, updateTo: T) {
    val table = if (connectionId in oneToManyContainer) {
      oneToManyContainer[connectionId]!!.also { it.removeKey(id) }
    }
    else {
      IntIntBiMap().also { oneToManyContainer[connectionId] = it }
    }
    table.put(id, updateTo.id.arrayId)
  }

  fun cloneTableFrom(connectionId: ConnectionId, other: RefsTable) {
    other.getOneToManyTable(connectionId)?.let {
      oneToManyContainer[connectionId] = it.copy()
    }
  }

  fun clear() = oneToManyContainer.clear()
}