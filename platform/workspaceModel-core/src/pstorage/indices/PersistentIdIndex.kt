// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.indices

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.PId

open class PersistentIdIndex private constructor(
  internal val index: BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>
) {
  constructor() : this(BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>())

  internal fun getIdsByPersistentId(persistentId: PersistentEntityId<*>): List<PId<out TypedEntity>>? =
    index.getKeysByValue(persistentId)

  internal fun getPersistentId(id: PId<out TypedEntity>): PersistentEntityId<*>? = index[id]

  internal fun copyIndex(): BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>> {
    val copy = BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>()
    index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
    return copy
  }

  class MutablePersistentIdIndex private constructor(
    index: BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>
  ) : PersistentIdIndex(index) {
    constructor() : this(BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>())

    internal fun index(id: PId<out TypedEntity>, persistentId: PersistentEntityId<*>? = null) {
      index.remove(id)
      if (persistentId == null) return
      index[id] = persistentId
    }

    fun toImmutable(): PersistentIdIndex = PersistentIdIndex(copyIndex())

    companion object {
      fun from(other: PersistentIdIndex): MutablePersistentIdIndex = MutablePersistentIdIndex(other.copyIndex())
    }
  }
}