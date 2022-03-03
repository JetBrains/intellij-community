// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.containers.HashSetInterner
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId.ConnectionType
import com.intellij.workspaceModel.storage.impl.containers.*
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.util.function.IntFunction

class ConnectionId private constructor(
  val parentClass: Int,
  val childClass: Int,
  val connectionType: ConnectionType,
  val isParentNullable: Boolean
) {
  enum class ConnectionType {
    ONE_TO_ONE,
    ONE_TO_MANY,
    ONE_TO_ABSTRACT_MANY,
    ABSTRACT_ONE_TO_ONE
  }

  /**
   * This function returns true if this connection allows removing parent of child.
   *
   * E.g. parent is optional (nullable) for child entity, so the parent can be safely removed.
   */
  fun canRemoveParent(): Boolean = isParentNullable

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ConnectionId

    if (parentClass != other.parentClass) return false
    if (childClass != other.childClass) return false
    if (connectionType != other.connectionType) return false
    if (isParentNullable != other.isParentNullable) return false

    return true
  }

  override fun hashCode(): Int {
    var result = parentClass.hashCode()
    result = 31 * result + childClass.hashCode()
    result = 31 * result + connectionType.hashCode()
    result = 31 * result + isParentNullable.hashCode()
    return result
  }

  override fun toString(): String {
    return "Connection(parent=${ClassToIntConverter.INSTANCE.getClassOrDie(parentClass).simpleName} " +
           "child=${ClassToIntConverter.INSTANCE.getClassOrDie(childClass).simpleName} $connectionType)"
  }

  fun debugStr(): String = """
    ConnectionId info:
      - Parent class: ${this.parentClass.findEntityClass<WorkspaceEntity>()}
      - Child class: ${this.childClass.findEntityClass<WorkspaceEntity>()}
      - Connection type: $connectionType
      - Parent of child is nullable: $isParentNullable
  """.trimIndent()

  companion object {
    /** This function should be [@Synchronized] because interner is not thread-save */
    @Synchronized
    fun <Parent : WorkspaceEntity, Child : WorkspaceEntity> create(
      parentClass: Class<Parent>,
      childClass: Class<Child>,
      connectionType: ConnectionType,
      isParentNullable: Boolean
    ): ConnectionId {
      val connectionId = ConnectionId(parentClass.toClassId(), childClass.toClassId(), connectionType, isParentNullable)
      return interner.intern(connectionId)
    }

    private val interner = HashSetInterner<ConnectionId>()
  }
}

/**
 * [oneToManyContainer]: [ImmutableNonNegativeIntIntBiMap] - key - child, value - parent
 */
internal class RefsTable internal constructor(
  override val oneToManyContainer: Map<ConnectionId, ImmutableNonNegativeIntIntBiMap>,
  override val oneToOneContainer: Map<ConnectionId, ImmutableIntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: Map<ConnectionId, LinkedBidirectionalMap<ChildEntityId, ParentEntityId>>,
  override val abstractOneToOneContainer: Map<ConnectionId, BiMap<ChildEntityId, ParentEntityId>>
) : AbstractRefsTable() {
  constructor() : this(HashMap(), HashMap(), HashMap(), HashMap())
}

