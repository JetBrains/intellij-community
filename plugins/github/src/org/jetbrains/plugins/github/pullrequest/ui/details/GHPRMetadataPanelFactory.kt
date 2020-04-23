// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRMetadataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class GHPRMetadataPanelFactory(private val model: SingleValueModel<GHPullRequest?>,
                               private val securityService: GHPRSecurityService,
                               private val metadataService: GHPRMetadataService,
                               private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory) {

  private val panel = JPanel(null)
  private val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, panel)

  fun create(): JComponent {
    val reviewersHandle = ReviewersListPanelHandle()
    val assigneesHandle = AssigneesListPanelHandle()
    val labelsHandle = LabelsListPanelHandle()

    return panel.apply {
      isOpaque = false
      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))

      addListPanel(this, reviewersHandle)
      addListPanel(this, assigneesHandle)
      addListPanel(this, labelsHandle)
    }
  }

  private inner class ReviewersListPanelHandle
    : LabeledListPanelHandle<GHPullRequestRequestedReviewer>(model, securityService,
                                                             GithubBundle.message("pull.request.no.reviewers"),
                                                             "${GithubBundle.message("pull.request.reviewers")}:") {

    override fun extractItems(details: GHPullRequest): List<GHPullRequestRequestedReviewer> =
      details.reviewRequests.mapNotNull { it.requestedReviewer }

    override fun getItemComponent(item: GHPullRequestRequestedReviewer) = createUserLabel(item)

    override fun showEditPopup(details: GHPullRequest, parentComponent: JComponent): CompletableFuture<CollectionDelta<GHPullRequestRequestedReviewer>>? {
      val author = model.value?.author as? GHUser ?: return null
      val reviewers = details.reviewRequests.mapNotNull { it.requestedReviewer }
      return GithubUIUtil
        .showChooserPopup(GithubBundle.message("pull.request.reviewers"), parentComponent, { list ->
          val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
          GithubUIUtil.SelectionListCellRenderer.PRReviewers(avatarIconsProvider)
        }, reviewers, metadataService.potentialReviewers.thenApply { it - author })
    }

    override fun adjust(indicator: ProgressIndicator,
                        pullRequestId: GHPRIdentifier,
                        delta: CollectionDelta<GHPullRequestRequestedReviewer>) =
      metadataService.adjustReviewers(indicator, pullRequestId, delta)
  }


  private inner class AssigneesListPanelHandle
    : LabeledListPanelHandle<GHUser>(model, securityService, GithubBundle.message("pull.request.unassigned"),
                                     "${GithubBundle.message("pull.request.assignees")}:") {

    override fun extractItems(details: GHPullRequest): List<GHUser> = details.assignees

    override fun getItemComponent(item: GHUser) = createUserLabel(item)

    override fun showEditPopup(details: GHPullRequest, parentComponent: JComponent): CompletableFuture<CollectionDelta<GHUser>>? = GithubUIUtil
      .showChooserPopup(GithubBundle.message("pull.request.assignees"), parentComponent, { list ->
        val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
        GithubUIUtil.SelectionListCellRenderer.Users(avatarIconsProvider)
      }, details.assignees, metadataService.issuesAssignees)

    override fun adjust(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHUser>) =
      metadataService.adjustAssignees(indicator, pullRequestId, delta)
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

    override fun showEditPopup(details: GHPullRequest, parentComponent: JComponent): CompletableFuture<CollectionDelta<GHLabel>>? =
      GithubUIUtil.showChooserPopup(GithubBundle.message("pull.request.labels"), parentComponent,
                                    { GithubUIUtil.SelectionListCellRenderer.Labels() },
                                    details.labels, metadataService.labels)

    override fun adjust(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHLabel>) =
      metadataService.adjustLabels(indicator, pullRequestId, delta)
  }

  private fun createLabelLabel(label: GHLabel) = Wrapper(GithubUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }

  companion object {
    private fun addListPanel(panel: JPanel, handle: LabeledListPanelHandle<*>) {
      panel.add(handle.label, CC().alignY("top"))
      panel.add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
    }
  }
}