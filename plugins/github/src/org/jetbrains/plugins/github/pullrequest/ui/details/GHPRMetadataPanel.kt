// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsMetadataService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.*
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class GHPRMetadataPanel(private val project: Project,
                                 private val model: SingleValueModel<GHPullRequest?>,
                                 private val securityService: GithubPullRequestsSecurityService,
                                 private val busyStateTracker: GithubPullRequestsBusyStateTracker,
                                 private val metadataService: GithubPullRequestsMetadataService,
                                 private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : JPanel(), Disposable {

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

    model.addValueChangedListener(this) {
      update()
    }
    update()

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
    : LabeledListPanelHandle<GHUser>(model, securityService, busyStateTracker, "No Reviewers", "Reviewers:") {
    override fun extractItems(details: GHPullRequest): List<GHUser> = details.reviewRequests.map { it.requestedReviewer }
      .filterIsInstance(GHUser::class.java)

    override fun getItemComponent(item: GHUser) = createUserLabel(item)

    override fun editList() {
      val details = model.value ?: return
      val reviewers = details.reviewRequests.map { it.requestedReviewer }.filterIsInstance<GHUser>()
      GithubUIUtil
        .showChooserPopup("Reviewers", editButton, { list ->
          val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
          GithubUIUtil.SelectionListCellRenderer.Users(avatarIconsProvider)
        }, reviewers, metadataService.collaboratorsWithPushAccess)
        .handleOnEdt(getAdjustmentHandler(details.number, "reviewer") { indicator, delta ->
          metadataService.adjustReviewers(indicator, details.number, delta)
        })
    }
  }

  private inner class AssigneesListPanelHandle
    : LabeledListPanelHandle<GHUser>(model, securityService, busyStateTracker, "Unassigned", "Assignees:") {

    override fun extractItems(details: GHPullRequest): List<GHUser> = details.assignees

    override fun getItemComponent(item: GHUser) = createUserLabel(item)

    override fun editList() {
      val details = model.value ?: return
      GithubUIUtil
        .showChooserPopup("Assignees", editButton, { list ->
          val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
          GithubUIUtil.SelectionListCellRenderer.Users(avatarIconsProvider)
        }, details.assignees, metadataService.issuesAssignees)
        .handleOnEdt(getAdjustmentHandler(details.number, "assignee") { indicator, delta ->
          metadataService.adjustAssignees(indicator, details.number, delta)
        })
    }
  }

  private fun createUserLabel(user: GHUser) = JLabel(user.login,
                                                     avatarIconsProvider.getIcon(user.avatarUrl),
                                                     SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
  }

  private inner class LabelsListPanelHandle
    : LabeledListPanelHandle<GHLabel>(model, securityService, busyStateTracker, "No Labels", "Labels:") {

    override fun extractItems(details: GHPullRequest): List<GHLabel>? = details.labels

    override fun getItemComponent(item: GHLabel) = createLabelLabel(item)

    override fun editList() {
      val details = model.value ?: return
      GithubUIUtil
        .showChooserPopup("Labels", editButton, { GithubUIUtil.SelectionListCellRenderer.Labels() }, details.labels, metadataService.labels)
        .handleOnEdt(getAdjustmentHandler(details.number, "label") { indicator, delta ->
          metadataService.adjustLabels(indicator, details.number, delta)
        })
    }
  }

  private fun createLabelLabel(label: GHLabel) = Wrapper(GithubUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }

  private fun <T> getAdjustmentHandler(pullRequest: Long, @Nls entityName: String,
                                       adjuster: (ProgressIndicator, CollectionDelta<T>) -> Unit)
    : (CollectionDelta<T>?, Throwable?) -> Unit {

    return handler@{ delta, error ->
      if (error != null) {
        if (!GithubAsyncUtil.isCancellation(error))
          GithubNotifications.showError(project, "Failed to adjust list of ${StringUtil.pluralize(entityName)}", error)
        return@handler
      }
      if (delta == null || delta.isEmpty) {
        return@handler
      }

      if (!busyStateTracker.acquire(pullRequest)) return@handler
      object : Task.Backgroundable(project, "Adjusting List of ${StringUtil.pluralize(entityName).capitalize()}...",
                                   true) {
        override fun run(indicator: ProgressIndicator) {
          adjuster(indicator, delta)
        }

        override fun onThrowable(error: Throwable) {
          GithubNotifications.showError(project, "Failed to adjust list of ${StringUtil.pluralize(entityName)}", error)
        }

        override fun onFinished() {
          busyStateTracker.release(pullRequest)
        }
      }.queue()
    }
  }
}