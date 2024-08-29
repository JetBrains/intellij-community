// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.ConnectionId.ConnectionType
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.*
import com.intellij.platform.workspace.storage.impl.references.*
import com.intellij.platform.workspace.storage.instrumentation.Modification
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.util.function.BiConsumer
import java.util.function.IntConsumer
import java.util.function.IntFunction


internal val ConnectionId.isOneToOne: Boolean
  get() = this.connectionType == ConnectionType.ONE_TO_ONE || this.connectionType == ConnectionType.ABSTRACT_ONE_TO_ONE

/**
 * [oneToManyContainer]: [ImmutableNonNegativeIntIntBiMap] - key - child, value - parent
 */
internal class RefsTable internal constructor(
  override val oneToManyContainer: ImmutableOneToManyContainer,
  override val oneToOneContainer: ImmutableOneToOneContainer,
  override val oneToAbstractManyContainer: ImmutableOneToAbstractManyContainer,
  override val abstractOneToOneContainer: ImmutableAbstractOneToOneContainer
) : AbstractRefsTable() {
  constructor() : this(ImmutableOneToManyContainer(), ImmutableOneToOneContainer(),
                       ImmutableOneToAbstractManyContainer(), ImmutableAbstractOneToOneContainer())
}

internal class MutableRefsTable(
  override val oneToManyContainer: MutableOneToManyContainer,
  override val oneToOneContainer: MutableOneToOneContainer,
  override val oneToAbstractManyContainer: MutableOneToAbstractManyContainer,
  override val abstractOneToOneContainer: MutableAbstractOneToOneContainer
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
      original.forEach { (k, v) -> copy.add(k, v) }
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

  fun removeRefsByParent(connectionId: ConnectionId, parentId: ParentEntityId): List<Modification> {
    val modifications = mutableListOf<Modification>()
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> {
        val removedChildren = getOneToManyMutableMap(connectionId).removeValue(parentId.id.arrayId)
        removedChildren.forEach(IntConsumer { childId ->
          modifications.add(Modification.Remove(parentId.id, createEntityId(childId, connectionId.childClass)))
        })
      }
      ConnectionType.ONE_TO_ONE -> {
        val removedChildId = getOneToOneMutableMap(connectionId).removeValue(parentId.id.arrayId)
        removedChildId?.let { modifications.add(Modification.Remove(parentId.id, createEntityId(it, connectionId.childClass))) }
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        val children = getOneToAbstractManyMutableMap(connectionId).removeValue(parentId)
        children.forEach { childId ->
          modifications.add(Modification.Remove(parentId.id, childId.id))
        }
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        val childId = getAbstractOneToOneMutableMap(connectionId).inverse().remove(parentId)
        childId?.let { modifications.add(Modification.Remove(parentId.id, it.id)) }
      }
    }
    return modifications
  }

  fun removeOneToAbstractOneRefByChild(connectionId: ConnectionId, childId: ChildEntityId): Modification? {
    val removedParent = getAbstractOneToOneMutableMap(connectionId).remove(childId) ?: return null
    return Modification.Remove(removedParent.id, childId.id)
  }

  fun removeOneToOneRefByChild(connectionId: ConnectionId, childId: EntityId): List<Modification> {
    val removedParent = getOneToOneMutableMap(connectionId).removeKey(childId.arrayId)

    return if (removedParent != null) {
      listOf(Modification.Remove(createEntityId(removedParent, connectionId.parentClass), childId))
    }
    else emptyList()
  }

  fun removeOneToManyRefsByChild(connectionId: ConnectionId, childId: ChildEntityId): Modification? {
    val removedParent = getOneToManyMutableMap(connectionId).removeKey(childId.id.arrayId) ?: return null
    return Modification.Remove(createEntityId(removedParent, connectionId.parentClass), childId.id)
  }

  fun removeOneToAbstractManyRefsByChild(connectionId: ConnectionId, childId: ChildEntityId): Modification? {
    val removedParent = getOneToAbstractManyMutableMap(connectionId).remove(childId) ?: return null
    return Modification.Remove(removedParent.id, childId.id)
  }

  fun removeParentToChildRef(connectionId: ConnectionId, parentId: ParentEntityId, childId: ChildEntityId): List<Modification> {
    when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> getOneToManyMutableMap(connectionId).remove(childId.id.arrayId, parentId.id.arrayId)
      ConnectionType.ONE_TO_ONE -> getOneToOneMutableMap(connectionId).remove(childId.id.arrayId, parentId.id.arrayId)
      ConnectionType.ONE_TO_ABSTRACT_MANY -> getOneToAbstractManyMutableMap(connectionId).remove(childId, parentId)
      ConnectionType.ABSTRACT_ONE_TO_ONE -> getAbstractOneToOneMutableMap(connectionId).remove(childId, parentId)
    }
    return listOf(Modification.Remove(parentId.id, childId.id))
  }

  internal fun replaceChildrenOfParent(connectionId: ConnectionId, parentId: ParentEntityId, newChildrenIds: List<ChildEntityId>): List<Modification> {
    if (newChildrenIds.size != newChildrenIds.toSet().size) {
      error("Children have duplicates: $newChildrenIds")
    }
    return when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> {
        replaceOneToManyChildrenOfParent(connectionId, parentId.id, newChildrenIds)
      }
      ConnectionType.ONE_TO_ONE -> {
        val copiedMap = getOneToOneMutableMap(connectionId)
        when (newChildrenIds.size) {
          0 -> {
            val previousChildId = copiedMap.removeValue(parentId.id.arrayId)
            if (previousChildId != null) {
              listOf(Modification.Remove(parentId.id, createEntityId(previousChildId, connectionId.childClass)))
            } else emptyList()
          }
          1 -> {
            replaceOneToOneChildOfParent(connectionId, parentId.id, newChildrenIds.single())
          }
          else -> error("Trying to add multiple children to one-to-one connection")
        }
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        replaceOneToAbstractManyChildrenOfParent(connectionId, parentId, newChildrenIds)
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        when (newChildrenIds.size) {
          0 -> {
            val copiedMap = getAbstractOneToOneMutableMap(connectionId)
            val removedChildId = copiedMap.inverse().remove(parentId)
            if (removedChildId != null) {
              listOf(Modification.Remove(parentId.id, removedChildId.id))
            } else emptyList()
          }
          1 -> {
            replaceOneToAbstractOneChildOfParent(connectionId, parentId, newChildrenIds.single())
          }
          else -> error("Trying to add multiple children to one-to-abstrace-one connection")
        }
      }
    }
  }

  fun replaceOneToManyChildrenOfParent(connectionId: ConnectionId,
                                       parentId: EntityId,
                                       newChildrenEntityIds: List<ChildEntityId>): List<Modification> {

    val newChildren = newChildrenEntityIds.mapToIntArray { it.id.arrayId }

    val copiedMap = getOneToManyMutableMap(connectionId)
    val existingChildren = copiedMap.getKeys(parentId.arrayId)
    if (existingChildren.isSameAs(newChildren)) return emptyList()

    return buildList {
      val removedKeys = copiedMap.removeValue(parentId.arrayId)
      removedKeys.forEach(IntConsumer {
        add(Modification.Remove(parentId, createEntityId(it, connectionId.childClass)))
      })
      val previousParents = copiedMap.addAll(newChildren, parentId.arrayId)
      previousParents.forEach(BiConsumer { child, parent ->
        add(Modification.Remove(createEntityId(parent, connectionId.parentClass), createEntityId(child, connectionId.childClass)))
      })
      newChildren.forEach { child ->
        add(Modification.Add(parentId, createEntityId(child, connectionId.childClass)))
      }
    }
  }

  private fun NonNegativeIntIntMultiMap.IntSequence.isSameAs(other: IntArray): Boolean {
    if (this.count() != other.size) return false
    var counter = 0
    var differenceDetected = false
    this.forEach(IntConsumer {
      if (other[counter] != it) {
        differenceDetected = true
        return@IntConsumer
      }
      counter += 1
    })
    return !differenceDetected
  }

  fun replaceOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                               parentId: ParentEntityId,
                                               newChildrenEntityIds: List<ChildEntityId>): List<Modification> {
    val copiedMap = getOneToAbstractManyMutableMap(connectionId)
    val existingChildren = copiedMap.getKeysByValue(parentId)
    if (existingChildren != null && existingChildren == newChildrenEntityIds) return emptyList()

    return buildList {
      val removedChildren = copiedMap.removeValue(parentId)
      removedChildren.forEach { add(Modification.Remove(parentId.id, it.id)) }
      newChildrenEntityIds.forEach {
        val previousParentOfChild = copiedMap.add(it, parentId)
        if (previousParentOfChild != null) add(Modification.Remove(previousParentOfChild.id, it.id))
        add(Modification.Add(parentId.id, it.id))
      }
    }
  }

  fun replaceOneToAbstractOneParentOfChild(
    connectionId: ConnectionId,
    childId: ChildEntityId,
    parentId: ParentEntityId
  ): List<Modification> {
    val existingParent = getOneToAbstractOneParent(connectionId, childId)
    if (existingParent == parentId) return emptyList()

    val copiedMap = getAbstractOneToOneMutableMap(connectionId)
    val removedParent = copiedMap.remove(childId)
    val removedChild = copiedMap.inverse().remove(parentId)
    copiedMap[childId] = parentId
    return buildList {
      if (removedParent != null) add(Modification.Remove(removedParent.id, childId.id))
      if (removedChild != null) add(Modification.Remove(parentId.id, removedChild.id))
      add(Modification.Add(parentId.id, childId.id))
    }
  }

  fun replaceOneToAbstractOneChildOfParent(connectionId: ConnectionId,
                                           parentId: ParentEntityId,
                                           childEntityId: ChildEntityId): List<Modification> {
    val existingParent = getOneToAbstractOneParent(connectionId, childEntityId)
    if (existingParent == parentId) return emptyList()

    return buildList {
      val copiedMap = getAbstractOneToOneMutableMap(connectionId)
      val previousChildId = copiedMap.inverse().remove(parentId)
      val previousParentId = copiedMap.put(childEntityId.id.asChild(), parentId)
      if (previousChildId != null) add(Modification.Remove(parentId.id, previousChildId.id))
      if (previousParentId != null) add(Modification.Remove(previousParentId.id, childEntityId.id))
      add(Modification.Add(parentId.id, childEntityId.id))
    }
  }

  fun replaceOneToOneChildOfParent(connectionId: ConnectionId, parentId: EntityId, childEntityId: ChildEntityId): List<Modification> {
    val existingChild = getOneToOneChild(connectionId, parentId.arrayId) { it }
    if (existingChild == childEntityId.id.arrayId) return emptyList()

    val copiedMap = getOneToOneMutableMap(connectionId)
    val removedParentId = copiedMap.removeKey(childEntityId.id.arrayId)
    val removedChildId = copiedMap.removeValue(parentId.arrayId)
    copiedMap.put(childEntityId.id.arrayId, parentId.arrayId)

    return buildList {
      if (removedChildId != null) add(Modification.Remove(parentId, createEntityId(removedChildId, connectionId.childClass)))
      if (removedParentId != null) add(Modification.Remove(createEntityId(removedParentId, connectionId.parentClass), childEntityId.id))
      add(Modification.Add(parentId, childEntityId.id))
    }
  }

  fun replaceOneToOneParentOfChild(
    connectionId: ConnectionId,
    childId: EntityId,
    parentId: EntityId,
  ): List<Modification> {
    val existingParent = getOneToOneParent(connectionId, childId.arrayId) { it }
    if (existingParent == parentId.arrayId) return emptyList()

    val copiedMap = getOneToOneMutableMap(connectionId)
    val removedParent = copiedMap.removeKey(childId.arrayId)
    val removedChild = copiedMap.removeValue(parentId.arrayId)
    copiedMap.put(childId.arrayId, parentId.arrayId)

    return buildList {
      if (removedParent != null) add(Modification.Remove(createEntityId(removedParent, connectionId.parentClass), childId))
      if (removedChild != null) add(Modification.Remove(parentId, createEntityId(removedChild, connectionId.childClass)))
      add(Modification.Add(parentId, childId))
    }
  }

  internal fun replaceParentOfChild(connectionId: ConnectionId, childId: ChildEntityId, parentId: ParentEntityId): List<Modification> {
    return when (connectionId.connectionType) {
      ConnectionType.ONE_TO_MANY -> {
        replaceOneToManyParentOfChild(connectionId, childId.id, parentId)
      }
      ConnectionType.ONE_TO_ONE -> {
        replaceOneToOneParentOfChild(connectionId, childId.id, parentId.id)
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        replaceOneToAbstractManyParentOfChild(connectionId, childId, parentId)
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        replaceOneToAbstractOneParentOfChild(connectionId, childId, parentId)
      }
    }
  }

  fun replaceOneToManyParentOfChild(
    connectionId: ConnectionId,
    childId: EntityId,
    parentId: ParentEntityId
  ): List<Modification> {
    return buildList {
      val copiedMap = getOneToManyMutableMap(connectionId)

      // Check if the reference already exists. This is needed to make fewer operations and not to break children ordering
      val existingParent = copiedMap.get(childId.arrayId)
      if (existingParent != NonNegativeIntIntBiMap.DEFAULT_RETURN_VALUE && existingParent == parentId.id.arrayId) return emptyList()

      val removedParent = copiedMap.removeKey(childId.arrayId)
      val removedChildren = copiedMap.addAll(intArrayOf(childId.arrayId), parentId.id.arrayId)
      if (removedParent != null) add(Modification.Remove(createEntityId(removedParent, connectionId.parentClass), childId))
      removedChildren.forEach { (child, parent) ->
        add(Modification.Remove(createEntityId(parent, connectionId.parentClass), createEntityId(child, connectionId.childClass)))
      }
      add(Modification.Add(parentId.id, childId))
    }
  }

  fun replaceOneToAbstractManyParentOfChild(
    connectionId: ConnectionId,
    childId: ChildEntityId,
    parentId: ParentEntityId
  ): List<Modification> {
    val copiedMap = getOneToAbstractManyMutableMap(connectionId)

    // Check if the reference already exists. This is needed to make fewer operations and not to break children ordering
    if (copiedMap[childId] == parentId) return emptyList()

    val removedParent = copiedMap.remove(childId)
    copiedMap.add(childId, parentId)
    return buildList {
      if (removedParent != null) add(Modification.Remove(removedParent.id, childId.id))
      add(Modification.Add(parentId.id, childId.id))
    }
  }

  fun toImmutable(): RefsTable = RefsTable(
    oneToManyContainer.toImmutable(),
    oneToOneContainer.toImmutable(),
    oneToAbstractManyContainer.toImmutable(),
    abstractOneToOneContainer.toImmutable()
  )

  companion object {
    fun from(other: RefsTable): MutableRefsTable = MutableRefsTable(
      other.oneToManyContainer.toMutableContainer(),
      other.oneToOneContainer.toMutableContainer(),
      other.oneToAbstractManyContainer.toMutableContainer(),
      other.abstractOneToOneContainer.toMutableContainer())
  }

  private fun <T> List<T>.mapToIntArray(action: (T) -> Int): IntArray {
    val intArrayList = IntArrayList()
    this.forEach { item ->
      intArrayList.add(action(item))
    }

    return intArrayList.toIntArray()
  }
}

