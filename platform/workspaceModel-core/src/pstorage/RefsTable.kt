// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.containers.*
import kotlin.reflect.KClass

internal data class ConnectionId<T : TypedEntity, SUBT : TypedEntity> private constructor(
  val parentClass: KClass<T>,
  val childClass: KClass<SUBT>,
  var isHard: Boolean,
  var connectionType: ConnectionType
) {
  enum class ConnectionType {
    ONE_TO_ONE,
    ONE_TO_MANY,
    ONE_TO_ABSTRACT_MANY
  }

  companion object {
    fun <T : TypedEntity, SUBT : TypedEntity> create(
      parentClass: KClass<T>, childClass: KClass<SUBT>, isHard: Boolean, connectionType: ConnectionType
    ): ConnectionId<T, SUBT> = ConnectionId(parentClass, childClass, isHard, connectionType)
  }
}

/**
 * [oneToManyContainer]: [IntIntBiMap] - key - child, value - parent
 */
internal class RefsTable internal constructor(
  override val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, IntIntBiMap>,
  override val oneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, IntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BidirectionalMap<PId<*>, PId<*>>>
) : AbstractRefsTable(oneToManyContainer, oneToOneContainer, oneToAbstractManyContainer) {
  constructor() : this(HashMap(), HashMap(), BidirectionalMap())
}

internal class MutableRefsTable(
  override val oneToManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntBiMap>,
  override val oneToOneContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, BidirectionalMap<PId<*>, PId<*>>>
) : AbstractRefsTable(oneToManyContainer, oneToOneContainer, oneToAbstractManyContainer) {

  constructor() : this(HashMap(), HashMap(), BidirectionalMap())

  private val oneToAbstractManyCopiedToModify: MutableSet<ConnectionId<out TypedEntity, out TypedEntity>> = HashSet()

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

  private fun <T : TypedEntity, SUBT : TypedEntity> getOneToAbstractManyMutableMap(connectionId: ConnectionId<T, SUBT>): BidirectionalMap<PId<*>, PId<*>> {
    if (connectionId !in oneToAbstractManyContainer) {
      oneToAbstractManyContainer[connectionId] = BidirectionalMap()
    }

    return if (connectionId in oneToAbstractManyCopiedToModify) {
      oneToAbstractManyContainer[connectionId]!!
    }
    else {
      val copy = BidirectionalMap<PId<*>, PId<*>>()
      val original = oneToAbstractManyContainer[connectionId]!!
      original.forEach { (k, v) -> copy[k] = v }
      oneToAbstractManyContainer[connectionId] = copy
      copy
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

  fun <T : TypedEntity, SUBT : TypedEntity> removeRefsByParent(connectionId: ConnectionId<T, SUBT>, parentId: PId<T>) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).removeValue(parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).removeValue(parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).removeValue(parentId)
    }
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

  fun <T : TypedEntity, SUBT : TypedEntity> removeParentToChildRef(connectionId: ConnectionId<T, SUBT>,
                                                                   parentId: PId<T>,
                                                                   childId: PId<SUBT>) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).remove(childId, parentId)
    }
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> updateChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                            parentId: PId<T>,
                                                                            childrenIds: List<PId<out TypedEntity>>) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeValue(parentId.arrayId)
        childrenIds.map { it.arrayId }.forEach { copiedMap.put(it, parentId.arrayId) }
      }
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        copiedMap.putForce(childrenIds.single().arrayId, parentId.arrayId)
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val copiedMap = getOneToAbstractManyMutableMap(connectionId)
        copiedMap.removeValue(parentId)
        childrenIds.forEach { copiedMap[it] = parentId }
      }
    }
  }

  fun <T : TypedEntity, SUBT : PTypedEntity> updateOneToManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                             parentId: Int,
                                                                             childrenEntities: Sequence<SUBT>) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenEntities.forEach { copiedMap.put(it.id.arrayId, parentId) }
  }

  fun <T : TypedEntity, SUBT : PTypedEntity> updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                                     parentId: PId<T>,
                                                                                     childrenEntities: Sequence<SUBT>) {
    val copiedMap = getOneToAbstractManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenEntities.forEach { copiedMap[it.id] = parentId }
  }

  fun <T : TypedEntity, SUBT : PTypedEntity> updateOneToOneChildOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                         parentId: Int,
                                                                         childEntity: SUBT) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    copiedMap.put(childEntity.id.arrayId, parentId)
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                          childId: Int,
                                                                          parentEntity: T) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.put(childId, parentEntity.id.arrayId)
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> updateParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                         childId: PId<SUBT>,
                                                                         parentId: PId<T>) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeKey(childId.arrayId)
        copiedMap.put(childId.arrayId, parentId.arrayId)
      }
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        copiedMap.removeKey(childId.arrayId)
        copiedMap.putForce(childId.arrayId, parentId.arrayId)
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val copiedMap = getOneToAbstractManyMutableMap(connectionId)
        copiedMap.remove(childId)
        copiedMap.put(childId, parentId)
      }
    }
  }

  fun <T : PTypedEntity, SUBT : TypedEntity> updateOneToManyParentOfChild(connectionId: ConnectionId<T, SUBT>, childId: Int, parent: T) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.put(childId, parent.id.arrayId)
  }

  fun toImmutable(): RefsTable = RefsTable(oneToManyContainer.mapValues { it.value.toImmutable() },
                                           oneToOneContainer.mapValues { it.value.toImmutable() },
                                           oneToAbstractManyContainer.mapValues {
                                             it.value.let { value ->
                                               val map = BidirectionalMap<PId<*>, PId<*>>()
                                               value.forEach { (k, v) -> map[k] = v }
                                               map
                                             }
                                           }
  )

  companion object {
    fun from(other: RefsTable): MutableRefsTable = MutableRefsTable(HashMap(other.oneToManyContainer), HashMap(other.oneToOneContainer),
                                                                    HashMap(other.oneToAbstractManyContainer))
  }
}

