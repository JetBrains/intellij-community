// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.details.commit.getCommitDetailsBackground
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHGitActor
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCommitsListCellRenderer
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

internal object GHPRCommitsBrowserComponent {

  val COMMITS_LIST_KEY = Key.create<JList<GHCommit>>("COMMITS_LIST")

  fun create(commitsModel: SingleValueModel<List<GHCommit>>, onCommitSelected: (GHCommit?) -> Unit): JComponent {
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
      GithubUIUtil.Lists.installSelectionOnFocus(it)
      GithubUIUtil.Lists.installSelectionOnRightClick(it)
      PopupHandler.installSelectionListPopup(it,
                                             DefaultActionGroup(actionManager.getAction("Github.PullRequest.Changes.Reload")),
                                             ActionPlaces.UNKNOWN, actionManager)
      ListSpeedSearch(it) { commit -> commit.messageHeadlineHTML }
    }

    commitsModel.addValueChangedListener {
      val currentList = commitsListModel.toList()
      val newList = commitsModel.value
      if (currentList != newList) {
        val selectedCommit = commitsList.selectedValue
        commitsListModel.replaceAll(newList)
        commitsList.setSelectedValue(selectedCommit, true)
      }
    }

    val commitDetailsModel = SingleValueModel<GHCommit?>(null)
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

  private fun createCommitDetailsComponent(model: SingleValueModel<GHCommit?>): JComponent {
    val messagePane = HtmlEditorPane().apply {
      font = FontUtil.getCommitMessageFont()
    }
    //TODO: show avatar
    val hashAndAuthorPane = HtmlEditorPane().apply {
      font = FontUtil.getCommitMetadataFont()
    }

    val commitDetailsPanel = ScrollablePanel(VerticalLayout(UI.scale(CommitDetailsPanel.INTERNAL_BORDER))).apply {
      border = JBUI.Borders.empty(CommitDetailsPanel.EXTERNAL_BORDER, CommitDetailsPanel.SIDE_BORDER)
      background = getCommitDetailsBackground()

      add(messagePane, VerticalLayout.FILL_HORIZONTAL)
      add(hashAndAuthorPane, VerticalLayout.FILL_HORIZONTAL)
    }
    val commitDetailsScrollPane = ScrollPaneFactory.createScrollPane(commitDetailsPanel, true).apply {
      isVisible = false
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    model.addAndInvokeValueChangedListener {
      val commit = model.value

      if (commit == null) {
        messagePane.setBody("")
        hashAndAuthorPane.setBody("")
        commitDetailsScrollPane.isVisible = false
      }
      else {
        val subject = "<b>${commit.messageHeadlineHTML}</b>"
        val body = commit.messageBodyHTML
        val fullMessage = if (body.isNotEmpty()) "$subject<br><br>$body" else subject

        messagePane.setBody(fullMessage)
        hashAndAuthorPane.setBody(getHashAndAuthorText(commit.oid, commit.author, commit.committer))
        commitDetailsScrollPane.isVisible = true
        commitDetailsPanel.scrollRectToVisible(Rectangle(0, 0, 0, 0))
        invokeLater {
          // JDK bug - need to force height recalculation (see JBR-2256)
          messagePane.setSize(messagePane.width, Int.MAX_VALUE / 2)
          hashAndAuthorPane.setSize(hashAndAuthorPane.width, Int.MAX_VALUE / 2)
        }
      }
    }

    return commitDetailsScrollPane
  }

  private fun getHashAndAuthorText(hash: String, author: GHGitActor?, committer: GHGitActor?): String {
    val authorUser = createUser(author)
    val authorTime = author?.date?.time ?: 0L
    val committerUser = createUser(committer)
    val committerTime = committer?.date?.time ?: 0L

    return CommitPresentationUtil.formatCommitHashAndAuthor(HashImpl.build(hash), authorUser, authorTime, committerUser, committerTime)
  }

  private val unknownUser = VcsUserImpl("unknown user", "")

  private fun createUser(actor: GHGitActor?): VcsUser {
    val name = actor?.name
    val email = actor?.email
    return if (name != null && email != null) {
      VcsUserImpl(name, email)
    }
    else unknownUser
  }
}