internal sealed class AbstractRefsTable {
  internal abstract val oneToManyContainer: ReferenceContainer<NonNegativeIntIntBiMap>
  internal abstract val oneToOneContainer: ReferenceContainer<IntIntUniqueBiMap>
  internal abstract val oneToAbstractManyContainer: ReferenceContainer<LinkedBidirectionalMap<ChildEntityId, ParentEntityId>>
  internal abstract val abstractOneToOneContainer: ReferenceContainer<BiMap<ChildEntityId, ParentEntityId>>

  fun <Parent : WorkspaceEntity, Child : WorkspaceEntity> findConnectionId(parentClass: Class<Parent>, childClass: Class<Child>): ConnectionId? {
    val parentClassId = parentClass.toClassId()
    val childClassId = childClass.toClassId()
    return (oneToManyContainer.keys.find { it.parentClass == parentClassId && it.childClass == childClassId }
            ?: oneToOneContainer.keys.find { it.parentClass == parentClassId && it.childClass == childClassId }
            ?: oneToAbstractManyContainer.keys.find {
              it.parentClass.findWorkspaceEntity().isAssignableFrom(parentClass) &&
              it.childClass.findWorkspaceEntity().isAssignableFrom(childClass)
            }
            ?: abstractOneToOneContainer.keys.find {
              it.parentClass.findWorkspaceEntity().isAssignableFrom(parentClass) &&
              it.childClass.findWorkspaceEntity().isAssignableFrom(childClass)
            })
  }

