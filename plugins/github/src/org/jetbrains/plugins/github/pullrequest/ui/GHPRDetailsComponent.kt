// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRMetadataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDirectionPanel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanelFactory
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponent {

  fun create(dataContext: GHPRDataContext,
             detailsModel: SingleValueModel<GHPullRequest?>,
             avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory): JComponent {

    val metaPanel = createPanel(detailsModel, dataContext.securityService,
                                dataContext.metadataService,
                                avatarIconsProviderFactory).apply {
      border = JBUI.Borders.empty(8)
    }

    val scrollablePanel = ScrollablePanel(VerticalFlowLayout(0, 0)).apply {
      isOpaque = false
      add(metaPanel)
    }
    val actionManager = ActionManager.getInstance()
    val scrollPane = ScrollPaneFactory.createScrollPane(scrollablePanel, true).apply {
      viewport.isOpaque = false
      isOpaque = false
    }.also {
      val actionGroup = actionManager.getAction("Github.PullRequest.Details.Popup") as ActionGroup
      PopupHandler.installPopupHandler(it, actionGroup, ActionPlaces.UNKNOWN, actionManager)
    }

    scrollPane.isVisible = detailsModel.value != null

    detailsModel.addValueChangedListener {
      scrollPane.isVisible = detailsModel.value != null
    }
    return scrollPane
  }

  private fun createPanel(model: SingleValueModel<GHPullRequest?>,
                          securityService: GHPRSecurityService,
                          metadataService: GHPRMetadataService,
                          avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory): JComponent {
    val panel = JPanel(VerticalLayout(8)).apply {
      isOpaque = false
    }
    val directionPanel = GHPRDirectionPanel()
    val title = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
      font = font.deriveFont((font.size * 1.2).toFloat())
    }
    val number = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
      font = font.deriveFont((font.size * 1.1).toFloat())
      foreground = UIUtil.getContextHelpForeground()
    }
    val metadataPanel = GHPRMetadataPanelFactory(model, securityService, metadataService, avatarIconsProviderFactory).create()
    val timelineLink = LinkLabel<Any>(GithubBundle.message("pull.request.view.conversations.action"), null) { label, _ ->
      val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return@LinkLabel
      ActionUtil.invokeAction(action, label, ActionPlaces.UNKNOWN, null, null)
    }

    with(panel) {
      val titlePanel = BorderLayoutPanel().addToCenter(title).addToRight(number).andTransparent()
      add(titlePanel)
      add(directionPanel)
      add(metadataPanel, VerticalLayout.FILL_HORIZONTAL)
      add(timelineLink)
    }

    Controller(model, directionPanel, title, number)

    return panel
  }

  private class Controller(private val model: SingleValueModel<GHPullRequest?>,
                           private val directionPanel: GHPRDirectionPanel,
                           private val title: JBLabel,
                           private val number: JBLabel) {

    init {
      model.addAndInvokeValueChangedListener {
        update()
      }
    }

    private fun update() {
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
  }
}