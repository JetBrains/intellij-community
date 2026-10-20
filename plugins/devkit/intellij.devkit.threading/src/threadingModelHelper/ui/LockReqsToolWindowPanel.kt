// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import com.intellij.devkit.threading.DevkitThreadingBundle
import com.intellij.devkit.threading.threadingModelHelper.LockReqsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import java.awt.CardLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

private const val NO_RESULT_CARD = "noResult"
private const val RESULT_CARD = "result"

/**
 * The content of the lock requirements tool window.
 * It shows the latest [AnalysisResult] of [LockReqsService] as a filterable list of paths and the details of the selected path.
 */
internal class LockReqsToolWindowPanel(project: Project, parentDisposable: Disposable) : JPanel(CardLayout()) {

  private var state = LockReqsViewState()
  private var shownResult: AnalysisResult? = null

  private val listModel = CollectionListModel<ExecutionPath>()

  private val list = JBList(listModel).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = createPathListRenderer()
  }

  private val details = PathDetailsPanel(project, parentDisposable)

  private val header = LockReqsHeader(
    onQueryChanged = { query -> updateState(state.copy(query = query)) },
    onSelectedTypesChanged = { types -> updateState(state.copy(selectedTypes = types)) },
  )

  init {
    list.addListSelectionListener { e ->
      if (!e.valueIsAdjusting) {
        details.showPath(list.selectedValue)
      }
    }
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        if (list.selectedValue == null) return false
        details.navigateToLockCheck()
        return true
      }
    }.installOn(list)

    val splitter = OnePixelSplitter(false, 0.45f).apply {
      firstComponent = ScrollPaneFactory.createScrollPane(list, true)
      secondComponent = details
    }
    val resultPanel = BorderLayoutPanel()
      .addToTop(header.component)
      .addToCenter(splitter)

    val noResultPanel = JBPanelWithEmptyText().apply {
      emptyText
        .setText(DevkitThreadingBundle.message("empty.text.no.analysis"))
        .appendLine(DevkitThreadingBundle.message("empty.text.no.analysis.hint"))
    }

    add(noResultPanel, NO_RESULT_CARD)
    add(resultPanel, RESULT_CARD)
    showResult(null)

    val service = project.service<LockReqsService>()
    launchOnShow("LockReqsToolWindowPanel") {
      service.currentResult.collect { result -> showResult(result) }
    }
  }

  private fun showResult(result: AnalysisResult?) {
    if (result != null && result === shownResult) return
    shownResult = result
    (layout as CardLayout).show(this, if (result == null) NO_RESULT_CARD else RESULT_CARD)
    updateState(state.copy(allPaths = result?.paths?.toList() ?: emptyList()))
  }

  private fun updateState(newState: LockReqsViewState) {
    state = newState
    val paths = newState.filteredPaths
    header.setPathsCount(paths.size)
    updateEmptyText()
    if (paths == listModel.items) return

    val selected = list.selectedValue
    listModel.replaceAll(paths)
    if (selected != null && selected in paths) {
      list.setSelectedValue(selected, false)
    }
  }

  private fun updateEmptyText() {
    if (state.allPaths.isEmpty()) {
      list.emptyText
        .setText(DevkitThreadingBundle.message("empty.text.no.paths"))
        .appendLine(DevkitThreadingBundle.message("empty.text.no.paths.hint"))
    }
    else {
      list.emptyText.setText(DevkitThreadingBundle.message("empty.text.no.matching.paths"))
    }
  }
}
