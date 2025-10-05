// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl.indices

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.BidirectionalLongMultiMap
import com.intellij.platform.workspace.storage.impl.containers.putAll
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.TestOnly

private typealias BidirectionalMap = BidirectionalLongMultiMap<SymbolicEntityId<*>>
//private typealias BidirectionalMap = BidirectionalMultiMap<EntityId, PersistentEntityId<*>>

internal sealed class AbstractMultimapStorageIndex protected constructor(
  internal open val index: BidirectionalMap,
) {

  internal fun getIdsByEntry(entitySource: SymbolicEntityId<*>): Set<EntityId> = index.getKeys(entitySource)

  internal fun getEntriesById(id: EntityId): Set<SymbolicEntityId<*>> = index.getValues(id)

  internal fun entries(): Collection<SymbolicEntityId<*>> = index.values

  internal fun toMap(): Map<Long, Set<SymbolicEntityId<*>>> {
    return index.toMap()
  }
}

internal class ImmutableMultimapStorageIndex internal constructor(
  index: BidirectionalMap,
) : AbstractMultimapStorageIndex(index) {
  constructor() : this(BidirectionalMap())
}

internal class MutableMultimapStorageIndex private constructor(
  // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
  override var index: BidirectionalMap,
) : AbstractMultimapStorageIndex(index), WorkspaceMutableIndex<SymbolicEntityId<*>> {

  private val firstAddedValues: MutableSet<SymbolicEntityId<*>> = CollectionFactory.createSmallMemoryFootprintSet()
  private val lastRemovedValues: MutableSet<SymbolicEntityId<*>> = CollectionFactory.createSmallMemoryFootprintSet()

  private var freezed = true

  /**
   * Does not track first added and last removed values
   * Must be used only during WSM deserialization
   */
  internal fun populateIndex(id: EntityId, elements: Set<SymbolicEntityId<*>>? = null) {
    startWrite()
    index.removeKey(id)
    if (elements == null) return
    elements.forEach { index.put(id, it) }
  }

  internal fun index(id: EntityId, element: SymbolicEntityId<*>) {
    startWrite()
    val firstAddedValue = index.put(id, element)
    if (firstAddedValue) firstAddedValues.add(element)
  }

  internal fun remove(id: EntityId, element: SymbolicEntityId<*>) {
    startWrite()
    val lastRemovedValue = index.remove(id, element)
    if (lastRemovedValue) lastRemovedValues.add(element)
  }

  @TestOnly
  internal fun clear() {
    startWrite()
    index.clear()
  }

  @TestOnly
  /**
   * Because this is TestOnly method we don't track firstAdded and lastRemoved values
   */
  internal fun copyFrom(another: AbstractMultimapStorageIndex) {
    startWrite()
    this.index.putAll(another.index)
  }

  private fun startWrite() {
    if (!freezed) return
    freezed = false
    index = copyIndex()
  }

  private fun copyIndex(): BidirectionalMap = index.copy()

  fun toImmutable(): ImmutableMultimapStorageIndex {
    freezed = true
    return ImmutableMultimapStorageIndex(index)
  }

  companion object {
    fun from(other: AbstractMultimapStorageIndex): MutableMultimapStorageIndex {
      if (other is MutableMultimapStorageIndex) other.freezed = true
      return MutableMultimapStorageIndex(other.index)
    }
  }

  override fun index(entity: WorkspaceEntityData<*>, data: SymbolicEntityId<*>) {
    val id = entity.createEntityId()
    this.index(id, data)
  }

  override fun remove(entity: WorkspaceEntityData<*>, data: SymbolicEntityId<*>) {
    val id = entity.createEntityId()
    this.remove(id, data)
  }

  internal fun addedValues(): Set<SymbolicEntityId<*>> = firstAddedValues

  internal fun removedValues(): Set<SymbolicEntityId<*>> = lastRemovedValues

  internal fun clearTrackedValues() {
    firstAddedValues.clear()
    lastRemovedValues.clear()
  }
}

