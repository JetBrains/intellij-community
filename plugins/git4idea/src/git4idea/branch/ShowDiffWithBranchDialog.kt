// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.dvcs.ui.CompareBranchesDiffPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.UIUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.util.GitLocalCommitCompareInfo
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JComponent

private val LOG = logger<ShowDiffWithBranchDialog>()

internal class ShowDiffWithBranchDialog(val project: Project,
                                        val branchName: String,
                                        val repositories: List<GitRepository>,
                                        val currentBranchName: String) : DialogWrapper(project, true) {

  init {
    isModal = false
    title = "Show Diff Between $branchName and Current Working Tree"
    setOKButtonText("Close")
    init()
  }

  private lateinit var mainPanel : CompareBranchesDiffPanel

  override fun createCenterPanel(): JComponent? {
    mainPanel = CompareBranchesDiffPanel(project, GitVcsSettings.getInstance(project), branchName, currentBranchName)
    UIUtil.setEnabled(mainPanel, false, true)

    val loadingPanel = JBLoadingPanel(BorderLayout(), disposable).apply {
      startLoading()
      add(mainPanel)
    }
    mainPanel.setEmptyText("")

    val modalityState = ModalityState.stateForComponent(rootPane)
    ApplicationManager.getApplication().executeOnPooledThread {
      val result = loadDiff()

      runInEdt(modalityState) {
        if (!isDisposed) {
          loadingPanel.stopLoading()

          when (result) {
            is LoadingResult.Success -> {
              mainPanel.setCompareInfo(result.compareInfo)
              mainPanel.setEmptyText("No differences")
              UIUtil.setEnabled(mainPanel, true, true)
            }
            is LoadingResult.Error -> Messages.showErrorDialog(rootPane, result.error)
          }
        }
      }
    }

    return loadingPanel
  }

  override fun createActions(): Array<Action> = arrayOf(okAction)

  override fun getDimensionServiceKey(): String = "ShowDiffWithBranchDialog"

  override fun getPreferredFocusedComponent(): JComponent = mainPanel

  private fun loadDiff() : LoadingResult {
    try {
      val compareInfo = GitLocalCommitCompareInfo(project, branchName)
      for (repository in repositories) {
        compareInfo.putTotalDiff(repository, GitBranchWorker.loadTotalDiff(repository, branchName))
      }
      return LoadingResult.Success(compareInfo)
    }
    catch (e: Exception) {
      LOG.warn(e)
      return LoadingResult.Error("Couldn't load diff with $branchName: ${e.message}")
    }
  }

  private sealed class LoadingResult {
    class Success(val compareInfo: GitLocalCommitCompareInfo): LoadingResult()
    class Error(val error: String) : LoadingResult()
  }
}