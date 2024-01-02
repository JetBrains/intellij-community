// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.ChangeLog
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.cache.CellUpdateInfo
import com.intellij.platform.workspace.storage.impl.cache.UpdateType
import com.intellij.platform.workspace.storage.impl.query.Token.WithEntityId
import com.intellij.platform.workspace.storage.impl.query.Token.WithInfo
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate

internal class CellChainId
internal class CellId

internal class CellChain(
  val cells: PersistentList<Cell<*>>,
  val id: CellChainId,
) {
  fun snapshotInput(snapshot: ImmutableEntityStorage): Pair<CellChain, List<Pair<ReadTraceHashSet, CellUpdateInfo>>> {
    val traces = ArrayList<Pair<ReadTraceHashSet, CellUpdateInfo>>()
    val newChain = cells.mutate {
      var tokens = TokenSet()
      it.indices.forEach { index ->
        val cell = it[index]
        if (index == 0) {
          val cellAndTokens = cell.snapshotInput(snapshot)
          tokens = cellAndTokens.tokenSet
          it[index] = cellAndTokens.newCell
          cellAndTokens.subscriptions.forEach { update ->
            val trace = update.second
            traces += update.first to CellUpdateInfo(this.id, cellAndTokens.newCell.id, trace)
          }
        }
        else {
          val cellAndTokens = cell.input(tokens, snapshot)
          tokens = cellAndTokens.tokenSet
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
                  changes: ChangeLog,
                  externalMappingChanges: Map<String, Set<EntityId>>,
                  cellToActivate: CellId): Pair<CellChain, List<Pair<ReadTraceHashSet, CellUpdateInfo>>> {
    val traces = ArrayList<Pair<ReadTraceHashSet, CellUpdateInfo>>()
    var myTokens = when (changeRequest.updateType) {
      is UpdateType.DIFF -> makeTokensForDiff(changes, externalMappingChanges)
      is UpdateType.RECALCULATE -> {
        val tokens = TokenSet()
        if (changeRequest.updateType.entityId != null) {
          tokens.add(WithEntityId(Operation.REMOVED, changeRequest.updateType.entityId))
          tokens.add(WithEntityId(Operation.ADDED, changeRequest.updateType.entityId))
        }
        else {
          tokens.add(WithInfo(Operation.REMOVED, changeRequest.updateType.key))
          tokens.add(WithInfo(Operation.ADDED, changeRequest.updateType.key))
        }
        tokens
      }
    }
    val newChain = cells.mutate { cellList ->
      val startingIndex = cellList.withIndex().first { it.value.id == cellToActivate }.index
      (startingIndex..cellList.lastIndex).forEach { index ->
        val cell = cellList[index]
        val cellAndTokens = cell.input(myTokens, newSnapshot)
        myTokens = cellAndTokens.tokenSet
        cellList[index] = cellAndTokens.newCell
        cellAndTokens.subscriptions.forEach { update ->
          val trace = update.second
          traces += update.first to CellUpdateInfo(id, cellAndTokens.newCell.id, trace)
        }
      }
    }.toChain(id)
    return newChain to traces
  }

  private fun makeTokensForDiff(changes: ChangeLog, externalMappingChanges: Map<String, Set<EntityId>>): TokenSet {
    val tokenSet = TokenSet()
    val createdTokens = HashSet<Pair<Operation, EntityId>>()

    changes.forEach { (entityId, change) ->
      when (change) {
        is ChangeEntry.AddEntity -> {
          if (createdTokens.add(Operation.ADDED to entityId)) tokenSet += WithEntityId(Operation.ADDED, entityId)
        }
        is ChangeEntry.RemoveEntity -> {
          if (createdTokens.add(Operation.REMOVED to entityId)) tokenSet += WithEntityId(Operation.REMOVED, entityId)
        }
        is ChangeEntry.ReplaceEntity -> {
          if (createdTokens.add(Operation.REMOVED to entityId)) tokenSet += WithEntityId(Operation.REMOVED, entityId)
          if (createdTokens.add(Operation.ADDED to entityId)) tokenSet += WithEntityId(Operation.ADDED, entityId)
        }
      }
    }

    externalMappingChanges.values.forEach { affectedIds ->
      affectedIds.forEach { entityId ->
        if (createdTokens.add(Operation.REMOVED to entityId)) tokenSet += WithEntityId(Operation.REMOVED, entityId)
        if (createdTokens.add(Operation.ADDED to entityId)) tokenSet += WithEntityId(Operation.ADDED, entityId)
      }
    }

    return tokenSet
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> data(): T {
    return cells.last().data() as T
  }

  private fun PersistentList<Cell<*>>.toChain(id: CellChainId) = CellChain(this, id)
}
