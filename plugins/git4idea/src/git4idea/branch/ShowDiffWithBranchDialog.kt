// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.dvcs.ui.CompareBranchesDiffPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLoadingPanel
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.util.GitLocalCommitCompareInfo
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JComponent

internal class ShowDiffWithBranchDialog(val project: Project,
                                        val branchName: String,
                                        val repositories: MutableList<GitRepository>,
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

    val loadingPanel = JBLoadingPanel(BorderLayout(), disposable).apply {
      startLoading()
      add(mainPanel)
    }

    val modalityState = ModalityState.stateForComponent(mainPanel)
    ApplicationManager.getApplication().executeOnPooledThread {
      val compareInfo = GitLocalCommitCompareInfo(project, branchName)
      for (repository in repositories) {
        compareInfo.putTotalDiff(repository, GitBranchWorker.loadTotalDiff(repository, branchName))
      }

      runInEdt(modalityState) {
        if (!isDisposed) {
          mainPanel.setCompareInfo(compareInfo)
          loadingPanel.stopLoading()
        }
      }
    }

    return loadingPanel
  }

  override fun createActions(): Array<Action> = arrayOf(okAction)

  override fun getDimensionServiceKey(): String = "ShowDiffWithBranchDialog"

  override fun getPreferredFocusedComponent(): JComponent = mainPanel

}