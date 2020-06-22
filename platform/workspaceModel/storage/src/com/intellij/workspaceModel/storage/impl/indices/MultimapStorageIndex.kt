// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.containers.copy
import com.intellij.workspaceModel.storage.impl.containers.putAll
import org.jetbrains.annotations.TestOnly

open class MultimapStorageIndex<T> private constructor(
  internal open val index: BidirectionalMultiMap<EntityId, T>
) {
  constructor() : this(BidirectionalMultiMap<EntityId, T>())

  internal fun getIdsByEntry(entitySource: T): Set<EntityId> = index.getKeys(entitySource)

  internal fun getEntriesById(id: EntityId): Set<T> = index.getValues(id)

  internal fun entries(): Collection<T> {
    return index.values
  }

  class MutableMultimapStorageIndex<T> private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: BidirectionalMultiMap<EntityId, T>
  ) : MultimapStorageIndex<T>(index) {

    private var freezed = true

    internal fun index(id: EntityId, elements: Set<T>? = null) {
      startWrite()
      index.removeKey(id)
      if (elements == null) return
      elements.forEach { index.put(id, it) }
    }

    internal fun index(id: EntityId, element: T) {
      startWrite()
      index.put(id, element)
    }

    internal fun remove(id: EntityId, element: T) {
      startWrite()
      index.remove(id, element)
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      index.clear()
    }

    @TestOnly
    internal fun copyFrom(another: MultimapStorageIndex<T>) {
      startWrite()
      this.index.putAll(another.index)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = copyIndex()
    }

    private fun copyIndex(): BidirectionalMultiMap<EntityId, T> = index.copy()

    fun toImmutable(): MultimapStorageIndex<T> {
      freezed = true
      return MultimapStorageIndex(index)
    }

    companion object {
      fun <T> from(other: MultimapStorageIndex<T>): MutableMultimapStorageIndex<T> {
        return MutableMultimapStorageIndex(other.index)
      }
    }
  }
}

