// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Condition
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.DataGetter
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

@ApiStatus.Internal
@Deprecated("Unused, see VcsLogTableCommitSelectionListener")
abstract class CommitSelectionListener<T : VcsCommitMetadata?> protected constructor(
  protected val graphTable: VcsLogGraphTable,
  private val commitDetailsGetter: DataGetter<out T?>,
) : ListSelectionListener {
  private var lastEvent: ListSelectionEvent? = null
  private var lastRequest: ProgressIndicator? = null

  override fun valueChanged(event: ListSelectionEvent?) {
    if (event != null && event.valueIsAdjusting) return

    lastEvent = event
    if (lastRequest != null) lastRequest!!.cancel()
    lastRequest = null

    ApplicationManager.getApplication().invokeLater(Runnable { processEvent() }, Condition { o: Any? -> lastEvent !== event })
    onLoadingScheduled()
  }

  private fun processEvent() {
    val rows = graphTable.selectedRowCount
    if (rows < 1) {
      onLoadingStopped()
      onEmptySelection()
    }
    else {
      val toLoad = onSelection(graphTable.selectedRows)
      onLoadingStarted()

      val indicator = EmptyProgressIndicator()
      lastRequest = indicator

      val model = graphTable.model
      val commitIds = toLoad.asSequence().map(model::getId).filterNotNull().toList()
      commitDetailsGetter.loadCommitsData(commitIds, Consumer { detailsList: List<T> ->
        if (lastRequest === indicator && !indicator.isCanceled) {
          if (toLoad.size != detailsList.size) {
            LOG.error("Loaded incorrect number of details " + detailsList + " for selection " + toLoad.contentToString())
          }
          lastRequest = null
          onDetailsLoaded(commitIds, detailsList)
          onLoadingStopped()
        }
      }, Consumer { t: Throwable ->
        if (lastRequest === indicator && !indicator.isCanceled) {
          lastRequest = null
          LOG.error("Error loading details for selection " + toLoad.contentToString(), t)
          onError(t)
          onLoadingStopped()
        }
      }, indicator)
    }
  }

  @RequiresEdt
  protected open fun onLoadingScheduled() {
  }

  @RequiresEdt
  protected abstract fun onLoadingStarted()

  @RequiresEdt
  protected abstract fun onLoadingStopped()

  @RequiresEdt
  protected abstract fun onError(error: Throwable)

  @RequiresEdt
  protected open fun onDetailsLoaded(commitsIds: List<Int>, detailsList: List<T>) {
    onDetailsLoaded(detailsList)
  }

  @RequiresEdt
  @Deprecated("onDetailsLoaded with additional parameter is preferred")
  protected open fun onDetailsLoaded(detailsList: List<T>) {}

  @RequiresEdt
  protected abstract fun onSelection(selection: IntArray): IntArray

  @RequiresEdt
  protected abstract fun onEmptySelection()

  companion object {
    private val LOG = Logger.getInstance(CommitSelectionListener::class.java)
  }
}