  fun getParentRefsOfChild(childId: ChildEntityId): Map<ConnectionId, ParentEntityId> {
    val childArrayId = childId.id.arrayId
    val childClassId = childId.id.clazz
    val childClass = childId.id.clazz.findWorkspaceEntity()

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
      .filterKeys { it.childClass.findWorkspaceEntity().isAssignableFrom(childClass) }
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.childClass.findWorkspaceEntity().isAssignableFrom(childClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      if (!bimap.containsKey(childId)) continue
      val value = bimap[childId] ?: continue
      val existingValue = res.putIfAbsent(connectionId, value)
      if (existingValue != null) thisLogger().error("This parent already exists")
    }

    return res
  }

  fun getChildrenByParent(connectionId: ConnectionId, parentId: ParentEntityId): List<ChildEntityId> {
    return when (connectionId.connectionType) {
      ConnectionType.ONE_TO_ONE -> {
        val map = oneToOneContainer[connectionId] ?: return emptyList()
        if (map.containsValue(parentId.id.arrayId)) {
          val childId = map.getKey(parentId.id.arrayId)
          listOf(createEntityId(childId, connectionId.childClass).asChild())
        }
        else emptyList()
      }
      ConnectionType.ONE_TO_MANY -> {
        oneToManyContainer[connectionId]
          ?.getKeys(parentId.id.arrayId)
          ?.map { createEntityId(it, connectionId.childClass).asChild() }
          ?.toList() ?: emptyList()
      }
      ConnectionType.ONE_TO_ABSTRACT_MANY -> {
        oneToAbstractManyContainer[connectionId]?.getKeysByValue(parentId) ?: emptyList()
      }
      ConnectionType.ABSTRACT_ONE_TO_ONE -> {
        abstractOneToOneContainer[connectionId]?.inverse()?.get(parentId)?.let { listOf(it) } ?: emptyList()
      }
    }
  }

