// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.containers.*
import kotlin.reflect.KClass

internal data class ConnectionId<T : TypedEntity, SUBT : TypedEntity> private constructor(
  val parentClass: KClass<T>,
  val childClass: KClass<SUBT>,
  var isHard: Boolean
) {

  companion object {
    fun <T : TypedEntity, SUBT : TypedEntity> create(
      parentClass: KClass<T>, childClass: KClass<SUBT>, isHard: Boolean
    ): ConnectionId<T, SUBT> = ConnectionId(parentClass, childClass, isHard)
  }
}

/**
 * [oneToManyContainer]: [IntIntBiMap] - key - child, value - parent
 */
internal class RefsTable internal constructor(
  override val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, IntIntBiMap>,
  override val oneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, IntIntUniqueBiMap>
) : AbstractRefsTable(oneToManyContainer, oneToOneContainer) {
  constructor() : this(HashMap(), HashMap())
}

internal class MutableRefsTable(
  override val oneToManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntBiMap>,
  override val oneToOneContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntUniqueBiMap>
) : AbstractRefsTable(oneToManyContainer, oneToOneContainer) {

  constructor() : this(HashMap(), HashMap())

  private fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyMutableMap(connectionId: ConnectionId<T, SUBT>): MutableIntIntBiMap {
    val bimap = oneToManyContainer[connectionId] ?: run {
      val empty = MutableIntIntBiMap()
      oneToManyContainer[connectionId] = empty
      return empty
    }

    return when (bimap) {
      is MutableIntIntBiMap -> bimap
      is IntIntBiMap -> {
        val copy = bimap.toMutable()
        oneToManyContainer[connectionId] = copy
        copy
      }
    }
  }

  private fun <T : TypedEntity, SUBT : TypedEntity> getOneToOneMutableMap(connectionId: ConnectionId<T, SUBT>): MutableIntIntUniqueBiMap {
    val bimap = oneToOneContainer[connectionId] ?: run {
      val empty = MutableIntIntUniqueBiMap()
      oneToOneContainer[connectionId] = empty
      return empty
    }

    return when (bimap) {
      is MutableIntIntUniqueBiMap -> bimap
      is IntIntUniqueBiMap -> {
        val copy = bimap.toMutable()
        oneToOneContainer[connectionId] = copy
        copy
      }
    }
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeOneToManyRefsByParent(connectionId: ConnectionId<T, SUBT>, parentId: Int) {
    getOneToManyMutableMap(connectionId).removeValue(parentId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeOneToOneRefByParent(connectionId: ConnectionId<T, SUBT>, parentId: Int) {
    getOneToOneMutableMap(connectionId).removeValue(parentId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeOneToOneRefByChild(connectionId: ConnectionId<T, SUBT>, childId: Int) {
    getOneToOneMutableMap(connectionId).removeKey(childId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeOneToManyRefsByChild(connectionId: ConnectionId<T, SUBT>, childId: Int) {
    getOneToManyMutableMap(connectionId).removeKey(childId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeOneToManyParentToChildRef(connectionId: ConnectionId<T, SUBT>,
                                                                            parentId: Int,
                                                                            childId: Int) {
    getOneToManyMutableMap(connectionId).remove(childId, parentId)
  }

  internal fun updateOneToManyChildrenOfParent(connectionId: ConnectionId<*, *>,
                                               parentId: Int,
                                               childrenIds: AbstractIntIntMultiMap.IntSequence) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenIds.forEach { copiedMap.put(it, parentId) }
  }

  fun <T : TypedEntity, SUBT : PTypedEntity<SUBT>> updateOneToManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                                   parentId: Int,
                                                                                   childrenEntities: Sequence<SUBT>) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenEntities.forEach { copiedMap.put(it.id.arrayId, parentId) }
  }

  fun <T : TypedEntity, SUBT : PTypedEntity<SUBT>> updateOneToOneChildOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                               parentId: Int,
                                                                               childEntity: SUBT) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    copiedMap.put(childEntity.id.arrayId, parentId)
  }

  fun <T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> updateOneToOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                                   childId: Int,
                                                                                   parentEntity: T) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.put(childId, parentEntity.id.arrayId)
  }

  internal fun updateOneToManyParentOfChild(connectionId: ConnectionId<*, *>, childId: Int, parentId: Int) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.put(childId, parentId)
  }


  fun <T : PTypedEntity<T>, SUBT : TypedEntity> updateOneToManyParentOfChild(connectionId: ConnectionId<T, SUBT>, childId: Int, parent: T) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.put(childId, parent.id.arrayId)
  }

  fun toImmutable(): RefsTable = RefsTable(oneToManyContainer.mapValues { it.value.toImmutable() },
                                           oneToOneContainer.mapValues { it.value.toImmutable() })

  companion object {
    fun from(other: RefsTable): MutableRefsTable = MutableRefsTable(HashMap(other.oneToManyContainer), HashMap(other.oneToOneContainer))
  }
}

