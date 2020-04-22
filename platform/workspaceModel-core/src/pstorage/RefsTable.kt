// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.containers.*
import pstorage.containers.LinkedBidirectionalMap
import kotlin.reflect.KClass

internal data class ConnectionId<T : TypedEntity, SUBT : TypedEntity> private constructor(
  val parentClass: KClass<T>,
  val childClass: KClass<SUBT>,
  val isHard: Boolean, // TODO: 22.04.2020 To be removed
  val connectionType: ConnectionType,
  val isParentNullable: Boolean,
  val isChildNullable: Boolean
) {
  enum class ConnectionType {
    ONE_TO_ONE,
    ONE_TO_MANY,
    ONE_TO_ABSTRACT_MANY,
    ABSTRACT_ONE_TO_ONE
  }

  companion object {
    fun <T : TypedEntity, SUBT : TypedEntity> create(
      parentClass: KClass<T>,
      childClass: KClass<SUBT>,
      isHard: Boolean,
      connectionType: ConnectionType,
      isParentNullable: Boolean,
      isChildNullable: Boolean
    ): ConnectionId<T, SUBT> = ConnectionId(parentClass, childClass, isHard, connectionType, isParentNullable, isChildNullable)
  }
}

/**
 * [oneToManyContainer]: [ImmutablePositiveIntIntBiMap] - key - child, value - parent
 */
internal class RefsTable internal constructor(
  override val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, ImmutablePositiveIntIntBiMap>,
  override val oneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, ImmutableIntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, LinkedBidirectionalMap<PId<out TypedEntity>, PId<out TypedEntity>>>,
  override val abstractOneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BiMap<PId<out TypedEntity>, PId<out TypedEntity>>>
) : AbstractRefsTable() {
  constructor() : this(HashMap(), HashMap(), HashMap(), HashMap())
}