  fun getChildrenRefsOfParentBy(parentId: ParentEntityId): Map<ConnectionId, List<ChildEntityId>> {
    val parentArrayId = parentId.id.arrayId
    val parentClassId = parentId.id.clazz
    val parentClass = parentId.id.clazz.findWorkspaceEntity()

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
      .filterKeys { it.parentClass.findWorkspaceEntity().isAssignableFrom(parentClass) }
    for ((connectionId, bimap) in filteredOneToAbstractMany) {
      val keys = bimap.getKeysByValue(parentId) ?: continue
      if (keys.isNotEmpty()) {
        val existingValue = res.putIfAbsent(connectionId, keys.map { it })
        if (existingValue != null) thisLogger().error("These children already exist")
      }
    }

    val filteredAbstractOneToOne = abstractOneToOneContainer
      .filterKeys { it.parentClass.findWorkspaceEntity().isAssignableFrom(parentClass) }
    for ((connectionId, bimap) in filteredAbstractOneToOne) {
      val key = bimap.inverse()[parentId]
      if (key == null) continue
      val existingValue = res.putIfAbsent(connectionId, listOf(key))
      if (existingValue != null) thisLogger().error("These children already exist")
    }

    return res
  }

  fun getOneToAbstractOneParent(connectionId: ConnectionId, childId: ChildEntityId): ParentEntityId? {
    return abstractOneToOneContainer[connectionId]?.get(childId)
  }

