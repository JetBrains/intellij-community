// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
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
  : JPanel(VerticalLayout(8)) {

  private val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, this)

  private val directionPanel = GHPRDirectionPanel()
  private val title = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
    font = font.deriveFont((font.size * 1.2).toFloat())
  }
  private val number = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
    font = font.deriveFont((font.size * 1.1).toFloat())
    foreground = UIUtil.getContextHelpForeground()
  }
  private val reviewersHandle = ReviewersListPanelHandle()
  private val assigneesHandle = AssigneesListPanelHandle()
  private val labelsHandle = LabelsListPanelHandle()
  private val timelineLink = LinkLabel<Any>(GithubBundle.message("pull.request.view.conversations.action"), null) { label, _ ->
    val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return@LinkLabel
    ActionUtil.invokeAction(action, label, ActionPlaces.UNKNOWN, null, null)
  }

  init {
    isOpaque = false

    val titlePanel = BorderLayoutPanel().addToCenter(title).addToRight(number).andTransparent()
    add(titlePanel)

    add(directionPanel)

    val fieldsPanel = JPanel(MigLayout(LC()
                                         .fillX()
                                         .gridGap("0", "0")
                                         .insets("0", "0", "0", "0"))).apply {
      isOpaque = false

      addListPanel(this, reviewersHandle)
      addListPanel(this, assigneesHandle)
      addListPanel(this, labelsHandle)
    }

    add(fieldsPanel, VerticalLayout.FILL_HORIZONTAL)
    add(timelineLink)

    fun update() {
      val pr = model.value
      directionPanel.direction = pr?.let { it.headLabel to it.baseRefName }
      title.icon = when (pr?.state) {
        GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
        GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
        GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
        null -> null
      }
      title.text = pr?.title
      number.text = " #${pr?.number}"
    }

    model.addAndInvokeValueChangedListener {
      update()
    }
  }

  private inner class ReviewersListPanelHandle
    : LabeledListPanelHandle<GHPullRequestRequestedReviewer>(model, securityService, GithubBundle.message("pull.request.no.reviewers"),
                                                             "${GithubBundle.message("pull.request.reviewers")}:") {
    override fun extractItems(details: GHPullRequest): List<GHPullRequestRequestedReviewer> =
      details.reviewRequests.mapNotNull { it.requestedReviewer }

    override fun getItemComponent(item: GHPullRequestRequestedReviewer) = createUserLabel(item)

    override fun editList() {
      val details = model.value ?: return
      val author = model.value?.author as? GHUser ?: return
      val reviewers = details.reviewRequests.mapNotNull { it.requestedReviewer }
      GithubUIUtil
        .showChooserPopup(GithubBundle.message("pull.request.reviewers"), editButton, { list ->
          val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
          GithubUIUtil.SelectionListCellRenderer.PRReviewers(avatarIconsProvider)
        }, reviewers, metadataService.potentialReviewers.thenApply { it - author })
        .handleOnEdt(getAdjustmentHandler("reviewer") { indicator, delta ->
          metadataService.adjustReviewers(indicator, details, delta)
        })
    }
  }

  private inner class AssigneesListPanelHandle
    : LabeledListPanelHandle<GHUser>(model, securityService, GithubBundle.message("pull.request.unassigned"),
                                     "${GithubBundle.message("pull.request.assignees")}:") {

    override fun extractItems(details: GHPullRequest): List<GHUser> = details.assignees

    override fun getItemComponent(item: GHUser) = createUserLabel(item)

    override fun editList() {
      val details = model.value ?: return
      GithubUIUtil
        .showChooserPopup(GithubBundle.message("pull.request.assignees"), editButton, { list ->
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
    : LabeledListPanelHandle<GHLabel>(model, securityService, GithubBundle.message("pull.request.no.labels"),
                                      "${GithubBundle.message("pull.request.labels")}:") {

    override fun extractItems(details: GHPullRequest): List<GHLabel>? = details.labels

    override fun getItemComponent(item: GHLabel) = createLabelLabel(item)

    override fun editList() {
      val details = model.value ?: return
      GithubUIUtil
        .showChooserPopup(GithubBundle.message("pull.request.labels"), editButton, { GithubUIUtil.SelectionListCellRenderer.Labels() },
                          details.labels, metadataService.labels)
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
          GithubNotifications.showError(project, GithubBundle.message("pull.request.adjustment.failed", StringUtil.pluralize(entityName)),
                                        error)
        return@handler
      }
      if (delta.isEmpty) {
        return@handler
      }

      object : Task.Backgroundable(project, GithubBundle.message("pull.request.adjustment.process.title",
                                                                 StringUtil.pluralize(entityName).capitalize()),
                                   true) {
        override fun run(indicator: ProgressIndicator) {
          adjuster(indicator, delta)
        }

        override fun onThrowable(error: Throwable) {
          GithubNotifications.showError(project, GithubBundle.message("pull.request.adjustment.failed", StringUtil.pluralize(entityName)),
                                        error)
        }
      }.queue()
    }
  }

  companion object {
    private fun addListPanel(panel: JPanel, handle: LabeledListPanelHandle<*>) {
      panel.add(handle.label, CC().alignY("top"))
      panel.add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
    }
  }
}