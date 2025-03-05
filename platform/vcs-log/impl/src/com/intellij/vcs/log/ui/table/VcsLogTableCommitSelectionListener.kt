// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Condition
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

internal abstract class VcsLogTableCommitSelectionListener(
  private val graphTable: VcsLogGraphTable,
) : ListSelectionListener {
  private var lastEvent: ListSelectionEvent? = null

  final override fun valueChanged(event: ListSelectionEvent?) {
    if (event != null && event.valueIsAdjusting) return
    lastEvent = event
    onHandlingScheduled()
    ApplicationManager.getApplication().invokeLater(Runnable {
      val model = graphTable.model
      val commitIds = graphTable.selectedRows.asSequence().map(model::getId).filterNotNull().toList()
      handleSelection(commitIds)
    }, Condition { o: Any? -> lastEvent !== event })
  }

  protected abstract fun handleSelection(commitIds: List<Int>)

  protected open fun onHandlingScheduled() = Unit
}