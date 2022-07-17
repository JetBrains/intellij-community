// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.table.JBTable
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.VcsCommitExternalStatus
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.VcsLogCustomColumn
import com.intellij.vcs.log.ui.table.column.isVisible
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.*
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.event.ChangeListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

abstract class VcsLogExternalStatusColumnService<T : VcsCommitExternalStatus> : Disposable {

  private val providers = mutableMapOf<GraphTableModel, CachingVcsCommitsDataLoader<T>>()

  fun initialize(table: VcsLogGraphTable, column: VcsLogCustomColumn<T>) {
    if (table.model in providers) return

    val loader = getDataLoader(table.logData.project)
    val provider = CachingVcsCommitsDataLoader(loader)
    loadDataForVisibleRows(table, column, provider)

    Disposer.register(this, provider)

    providers[table.model] = provider
    Disposer.register(table, Disposable {
      providers.remove(table.model)
      Disposer.dispose(provider)
    })
  }

  fun getStatus(model: GraphTableModel, row: Int): T? = model.getCommitId(row)?.let { providers[model]?.getData(it) }

  abstract fun getDataLoader(project: Project): VcsCommitsDataLoader<T>

  override fun dispose() {
    providers.clear()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  companion object {

    @OptIn(FlowPreview::class)
    private fun <T : VcsCommitExternalStatus> loadDataForVisibleRows(table: VcsLogGraphTable,
                                                                     column: VcsLogCustomColumn<T>,
                                                                     dataProvider: VcsCommitsDataLoader<T>) {

      // `later()` is important here to ensure [VcsLogGraphTable] is already wrapped with scroll pane
      val scope = CoroutineScope(AppUIExecutor.onUiThread().later().coroutineDispatchingContext())
      Disposer.register(dataProvider) {
        scope.cancel()
      }

      scope.launch(CoroutineName("Vcs log table ${table.id} rows visibility tracker")) {
        combine(
          table.columnVisibilityFlow(column),
          combine(table.modelChangedFlow(), table.expandedVisibleRowsFlow(15)) { _, rowsRange -> rowsRange }
            .debounce(300L),
          ::Pair
        ).collectLatest { (isColumnVisible, rowsRange) ->
          val commits: List<CommitId> =
            if (rowsRange.isEmpty() || !isColumnVisible) emptyList()
            else rowsRange.limitedBy(0 until table.model.rowCount).mapNotNull(table.model::getCommitId)
          dataProvider.loadData(commits) {
            table.onColumnDataChanged(column)
          }
        }
      }
    }

    private fun VcsLogGraphTable.columnVisibilityFlow(column: VcsLogColumn<*>): Flow<Boolean> {
      val flow = callbackFlow {
        val emit = { trySend(column.isVisible(properties)) }
        val listener = object : VcsLogUiProperties.PropertiesChangeListener {
          override fun <T> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) {
            emit()
          }
        }

        emit() // initial value

        properties.addChangeListener(listener)
        awaitClose { properties.removeChangeListener(listener) }
      }

      return flow.distinctUntilChanged()
    }

    private fun JTable.modelChangedFlow(): Flow<Unit> =
      callbackFlow {
        val emit: () -> Unit = {
          trySend(Unit).onClosed { throw IllegalStateException(it) }
        }
        val listener = TableModelListener {
          if (it.column == TableModelEvent.ALL_COLUMNS) emit()
        }

        emit() // initial value

        model.addTableModelListener(listener)
        awaitClose { model.removeTableModelListener(listener) }
      }

    private fun JBTable.expandedVisibleRowsFlow(delta: Int): Flow<IntRange> =
      visibleRowsFlow()
        .scan(IntRange.EMPTY) { currentRangeWithDelta, newRange ->
          if (newRange in currentRangeWithDelta) currentRangeWithDelta
          else newRange.expandBy(delta).limitedBy(0 until rowCount)
        }
        .drop(1) // skip initial `IntRange.EMPTY` from `scan`
        .distinctUntilChanged()

    private fun JBTable.visibleRowsFlow(): Flow<IntRange> {
      val viewport = parent ?: return emptyFlow()
      val scrollPane = viewport.parent as? JScrollPane ?: return emptyFlow()

      val flow = callbackFlow {
        val emit: () -> Unit = { trySend(getVisibleRows()).onClosed { throw IllegalStateException(it) } }
        val listener = ChangeListener { emit() }

        emit() // initial value

        scrollPane.verticalScrollBar.model.addChangeListener(listener)
        awaitClose { scrollPane.verticalScrollBar.model.removeChangeListener(listener) }
      }

      return flow.distinctUntilChanged()
    }

    private fun JBTable.getVisibleRows(): IntRange {
      val visibleRows = ScrollingUtil.getVisibleRows(this)
      if (visibleRows.first < 0 || visibleRows.second < 0) return IntRange.EMPTY

      return visibleRows.first..visibleRows.second
    }

    private fun IntRange.limitedBy(limit: IntRange): IntRange = kotlin.math.max(first, limit.first)..kotlin.math.min(last, limit.last)

    private fun IntRange.expandBy(delta: Int): IntRange = (first - delta)..(last + delta)

    private operator fun IntRange.contains(value: IntRange): Boolean = value.first in this && value.last in this
  }
}