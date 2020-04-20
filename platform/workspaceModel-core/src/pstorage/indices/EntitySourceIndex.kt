// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.indices

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.PId

open class EntitySourceIndex private constructor(
  internal val index: BidirectionalMap<PId<out TypedEntity>, EntitySource>
) {
  constructor() : this(BidirectionalMap<PId<out TypedEntity>, EntitySource>())

  internal fun getIdsByEntitySource(entitySource: EntitySource): List<PId<out TypedEntity>>? =
    index.getKeysByValue(entitySource)

  internal fun getEntitySource(id: PId<out TypedEntity>): EntitySource? = index[id]

  internal fun copyIndex(): BidirectionalMap<PId<out TypedEntity>, EntitySource> {
    val copy = BidirectionalMap<PId<out TypedEntity>, EntitySource>()
    index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
    return copy
  }

  class MutableEntitySourceIndex private constructor(
    index: BidirectionalMap<PId<out TypedEntity>, EntitySource>
  ) : EntitySourceIndex(index) {
    constructor() : this(BidirectionalMap<PId<out TypedEntity>, EntitySource>())

    internal fun index(id: PId<out TypedEntity>, entitySource: EntitySource? = null) {
      index.remove(id)
      if (entitySource == null) return
      index[id] = entitySource
    }

    fun toImmutable(): EntitySourceIndex = EntitySourceIndex(copyIndex())

    companion object {
      fun from(other: EntitySourceIndex): MutableEntitySourceIndex = MutableEntitySourceIndex(other.copyIndex())
    }
  }
}