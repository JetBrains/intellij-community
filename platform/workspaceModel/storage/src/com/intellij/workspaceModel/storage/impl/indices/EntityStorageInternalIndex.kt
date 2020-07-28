// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.containers.copy
import org.jetbrains.annotations.TestOnly

open class EntityStorageInternalIndex<T> private constructor(
  internal open val index: BidirectionalMap<EntityId, T>,
  protected val oneToOneAssociation: Boolean
) {
  constructor(oneToOneAssociation: Boolean) : this(BidirectionalMap<EntityId, T>(), oneToOneAssociation)

  internal fun getIdsByEntry(entitySource: T): List<EntityId>? =
    index.getKeysByValue(entitySource)

  internal fun getEntryById(id: EntityId): T? = index[id]

  internal fun entries(): Collection<T> {
    return index.values
  }

  class MutableEntityStorageInternalIndex<T> private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: BidirectionalMap<EntityId, T>,
    oneToOneAssociation: Boolean
  ) : EntityStorageInternalIndex<T>(index, oneToOneAssociation) {

    private var freezed = true

    internal fun index(id: EntityId, entitySource: T? = null) {
      startWrite()
      index.remove(id)
      if (entitySource == null) return
      index[id] = entitySource
      if (oneToOneAssociation) {
        if (index.getKeysByValue(entitySource)?.size ?: 0 > 1) error("One to one association is violated. Id: $id, Entity: $entitySource")
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
      index = copyIndex()
    }

    private fun copyIndex(): BidirectionalMap<EntityId, T> = index.copy()

    fun toImmutable(): EntityStorageInternalIndex<T> {
      freezed = true
      return EntityStorageInternalIndex(index, this.oneToOneAssociation)
    }

    companion object {
      fun <T> from(other: EntityStorageInternalIndex<T>): MutableEntityStorageInternalIndex<T> {
        return MutableEntityStorageInternalIndex(other.index, other.oneToOneAssociation)
      }
    }
  }
}

