// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.util.containers.HashSetInterner
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId.ConnectionType
import com.intellij.workspaceModel.storage.impl.containers.*

/**
 * [isChildNullable] property is ignored for [ConnectionType.ONE_TO_ABSTRACT_MANY] and [ConnectionType.ONE_TO_MANY]
 */
internal class ConnectionId private constructor(
  val parentClass: Int,
  val childClass: Int,
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

  /**
   * This function returns true if this connection allows removing children of parent.
   *
   * E.g. child is nullable for parent entity, so the child can be safely removed.
   */
  fun canRemoveChild(): Boolean {
    return connectionType == ConnectionType.ONE_TO_ABSTRACT_MANY || connectionType == ConnectionType.ONE_TO_MANY || isChildNullable
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
    if (isChildNullable != other.isChildNullable) return false

    return true
  }

  override fun hashCode(): Int {
    var result = parentClass.hashCode()
    result = 31 * result + childClass.hashCode()
    result = 31 * result + connectionType.hashCode()
    result = 31 * result + isParentNullable.hashCode()
    result = 31 * result + isChildNullable.hashCode()
    return result
  }

  override fun toString(): String {
    return "Connection(parent=${ClassToIntConverter.getClassOrDie(
      parentClass).simpleName} " +
           "child=${ClassToIntConverter.getClassOrDie(
             childClass).simpleName} $connectionType)"
  }

  fun debugStr(): String = """
    ConnectionId info:
      - Parent class: ${this.parentClass.findEntityClass<WorkspaceEntity>()}
      - Child class: ${this.childClass.findEntityClass<WorkspaceEntity>()}
      - Connection type: $connectionType
      - Parent of child is nullable: $isParentNullable
      - Child of parent is nullable: $isChildNullable
  """.trimIndent()

  companion object {
    /** This function should be [@Synchronized] because interner is not thread-save */
    @Synchronized
    fun <Parent : WorkspaceEntity, Child : WorkspaceEntity> create(
      parentClass: Class<Parent>,
      childClass: Class<Child>,
      connectionType: ConnectionType,
      isParentNullable: Boolean,
      isChildNullable: Boolean
    ): ConnectionId {
      val connectionId = ConnectionId(parentClass.toClassId(), childClass.toClassId(), connectionType, isParentNullable, isChildNullable)
      return interner.intern(connectionId)
    }

    private val interner = HashSetInterner<ConnectionId>()
  }
}

/**
 * [oneToManyContainer]: [ImmutablePositiveIntIntBiMap] - key - child, value - parent
 */
internal class RefsTable internal constructor(
  override val oneToManyContainer: Map<ConnectionId, ImmutablePositiveIntIntBiMap>,
  override val oneToOneContainer: Map<ConnectionId, ImmutableIntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: Map<ConnectionId, LinkedBidirectionalMap<EntityId, EntityId>>,
  override val abstractOneToOneContainer: Map<ConnectionId, BiMap<EntityId, EntityId>>
) : AbstractRefsTable() {
  constructor() : this(HashMap(), HashMap(), HashMap(), HashMap())
}

