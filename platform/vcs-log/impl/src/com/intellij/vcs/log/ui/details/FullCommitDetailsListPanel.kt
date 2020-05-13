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
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.SingleTaskController
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.border.Border

@ApiStatus.Experimental
abstract class FullCommitDetailsListPanel(
  project: Project,
  parent: Disposable,
  modalityState: ModalityState
) : BorderLayoutPanel() {
  companion object {
    private const val DETAILS_SPLITTER = "Full.Commit.Details.List.Splitter" // NON-NLS
  }

  private val changesBrowserWithLoadingPanel = ChangesBrowserWithLoadingPanel(project, parent)
  private val changesLoadingController = ChangesLoadingController(project, parent, modalityState, changesBrowserWithLoadingPanel,
                                                                  ::loadChanges)
  private val commitDetails = object : CommitDetailsListPanel<CommitDetailsPanel>(parent) {
    init {
      border = JBUI.Borders.empty()
    }

    override fun getCommitDetailsPanel() = CommitDetailsPanel(project) {}
  }

  init {
    val detailsSplitter = OnePixelSplitter(true, DETAILS_SPLITTER, 0.67f)
    detailsSplitter.firstComponent = changesBrowserWithLoadingPanel
    detailsSplitter.secondComponent = commitDetails
    addToCenter(detailsSplitter)
  }

  fun commitsSelected(selectedCommits: List<VcsCommitMetadata>) {
    commitDetails.setCommits(selectedCommits)
    changesLoadingController.request(selectedCommits)
  }

  @CalledInBackground
  @Throws(VcsException::class)
  protected abstract fun loadChanges(commits: List<VcsCommitMetadata>): List<Change>
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
  VcsLogBundle.message("loading.commit.changes"),
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
    val task: Task.Backgroundable = object : Task.Backgroundable(project, VcsLogBundle.message("loading.commit.changes")) {
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

  override fun cancelRunningTasks(requests: List<List<VcsCommitMetadata>>) = true
}