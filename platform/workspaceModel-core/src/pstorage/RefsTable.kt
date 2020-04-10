// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
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
    ONE_TO_ABSTRACT_MANY,
    ABSTRACT_ONE_TO_ONE
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
  override val oneToAbstractManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BidirectionalMap<PId<*>, PId<*>>>,
  override val abstractOneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BiMap<PId<*>, PId<*>>>
) : AbstractRefsTable() {
  constructor() : this(HashMap(), HashMap(), BidirectionalMap(), HashBiMap.create())
}

internal class MutableRefsTable(
  override val oneToManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntBiMap>,
  override val oneToOneContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, BidirectionalMap<PId<*>, PId<*>>>,
  override val abstractOneToOneContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, BiMap<PId<*>, PId<*>>>
) : AbstractRefsTable() {

  constructor() : this(HashMap(), HashMap(), BidirectionalMap(), HashBiMap.create())

  private val oneToAbstractManyCopiedToModify: MutableSet<ConnectionId<out TypedEntity, out TypedEntity>> = HashSet()
  private val abstractOneToOneCopiedToModify: MutableSet<ConnectionId<out TypedEntity, out TypedEntity>> = HashSet()

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

  private fun <T : TypedEntity, SUBT : TypedEntity> getAbstractOneToOneMutableMap(connectionId: ConnectionId<T, SUBT>): BiMap<PId<*>, PId<*>> {
    if (connectionId !in abstractOneToOneContainer) {
      abstractOneToOneContainer[connectionId] = HashBiMap.create()
    }

    return if (connectionId in abstractOneToOneCopiedToModify) {
      abstractOneToOneContainer[connectionId]!!
    }
    else {
      val copy = HashBiMap.create<PId<*>, PId<*>>()
      val original = abstractOneToOneContainer[connectionId]!!
      original.forEach { (k, v) -> copy[k] = v }
      abstractOneToOneContainer[connectionId] = copy
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
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).removeValue(parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).removeValue(parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).removeValue(parentId)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).inverse().remove(parentId)
    }.let { }
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
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).remove(childId, parentId)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).remove(childId, parentId)
    }.let { }
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> updateChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                            parentId: PId<T>,
                                                                            childrenIds: List<PId<out TypedEntity>>) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeValue(parentId.arrayId)
        val children = childrenIds.map { it.arrayId }.toIntArray()
        copiedMap.putAll(children, parentId.arrayId)
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
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val copiedMap = getAbstractOneToOneMutableMap(connectionId)
        copiedMap.inverse().remove(parentId)
        childrenIds.forEach { copiedMap[it] = parentId }
      }
    }.let { }
  }

  fun <T : TypedEntity, SUBT : PTypedEntity> updateOneToManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                             parentId: Int,
                                                                             childrenEntities: Sequence<SUBT>) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    val children = childrenEntities.map { it.id.arrayId }.toList().toIntArray()
    copiedMap.putAll(children, parentId)
  }

  fun <T : TypedEntity, SUBT : PTypedEntity> updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                                     parentId: PId<T>,
                                                                                     childrenEntities: Sequence<SUBT>) {
    val copiedMap = getOneToAbstractManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenEntities.forEach { copiedMap[it.id] = parentId }
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity> updateOneToAbstractOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                                  childId: PId<SUBT>,
                                                                                  parentEntity: T) {
    val copiedMap = getAbstractOneToOneMutableMap(connectionId)
    copiedMap.remove(childId)
    copiedMap[childId] = parentEntity.id
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
                                                                         parentId: PId<out TypedEntity>) {
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeKey(childId.arrayId)
        copiedMap.putAll(intArrayOf(childId.arrayId), parentId.arrayId)
      }
      ConnectionId.ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        copiedMap.removeKey(childId.arrayId)
        copiedMap.putForce(childId.arrayId, parentId.arrayId)
      }
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val copiedMap = getOneToAbstractManyMutableMap(connectionId)
        copiedMap.remove(childId)
        copiedMap[childId] = parentId
      }
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val copiedMap = getAbstractOneToOneMutableMap(connectionId)
        copiedMap.remove(childId)
        copiedMap[childId] = parentId
      }
    }.let { }
  }

  fun <T : PTypedEntity, SUBT : TypedEntity> updateOneToManyParentOfChild(connectionId: ConnectionId<T, SUBT>, childId: Int, parent: T) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.putAll(intArrayOf(childId), parent.id.arrayId)
  }

  fun toImmutable(): RefsTable = RefsTable(oneToManyContainer.mapValues { it.value.toImmutable() },
                                           oneToOneContainer.mapValues { it.value.toImmutable() },
                                           oneToAbstractManyContainer.mapValues {
                                             it.value.let { value ->
                                               val map = BidirectionalMap<PId<*>, PId<*>>()
                                               value.forEach { (k, v) -> map[k] = v }
                                               map
                                             }
                                           },
                                           abstractOneToOneContainer.mapValues {
                                             it.value.let { value ->
                                               val map = HashBiMap.create<PId<*>, PId<*>>()
                                               value.forEach { (k, v) -> map[k] = v }
                                               map
                                             }
                                           }
  )

  companion object {
    fun from(other: RefsTable): MutableRefsTable = MutableRefsTable(HashMap(other.oneToManyContainer), HashMap(other.oneToOneContainer),
                                                                    HashMap(other.oneToAbstractManyContainer),
                                                                    HashMap(other.abstractOneToOneContainer))
  }
}

