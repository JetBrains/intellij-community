// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class GithubPullRequestMetadataPanel(private val model: GithubPullRequestDetailsModel,
                                              private val iconsProvider: CachingGithubAvatarIconsProvider)
  : JPanel(), Disposable {

  private val directionPanel = GithubPullRequestDirectionPanel()
  private val reviewersHandle = LabeledListPanelHandle.create("No Reviewers", "Reviewers:", ::createUserLabel)
  private val assigneesHandle = LabeledListPanelHandle.create("Unassigned", "Assignees:", ::createUserLabel)
  private val labelsHandle = LabeledListPanelHandle.create("No Labels", "Labels:", ::createLabelLabel)

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

    model.addDetailsChangedListener(this) {
      directionPanel.direction = model.details?.let { it.head to it.base }
      reviewersHandle.list = model.details?.requestedReviewers
      assigneesHandle.list = model.details?.assignees
      labelsHandle.list = model.details?.labels
    }
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

  override fun dispose() {}
}