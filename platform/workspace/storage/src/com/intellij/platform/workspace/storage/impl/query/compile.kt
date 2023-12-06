// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.query.EntityBoundAssociationQuery
import com.intellij.platform.workspace.storage.query.EntityBoundCollectionQuery
import com.intellij.platform.workspace.storage.query.StorageQuery
import kotlinx.collections.immutable.toPersistentList

internal fun <T> compile(query: StorageQuery<T>, cellCollector: MutableList<Cell<*>> = mutableListOf()): CellOrchestra {
  when (query) {
    is EntityBoundCollectionQuery<*> -> {
      when (query) {
        is EntityBoundCollectionQuery.EachOfType<*> -> {
          cellCollector.prepend(EntitiesCell(query.type))
        }
        is EntityBoundCollectionQuery.FlatMapTo<*, *> -> {
          cellCollector.prepend(FlatMapCell(query.map))
          compile(query.from, cellCollector)
        }
      }
    }
    is EntityBoundAssociationQuery<*, *> -> {
      when (query) {
        is EntityBoundAssociationQuery.GroupBy<*, *, *> -> {
          cellCollector.prepend(GroupByCell(query.keySelector, query.valueTransformer))
          compile(query.from, cellCollector)
        }
      }
    }
  }
  return CellOrchestra(cellCollector.toPersistentList())
}

private fun <T> MutableList<T>.prepend(data: T) {
  this.add(0, data)
}