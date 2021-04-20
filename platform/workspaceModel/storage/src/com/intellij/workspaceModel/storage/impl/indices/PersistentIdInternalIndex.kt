// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.google.common.collect.HashBiMap
import com.intellij.workspaceModel.storage.impl.EntityId
import org.jetbrains.annotations.TestOnly

open class PersistentIdInternalIndex<T> private constructor(
  internal open val index: HashBiMap<EntityId, T>
) {
  constructor() : this(HashBiMap.create<EntityId, T>())

  internal fun getIdsByEntry(entry: T): EntityId? = index.inverse()[entry]

  internal fun getEntryById(id: EntityId): T? = index[id]

  internal fun entries(): Collection<T> {
    return index.values
  }

  class MutablePersistentIdInternalIndex<T> private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: HashBiMap<EntityId, T>
  ) : PersistentIdInternalIndex<T>(index) {

    private var freezed = true

    internal fun index(id: EntityId, entry: T? = null) {
      startWrite()
      if (entry == null) {
        index.remove(id)
        return
      }
      index[id] = entry
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      index.clear()
    }

    @TestOnly
    internal fun copyFrom(another: PersistentIdInternalIndex<T>) {
      startWrite()
      this.index.putAll(another.index)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = HashBiMap.create(index)
    }

    fun toImmutable(): PersistentIdInternalIndex<T> {
      freezed = true
      return PersistentIdInternalIndex(this.index)
    }

    companion object {
      fun <T> from(other: PersistentIdInternalIndex<T>): MutablePersistentIdInternalIndex<T> {
        if (other is MutablePersistentIdInternalIndex<T>) other.freezed = true
        return MutablePersistentIdInternalIndex(other.index)
      }
    }
  }
}

