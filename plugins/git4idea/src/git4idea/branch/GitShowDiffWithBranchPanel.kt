// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.dvcs.ui.CompareBranchesDiffPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.impl.ChangesBrowserToolWindow.createDiffPreview
import com.intellij.openapi.vcs.impl.ChangesBrowserToolWindow.showTab
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.content.ContentFactory
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.GitLocalCommitCompareInfo
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout

private val LOG = logger<GitShowDiffWithBranchPanel>()

class GitShowDiffWithBranchPanel(val project: Project,
                                 val branchName: String,
                                 val repositories: List<GitRepository>,
                                 val currentBranchName: String) {
  private val disposable: Disposable = Disposer.newDisposable()

  private val diffPanel: CompareBranchesDiffPanel
  private val loadingPanel: JBLoadingPanel

  init {
    diffPanel = CompareBranchesDiffPanel(project, GitVcsSettings.getInstance(project), branchName, currentBranchName)
    diffPanel.changesBrowser.hideViewerBorder()
    diffPanel.disableControls()
    diffPanel.setEmptyText("")

    val changesBrowser = diffPanel.changesBrowser
    val diffPreview = createDiffPreview(project, changesBrowser, disposable)
    changesBrowser.setShowDiffActionPreview(diffPreview)

    loadingPanel = JBLoadingPanel(BorderLayout(), disposable).apply {
      startLoading()
      add(diffPanel)
    }

    loadDiffInBackground()
  }

  fun showAsTab() {
    val title = GitBundle.message("show.diff.between.dialog.title", branchName)
    val content = ContentFactory.SERVICE.getInstance().createContent(loadingPanel, title, false)
    content.preferredFocusableComponent = diffPanel.preferredFocusComponent
    content.setDisposer(disposable)
    showTab(project, content)
  }

  private fun loadDiffInBackground() {
    ApplicationManager.getApplication().executeOnPooledThread {
      val result = loadDiff()
      runInEdt {
        showDiff(result)
      }
    }
  }

  private fun loadDiff(): LoadingResult {
    try {
      val compareInfo = GitLocalCommitCompareInfo(project, branchName)
      for (repository in repositories) {
        compareInfo.putTotalDiff(repository, GitBranchWorker.loadTotalDiff(repository, branchName))
      }
      return LoadingResult.Success(compareInfo)
    }
    catch (e: Exception) {
      LOG.warn(e)
      return LoadingResult.Error(GitBundle.message("show.diff.between.dialog.could.not.load.diff.with.branch.error", branchName, e.message))
    }
  }

  private fun showDiff(result: LoadingResult) {
    if (Disposer.isDisposed(disposable)) return
    loadingPanel.stopLoading()

    when (result) {
      is LoadingResult.Success -> {
        diffPanel.setCompareInfo(result.compareInfo)
        diffPanel.setEmptyText(GitBundle.message("show.diff.between.dialog.no.differences.empty.text"))
        diffPanel.enableControls()
      }
      is LoadingResult.Error -> Messages.showErrorDialog(diffPanel, result.error)
    }
  }

  private sealed class LoadingResult {
    class Success(val compareInfo: GitLocalCommitCompareInfo) : LoadingResult()
    class Error(@Nls val error: String) : LoadingResult()
  }
}