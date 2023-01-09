// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.google.common.collect.HashBiMap
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.impl.EntityId
import org.jetbrains.annotations.TestOnly

open class SymbolicIdInternalIndex private constructor(
  internal open val index: HashBiMap<EntityId, SymbolicEntityId<*>>
) {
  constructor() : this(HashBiMap.create<EntityId, SymbolicEntityId<*>>())

  internal fun getIdsByEntry(symbolicId: SymbolicEntityId<*>): EntityId? = index.inverse()[symbolicId]

  internal fun getEntryById(id: EntityId): SymbolicEntityId<*>? = index[id]

  internal fun entries(): Collection<SymbolicEntityId<*>> {
    return index.values
  }

  class MutableSymbolicIdInternalIndex private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: HashBiMap<EntityId, SymbolicEntityId<*>>
  ) : SymbolicIdInternalIndex(index) {

    private var freezed = true

    internal fun index(id: EntityId, symbolicId: SymbolicEntityId<*>? = null) {
      startWrite()
      if (symbolicId == null) {
        index.remove(id)
        return
      }
      index[id] = symbolicId
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      index.clear()
    }

    @TestOnly
    internal fun copyFrom(another: SymbolicIdInternalIndex) {
      startWrite()
      this.index.putAll(another.index)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = HashBiMap.create(index)
    }

    fun toImmutable(): SymbolicIdInternalIndex {
      freezed = true
      return SymbolicIdInternalIndex(this.index)
    }

    companion object {
      fun from(other: SymbolicIdInternalIndex): MutableSymbolicIdInternalIndex {
        if (other is MutableSymbolicIdInternalIndex) other.freezed = true
        return MutableSymbolicIdInternalIndex(other.index)
      }
    }
  }
}

