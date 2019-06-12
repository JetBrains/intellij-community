// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsMetadataService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class GithubPullRequestMetadataPanel(private val model: GithubPullRequestDetailsModel,
                                              private val securityService: GithubPullRequestsSecurityService,
                                              private val busyStateTracker: GithubPullRequestsBusyStateTracker,
                                              private val metadataService: GithubPullRequestsMetadataService,
                                              private val iconsProvider: CachingGithubAvatarIconsProvider)
  : JPanel(), Disposable {

  private val directionPanel = GithubPullRequestDirectionPanel()
  private val reviewersHandle = ReviewersListPanelHandle()
  private val assigneesHandle = AssigneesListPanelHandle()
  private val labelsHandle = LabelsListPanelHandle()

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
    }

    Disposer.register(this, reviewersHandle)
    Disposer.register(this, assigneesHandle)
    Disposer.register(this, labelsHandle)
  }

  private fun addListPanel(handle: LabeledListPanelHandle<*>) {
    add(handle.label, CC().alignY("top"))
    add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
  }

  override fun dispose() {}

  private inner class ReviewersListPanelHandle
    : LabeledListPanelHandle<GithubUser>(model, securityService, busyStateTracker, "No Reviewers", "Reviewers:") {
    override fun extractItems(details: GithubPullRequestDetailed): List<GithubUser> = details.requestedReviewers

    override fun getItemComponent(item: GithubUser) = createUserLabel(item)

    override fun editList() {
      model.details?.run { metadataService.adjustReviewers(number, editButton) }
    }
  }

  private inner class AssigneesListPanelHandle
    : LabeledListPanelHandle<GithubUser>(model, securityService, busyStateTracker, "Unassigned", "Assignees:") {

    override fun extractItems(details: GithubPullRequestDetailed): List<GithubUser> = details.assignees

    override fun getItemComponent(item: GithubUser) = createUserLabel(item)

    override fun editList() {
      model.details?.run { metadataService.adjustAssignees(number, editButton) }
    }
  }

  private fun createUserLabel(user: GithubUser) = JLabel(user.login,
                                                         iconsProvider.getIcon(user.avatarUrl),
                                                         SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
  }

  private inner class LabelsListPanelHandle
    : LabeledListPanelHandle<GithubIssueLabel>(model, securityService, busyStateTracker, "No Labels", "Labels:") {

    override fun extractItems(details: GithubPullRequestDetailed): List<GithubIssueLabel>? = details.labels

    override fun getItemComponent(item: GithubIssueLabel) = createLabelLabel(item)

    override fun editList() {
      model.details?.run { metadataService.adjustLabels(number, editButton) }
    }
  }

  private fun createLabelLabel(label: GithubIssueLabel) = Wrapper(GithubUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }
}