internal class MutableRefsTable(
  override val oneToManyContainer: MutableMap<ConnectionId, NonNegativeIntIntBiMap>,
  override val oneToOneContainer: MutableMap<ConnectionId, IntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: MutableMap<ConnectionId, LinkedBidirectionalMap<ChildEntityId, ParentEntityId>>,
  override val abstractOneToOneContainer: MutableMap<ConnectionId, BiMap<ChildEntityId, ParentEntityId>>
) : AbstractRefsTable() {

  private val oneToAbstractManyCopiedToModify: MutableSet<ConnectionId> = HashSet()
  private val abstractOneToOneCopiedToModify: MutableSet<ConnectionId> = HashSet()

  private fun getOneToManyMutableMap(connectionId: ConnectionId): MutableNonNegativeIntIntBiMap {
    val bimap = oneToManyContainer[connectionId] ?: run {
      val empty = MutableNonNegativeIntIntBiMap()
      oneToManyContainer[connectionId] = empty
      return empty
    }

    return when (bimap) {
      is MutableNonNegativeIntIntBiMap -> bimap
      is ImmutableNonNegativeIntIntBiMap -> {
        val copy = bimap.toMutable()
        oneToManyContainer[connectionId] = copy
        copy
      }
    }
  }

  private fun getOneToAbstractManyMutableMap(connectionId: ConnectionId): LinkedBidirectionalMap<ChildEntityId, ParentEntityId> {
    if (connectionId !in oneToAbstractManyContainer) {
      oneToAbstractManyContainer[connectionId] = LinkedBidirectionalMap()
    }

    return if (connectionId in oneToAbstractManyCopiedToModify) {
      oneToAbstractManyContainer[connectionId]!!
    }
    else {
      val copy = LinkedBidirectionalMap<ChildEntityId, ParentEntityId>()
      val original = oneToAbstractManyContainer[connectionId]!!
      original.forEach { (k, v) -> copy[k] = v }
      oneToAbstractManyContainer[connectionId] = copy
      oneToAbstractManyCopiedToModify.add(connectionId)
      copy
    }
  }

  private fun getAbstractOneToOneMutableMap(connectionId: ConnectionId): BiMap<ChildEntityId, ParentEntityId> {
    if (connectionId !in abstractOneToOneContainer) {
      abstractOneToOneContainer[connectionId] = HashBiMap.create()
    }

    return if (connectionId in abstractOneToOneCopiedToModify) {
      abstractOneToOneContainer[connectionId]!!
    }
    else {
      val copy = HashBiMap.create<ChildEntityId, ParentEntityId>()
      val original = abstractOneToOneContainer[connectionId]!!
      original.forEach { (k, v) -> copy[k] = v }
      abstractOneToOneContainer[connectionId] = copy
      abstractOneToOneCopiedToModify.add(connectionId)
      copy
    }
  }

  private fun getOneToOneMutableMap(connectionId: ConnectionId): MutableIntIntUniqueBiMap {
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

  fun removeRefsByParent(connectionId: ConnectionId, parentId: ParentEntityId) {
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).removeValue(parentId.id.arrayId)
      ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).removeValue(parentId.id.arrayId)
      ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).removeValue(parentId)
      ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).inverse().remove(parentId)
    }.let { }
  }

  fun removeOneToOneRefByParent(connectionId: ConnectionId, parentId: Int) {
    getOneToOneMutableMap(connectionId).removeValue(parentId)
  }

  fun removeOneToAbstractOneRefByParent(connectionId: ConnectionId, parentId: ParentEntityId) {
    getAbstractOneToOneMutableMap(connectionId).inverse().remove(parentId)
  }

  fun removeOneToOneRefByChild(connectionId: ConnectionId, childId: Int) {
    getOneToOneMutableMap(connectionId).removeKey(childId)
  }

  fun removeOneToManyRefsByChild(connectionId: ConnectionId, childId: Int) {
    getOneToManyMutableMap(connectionId).removeKey(childId)
  }

  fun removeParentToChildRef(connectionId: ConnectionId, parentId: ParentEntityId, childId: ChildEntityId) {
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).remove(childId.id.arrayId, parentId.id.arrayId)
      ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).remove(childId.id.arrayId, parentId.id.arrayId)
      ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).remove(childId, parentId)
      ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).remove(childId, parentId)
    }.let { }
  }

  internal fun updateChildrenOfParent(connectionId: ConnectionId, parentId: ParentEntityId, childrenIds: List<ChildEntityId>) {
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeValue(parentId.id.arrayId)
        val children = childrenIds.map { it.id.arrayId }.toIntArray()
        copiedMap.putAll(children, parentId.id.arrayId)
      }
      ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        copiedMap.putForce(childrenIds.single().id.arrayId, parentId.id.arrayId)
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val copiedMap = getOneToAbstractManyMutableMap(connectionId)
        copiedMap.removeValue(parentId)

        // In theory this removing can be avoided because keys will be replaced anyway, but without this cleanup we may get an
        // incorrect ordering of the children
        childrenIds.forEach { copiedMap.remove(it) }

        childrenIds.forEach { copiedMap[it] = parentId }
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val copiedMap = getAbstractOneToOneMutableMap(connectionId)
        copiedMap.inverse().remove(parentId)
        childrenIds.forEach { copiedMap[it] = parentId }
      }
    }.let { }
  }

  fun updateOneToManyChildrenOfParent(connectionId: ConnectionId, parentId: Int, childrenEntityIds: Sequence<ChildEntityId>) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    val children = childrenEntityIds.mapToIntArray { it.id.arrayId }
    copiedMap.putAll(children, parentId)
  }

  fun updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                              parentId: ParentEntityId,
                                              childrenEntityIds: Sequence<ChildEntityId>) {
    val copiedMap = getOneToAbstractManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenEntityIds.forEach { copiedMap[it] = parentId }
  }

  fun <Parent : WorkspaceEntityBase, OriginParent : Parent> updateOneToAbstractOneParentOfChild(connectionId: ConnectionId,
                                                                                                childId: ChildEntityId,
                                                                                                parentEntity: OriginParent) {
    val copiedMap = getAbstractOneToOneMutableMap(connectionId)
    copiedMap.remove(childId)
    copiedMap[childId] = parentEntity.id.asParent()
  }

  fun updateOneToAbstractOneChildOfParent(connectionId: ConnectionId,
                                          parentId: ParentEntityId,
                                          childEntityId: ChildEntityId) {
    val copiedMap = getAbstractOneToOneMutableMap(connectionId)
    copiedMap.inverse().remove(parentId)
    copiedMap[childEntityId.id.asChild()] = parentId
  }

  fun updateOneToOneChildOfParent(connectionId: ConnectionId, parentId: Int, childEntityId: ChildEntityId) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    copiedMap.put(childEntityId.id.arrayId, parentId)
  }

  fun <Parent : WorkspaceEntityBase> updateOneToOneParentOfChild(connectionId: ConnectionId, childId: Int, parentEntity: Parent) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.putForce(childId, parentEntity.id.arrayId)
  }

  internal fun updateParentOfChild(connectionId: ConnectionId, childId: ChildEntityId, parentId: ParentEntityId) {
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeKey(childId.id.arrayId)
        copiedMap.putAll(intArrayOf(childId.id.arrayId), parentId.id.arrayId)
      }
      ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        copiedMap.removeKey(childId.id.arrayId)
        copiedMap.putForce(childId.id.arrayId, parentId.id.arrayId)
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val copiedMap = getOneToAbstractManyMutableMap(connectionId)
        copiedMap.remove(childId)
        copiedMap[childId] = parentId
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val copiedMap = getAbstractOneToOneMutableMap(connectionId)
        copiedMap.remove(childId)
        copiedMap.forcePut(childId, parentId)
        Unit
      }
    }.let { }
  }

  fun <Parent : WorkspaceEntityBase> updateOneToManyParentOfChild(connectionId: ConnectionId, childId: Int, parent: Parent) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.putAll(intArrayOf(childId), parent.id.arrayId)
  }

  fun toImmutable(): RefsTable = RefsTable(
    oneToManyContainer.mapValues { it.value.toImmutable() },
    oneToOneContainer.mapValues { it.value.toImmutable() },
    oneToAbstractManyContainer.mapValues {
      it.value.let { value ->
        val map = LinkedBidirectionalMap<ChildEntityId, ParentEntityId>()
        value.forEach { (k, v) -> map[k] = v }
        map
      }
    },
    abstractOneToOneContainer.mapValues {
      it.value.let { value ->
        val map = HashBiMap.create<ChildEntityId, ParentEntityId>()
        value.forEach { (k, v) -> map[k] = v }
        map
      }
    }
  )

  companion object {
    fun from(other: RefsTable): MutableRefsTable = MutableRefsTable(
      HashMap(other.oneToManyContainer), HashMap(other.oneToOneContainer),
      HashMap(other.oneToAbstractManyContainer),
      HashMap(other.abstractOneToOneContainer))
  }

  private fun <T> Sequence<T>.mapToIntArray(action: (T) -> Int): IntArray {
    val intArrayList = IntArrayList()
    this.forEach { item ->
      intArrayList.add(action(item))
    }

    return intArrayList.toIntArray()
  }
}