internal sealed class AbstractRefsTable constructor(
  internal open val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntBiMap>,
  internal open val oneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntUniqueBiMap>,
  internal open val oneToAbstractManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BidirectionalMap<PId<*>, PId<*>>>
) {

  fun <T : TypedEntity, SUBT : TypedEntity> findConnectionId(parentClass: Class<T>, childClass: Class<SUBT>): ConnectionId<T, SUBT>? {
    // TODO: 07.04.2020 broken for abstract container
    return (oneToManyContainer.keys.find { it.parentClass == parentClass.kotlin && it.childClass == childClass.kotlin }
            ?: oneToOneContainer.keys.find { it.parentClass == parentClass.kotlin && it.childClass == childClass.kotlin }
            ?: oneToAbstractManyContainer.keys.find { it.parentClass == parentClass.kotlin && it.childClass == childClass.kotlin })
      as ConnectionId<T, SUBT>?
  }

  fun <T : TypedEntity, SUBT : TypedEntity> findConnectionIdOrDie(parentClass: Class<T>, childClass: Class<SUBT>): ConnectionId<T, SUBT> {
    return findConnectionId(parentClass, childClass) ?: error("ConnectionId doesn't exist")
  }

  fun <T : TypedEntity> getParentRefsOfChild(childId: PId<T>, hardReferencesOnly: Boolean): Set<PId<out TypedEntity>> {
    val childArrayId = childId.arrayId
    val childClass = childId.clazz.java

    val res = HashSet<PId<out TypedEntity>>()

    val filteredOneToMany = oneToManyContainer.filterKeys { it.childClass.java == childClass && (!hardReferencesOnly || it.isHard) }
    for ((connectionId, bimap) in filteredOneToMany) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      res += PId(value, connectionId.parentClass)
    }

    val filteredOneToOne = oneToOneContainer.filterKeys { it.childClass.java == childClass && (!hardReferencesOnly || it.isHard) }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      res += PId(value, connectionId.parentClass)
    }

    val filteredOneToAbstractOne = oneToAbstractManyContainer.filterKeys { it.childClass.java == childClass && (!hardReferencesOnly || it.isHard) }
    for ((_, bimap) in filteredOneToAbstractOne) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap.get(childId) ?: continue
      res += value
    }

    return res
  }

  fun <T : TypedEntity> getChildrenRefsOfParent(parentId: PId<T>, hardReferencesOnly: Boolean): Set<PId<out TypedEntity>> {
    val parentArrayId = parentId.arrayId
    val parentClass = parentId.clazz.java

    val res = HashSet<PId<out TypedEntity>>()

    val filteredOneToMany = oneToManyContainer.filterKeys { it.parentClass.java == parentClass && (!hardReferencesOnly || it.isHard) }
    for ((connectionId, bimap) in filteredOneToMany) {
      val keys = bimap.getKeys(parentArrayId)
      if (!keys.isEmpty()) {
        res += keys.map { PId(it, connectionId.childClass) }
      }
    }

    val filteredOneToOne = oneToOneContainer.filterKeys { it.parentClass.java == parentClass && (!hardReferencesOnly || it.isHard) }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsValue(parentArrayId)) continue
      val key = bimap.getKey(parentArrayId)
      res += PId(key, connectionId.childClass)
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer.filterKeys { it.parentClass.java == parentClass && (!hardReferencesOnly || it.isHard) }
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      val keys = bimap.getKeysByValue(parentId) ?: continue
      if (keys.isNotEmpty()) {
        res += keys
      }
    }

    return res
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                 parentId: Int): AbstractIntIntMultiMap.IntSequence? {
    // TODO: 26.03.2020 What about missing values?
    return oneToManyContainer[connectionId]?.getKeys(parentId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToAbstractManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                         parentId: PId<*>): List<PId<*>>? {
    // TODO: 26.03.2020 What about missing values?
    return oneToAbstractManyContainer[connectionId]?.getKeysByValue(parentId)
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
