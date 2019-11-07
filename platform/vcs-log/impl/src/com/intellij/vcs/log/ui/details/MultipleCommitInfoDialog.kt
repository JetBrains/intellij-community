// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.Consumer
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.SingleTaskController
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInBackground
import java.awt.BorderLayout
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.border.Border

@ApiStatus.Experimental
abstract class MultipleCommitInfoDialog(private val project: Project, commits: List<VcsCommitMetadata>)
  : DialogWrapper(project, true) {
  companion object {
    private const val DIALOG_WIDTH = 600
    private const val DIALOG_HEIGHT = 400
    private const val DIMENSION_KEY = "Vcs.Multiple.Commit.Info.Dialog.Key"
    private const val CHANGES_SPLITTER = "Vcs.Multiple.Commit.Info.Dialog.Changes.Splitter"
    private const val DETAILS_SPLITTER = "Vcs.Multiple.Commit.Info.Dialog.Details.Splitter"
  }

  private val commitsList = JBList<VcsCommitMetadata>()
  private val changesBrowserWithLoadingPanel = ChangesBrowserWithLoadingPanel(project, disposable)
  private val modalityState = ModalityState.stateForComponent(window)
  private val changesLoadingController = ChangesLoadingController(project, disposable, modalityState, changesBrowserWithLoadingPanel,
                                                                  ::loadChanges)
  private val commitDetails = object : CommitDetailsListPanel<CommitDetailsPanel>(myDisposable) {
    init {
      border = JBUI.Borders.empty()
    }

    override fun getCommitDetailsPanel() = CommitDetailsPanel(project) {}
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
      commitDetails.setCommits(selectedCommits)
      changesLoadingController.request(selectedCommits)
    }
    installSpeedSearch()
    init()
  }

  final override fun init() {
    super.init()
  }

  override fun getDimensionServiceKey() = DIMENSION_KEY

  override fun getPreferredFocusedComponent() = commitsList

  @CalledInBackground
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
    ListSpeedSearch<VcsCommitMetadata>(commitsList) { it.subject }
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
    val detailsSplitter = OnePixelSplitter(true, DETAILS_SPLITTER, 0.67f)
    detailsSplitter.firstComponent = changesBrowserWithLoadingPanel
    detailsSplitter.secondComponent = commitDetails
    commitInfoSplitter.secondComponent = detailsSplitter
    addToCenter(commitInfoSplitter)
  }
}

private class ChangesBrowserWithLoadingPanel(project: Project, disposable: Disposable) : JPanel(BorderLayout()) {
  private val changesBrowser = object : SimpleChangesBrowser(project, false, false) {
    override fun createViewerBorder(): Border {
      return IdeBorderFactory.createBorder(SideBorder.TOP)
    }
  }

  private val changesBrowserLoadingPanel =
    JBLoadingPanel(BorderLayout(), disposable, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
      add(changesBrowser, BorderLayout.CENTER)
    }

  init {
    add(changesBrowserLoadingPanel)
  }

  fun startLoading() {
    changesBrowserLoadingPanel.startLoading()
    changesBrowser.viewer.setEmptyText(DiffBundle.message("diff.count.differences.status.text", 0))
  }

  fun stopLoading(changes: List<Change>) {
    changesBrowserLoadingPanel.stopLoading()
    changesBrowser.setChangesToDisplay(changes)
  }

  fun setErrorText(error: String) {
    changesBrowser.setChangesToDisplay(emptyList())
    changesBrowser.viewer.emptyText.setText(error, SimpleTextAttributes.ERROR_ATTRIBUTES)
  }
}

private class ChangesLoadingController(
  private val project: Project,
  disposable: Disposable,
  private val modalityState: ModalityState,
  private val changesBrowser: ChangesBrowserWithLoadingPanel,
  private val loader: (List<VcsCommitMetadata>) -> List<Change>
) : SingleTaskController<List<VcsCommitMetadata>, List<Change>>(
  "Loading Commit Changes",
  Consumer { changes ->
    runInEdt(modalityState = modalityState) {
      changesBrowser.stopLoading(changes)
    }
  },
  disposable
) {
  override fun startNewBackgroundTask(): SingleTask {
    runInEdt(modalityState = modalityState) {
      changesBrowser.startLoading()
    }
    val task: Task.Backgroundable = object : Task.Backgroundable(project, "Loading Commit Changes") {
      override fun run(indicator: ProgressIndicator) {
        var result: List<Change>? = null
        try {
          val request = popRequest() ?: return
          result = loader(request)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: VcsException) {
          runInEdt(modalityState = modalityState) {
            changesBrowser.setErrorText(e.message)
          }
        }
        finally {
          taskCompleted(result ?: emptyList())
        }
      }
    }
    val indicator = EmptyProgressIndicator()
    val future = (ProgressManager.getInstance() as CoreProgressManager).runProcessWithProgressAsynchronously(task, indicator, null)
    return SingleTaskImpl(future, indicator)
  }

  override fun cancelRunningTasks(requests: Array<out List<VcsCommitMetadata>>) = true
}