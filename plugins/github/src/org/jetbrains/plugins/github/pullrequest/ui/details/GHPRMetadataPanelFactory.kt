// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.component.LabeledListPanelHandle
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class GHPRMetadataPanelFactory(private val model: GHPRMetadataModel,
                               private val avatarIconsProvider: GHAvatarIconsProvider) {

  private val panel = JPanel(null)

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
    : LabeledListPanelHandle<GHPullRequestRequestedReviewer>(model,
                                                             GithubBundle.message("pull.request.no.reviewers"),
                                                             "${GithubBundle.message("pull.request.reviewers")}:") {

    override fun getItems(): List<GHPullRequestRequestedReviewer> = model.reviewers

    override fun getItemComponent(item: GHPullRequestRequestedReviewer) = createUserLabel(item)

    override fun showEditPopup(parentComponent: JComponent): CompletableFuture<CollectionDelta<GHPullRequestRequestedReviewer>> {
      return GHUIUtil
        .showChooserPopup(parentComponent, GHUIUtil.SelectionPresenters.PRReviewers(avatarIconsProvider),
                          model.reviewers, model.loadPotentialReviewers())
    }

    override fun adjust(indicator: ProgressIndicator, delta: CollectionDelta<GHPullRequestRequestedReviewer>) =
      model.adjustReviewers(indicator, delta)
  }


  private inner class AssigneesListPanelHandle
    : LabeledListPanelHandle<GHUser>(model,
                                     GithubBundle.message("pull.request.unassigned"),
                                     "${GithubBundle.message("pull.request.assignees")}:") {

    override fun getItems(): List<GHUser> = model.assignees

    override fun getItemComponent(item: GHUser) = createUserLabel(item)

    override fun showEditPopup(parentComponent: JComponent): CompletableFuture<CollectionDelta<GHUser>> = GHUIUtil
      .showChooserPopup(parentComponent, GHUIUtil.SelectionPresenters.Users(avatarIconsProvider),
                        model.assignees, model.loadPotentialAssignees())

    override fun adjust(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>) =
      model.adjustAssignees(indicator, delta)
  }

  private fun createUserLabel(user: GHPullRequestRequestedReviewer) = JLabel(user.shortName,
                                                                             avatarIconsProvider.getIcon(user.avatarUrl,
                                                                                                         Avatar.Sizes.BASE),
                                                                             SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
  }

  private inner class LabelsListPanelHandle
    : LabeledListPanelHandle<GHLabel>(model,
                                      GithubBundle.message("pull.request.no.labels"),
                                      "${GithubBundle.message("pull.request.labels")}:") {

    override fun getItems(): List<GHLabel> = model.labels

    override fun getItemComponent(item: GHLabel) = createLabelLabel(item)

    override fun showEditPopup(parentComponent: JComponent): CompletableFuture<CollectionDelta<GHLabel>> =
      GHUIUtil.showChooserPopup(parentComponent, GHUIUtil.SelectionPresenters.Labels(),
                                model.labels, model.loadAssignableLabels())

    override fun adjust(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>) =
      model.adjustLabels(indicator, delta)
  }

  private fun createLabelLabel(label: GHLabel) = Wrapper(GHUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }

  companion object {
    private fun addListPanel(panel: JPanel, handle: LabeledListPanelHandle<*>) {
      panel.add(handle.label, CC().alignY("top").width(":${handle.preferredLabelWidth}px:"))
      panel.add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
    }
  }
}