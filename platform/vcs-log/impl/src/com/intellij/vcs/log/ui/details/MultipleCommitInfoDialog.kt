// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.details

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JList
import javax.swing.ScrollPaneConstants

@ApiStatus.Experimental
abstract class MultipleCommitInfoDialog(private val project: Project, commits: List<VcsCommitMetadata>)
  : DialogWrapper(project, true) {
  companion object {
    private const val DIALOG_WIDTH = 600
    private const val DIALOG_HEIGHT = 400
    @NonNls
    private const val DIMENSION_KEY = "Vcs.Multiple.Commit.Info.Dialog.Key"
    @NonNls
    private const val CHANGES_SPLITTER = "Vcs.Multiple.Commit.Info.Dialog.Changes.Splitter"
  }

  private val commitsList = JBList<VcsCommitMetadata>()
  private val modalityState = ModalityState.stateForComponent(window)
  private val fullCommitDetailsListPanel = object : FullCommitDetailsListPanel(project, disposable, modalityState) {
    @RequiresBackgroundThread
    @Throws(VcsException::class)
    override fun loadChanges(commits: List<VcsCommitMetadata>): List<Change> = this@MultipleCommitInfoDialog.loadChanges(commits)
  }

  init {
    commitsList.border = JBUI.Borders.emptyTop(10)
    val model = FilteringListModel(CollectionListModel(commits))
    commitsList.model = model
    resetFilter()
    commitsList.cellRenderer = object : ColoredListCellRenderer<VcsCommitMetadata>() {
      override fun customizeCellRenderer(
        list: JList<out VcsCommitMetadata>,
        value: VcsCommitMetadata?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
      ) {
        border = JBUI.Borders.empty()
        ipad = JBUI.insetsLeft(20)
        if (value != null) {
          append(value.subject)
          SpeedSearchUtil.applySpeedSearchHighlighting(commitsList, this, true, selected)
        }
      }
    }
    commitsList.selectionModel.addListSelectionListener { e ->
      if (e.valueIsAdjusting) {
        return@addListSelectionListener
      }
      val selectedCommits = commitsList.selectedIndices.map { model.getElementAt(it) }
      fullCommitDetailsListPanel.commitsSelected(selectedCommits)
    }
    installSpeedSearch()
    init()
  }

  final override fun init() {
    super.init()
  }

  override fun getDimensionServiceKey() = DIMENSION_KEY

  override fun getPreferredFocusedComponent() = commitsList

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  protected abstract fun loadChanges(commits: List<VcsCommitMetadata>): List<Change>

  fun setFilter(condition: (VcsCommitMetadata) -> Boolean) {
    val selectedCommits = commitsList.selectedValuesList.toSet()
    val model = commitsList.model as FilteringListModel<VcsCommitMetadata>
    model.setFilter(condition)


    val selectedIndicesAfterFilter = mutableListOf<Int>()
    for (index in 0 until model.size) {
      val commit = model.getElementAt(index)
      if (commit in selectedCommits) {
        selectedIndicesAfterFilter.add(index)
      }
    }
    if (selectedIndicesAfterFilter.isEmpty()) {
      commitsList.selectedIndex = 0
    }
    else {
      commitsList.selectedIndices = selectedIndicesAfterFilter.toIntArray()
    }
  }

  fun resetFilter() {
    setFilter { true }
  }

  private fun installSpeedSearch() {
    ListSpeedSearch(commitsList) { it.subject }
  }

  override fun createActions() = arrayOf(okAction)

  override fun getStyle() = DialogStyle.COMPACT

  override fun createCenterPanel() = BorderLayoutPanel().apply {
    preferredSize = JBDimension(DIALOG_WIDTH, DIALOG_HEIGHT)
    val commitInfoSplitter = OnePixelSplitter(CHANGES_SPLITTER, 0.5f)
    commitInfoSplitter.setHonorComponentsMinimumSize(false)
    val commitsListScrollPane = JBScrollPane(
      commitsList,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )
    commitsListScrollPane.border = JBUI.Borders.empty()
    commitInfoSplitter.firstComponent = commitsListScrollPane
    commitInfoSplitter.secondComponent = fullCommitDetailsListPanel
    addToCenter(commitInfoSplitter)
  }
}