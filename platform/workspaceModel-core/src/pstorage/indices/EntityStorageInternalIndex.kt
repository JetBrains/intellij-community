// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.indices

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.pstorage.PId

open class EntityStorageInternalIndex<T> private constructor(
  internal open val index: BidirectionalMap<PId, T>
) {
  constructor() : this(BidirectionalMap<PId, T>())

  internal fun getIdsByEntry(entitySource: T): List<PId>? =
    index.getKeysByValue(entitySource)

  internal fun getEntryById(id: PId): T? = index[id]

  internal fun entries(): Collection<T> {
    return index.values
  }

  class MutableEntityStorageInternalIndex<T> private constructor(
    override var index: BidirectionalMap<PId, T>
  ) : EntityStorageInternalIndex<T>(index) {

    private var freezed = true

    internal fun index(id: PId, entitySource: T? = null) {
      startWrite()
      index.remove(id)
      if (entitySource == null) return
      index[id] = entitySource
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = copyIndex()
    }

    private fun copyIndex(): BidirectionalMap<PId, T> {
      val copy = BidirectionalMap<PId, T>()
      index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
      return copy
    }

    fun toImmutable(): EntityStorageInternalIndex<T> {
      freezed = true
      return EntityStorageInternalIndex(index)
    }

    companion object {
      fun <T> from(other: EntityStorageInternalIndex<T>): MutableEntityStorageInternalIndex<T> {
        return MutableEntityStorageInternalIndex(other.index)
      }
    }
  }
}