  fun getOneToAbstractManyParent(connectionId: ConnectionId, childId: ChildEntityId): ParentEntityId? {
    val map = oneToAbstractManyContainer[connectionId]
    return map?.get(childId)
  }

  fun <Child> getOneToOneChild(connectionId: ConnectionId, parentId: Int, transformer: IntFunction<Child?>): Child? {
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsValue(parentId)) return null

    return transformer.apply(bimap.getKey(parentId))
  }

  fun <Parent> getOneToOneParent(connectionId: ConnectionId, childId: Int, transformer: IntFunction<Parent?>): Parent? {
    val bimap = oneToOneContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer.apply(bimap.get(childId))
  }

  fun <Parent> getOneToManyParent(connectionId: ConnectionId, childId: Int, transformer: IntFunction<Parent?>): Parent? {
    val bimap = oneToManyContainer[connectionId] ?: return null
    if (!bimap.containsKey(childId)) return null

    return transformer.apply(bimap.get(childId))
  }
}

@JvmInline
internal value class ChildEntityId(val id: EntityId) {
  override fun toString(): String {
    return "ChildEntityId(id=${id.asString()})"
  }
}

@JvmInline
internal value class ParentEntityId(val id: EntityId) {
  override fun toString(): String {
    return "ParentEntityId(id=${id.asString()})"
  }
}

internal fun EntityId.asChild(): ChildEntityId = ChildEntityId(this)
internal fun EntityId.asParent(): ParentEntityId = ParentEntityId(this)

internal fun sameClass(fromConnectionId: Int, myClazz: Int, type: ConnectionType): Boolean {
  return when (type) {
    ConnectionType.ONE_TO_ONE, ConnectionType.ONE_TO_MANY -> fromConnectionId == myClazz
    ConnectionType.ONE_TO_ABSTRACT_MANY, ConnectionType.ABSTRACT_ONE_TO_ONE -> {
      fromConnectionId.findWorkspaceEntity().isAssignableFrom(myClazz.findWorkspaceEntity())
    }
  }
}

