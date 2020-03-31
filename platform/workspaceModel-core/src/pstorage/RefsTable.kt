// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.containers.IntIntBiMap
import com.intellij.workspace.api.pstorage.containers.IntIntMultiMap
import kotlin.reflect.KClass

internal data class ConnectionId<T : TypedEntity, SUBT : TypedEntity> private constructor(
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

internal open class RefsTable internal constructor(
  internal open val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, IntIntBiMap>
) {

  constructor() : this(HashMap())

  fun <T : TypedEntity> getChildren(id: Int, entityClass: Class<T>): Map<ConnectionId<T, out TypedEntity>, IntIntMultiMap.IntSequence> {
    val filtered = oneToManyContainer.filterKeys { it.toSingleClass.java == entityClass }
    val res = HashMap<ConnectionId<T, out TypedEntity>, IntIntMultiMap.IntSequence>()
    for ((connectionId, bimap) in filtered) {
      val keys = bimap.getKeys(id)
      if (!keys.isEmpty()) {
        res[connectionId as ConnectionId<T, out TypedEntity>] = keys
      }
    }
    return res
  }

  fun <T : TypedEntity> getParents(id: Int, entityClass: Class<T>): Map<ConnectionId<out TypedEntity, T>, Int> {
    val filtered = oneToManyContainer.filterKeys { it.toSequenceClass.java == entityClass }
    val res = HashMap<ConnectionId<out TypedEntity, T>, Int>()
    for ((connectionId, bimap) in filtered) {
      val keys = bimap.get(id)
      if (keys != -1) {
        res[connectionId as ConnectionId<out TypedEntity, T>] = keys
      }
    }
    return res
  }

  fun <T : TypedEntity> getHardReferencesOf(entityClass: Class<T>,
                                            localIndex: Int): Map<KClass<out TypedEntity>, IntIntMultiMap.IntSequence> {
    val filtered = oneToManyContainer.filterKeys { it.toSingleClass.java == entityClass && it.isHard }
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
}

internal class MutableRefsTable : RefsTable() {
  override val oneToManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, IntIntBiMap> = HashMap()
  private val copiedToModify: MutableSet<ConnectionId<out TypedEntity, out TypedEntity>> = HashSet()

  private fun <T : TypedEntity, SUBT : TypedEntity> getMutableMap(connectionId: ConnectionId<T, SUBT>): IntIntBiMap {
    if (connectionId in copiedToModify) return oneToManyContainer[connectionId] ?: error("")
    val copy = oneToManyContainer[connectionId]?.copy() ?: IntIntBiMap()
    oneToManyContainer[connectionId] = copy
    copiedToModify.add(connectionId)
    return copy
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeOneToMany(connectionId: ConnectionId<T, SUBT>, id: Int) {
    getMutableMap(connectionId).removeValue(id)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeManyToOne(connectionId: ConnectionId<T, SUBT>, id: Int) {
    getMutableMap(connectionId).removeKey(id)
  }

  internal fun updateOneToMany(connectionId: ConnectionId<*, *>, id: Int, updateTo: IntIntMultiMap.IntSequence) {
    val copiedMap = getMutableMap(connectionId)
    copiedMap.removeValue(id)
    updateTo.forEach { copiedMap.put(it, id) }
  }

  fun <T : TypedEntity, SUBT : PTypedEntity<SUBT>> updateOneToMany(connectionId: ConnectionId<T, SUBT>, id: Int, updateTo: Sequence<SUBT>) {
    val copiedMap = getMutableMap(connectionId)
    copiedMap.removeValue(id)
    updateTo.forEach { copiedMap.put(it.id.arrayId, id) }
  }

  internal fun updateManyToOne(connectionId: ConnectionId<*, *>, id: Int, updateTo: Int) {
    val copiedMap = getMutableMap(connectionId)
    copiedMap.removeKey(id)
    copiedMap.put(id, updateTo)
  }


  fun <T : PTypedEntity<T>, SUBT : TypedEntity> updateManyToOne(connectionId: ConnectionId<T, SUBT>, id: Int, updateTo: T) {
    val copiedMap = getMutableMap(connectionId)
    copiedMap.removeKey(id)
    copiedMap.put(id, updateTo.id.arrayId)
  }

  fun toImmutable(): RefsTable {
    copiedToModify.clear()
    return RefsTable(HashMap(oneToManyContainer))
  }

  companion object {
    fun from(other: RefsTable): MutableRefsTable {
      val res = MutableRefsTable()
      res.oneToManyContainer.putAll(other.oneToManyContainer)
      return res
    }
  }
}