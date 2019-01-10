// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubPullRequest
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class GithubPullRequestMetadataPanel(private val iconsProvider: CachingGithubAvatarIconsProvider) : JPanel() {
  private val directionPanel = GithubPullRequestDirectionPanel()
  var direction: Pair<GithubPullRequest.Tag, GithubPullRequest.Tag>?
    get() = directionPanel.direction
    set(value) {
      directionPanel.direction = value
    }

  private val reviewersHandle = LabeledListPanelHandle.create("No Reviewers", "Reviewers:", ::createUserLabel)
  var reviewers: List<GithubUser>?
    get() = reviewersHandle.list
    set(value) {
      reviewersHandle.list = value
    }

  private val assigneesHandle = LabeledListPanelHandle.create("Unassigned", "Assignees:", ::createUserLabel)
  var assignees: List<GithubUser>?
    get() = assigneesHandle.list
    set(value) {
      assigneesHandle.list = value
    }

  private val labelsHandle = LabeledListPanelHandle.create("No Labels", "Labels:", ::createLabelLabel)
  var labels: List<GithubIssueLabel>?
    get() = labelsHandle.list
    set(value) {
      labelsHandle.list = value
    }

  init {
    isOpaque = false
    layout = MigLayout(LC()
                         .fillX()
                         .gridGap("0", "0")
                         .insets("0", "0", "0", "0"))

    add(directionPanel, CC()
      .minWidth("0")
      .spanX(2).growX()
      .wrap())
    addListPanel(reviewersHandle)
    addListPanel(assigneesHandle)
    addListPanel(labelsHandle)
  }

  private fun addListPanel(handle: LabeledListPanelHandle<*>) {
    add(handle.label, CC().alignY("top"))
    add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
  }

  private fun createUserLabel(user: GithubUser) = JLabel(user.login, iconsProvider.getIcon(user), SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
  }

  private fun createLabelLabel(label: GithubIssueLabel) = Wrapper(GithubUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }
}