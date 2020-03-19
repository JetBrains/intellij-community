// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRMetadataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.*
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class GHPRMetadataPanel(private val project: Project,
                                 private val model: SingleValueModel<GHPullRequest?>,
                                 private val securityService: GHPRSecurityService,
                                 private val metadataService: GHPRMetadataService,
                                 private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : JPanel() {

  private val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, this)

  private val directionPanel = GHPRDirectionPanel()
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

    fun update() {
      directionPanel.direction = model.value?.let { it.headLabel to it.baseRefName }
    }

    model.addValueChangedListener {
      update()
    }
    update()
  }

  private fun addListPanel(handle: LabeledListPanelHandle<*>) {
    add(handle.label, CC().alignY("top"))
    add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
  }

  private inner class ReviewersListPanelHandle
    : LabeledListPanelHandle<GHPullRequestRequestedReviewer>(model, securityService, "No Reviewers", "Reviewers:") {
    override fun extractItems(details: GHPullRequest): List<GHPullRequestRequestedReviewer> =
      details.reviewRequests.mapNotNull { it.requestedReviewer }

    override fun getItemComponent(item: GHPullRequestRequestedReviewer) = createUserLabel(item)

    override fun editList() {
      val details = model.value ?: return
      val author = model.value?.author as? GHUser ?: return
      val reviewers = details.reviewRequests.mapNotNull { it.requestedReviewer }
      GithubUIUtil
        .showChooserPopup("Reviewers", editButton, { list ->
          val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
          GithubUIUtil.SelectionListCellRenderer.PRReviewers(avatarIconsProvider)
        }, reviewers, metadataService.potentialReviewers.thenApply { it - author })
        .handleOnEdt(getAdjustmentHandler("reviewer") { indicator, delta ->
          metadataService.adjustReviewers(indicator, details, delta)
        })
    }
  }

  private inner class AssigneesListPanelHandle
    : LabeledListPanelHandle<GHUser>(model, securityService, "Unassigned", "Assignees:") {

    override fun extractItems(details: GHPullRequest): List<GHUser> = details.assignees

    override fun getItemComponent(item: GHUser) = createUserLabel(item)

    override fun editList() {
      val details = model.value ?: return
      GithubUIUtil
        .showChooserPopup("Assignees", editButton, { list ->
          val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
          GithubUIUtil.SelectionListCellRenderer.Users(avatarIconsProvider)
        }, details.assignees, metadataService.issuesAssignees)
        .handleOnEdt(getAdjustmentHandler("assignee") { indicator, delta ->
          metadataService.adjustAssignees(indicator, details, delta)
        })
    }
  }

  private fun createUserLabel(user: GHPullRequestRequestedReviewer) = JLabel(user.shortName,
                                                                             avatarIconsProvider.getIcon(user.avatarUrl),
                                                                             SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
  }

  private inner class LabelsListPanelHandle
    : LabeledListPanelHandle<GHLabel>(model, securityService, "No Labels", "Labels:") {

    override fun extractItems(details: GHPullRequest): List<GHLabel>? = details.labels

    override fun getItemComponent(item: GHLabel) = createLabelLabel(item)

    override fun editList() {
      val details = model.value ?: return
      GithubUIUtil
        .showChooserPopup("Labels", editButton, { GithubUIUtil.SelectionListCellRenderer.Labels() }, details.labels, metadataService.labels)
        .handleOnEdt(getAdjustmentHandler("label") { indicator, delta ->
          metadataService.adjustLabels(indicator, details, delta)
        })
    }
  }

  private fun createLabelLabel(label: GHLabel) = Wrapper(GithubUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }

  private fun <T> getAdjustmentHandler(@Nls entityName: String,
                                       adjuster: (ProgressIndicator, CollectionDelta<T>) -> Unit)
    : (CollectionDelta<T>, Throwable?) -> Unit {

    return handler@{ delta, error ->
      if (error != null) {
        if (!GithubAsyncUtil.isCancellation(error))
          GithubNotifications.showError(project, "Failed to adjust list of ${StringUtil.pluralize(entityName)}", error)
        return@handler
      }
      if (delta.isEmpty) {
        return@handler
      }

      object : Task.Backgroundable(project, "Adjusting List of ${StringUtil.pluralize(entityName).capitalize()}...",
                                   true) {
        override fun run(indicator: ProgressIndicator) {
          adjuster(indicator, delta)
        }

        override fun onThrowable(error: Throwable) {
          GithubNotifications.showError(project, "Failed to adjust list of ${StringUtil.pluralize(entityName)}", error)
        }
      }.queue()
    }
  }
}