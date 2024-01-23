// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.cache.CellUpdateInfo
import com.intellij.platform.workspace.storage.impl.cache.EntityStorageChange
import com.intellij.platform.workspace.storage.impl.cache.UpdateType
import com.intellij.platform.workspace.storage.impl.cache.makeTokensForDiff
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate

public class QueryId
internal class CellId

internal class CellChain(
  val cells: PersistentList<Cell<*>>,
  val id: QueryId,
) {
  fun snapshotInput(snapshot: ImmutableEntityStorage): Pair<CellChain, List<Pair<ReadTraceHashSet, CellUpdateInfo>>> {
    val traces = ArrayList<Pair<ReadTraceHashSet, CellUpdateInfo>>()
    val newChain = cells.mutate {
      var tokens = MatchSet()
      it.indices.forEach { index ->
        val cell = it[index]
        if (index == 0) {
          val cellAndTokens = cell.snapshotInput(snapshot)
          tokens = cellAndTokens.matchSet
          it[index] = cellAndTokens.newCell
          cellAndTokens.subscriptions.forEach { update ->
            val trace = update.second
            traces += update.first to CellUpdateInfo(this.id, cellAndTokens.newCell.id, trace)
          }
        }
        else {
          val cellAndTokens = cell.input(tokens, snapshot)
          tokens = cellAndTokens.matchSet
          it[index] = cellAndTokens.newCell
          cellAndTokens.subscriptions.forEach { update ->
            val trace = update.second
            traces += update.first to CellUpdateInfo(this.id, cellAndTokens.newCell.id, trace)
          }
        }
      }
    }.toChain(this.id)
    return newChain to traces
  }

  fun changeInput(newSnapshot: ImmutableEntityStorage,
                  changeRequest: CellUpdateInfo,
                  changes: EntityStorageChange,
                  cellToActivate: CellId): Pair<CellChain, List<Pair<ReadTraceHashSet, CellUpdateInfo>>> {
    val traces = ArrayList<Pair<ReadTraceHashSet, CellUpdateInfo>>()
    var myTokens = when (changeRequest.updateType) {
      is UpdateType.DIFF -> changes.makeTokensForDiff()
      is UpdateType.RECALCULATE -> {
        val tokens = MatchSet()
        val match = changeRequest.updateType.match

        tokens.removedMatch(match)
        if (match.isValid(newSnapshot)) {
          tokens.addedMatch(match)
        }
        tokens
      }
    }
    val newChain = cells.mutate { cellList ->
      val startingIndex = cellList.withIndex().first { it.value.id == cellToActivate }.index
      (startingIndex..cellList.lastIndex).forEach { index ->
        val cell = cellList[index]
        val cellAndTokens = cell.input(myTokens, newSnapshot)
        myTokens = cellAndTokens.matchSet
        cellList[index] = cellAndTokens.newCell
        cellAndTokens.subscriptions.forEach { update ->
          val trace = update.second
          traces += update.first to CellUpdateInfo(id, cellAndTokens.newCell.id, trace)
        }
      }
    }.toChain(id)
    return newChain to traces
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> data(): T {
    return cells.last().data() as T
  }

  private fun PersistentList<Cell<*>>.toChain(id: QueryId) = CellChain(this, id)
}
