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
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.isVisible
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import kotlin.math.min

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

private const val TRACK_SIZE = 50

private class GitCommitSignatureTracker(
  private val table: VcsLogGraphTable,
  private val column: GitCommitSignatureLogColumn
) : Disposable {

  private val project: Project get() = table.logData.project
  private val model: GraphTableModel get() = table.model

  private val scope = CoroutineScope(onUiThread().coroutineDispatchingContext())
    .also { Disposer.register(this) { it.cancel() } }

  private val commitSignatures = mutableMapOf<CommitId, GitCommitSignature>()

  fun getCommitSignature(row: Int): GitCommitSignature? {
    val commitId = model.getCommitId(row) ?: return null
    return commitSignatures[commitId]
  }

  override fun dispose() = Unit

  fun track() {
    scope.launch(CoroutineName("Git Commit Signature Tracker - ${table.id}")) {
      val trackedEvents =
        combine(table.modelChangedFlow(), table.columnVisibilityFlow(column)) { _, isColumnVisible -> isColumnVisible }

      trackedEvents.collectLatest { isColumnVisible ->
        if (!isColumnVisible) return@collectLatest
        update()
      }
    }
  }

  private suspend fun update() =
    try {
      doUpdate()
    }
    catch (e: VcsException) {
      LOG.warn("Failed to load commit signatures", e)
    }

  @Throws(VcsException::class)
  private suspend fun doUpdate() {
    val size = min(TRACK_SIZE, table.rowCount)
    val rows = IntArray(size) { it }
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