internal sealed class AbstractRefsTable constructor(
  internal open val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntBiMap>,
  internal open val oneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntUniqueBiMap>
) {

  fun <T : TypedEntity, SUBT : TypedEntity> findConnectionId(parentClass: Class<T>, childClass: Class<SUBT>): ConnectionId<T, SUBT>? {
    return (oneToManyContainer.keys.find { it.parentClass == parentClass.kotlin && it.childClass == childClass.kotlin }
            ?: oneToOneContainer.keys.find { it.parentClass == parentClass.kotlin && it.childClass == childClass.kotlin })
      as ConnectionId<T, SUBT>?
  }

  fun <T : TypedEntity> getOneToManyChildren(
    parentId: Int, parentClass: Class<T>
  ): Map<ConnectionId<T, out TypedEntity>, AbstractIntIntMultiMap.IntSequence> {
    val filtered = oneToManyContainer.filterKeys { it.parentClass.java == parentClass }
    val res = HashMap<ConnectionId<T, out TypedEntity>, AbstractIntIntMultiMap.IntSequence>()
    for ((connectionId, bimap) in filtered) {
      val keys = bimap.getKeys(parentId)
      if (!keys.isEmpty()) {
        res[connectionId as ConnectionId<T, out TypedEntity>] = keys
      }
    }
    return res
  }

  fun <T : TypedEntity> getOneToManyParents(childId: Int, childClass: Class<T>): Map<ConnectionId<out TypedEntity, T>, Int> {
    val filtered = oneToManyContainer.filterKeys { it.childClass.java == childClass }
    val res = HashMap<ConnectionId<out TypedEntity, T>, Int>()
    for ((connectionId, bimap) in filtered) {
      if (bimap.containsKey(childId)) {
        res[connectionId as ConnectionId<out TypedEntity, T>] = bimap.get(childId)
      }
    }
    return res
  }

  fun <T : TypedEntity> getOneToManyHardParentReferencesOfChild(
    childId: Int, childClass: Class<T>
  ): Map<KClass<out TypedEntity>, Int> {
    val filtered = oneToManyContainer.filterKeys { it.childClass.java == childClass && it.isHard }
    val res = HashMap<KClass<out TypedEntity>, Int>()
    for ((connectionId, bimap) in filtered) {
      if (bimap.containsKey(childId)) {
        res[connectionId.parentClass] = bimap.get(childId)
      }
    }
    return res
  }

  fun <T : TypedEntity> getOneToManyHardChildReferencesOfParent(
    parentId: Int, parentClass: Class<T>
  ): Map<KClass<out TypedEntity>, AbstractIntIntMultiMap.IntSequence> {
    val filtered = oneToManyContainer.filterKeys { it.parentClass.java == parentClass && it.isHard }
    val res = HashMap<KClass<out TypedEntity>, AbstractIntIntMultiMap.IntSequence>()
    for ((connectionId, bimap) in filtered) {
      val keys = bimap.getKeys(parentId)
      if (!keys.isEmpty()) {
        val klass = connectionId.childClass
        res[klass] = keys
      }
    }
    return res
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                 parentId: Int): AbstractIntIntMultiMap.IntSequence? {
    // TODO: 26.03.2020 What about missing values?
    return oneToManyContainer[connectionId]?.getKeys(parentId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToOneChild(connectionId: ConnectionId<T, SUBT>,
                                                             parentId: Int,
                                                             transformer: (Int) -> SUBT?): SUBT? {
    // TODO: 26.03.2020 What about missing values?
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsValue(parentId)) return null

    return transformer(bimap.getKey(parentId))
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToOneParent(connectionId: ConnectionId<T, SUBT>,
                                                              childId: Int,
                                                              transformer: (Int) -> T?): T? {
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer(bimap.get(childId))
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyParent(connectionId: ConnectionId<T, SUBT>,
                                                               childId: Int,
                                                               transformer: (Int) -> T?): T? {
    val bimap = oneToManyContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer(bimap.get(childId))
  }
}