internal class MutableRefsTable(
  override val oneToManyContainer: MutableMap<ConnectionId, PositiveIntIntBiMap>,
  override val oneToOneContainer: MutableMap<ConnectionId, IntIntUniqueBiMap>,
  override val oneToAbstractManyContainer: MutableMap<ConnectionId, LinkedBidirectionalMap<EntityId, EntityId>>,
  override val abstractOneToOneContainer: MutableMap<ConnectionId, BiMap<EntityId, EntityId>>
) : AbstractRefsTable() {

  private val oneToAbstractManyCopiedToModify: MutableSet<ConnectionId> = HashSet()
  private val abstractOneToOneCopiedToModify: MutableSet<ConnectionId> = HashSet()

  private fun getOneToManyMutableMap(connectionId: ConnectionId): MutablePositiveIntIntBiMap {
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

  private fun getOneToAbstractManyMutableMap(connectionId: ConnectionId): LinkedBidirectionalMap<EntityId, EntityId> {
    if (connectionId !in oneToAbstractManyContainer) {
      oneToAbstractManyContainer[connectionId] = LinkedBidirectionalMap()
    }

    return if (connectionId in oneToAbstractManyCopiedToModify) {
      oneToAbstractManyContainer[connectionId]!!
    }
    else {
      val copy = LinkedBidirectionalMap<EntityId, EntityId>()
      val original = oneToAbstractManyContainer[connectionId]!!
      original.forEach { (k, v) -> copy[k] = v }
      oneToAbstractManyContainer[connectionId] = copy
      oneToAbstractManyCopiedToModify.add(connectionId)
      copy
    }
  }

  private fun getAbstractOneToOneMutableMap(connectionId: ConnectionId): BiMap<EntityId, EntityId> {
    if (connectionId !in abstractOneToOneContainer) {
      abstractOneToOneContainer[connectionId] = HashBiMap.create()
    }

    return if (connectionId in abstractOneToOneCopiedToModify) {
      abstractOneToOneContainer[connectionId]!!
    }
    else {
      val copy = HashBiMap.create<EntityId, EntityId>()
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

  fun removeRefsByParent(connectionId: ConnectionId, parentId: EntityId) {
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).removeValue(parentId.arrayId)
      ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).removeValue(parentId.arrayId)
      ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).removeValue(parentId)
      ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).inverse().remove(parentId)
    }.let { }
  }

  fun removeOneToOneRefByParent(connectionId: ConnectionId, parentId: Int) {
    getOneToOneMutableMap(connectionId).removeValue(parentId)
  }

  fun removeOneToOneRefByChild(connectionId: ConnectionId, childId: Int) {
    getOneToOneMutableMap(connectionId).removeKey(childId)
  }

  fun removeOneToManyRefsByChild(connectionId: ConnectionId, childId: Int) {
    getOneToManyMutableMap(connectionId).removeKey(childId)
  }

  fun removeParentToChildRef(connectionId: ConnectionId, parentId: EntityId, childId: EntityId) {
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).remove(childId.arrayId, parentId.arrayId)
      ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).remove(childId, parentId)
      ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).remove(childId, parentId)
    }.let { }
  }

  internal fun updateChildrenOfParent(connectionId: ConnectionId, parentId: EntityId, childrenIds: Collection<EntityId>) {
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeValue(parentId.arrayId)
        val children = childrenIds.map { it.arrayId }.toIntArray()
        copiedMap.putAll(children, parentId.arrayId)
      }
      ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        copiedMap.putForce(childrenIds.single().arrayId, parentId.arrayId)
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val copiedMap = getOneToAbstractManyMutableMap(connectionId)
        copiedMap.removeValue(parentId)
        childrenIds.forEach { copiedMap[it] = parentId }
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val copiedMap = getAbstractOneToOneMutableMap(connectionId)
        copiedMap.inverse().remove(parentId)
        childrenIds.forEach { copiedMap[it] = parentId }
      }
    }.let { }
  }

  fun <Child : WorkspaceEntityBase> updateOneToManyChildrenOfParent(connectionId: ConnectionId, parentId: Int, childrenEntities: Sequence<Child>) {
    val copiedMap = getOneToManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    val children = childrenEntities.map { it.id.arrayId }.toList().toIntArray()
    copiedMap.putAll(children, parentId)
  }

  fun <Child : WorkspaceEntityBase> updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                           parentId: EntityId,
                                                                           childrenEntities: Sequence<Child>) {
    val copiedMap = getOneToAbstractManyMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    childrenEntities.forEach { copiedMap[it.id] = parentId }
  }

  fun <Parent : WorkspaceEntityBase, OriginParent : Parent> updateOneToAbstractOneParentOfChild(connectionId: ConnectionId, childId: EntityId,
                                                                                                parentEntity: OriginParent) {
    val copiedMap = getAbstractOneToOneMutableMap(connectionId)
    copiedMap.remove(childId)
    copiedMap[childId] = parentEntity.id
  }

  fun <Child : WorkspaceEntityBase> updateOneToOneChildOfParent(connectionId: ConnectionId, parentId: Int, childEntity: Child) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeValue(parentId)
    copiedMap.put(childEntity.id.arrayId, parentId)
  }

  fun <Parent : WorkspaceEntityBase> updateOneToOneParentOfChild(connectionId: ConnectionId, childId: Int, parentEntity: Parent) {
    val copiedMap = getOneToOneMutableMap(connectionId)
    copiedMap.removeKey(childId)
    copiedMap.put(childId, parentEntity.id.arrayId)
  }

  internal fun updateParentOfChild(connectionId: ConnectionId, childId: EntityId, parentId: EntityId) {
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> {
        val copiedMap = getOneToManyMutableMap(connectionId)
        copiedMap.removeKey(childId.arrayId)
        copiedMap.putAll(intArrayOf(childId.arrayId), parentId.arrayId)
      }
      ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        copiedMap.removeKey(childId.arrayId)
        copiedMap.putForce(childId.arrayId, parentId.arrayId)
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val copiedMap = getOneToAbstractManyMutableMap(connectionId)
        copiedMap.remove(childId)
        copiedMap[childId] = parentId
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val copiedMap = getAbstractOneToOneMutableMap(connectionId)
        copiedMap.remove(childId)
        copiedMap[childId] = parentId
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
        val map = LinkedBidirectionalMap<EntityId, EntityId>()
        value.forEach { (k, v) -> map[k] = v }
        map
      }
    },
    abstractOneToOneContainer.mapValues {
      it.value.let { value ->
        val map = HashBiMap.create<EntityId, EntityId>()
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
}

internal sealed class AbstractRefsTable {

  internal abstract val oneToManyContainer: Map<ConnectionId, PositiveIntIntBiMap>
  internal abstract val oneToOneContainer: Map<ConnectionId, IntIntUniqueBiMap>
  internal abstract val oneToAbstractManyContainer: Map<ConnectionId, LinkedBidirectionalMap<EntityId, EntityId>>
  internal abstract val abstractOneToOneContainer: Map<ConnectionId, BiMap<EntityId, EntityId>>

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

  fun getParentRefsOfChild(childId: EntityId): Map<ConnectionId, EntityId> {
    val childArrayId = childId.arrayId
    val childClassId = childId.clazz
    val childClass = childId.clazz.findEntityClass<WorkspaceEntity>()

    val res = HashMap<ConnectionId, EntityId>()

    val filteredOneToMany = oneToManyContainer.filterKeys { it.childClass == childClassId }
    for ((connectionId, bimap) in filteredOneToMany) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, EntityId(value, connectionId.parentClass))
      if (existingValue != null) error("This parent already exists")
    }

    val filteredOneToOne = oneToOneContainer.filterKeys { it.childClass == childClassId }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsKey(childArrayId)) continue
      val value = bimap.get(childArrayId)
      val existingValue = res.putIfAbsent(connectionId, EntityId(value, connectionId.parentClass))
      if (existingValue != null) error("This parent already exists")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.childClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(childClass) }
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) error("This parent already exists")
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.childClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(childClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) error("This parent already exists")
    }

    return res
  }

  fun getChildrenRefsOfParentBy(parentId: EntityId): Map<ConnectionId, Set<EntityId>> {
    val parentArrayId = parentId.arrayId
    val parentClassId = parentId.clazz
    val parentClass = parentId.clazz.findEntityClass<WorkspaceEntity>()

    val res = HashMap<ConnectionId, Set<EntityId>>()

    val filteredOneToMany = oneToManyContainer.filterKeys { it.parentClass == parentClassId }
    for ((connectionId, bimap) in filteredOneToMany) {
      val keys = bimap.getKeys(parentArrayId)
      if (!keys.isEmpty()) {
        val children = keys.map { EntityId(it, connectionId.childClass) }.toSet()
        val existingValue = res.putIfAbsent(connectionId, children)
        if (existingValue != null) error("These children already exist")
      }
    }

    val filteredOneToOne = oneToOneContainer.filterKeys { it.parentClass == parentClassId }
    for ((connectionId, bimap) in filteredOneToOne) {
      if (!bimap.containsValue(parentArrayId)) continue
      val key = bimap.getKey(parentArrayId)
      val existingValue = res.putIfAbsent(connectionId, setOf(EntityId(key, connectionId.childClass)))
      if (existingValue != null) error("These children already exist")
    }

    val filteredOneToAbstractMany = oneToAbstractManyContainer
      .filterKeys { it.parentClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(parentClass) }
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      val keys = bimap.getKeysByValue(parentId) ?: continue
      if (keys.isNotEmpty()) {
        val existingValue = res.putIfAbsent(connectionId, keys.toSet())
        if (existingValue != null) error("These children already exist")
      }
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.parentClass.findEntityClass<WorkspaceEntity>().isAssignableFrom(parentClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      val key = bimap.inverse()[parentId]
      if (key == null) continue
      val existingValue = res.putIfAbsent(connectionId, setOf(key))
      if (existingValue != null) error("These children already exist")
    }

    return res
  }

  fun getOneToManyChildren(connectionId: ConnectionId, parentId: Int): PositiveIntIntMultiMap.IntSequence? {
    return oneToManyContainer[connectionId]?.getKeys(parentId)
  }

  fun getOneToAbstractManyChildren(connectionId: ConnectionId, parentId: EntityId): List<EntityId>? {
    val map = oneToAbstractManyContainer[connectionId]
    return map?.getKeysByValue(parentId)
  }

  fun getAbstractOneToOneChildren(connectionId: ConnectionId, parentId: EntityId): EntityId? {
    val map = abstractOneToOneContainer[connectionId]
    return map?.inverse()?.get(parentId)
  }

  fun getOneToAbstractOneParent(connectionId: ConnectionId, childId: EntityId): EntityId? {
    return abstractOneToOneContainer[connectionId]?.get(childId)
  }

  fun <Child : WorkspaceEntity> getOneToOneChild(connectionId: ConnectionId, parentId: Int, transformer: (Int) -> Child?): Child? {
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsValue(parentId)) return null

    return transformer(bimap.getKey(parentId))
  }

  fun <Parent : WorkspaceEntity> getOneToOneParent(connectionId: ConnectionId, childId: Int, transformer: (Int) -> Parent?): Parent? {
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer(bimap.get(childId))
  }

  fun <Parent : WorkspaceEntity> getOneToManyParent(connectionId: ConnectionId, childId: Int, transformer: (Int) -> Parent?): Parent? {
    val bimap = oneToManyContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer(bimap.get(childId))
  }
}
