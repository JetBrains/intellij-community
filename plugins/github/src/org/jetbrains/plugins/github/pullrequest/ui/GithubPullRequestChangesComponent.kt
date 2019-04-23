// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.vcs.log.ui.frame.ProgressStripe
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitCommit
import org.jetbrains.plugins.github.pullrequest.comment.GithubPullRequestDiffCommentsProvider
import org.jetbrains.plugins.github.pullrequest.comment.GithubPullRequestFilesDiffCommentsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import java.awt.BorderLayout
import javax.swing.border.Border
import javax.swing.tree.DefaultTreeModel
import kotlin.properties.Delegates

internal class GithubPullRequestChangesComponent(private val project: Project,
                                                 private val projectUiSettings: GithubPullRequestsProjectUISettings,
                                                 private val diffCommentComponentFactory: GithubPullRequestEditorCommentsThreadComponentFactory)
  : GithubDataLoadingComponent<List<GitCommit>>(), Disposable {

  private val changesBrowser = PullRequestChangesBrowserWithError()
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
  private val backgroundLoadingPanel = ProgressStripe(loadingPanel, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  val diffAction = changesBrowser.diffAction

  init {
    loadingPanel.add(changesBrowser, BorderLayout.CENTER)
    changesBrowser.emptyText.text = DEFAULT_EMPTY_TEXT
    setContent(loadingPanel)
    Disposer.register(this, changesBrowser)
  }

  override fun extractRequest(provider: GithubPullRequestDataProvider) = provider.logCommitsRequest

  override fun resetUI() {
    changesBrowser.emptyText.text = DEFAULT_EMPTY_TEXT
    changesBrowser.commits = emptyList()
  }

  override fun handleResult(result: List<GitCommit>) {
    changesBrowser.emptyText.text = "Pull request does not contain any changes"
    changesBrowser.commits = result
  }

  override fun handleError(error: Throwable) {
    changesBrowser.emptyText
      .clear()
      .appendText("Can't load changes", SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
  }

  override fun setBusy(busy: Boolean) {
    if (busy) {
      if (changesBrowser.commits.isNullOrEmpty()) {
        changesBrowser.emptyText.clear()
        loadingPanel.startLoading()
      }
      else {
        backgroundLoadingPanel.startLoading()
      }
    }
    else {
      loadingPanel.stopLoading()
      backgroundLoadingPanel.stopLoading()
    }
  }

  override fun dispose() {}

  internal inner class PullRequestChangesBrowserWithError
    : ChangesBrowserBase(project, false, false), ComponentWithEmptyText, Disposable {

    var commits: List<GitCommit> by Delegates.observable(listOf()) { _, _, _ ->
      myViewer.rebuildTree()
    }

    init {
      projectUiSettings.addChangesListener(this) {
        myViewer.rebuildTree()
      }
      init()
    }

    override fun updateDiffContext(chain: DiffRequestChain) {
      super.updateDiffContext(chain)
      if (projectUiSettings.zipChanges) {
        chain.putUserData(GithubPullRequestDiffCommentsProvider.KEY,
                          GithubPullRequestFilesDiffCommentsProvider(dataProvider!!, diffCommentComponentFactory))
      }
      else {
        //TODO: commits comments provider
      }
    }

    override fun getEmptyText() = myViewer.emptyText

    override fun createViewerBorder(): Border = IdeBorderFactory.createBorder(SideBorder.TOP)

    override fun buildTreeModel(): DefaultTreeModel {
      val builder = MyTreeModelBuilder(myProject, grouping)
      if (projectUiSettings.zipChanges) {
        val zipped = CommittedChangesTreeBrowser.zipChanges(commits.flatMap { it.changes })
        builder.setChanges(zipped, null)
      }
      else {
        for (commit in commits) {
          builder.addCommit(commit)
        }
      }
      return builder.build()
    }

    override fun dispose() {}
  }

  private class MyTreeModelBuilder(project: Project, grouping: ChangesGroupingPolicyFactory) : TreeModelBuilder(project, grouping) {
    fun addCommit(commit: GitCommit) {
      val parentNode = CommitTagBrowserNode(commit)
      parentNode.markAsHelperNode()

      myModel.insertNodeInto(parentNode, myRoot, myRoot.childCount)
      for (change in commit.changes) {
        insertChangeNode(change, parentNode, createChangeNode(change, null))
      }
    }
  }

  private class CommitTagBrowserNode(val commit: GitCommit) : ChangesBrowserNode<Any>(commit) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.icon = AllIcons.Vcs.CommitNode
      renderer.append(commit.subject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      renderer.append(" by ${commit.author.name} on ${DateFormatUtil.formatDate(commit.authorTime)}",
                      SimpleTextAttributes.GRAYED_ATTRIBUTES)

      val tooltip = "commit ${commit.id.asString()}\n" +
                    "Author: ${commit.author.name}\n" +
                    "Date: ${DateFormatUtil.formatDateTime(commit.authorTime)}\n\n" +
                    commit.fullMessage
      renderer.toolTipText = XmlStringUtil.escapeString(tooltip)
    }

    override fun getTextPresentation(): String {
      return commit.subject
    }
  }

  class ToggleZipCommitsAction : ToggleAction("Commit"), DumbAware {

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = e.getData(ChangesBrowserBase.DATA_KEY) is PullRequestChangesBrowserWithError
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val project = e.project ?: return false
      return !GithubPullRequestsProjectUISettings.getInstance(project).zipChanges
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return
      GithubPullRequestsProjectUISettings.getInstance(project).zipChanges = !state
    }
  }

  companion object {
    //language=HTML
    private const val DEFAULT_EMPTY_TEXT = "Select pull request to view list of changed files"
  }
}