internal class MutableRefsTable(
  override val oneToManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, PositiveIntIntBiMap>,
  override val oneToOneContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, IntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, LinkedBidirectionalMap<PId<out TypedEntity>, PId<out TypedEntity>>>,
  override val abstractOneToOneContainer: MutableMap<ConnectionId<out TypedEntity, out TypedEntity>, BiMap<PId<out TypedEntity>, PId<out TypedEntity>>>
) : AbstractRefsTable() {

  constructor() : this(HashMap(), HashMap(), HashMap(), HashMap())

  private val oneToAbstractManyCopiedToModify: MutableSet<ConnectionId<out TypedEntity, out TypedEntity>> = HashSet()
  private val abstractOneToOneCopiedToModify: MutableSet<ConnectionId<out TypedEntity, out TypedEntity>> = HashSet()

  private fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyMutableMap(connectionId: ConnectionId<T, SUBT>): MutablePositiveIntIntBiMap {
    val bimap = oneToManyContainer[connectionId] ?: run {
      val empty = MutablePositiveIntIntBiMap()
      oneToManyContainer[connectionId] = empty
      return empty
    }

    return when (bimap) {
      is MutablePositiveIntIntBiMap -> bimap
      is ImmutablePositiveIntIntBiMap -> {
        val copy = bimap.toMutable()
        oneToManyContainer[connectionId] = copy
        copy
      }
    }
  }

  private fun <T : TypedEntity, SUBT : TypedEntity> getOneToAbstractManyMutableMap(connectionId: ConnectionId<T, SUBT>): LinkedBidirectionalMap<PId<out TypedEntity>, PId<out TypedEntity>> {
    if (connectionId !in oneToAbstractManyContainer) {
      oneToAbstractManyContainer[connectionId] = LinkedBidirectionalMap()
    }

    return if (connectionId in oneToAbstractManyCopiedToModify) {
      oneToAbstractManyContainer[connectionId]!!
    }
    else {
      val copy = LinkedBidirectionalMap<PId<out TypedEntity>, PId<out TypedEntity>>()
      val original = oneToAbstractManyContainer[connectionId]!!
      original.forEach { (k, v) -> copy[k] = v }
      oneToAbstractManyContainer[connectionId] = copy
      oneToAbstractManyCopiedToModify.add(connectionId)
      copy
    }
  }

  private fun <T : TypedEntity, SUBT : TypedEntity> getAbstractOneToOneMutableMap(connectionId: ConnectionId<T, SUBT>): BiMap<PId<out TypedEntity>, PId<out TypedEntity>> {
    if (connectionId !in abstractOneToOneContainer) {
      abstractOneToOneContainer[connectionId] = HashBiMap.create()
    }

    return if (connectionId in abstractOneToOneCopiedToModify) {
      abstractOneToOneContainer[connectionId]!!
    }
    else {
      val copy = HashBiMap.create<PId<out TypedEntity>, PId<out TypedEntity>>()
      val original = abstractOneToOneContainer[connectionId]!!
      original.forEach { (k, v) -> copy[k] = v }
      abstractOneToOneContainer[connectionId] = copy
      abstractOneToOneCopiedToModify.add(connectionId)
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
      is ImmutableIntIntUniqueBiMap -> {
        val copy = bimap.toMutable()
        oneToOneContainer[connectionId] = copy
        copy
      }
    }
  }

  fun <T : TypedEntity, SUBT : TypedEntity> removeRefsByParent(connectionId: ConnectionId<T, SUBT>, parentId: PId<out T>) {
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
                                                                   parentId: PId<out T>,
                                                                   childId: PId<out SUBT>) {
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).remove(childId, parentId)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).remove(childId, parentId)
    }.let { }
  }

  internal fun <T : TypedEntity, SUBT : TypedEntity> updateChildrenOfParent(connectionId: ConnectionId<T, SUBT>,
                                                                            parentId: PId<out T>,
                                                                            childrenIds: List<PId<out SUBT>>) {
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
                                                                                     parentId: PId<out T>,
                                                                                     childrenEntities: Sequence<SUBT>) {
    val copiedMap = getOneToAbstractManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenEntities.forEach { copiedMap[it.id] = parentId }
  }

  fun <T : PTypedEntity, SUBT : PTypedEntity, REALT : T> updateOneToAbstractOneParentOfChild(connectionId: ConnectionId<T, SUBT>,
                                                                                  childId: PId<out SUBT>,
                                                                                  parentEntity: REALT) {
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
                                                                         childId: PId<out SUBT>,
                                                                         parentId: PId<out T>) {
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
                                               val map = LinkedBidirectionalMap<PId<*>, PId<*>>()
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

  internal abstract val oneToManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, PositiveIntIntBiMap>
  internal abstract val oneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, IntIntUniqueBiMap>
  internal abstract val oneToAbstractManyContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, LinkedBidirectionalMap<PId<out TypedEntity>, PId<out TypedEntity>>>
  internal abstract val abstractOneToOneContainer: Map<ConnectionId<out TypedEntity, out TypedEntity>, BiMap<PId<out TypedEntity>, PId<out TypedEntity>>>

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

  fun <SUBT : TypedEntity> getParentRefsOfChild(childId: PId<out SUBT>, hardReferencesOnly: Boolean): ParentConnectionsInfo<SUBT> {
    val childArrayId = childId.arrayId
    val childClass = childId.clazz.java

    val res = HashMap<ConnectionId<TypedEntity, SUBT>, PId<TypedEntity>>()

    val filteredOneToMany = oneToManyContainer
      .filterKeys { it.childClass.java == childClass && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<TypedEntity, SUBT>, PositiveIntIntBiMap>
    for ((connectionId, bimap) in filteredOneToMany) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, PId(value, connectionId.parentClass))
      if (existingValue != null) error("This parent already exists")
    }

    val filteredOneToOne = oneToOneContainer
      .filterKeys { it.childClass.java == childClass && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<TypedEntity, SUBT>, IntIntUniqueBiMap>
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, PId(value, connectionId.parentClass))
      if (existingValue != null) error("This parent already exists")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.childClass.java.isAssignableFrom(childClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<TypedEntity, SUBT>, LinkedBidirectionalMap<PId<out SUBT>, PId<TypedEntity>>>
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) error("This parent already exists")
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.childClass.java.isAssignableFrom(childClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<TypedEntity, SUBT>, BiMap<PId<out SUBT>, PId<TypedEntity>>>
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) error("This parent already exists")
    }

    return res
  }

  fun <T : TypedEntity> getChildrenRefsOfParentBy(parentId: PId<out T>, hardReferencesOnly: Boolean): ChildrenConnectionsInfo<T> {
    val parentArrayId = parentId.arrayId
    val parentClass = parentId.clazz.java

    val res = HashMap<ConnectionId<T, TypedEntity>, Set<PId<TypedEntity>>>()

    val filteredOneToMany = oneToManyContainer
      .filterKeys { it.parentClass.java == parentClass && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<T, TypedEntity>, PositiveIntIntBiMap>
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
      as Map<ConnectionId<T, TypedEntity>, IntIntUniqueBiMap>
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsValue(parentArrayId)) continue
      val key = bimap.getKey(parentArrayId)
      val existingValue = res.putIfAbsent(connectionId, setOf(PId(key, connectionId.childClass)))
      if (existingValue != null) error("These children already exist")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.parentClass.java.isAssignableFrom(parentClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<T, TypedEntity>, LinkedBidirectionalMap<PId<TypedEntity>, PId<out T>>>
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      val keys = bimap.getKeysByValue(parentId) ?: continue
      if (keys.isNotEmpty()) {
        val existingValue = res.putIfAbsent(connectionId, keys.toSet())
        if (existingValue != null) error("These children already exist")
      }
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.parentClass.java.isAssignableFrom(parentClass) && (!hardReferencesOnly || it.isHard) }
      as Map<ConnectionId<T, TypedEntity>, BiMap<PId<TypedEntity>, PId<out T>>>
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      val key = bimap.inverse().get(parentId)
      if (key == null) continue
      val existingValue = res.putIfAbsent(connectionId, setOf(key))
      if (existingValue != null) error("These children already exist")
    }

    return res
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                 parentId: Int): PositiveIntIntMultiMap.IntSequence? {
    // TODO: 26.03.2020 What about missing values?
    return oneToManyContainer[connectionId]?.getKeys(parentId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getOneToAbstractManyChildren(connectionId: ConnectionId<T, SUBT>,
                                                                         parentId: PId<out T>): List<PId<out SUBT>>? {
    // TODO: 26.03.2020 What about missing values?
    val map = oneToAbstractManyContainer[connectionId] as LinkedBidirectionalMap<PId<out SUBT>, PId<out T>>?
    return map?.getKeysByValue(parentId)
  }

  fun <T : TypedEntity, SUBT : TypedEntity> getAbstractOneToOneChildren(connectionId: ConnectionId<T, SUBT>,
                                                                        parentId: PId<out T>): PId<out SUBT>? {
    // TODO: 26.03.2020 What about missing values?
    val map = abstractOneToOneContainer[connectionId] as BiMap<PId<out SUBT>, PId<out T>>?
    return map?.inverse()?.get(parentId)
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