internal sealed class AbstractRefsTable {

  internal abstract val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntBiMap>
  internal abstract val oneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, AbstractIntIntUniqueBiMap>
  internal abstract val oneToAbstractManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BidirectionalMap<PId<*>, PId<*>>>
  internal abstract val abstractOneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BiMap<PId<*>, PId<*>>>

  fun <T : TypedEntity, SUBT : TypedEntity> findConnectionId(parentClass: Class<T>, childClass: Class<SUBT>): ConnectionId<T, SUBT>? {
    // TODO: 07.04.2020 broken for abstract container
    return (oneToManyContainer.keys.find { it.parentClass == parentClass.kotlin && it.childClass == childClass.kotlin }
            ?: oneToOneContainer.keys.find { it.parentClass == parentClass.kotlin && it.childClass == childClass.kotlin }
            ?: oneToAbstractManyContainer.keys.find {
              it.parentClass.java.isAssignableFrom(parentClass) && it.childClass.java.isAssignableFrom(childClass)
            }
            ?: abstractOneToOneContainer.keys.find {
              it.parentClass.java.isAssignableFrom(parentClass) && it.childClass.java.isAssignableFrom(childClass)
            })
      as ConnectionId<T, SUBT>?
  }

  fun <SUBT : TypedEntity> getParentRefsOfChild(childId: PId<SUBT>, hardReferencesOnly: Boolean): ParentConnectionsInfo<SUBT> {
    val childArrayId = childId.arrayId
    val childClass = childId.clazz.java

    val res = HashMap<ConnectionId<out TypedEntity, SUBT>, PId<out TypedEntity>>()

    val filteredOneToMany = oneToManyContainer
      .filterKeys { it.childClass.java == childClass && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<out TypedEntity, SUBT>, AbstractIntIntBiMap>
    for ((connectionId, bimap) in filteredOneToMany) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, PId(value, connectionId.parentClass))
      if (existingValue != null) error("This parent already exists")
    }

    val filteredOneToOne = oneToOneContainer
      .filterKeys { it.childClass.java == childClass && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<out TypedEntity, SUBT>, AbstractIntIntUniqueBiMap>
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, PId(value, connectionId.parentClass))
      if (existingValue != null) error("This parent already exists")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.childClass.java.isAssignableFrom(childClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<out TypedEntity, SUBT>, BidirectionalMap<PId<*>, PId<*>>>
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) error("This parent already exists")
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.childClass.java.isAssignableFrom(childClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<out TypedEntity, SUBT>, BiMap<PId<*>, PId<*>>>
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) error("This parent already exists")
    }

    return res
  }

  fun <T : TypedEntity> getChildrenRefsOfParentBy(parentId: PId<T>, hardReferencesOnly: Boolean): ChildrenConnectionsInfo<T> {
    val parentArrayId = parentId.arrayId
    val parentClass = parentId.clazz.java

    val res = HashMap<ConnectionId<T, out TypedEntity>, Set<PId<out TypedEntity>>>()

    val filteredOneToMany = oneToManyContainer
      .filterKeys { it.parentClass.java == parentClass && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<T, out TypedEntity>, AbstractIntIntBiMap>
    for ((connectionId, bimap) in filteredOneToMany) {
      val keys = bimap.getKeys(parentArrayId)
      if (!keys.isEmpty()) {
        val children = keys.map { PId(it, connectionId.childClass) }.toSet()
        val existingValue = res.putIfAbsent(connectionId, children)
        if (existingValue != null) error("These children already exist")
      }
    }

    val filteredOneToOne = oneToOneContainer
      .filterKeys { it.parentClass.java == parentClass && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<T, out TypedEntity>, AbstractIntIntUniqueBiMap>
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsValue(parentArrayId)) continue
      val key = bimap.getKey(parentArrayId)
      val existingValue = res.putIfAbsent(connectionId, setOf(PId(key, connectionId.childClass)))
      if (existingValue != null) error("These children already exist")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.parentClass.java.isAssignableFrom(parentClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<T, out TypedEntity>, BidirectionalMap<PId<*>, PId<*>>>
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      val keys: List<PId<*>> = bimap.getKeysByValue(parentId) ?: continue
      if (keys.isNotEmpty()) {
        val existingValue = res.putIfAbsent(connectionId, keys.toSet())
        if (existingValue != null) error("These children already exist")
      }
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.parentClass.java.isAssignableFrom(parentClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<T, out TypedEntity>, BiMap<PId<*>, PId<*>>>
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      val key: PId<*>? = bimap.inverse().get(parentId)
      if (key == null) continue
      val existingValue = res.putIfAbsent(connectionId, setOf(key))
      if (existingValue != null) error("These children already exist")
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

  fun <T : TypedEntity, SUBT : TypedEntity> getAbstractOneToOneChildren(connectionId: ConnectionId<T, SUBT>,
                                                                        parentId: PId<*>): PId<*>? {
    // TODO: 26.03.2020 What about missing values?
    return abstractOneToOneContainer[connectionId]?.inverse()?.get(parentId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToAbstractOneParent(connectionId: ConnectionId<T, SUBT>,
                                                                      childId: PId<*>): PId<*>? {
    // TODO: 26.03.2020 What about missing values?
    return abstractOneToOneContainer[connectionId]?.get(childId)
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
