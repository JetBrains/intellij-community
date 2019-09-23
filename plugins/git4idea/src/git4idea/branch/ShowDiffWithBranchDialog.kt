// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.dvcs.ui.CompareBranchesDiffPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.util.GitLocalCommitCompareInfo
import java.awt.BorderLayout
import javax.swing.JComponent

private val LOG = logger<ShowDiffWithBranchDialog>()

internal class ShowDiffWithBranchDialog(val project: Project,
                                        val branchName: String,
                                        val repositories: List<GitRepository>,
                                        val currentBranchName: String
) : FrameWrapper(project, "ShowDiffWithBranchDialog") {

  private var diffPanel : CompareBranchesDiffPanel
  private val loadingPanel: JBLoadingPanel

  init {
    setTitle("Show Diff Between $branchName and Current Working Tree")
    closeOnEsc()

    diffPanel = CompareBranchesDiffPanel(project, GitVcsSettings.getInstance(project), branchName, currentBranchName)
    diffPanel.disableControls()
    diffPanel.setEmptyText("")

    loadingPanel = JBLoadingPanel(BorderLayout(), this).apply {
      startLoading()
      add(diffPanel)
    }

    val rootPanel = JBUI.Panels.simplePanel()
    rootPanel.addToCenter(loadingPanel)
    rootPanel.border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)
    setComponent(rootPanel)
  }

  override fun show() {
    super.show()

    val modalityState = ModalityState.stateForComponent(diffPanel)
    ApplicationManager.getApplication().executeOnPooledThread {
      val result = loadDiff()

      runInEdt(modalityState) {
        if (!isDisposed) {
          loadingPanel.stopLoading()

          when (result) {
            is LoadingResult.Success -> {
              diffPanel.setCompareInfo(result.compareInfo)
              diffPanel.setEmptyText("No differences")
              diffPanel.enableControls()
            }
            is LoadingResult.Error -> Messages.showErrorDialog(diffPanel, result.error)
          }
        }
      }
    }
  }

  override fun getPreferredFocusedComponent(): JComponent = diffPanel.preferredFocusComponent

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