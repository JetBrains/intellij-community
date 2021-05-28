// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.containers.copy
import com.intellij.workspaceModel.storage.impl.containers.putAll
import org.jetbrains.annotations.TestOnly

open class MultimapStorageIndex private constructor(
  internal open val index: BidirectionalMultiMap<EntityId, PersistentEntityId<*>>
) {
  constructor() : this(BidirectionalMultiMap<EntityId, PersistentEntityId<*>>())

  internal fun getIdsByEntry(entitySource: PersistentEntityId<*>): Set<EntityId> = index.getKeys(entitySource)

  internal fun getEntriesById(id: EntityId): Set<PersistentEntityId<*>> = index.getValues(id)

  internal fun entries(): Collection<PersistentEntityId<*>> = index.values

  class MutableMultimapStorageIndex private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: BidirectionalMultiMap<EntityId, PersistentEntityId<*>>
  ) : MultimapStorageIndex(index) {

    private var freezed = true

    internal fun index(id: EntityId, elements: Set<PersistentEntityId<*>>? = null) {
      startWrite()
      index.removeKey(id)
      if (elements == null) return
      elements.forEach { index.put(id, it) }
    }

    internal fun index(id: EntityId, element: PersistentEntityId<*>) {
      startWrite()
      index.put(id, element)
    }

    internal fun remove(id: EntityId, element: PersistentEntityId<*>) {
      startWrite()
      index.remove(id, element)
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      index.clear()
    }

    @TestOnly
    internal fun copyFrom(another: MultimapStorageIndex) {
      startWrite()
      this.index.putAll(another.index)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = copyIndex()
    }

    private fun copyIndex(): BidirectionalMultiMap<EntityId, PersistentEntityId<*>> = index.copy()

    fun toImmutable(): MultimapStorageIndex {
      freezed = true
      return MultimapStorageIndex(index)
    }

    companion object {
      fun from(other: MultimapStorageIndex): MutableMultimapStorageIndex {
        if (other is MutableMultimapStorageIndex) other.freezed = true
        return MutableMultimapStorageIndex(other.index)
      }
    }
  }
}

