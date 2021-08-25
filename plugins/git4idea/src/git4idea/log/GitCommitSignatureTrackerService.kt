// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor.onUiThread
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.ui.ScrollingUtil
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.isVisible
import com.intellij.vcs.log.util.contains
import com.intellij.vcs.log.util.expandBy
import com.intellij.vcs.log.util.limitedBy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.swing.JScrollPane
import javax.swing.event.ChangeListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

@Service(Service.Level.PROJECT)
internal class GitCommitSignatureTrackerService {
  private val trackers = mutableMapOf<GraphTableModel, GitCommitSignatureTracker>()

  fun getCommitSignature(model: GraphTableModel, row: Int): GitCommitSignature? =
    trackers[model]?.getCommitSignature(row)

  fun ensureTracking(table: VcsLogGraphTable, column: GitCommitSignatureLogColumn) {
    if (table.model in trackers) return

    val tracker = GitCommitSignatureTracker(table, column)
    trackers[table.model] = tracker
    Disposer.register(tracker) { trackers -= table.model }
    Disposer.register(table, tracker)

    tracker.track()
  }

  companion object {
    fun getInstance(project: Project): GitCommitSignatureTrackerService = project.service()
  }
}

private val LOG = logger<GitCommitSignatureTracker>()

private const val TRACKED_ROWS_DELTA = 15
private const val TRACKED_ROWS_DELAY = 300L

private class GitCommitSignatureTracker(
  private val table: VcsLogGraphTable,
  private val column: GitCommitSignatureLogColumn
) : Disposable {

  private val project: Project get() = table.logData.project
  private val model: GraphTableModel get() = table.model

  // `later()` is important here to ensure [VcsLogGraphTable] is already wrapped with scroll pane
  private val scope = CoroutineScope(onUiThread().later().coroutineDispatchingContext())
    .also { Disposer.register(this) { it.cancel() } }

  private val commitSignatures = mutableMapOf<CommitId, GitCommitSignature>()

  fun getCommitSignature(row: Int): GitCommitSignature? {
    val commitId = model.getCommitId(row) ?: return null
    return commitSignatures[commitId]
  }

  override fun dispose() = Unit

  @Suppress("EXPERIMENTAL_API_USAGE")
  fun track() {
    scope.launch(CoroutineName("Git Commit Signature Tracker - ${table.id}")) {
      val trackedRowsFlow =
        combine(table.modelChangedFlow(), table.visibleRowsFlow(TRACKED_ROWS_DELTA)) { _, rowsRange -> rowsRange }

      combine(
        trackedRowsFlow.debounce(TRACKED_ROWS_DELAY),
        table.columnVisibilityFlow(column),
        ::Pair
      ).collectLatest { (rowsRange, isColumnVisible) ->
        if (rowsRange.isEmpty() || !isColumnVisible) return@collectLatest
        update(rowsRange)
      }
    }
  }

  private suspend fun update(rowsRange: IntRange) =
    try {
      doUpdate(rowsRange)
    }
    catch (e: VcsException) {
      LOG.warn("Failed to load commit signatures", e)
    }

  @Throws(VcsException::class)
  private suspend fun doUpdate(rowsRange: IntRange) {
    val rows = rowsRange.toList().toIntArray()
    val rootCommits = model.getCommitIds(rows).groupBy { it.root }

    for ((root, commits) in rootCommits) {
      val signatures = loadCommitSignatures(project, root, commits.map { it.hash })

      commitSignatures.keys.removeIf { it.root == root }
      commitSignatures += signatures.mapKeys { (hash, _) -> CommitId(hash, root) }

      fireColumnDataChanged()
    }
  }

  private fun fireColumnDataChanged() = table.repaint()
}

@Suppress("EXPERIMENTAL_API_USAGE")
private fun VcsLogGraphTable.modelChangedFlow(): Flow<Unit> =
  callbackFlow {
    val emit = { offer(Unit) }
    val listener = TableModelListener {
      if (it.column == TableModelEvent.ALL_COLUMNS) emit()
    }

    emit() // initial value

    model.addTableModelListener(listener)
    awaitClose { model.removeTableModelListener(listener) }
  }

@Suppress("EXPERIMENTAL_API_USAGE")
private fun VcsLogGraphTable.columnVisibilityFlow(column: VcsLogColumn<*>): Flow<Boolean> {
  val flow = callbackFlow {
    val emit = { offer(column.isVisible(properties)) }
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

@Suppress("EXPERIMENTAL_API_USAGE")
private fun VcsLogGraphTable.visibleRowsFlow(delta: Int): Flow<IntRange> =
  visibleRowsFlow()
    .scan(IntRange.EMPTY) { currentRangeWithDelta, newRange ->
      if (newRange in currentRangeWithDelta) currentRangeWithDelta
      else newRange.expandBy(delta).limitedBy(0 until rowCount)
    }
    .drop(1) // skip initial `IntRange.EMPTY` from `scan`
    .distinctUntilChanged()

@Suppress("EXPERIMENTAL_API_USAGE")
private fun VcsLogGraphTable.visibleRowsFlow(): Flow<IntRange> {
  val viewport = parent ?: return emptyFlow()
  val scrollPane = viewport.parent as? JScrollPane ?: return emptyFlow()

  val flow = callbackFlow {
    val emit = { offer(getVisibleRows()) }
    val listener = ChangeListener { emit() }

    emit() // initial value

    scrollPane.verticalScrollBar.model.addChangeListener(listener)
    awaitClose { scrollPane.verticalScrollBar.model.removeChangeListener(listener) }
  }

  return flow.distinctUntilChanged()
}

private fun VcsLogGraphTable.getVisibleRows(): IntRange {
  val visibleRows = ScrollingUtil.getVisibleRows(this)
  if (visibleRows.first < 0 || visibleRows.second < 0) return IntRange.EMPTY

  return visibleRows.first..visibleRows.second
}