internal sealed class AbstractRefsTable {

  internal abstract val oneToManyContainer: Map<ConnectionId, NonNegativeIntIntBiMap>
  internal abstract val oneToOneContainer: Map<ConnectionId, IntIntUniqueBiMap>
  internal abstract val oneToAbstractManyContainer: Map<ConnectionId, LinkedBidirectionalMap<ChildEntityId, ParentEntityId>>
  internal abstract val abstractOneToOneContainer: Map<ConnectionId, BiMap<ChildEntityId, ParentEntityId>>

  fun <Parent : WorkspaceEntity, Child : WorkspaceEntity> findConnectionId(parentClass: Class<Parent>, childClass: Class<Child>): ConnectionId? {
    val parentClassId = parentClass.toClassId()
    val childClassId = childClass.toClassId()
    return (oneToManyContainer.keys.find { it.parentClass == parentClassId && it.childClass == childClassId }
            ?: oneToOneContainer.keys.find { it.parentClass == parentClassId && it.childClass == childClassId }
            ?: oneToAbstractManyContainer.keys.find {
              it.parentClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(parentClass) &&
              it.childClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(childClass)
            }
            ?: abstractOneToOneContainer.keys.find {
              it.parentClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(parentClass) &&
              it.childClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(childClass)
            })
  }

