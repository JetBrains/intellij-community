// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitCommit
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewThreadsProvider
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.border.Border
import javax.swing.tree.DefaultTreeModel

internal class GHPRChangesBrowser(private val model: SingleValueModel<List<GitCommit>?>,
                                  project: Project,
                                  private val projectUiSettings: GithubPullRequestsProjectUISettings)
  : ChangesBrowserBase(project, false, false),
    ComponentWithEmptyText,
    Disposable {

  var diffReviewThreadsProvider: GHPRDiffReviewThreadsProvider? = null

  init {
    projectUiSettings.addChangesListener(this) {
      myViewer.rebuildTree()
    }
    init()
    model.addValueChangedListener {
      myViewer.rebuildTree()
    }
  }

  override fun updateDiffContext(chain: DiffRequestChain) {
    super.updateDiffContext(chain)
    if (projectUiSettings.zipChanges) {
      chain.putUserData(GHPRDiffReviewThreadsProvider.KEY, diffReviewThreadsProvider)
    }
    else {
      //TODO: commits comments provider
    }
  }

  override fun getEmptyText() = myViewer.emptyText

  override fun createViewerBorder(): Border = IdeBorderFactory.createBorder(SideBorder.TOP)

  override fun buildTreeModel(): DefaultTreeModel {
    val commits = model.value.orEmpty()
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

  override fun dispose() {
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
      e.presentation.isEnabledAndVisible = e.getData(DATA_KEY) is GHPRChangesBrowser
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
}