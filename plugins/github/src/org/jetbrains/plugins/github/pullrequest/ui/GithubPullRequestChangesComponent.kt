// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.ComponentWithEmptyText
import java.awt.BorderLayout
import javax.swing.border.Border
import kotlin.properties.Delegates

internal class GithubPullRequestChangesComponent(project: Project) : GithubDataLoadingComponent<List<Change>>(), Disposable {

  private val changesBrowser = PullRequestChangesBrowserWithError(project)
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  val diffAction = changesBrowser.diffAction

  init {
    loadingPanel.add(changesBrowser, BorderLayout.CENTER)
    changesBrowser.emptyText.text = DEFAULT_EMPTY_TEXT
    setContent(loadingPanel)
  }

  override fun reset() {
    changesBrowser.emptyText.text = DEFAULT_EMPTY_TEXT
    changesBrowser.changes = emptyList()
  }

  override fun handleResult(result: List<Change>) {
    changesBrowser.emptyText.text = "Pull request does not contain any changes"
    changesBrowser.changes = result
  }

  override fun handleError(error: Throwable) {
    changesBrowser.emptyText
      .clear()
      .appendText("Cannot load changes", SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
  }

  override fun setBusy(busy: Boolean) {
    if (busy) {
      changesBrowser.emptyText.clear()
      loadingPanel.startLoading()
    }
    else {
      loadingPanel.stopLoading()
    }
  }

  override fun dispose() {}

  companion object {
    //language=HTML
    private const val DEFAULT_EMPTY_TEXT = "Select pull request to view list of changed files"

    private class PullRequestChangesBrowserWithError(project: Project)
      : ChangesBrowserBase(project, false, false), ComponentWithEmptyText {

      var changes: List<Change> by Delegates.observable(listOf()) { _, _, _ ->
        myViewer.rebuildTree()
      }

      init {
        init()
      }

      override fun buildTreeModel() = TreeModelBuilder.buildFromChanges(myProject, grouping, changes, null)

      override fun onDoubleClick() {
        if (canShowDiff()) super.onDoubleClick()
      }

      override fun getEmptyText() = myViewer.emptyText

      override fun createViewerBorder(): Border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
  }
}