  fun getParentRefsOfChild(childId: ChildEntityId): Map<ConnectionId, ParentEntityId> {
    val childArrayId = childId.id.arrayId
    val childClassId = childId.id.clazz
    val childClass = childId.id.clazz.findEntityClass<WorkspaceEntity>()

    val res = HashMap<ConnectionId, ParentEntityId>()

    val filteredOneToMany = oneToManyContainer.filterKeys { it.childClass == childClassId }
    for ((connectionId, bimap) in filteredOneToMany) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, createEntityId(value, connectionId.parentClass).asParent())
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    val filteredOneToOne = oneToOneContainer.filterKeys { it.childClass == childClassId }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, createEntityId(value, connectionId.parentClass).asParent())
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.childClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(childClass) }
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.childClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(childClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    return res
  }

  fun getParentOneToOneRefsOfChild(childId: ChildEntityId): Map<ConnectionId, ParentEntityId> {
    val childArrayId = childId.id.arrayId
    val childClassId = childId.id.clazz
    val childClass = childId.id.clazz.findEntityClass<WorkspaceEntity>()

    val res = HashMap<ConnectionId, ParentEntityId>()

    val filteredOneToOne = oneToOneContainer.filterKeys { it.childClass == childClassId }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, createEntityId(value, connectionId.parentClass).asParent())
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.childClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(childClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    return res
  }

  fun getChildrenRefsOfParentBy(parentId: ParentEntityId): Map<ConnectionId, List<ChildEntityId>> {
    val parentArrayId = parentId.id.arrayId
    val parentClassId = parentId.id.clazz
    val parentClass = parentId.id.clazz.findEntityClass<WorkspaceEntity>()

    val res = HashMap<ConnectionId, List<ChildEntityId>>()

    val filteredOneToMany = oneToManyContainer.filterKeys { it.parentClass == parentClassId }
    for ((connectionId, bimap) in filteredOneToMany) {
      val keys = bimap.getKeys(parentArrayId)
      if (!keys.isEmpty()) {
        val children = keys.map { createEntityId(it, connectionId.childClass) }.mapTo(ArrayList()) { it.asChild() }
        val existingValue = res.putIfAbsent(connectionId, children)
        if (existingValue != null) thisLogger().error("These children already exist")
      }
    }

    val filteredOneToOne = oneToOneContainer.filterKeys { it.parentClass == parentClassId }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsValue(parentArrayId)) continue
      val key = bimap.getKey(parentArrayId)
      val existingValue = res.putIfAbsent(connectionId, listOf(createEntityId(key, connectionId.childClass).asChild()))
      if (existingValue != null) thisLogger().error("These children already exist")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.parentClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(parentClass) }
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      val keys = bimap.getKeysByValue(parentId) ?: continue
      if (keys.isNotEmpty()) {
        val existingValue = res.putIfAbsent(connectionId, keys.map { it })
        if (existingValue != null) thisLogger().error("These children already exist")
      }
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.parentClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(parentClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      val key = bimap.inverse()[parentId]
      if (key == null) continue
      val existingValue = res.putIfAbsent(connectionId, listOf(key))
      if (existingValue != null) thisLogger().error("These children already exist")
    }

    return res
  }

  fun getChildrenOneToOneRefsOfParentBy(parentId: ParentEntityId): Map<ConnectionId, ChildEntityId> {
    val parentArrayId = parentId.id.arrayId
    val parentClassId = parentId.id.clazz
    val parentClass = parentId.id.clazz.findEntityClass<WorkspaceEntity>()

    val res = HashMap<ConnectionId, ChildEntityId>()

    val filteredOneToOne = oneToOneContainer.filterKeys { it.parentClass == parentClassId }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsValue(parentArrayId)) continue
      val key = bimap.getKey(parentArrayId)
      val existingValue = res.putIfAbsent(connectionId, createEntityId(key, connectionId.childClass).asChild())
      if (existingValue != null) thisLogger().error("These children already exist")
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.parentClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(parentClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      val key = bimap.inverse()[parentId]
      if (key == null) continue
      val existingValue = res.putIfAbsent(connectionId, key)
      if (existingValue != null) thisLogger().error("These children already exist")
    }

    return res
  }

  fun getOneToManyChildren(connectionId: ConnectionId, parentId: Int): NonNegativeIntIntMultiMap.IntSequence? {
    return oneToManyContainer[connectionId]?.getKeys(parentId)
  }

  fun getOneToAbstractManyChildren(connectionId: ConnectionId, parentId: ParentEntityId): List<ChildEntityId>? {
    val map = oneToAbstractManyContainer[connectionId]
    return map?.getKeysByValue(parentId)
  }

  fun getAbstractOneToOneChildren(connectionId: ConnectionId, parentId: ParentEntityId): ChildEntityId? {
    val map = abstractOneToOneContainer[connectionId]
    return map?.inverse()?.get(parentId)
  }

  fun getOneToAbstractOneParent(connectionId: ConnectionId, childId: ChildEntityId): ParentEntityId? {
    return abstractOneToOneContainer[connectionId]?.get(childId)
  }

  fun <Child : WorkspaceEntity> getOneToOneChild(connectionId: ConnectionId, parentId: Int, transformer: IntFunction<Child?>): Child? {
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsValue(parentId)) return null

    return transformer.apply(bimap.getKey(parentId))
  }

  fun <Parent : WorkspaceEntity> getOneToOneParent(connectionId: ConnectionId, childId: Int, transformer: IntFunction<Parent?>): Parent? {
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer.apply(bimap.get(childId))
  }

  fun <Parent : WorkspaceEntity> getOneToManyParent(connectionId: ConnectionId, childId: Int, transformer: IntFunction<Parent?>): Parent? {
    val bimap = oneToManyContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer.apply(bimap.get(childId))
  }
}

// TODO: 25.05.2021 Make it value class
internal data class ChildEntityId(val id: EntityId) {
  override fun toString(): String {
    return "ChildEntityId(id=${id.asString()})"
  }
}
internal data class ParentEntityId(val id: EntityId) {
  override fun toString(): String {
    return "ParentEntityId(id=${id.asString()})"
  }
}

internal fun EntityId.asChild(): ChildEntityId = ChildEntityId(this)
internal fun EntityId.asParent(): ParentEntityId = ParentEntityId(this)
