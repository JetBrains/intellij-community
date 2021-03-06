// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.containers.BidirectionalSetMap
import com.intellij.workspaceModel.storage.impl.containers.copy
import org.jetbrains.annotations.TestOnly

open class EntityStorageInternalIndex<T> private constructor(
  internal open val index: BidirectionalSetMap<EntityId, T>,
  protected val oneToOneAssociation: Boolean
) {
  constructor(oneToOneAssociation: Boolean) : this(BidirectionalSetMap<EntityId, T>(), oneToOneAssociation)

  internal fun getIdsByEntry(entry: T): List<EntityId>? = index.getKeysByValue(entry)?.toList()

  internal fun getEntryById(id: EntityId): T? = index[id]

  internal fun entries(): Collection<T> {
    return index.values
  }

  class MutableEntityStorageInternalIndex<T> private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: BidirectionalSetMap<EntityId, T>,
    oneToOneAssociation: Boolean
  ) : EntityStorageInternalIndex<T>(index, oneToOneAssociation) {

    private var freezed = true

    internal fun index(id: EntityId, entry: T? = null) {
      startWrite()
      if (entry == null) {
        index.remove(id)
        return
      }
      index[id] = entry
      if (oneToOneAssociation) {
        if (index.getKeysByValue(entry)?.size ?: 0 > 1) {
          thisLogger().error("One to one association is violated. Id: $id, Entity: $entry. This id is already associated with ${index.getKeysByValue(entry)}")
        }
      }
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      index.clear()
    }

    @TestOnly
    internal fun copyFrom(another: EntityStorageInternalIndex<T>) {
      startWrite()
      this.index.putAll(another.index)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = index.copy()
    }

    fun toImmutable(): EntityStorageInternalIndex<T> {
      freezed = true
      return EntityStorageInternalIndex(this.index, this.oneToOneAssociation)
    }

    companion object {
      fun <T> from(other: EntityStorageInternalIndex<T>): MutableEntityStorageInternalIndex<T> {
        if (other is MutableEntityStorageInternalIndex<T>) other.freezed = true
        return MutableEntityStorageInternalIndex(other.index, other.oneToOneAssociation)
      }
    }
  }
}

