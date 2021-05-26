// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import org.jetbrains.plugins.github.api.data.GHGitActor
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

internal class GHPRCommitsBrowserComponentFactory(private val project: Project) {

  fun create(commitsModel: SingleValueModel<List<VcsCommitMetadata>>, onCommitSelected: (VcsCommitMetadata?) -> Unit): JComponent {
    val commitsListModel = CollectionListModel(commitsModel.value)

    val actionManager = ActionManager.getInstance()
    val commitsList = JBList(commitsListModel).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      val renderer = GHPRCommitsListCellRenderer()
      cellRenderer = renderer
      UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer.panel))
      emptyText.text = GithubBundle.message("pull.request.does.not.contain.commits")
    }.also {
      ScrollingUtil.installActions(it)
      ListUiUtil.Selection.installSelectionOnFocus(it)
      ListUiUtil.Selection.installSelectionOnRightClick(it)
      PopupHandler.installSelectionListPopup(it,
                                             DefaultActionGroup(actionManager.getAction("Github.PullRequest.Changes.Reload")),
                                             ActionPlaces.UNKNOWN, actionManager)
      ListSpeedSearch(it) { commit -> commit.subject }
    }

    commitsModel.addAndInvokeValueChangedListener {
      val currentList = commitsListModel.toList()
      val newList = commitsModel.value
      if (currentList != newList) {
        val selectedCommit = commitsList.selectedValue
        commitsListModel.replaceAll(newList)
        commitsList.setSelectedValue(selectedCommit, true)
      }
    }

    val commitDetailsModel = SingleValueModel<VcsCommitMetadata?>(null)
    val commitDetailsComponent = createCommitDetailsComponent(commitDetailsModel)

    commitsList.addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener
      onCommitSelected(commitsList.selectedValue)
    }

    val commitsScrollPane = ScrollPaneFactory.createScrollPane(commitsList, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    val commitsBrowser = OnePixelSplitter(true, "Github.PullRequest.Commits.Browser", 0.7f).apply {
      firstComponent = commitsScrollPane
      secondComponent = commitDetailsComponent

      UIUtil.putClientProperty(this, COMMITS_LIST_KEY, commitsList)
    }

    commitsList.addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener

      val index = commitsList.selectedIndex
      commitDetailsModel.value = if (index != -1) commitsListModel.getElementAt(index) else null
      commitsBrowser.validate()
      commitsBrowser.repaint()
      if (index != -1) ScrollingUtil.ensureRangeIsVisible(commitsList, index, index)
    }

    return commitsBrowser
  }

  private fun createCommitDetailsComponent(model: SingleValueModel<VcsCommitMetadata?>): JComponent {

    val commitDetailsPanel = CommitDetailsPanel(project) {}
    val scrollpane = ScrollPaneFactory.createScrollPane(commitDetailsPanel, true).apply {
      isVisible = false
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    model.addAndInvokeValueChangedListener {
      val commit = model.value
      if (commit != null) {
        val hashAndAuthor = CommitPresentationUtil.formatCommitHashAndAuthor(commit.id, commit.author, commit.authorTime, commit.committer,
                                                                             commit.commitTime)

        val presentation = object : CommitPresentationUtil.CommitPresentation(project, commit.root, commit.fullMessage, hashAndAuthor,
                                                                              MultiMap.empty()) {
          override fun getText(): String {
            val separator = myRawMessage.indexOf("\n\n")
            val subject = if (separator > 0) myRawMessage.substring(0, separator) else myRawMessage
            val description = myRawMessage.substring(subject.length)
            if (subject.contains("\n")) {
              // subject has new lines => that is not a subject
              return myRawMessage
            }

            return """<b>$subject</b><br/><br/>$description"""
          }
        }
        commitDetailsPanel.setCommit(CommitId(commit.id, commit.root), presentation)
      }
      scrollpane.isVisible = commit != null
    }
    return scrollpane
  }

  companion object {
    val COMMITS_LIST_KEY = Key.create<JList<VcsCommitMetadata>>("COMMITS_LIST")
  }
}