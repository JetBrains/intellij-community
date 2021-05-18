// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.google.common.collect.HashBiMap
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.impl.EntityId
import org.jetbrains.annotations.TestOnly

open class PersistentIdInternalIndex private constructor(
  internal open val index: HashBiMap<EntityId, PersistentEntityId<*>>
) {
  constructor() : this(HashBiMap.create<EntityId, PersistentEntityId<*>>())

  internal fun getIdsByEntry(persistentId: PersistentEntityId<*>): EntityId? = index.inverse()[persistentId]

  internal fun getEntryById(id: EntityId): PersistentEntityId<*>? = index[id]

  internal fun entries(): Collection<PersistentEntityId<*>> {
    return index.values
  }

  class MutablePersistentIdInternalIndex private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: HashBiMap<EntityId, PersistentEntityId<*>>
  ) : PersistentIdInternalIndex(index) {

    private var freezed = true

    internal fun index(id: EntityId, persistentId: PersistentEntityId<*>? = null) {
      startWrite()
      if (persistentId == null) {
        index.remove(id)
        return
      }
      index[id] = persistentId
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      index.clear()
    }

    @TestOnly
    internal fun copyFrom(another: PersistentIdInternalIndex) {
      startWrite()
      this.index.putAll(another.index)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = HashBiMap.create(index)
    }

    fun toImmutable(): PersistentIdInternalIndex {
      freezed = true
      return PersistentIdInternalIndex(this.index)
    }

    companion object {
      fun from(other: PersistentIdInternalIndex): MutablePersistentIdInternalIndex {
        if (other is MutablePersistentIdInternalIndex) other.freezed = true
        return MutablePersistentIdInternalIndex(other.index)
      }
    }
  }
}

