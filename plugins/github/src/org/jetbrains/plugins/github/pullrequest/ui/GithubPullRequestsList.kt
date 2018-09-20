// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*

internal class GithubPullRequestsList(avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                      model: ListModel<GithubSearchedIssue>)
  : JBList<GithubSearchedIssue>(model), Disposable {

  private val avatarIconSize = JBValue.UIInteger("Github.PullRequests.List.Assignee.Avatar.Size", 20)
  private val avatarIconsProvider = avatarIconsProviderFactory.create(avatarIconSize, this)

  init {
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

    val renderer = PullRequestsListCellRenderer()
    cellRenderer = renderer
    UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

    ScrollingUtil.installActions(this)
    Disposer.register(this, avatarIconsProvider)
  }

  override fun dispose() {}

  private inner class PullRequestsListCellRenderer : ListCellRenderer<GithubSearchedIssue>, JPanel() {

    private val stateIcon = JLabel()
    private val title = JLabel()
    private val info = JLabel()
    private val labels = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
    private val assignees = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
    }

    init {
      border = JBUI.Borders.empty(5, 8)

      layout = MigLayout(LC().debug()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX())

      add(stateIcon, CC()
        .gapAfter("${JBUI.scale(5)}px"))
      add(title, CC()
        .minWidth("0px"))
      add(labels, CC()
        .growX()
        .pushX())
      add(assignees, CC()
        .spanY(2)
        .wrap())
      add(info, CC()
        .minWidth("0px")
        .skip(1)
        .spanX(2))
    }

    override fun getListCellRendererComponent(list: JList<out GithubSearchedIssue>,
                                              value: GithubSearchedIssue,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      UIUtil.setBackgroundRecursively(this, GithubUIUtil.List.WithTallRow.background(list, isSelected))
      val primaryTextColor = GithubUIUtil.List.WithTallRow.foreground(list, isSelected)
      val secondaryTextColor = GithubUIUtil.List.WithTallRow.secondaryForeground(list, isSelected)

      stateIcon.apply {
        icon = if (value.state == GithubIssueState.open) GithubIcons.PullRequestOpen else GithubIcons.PullRequestClosed
      }
      title.apply {
        text = value.title
        foreground = primaryTextColor
      }
      info.apply {
        text = "#${value.number} ${value.user.login} on ${DateFormatUtil.formatDate(value.createdAt)}"
        foreground = secondaryTextColor
      }
      labels.apply {
        removeAll()
        for (label in value.labels) {
          add(JBLabel(" ${label.name} ", UIUtil.ComponentStyle.MINI).apply {
            foreground = Color.black
            background = Color.decode("0x${label.color}")
          }.andOpaque())
        }
      }
      assignees.apply {
        removeAll()
        for (assignee in value.assignees) {
          if (componentCount != 0) {
            add(Box.createRigidArea(JBDimension(UIUtil.DEFAULT_HGAP, 0)))
          }
          add(JLabel().apply {
            icon = assignee.let { avatarIconsProvider.getIcon(it) }
            toolTipText = assignee.login
          })
        }
      }

      return this
    }
  }
}