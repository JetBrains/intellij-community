// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

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
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDirectionPanel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanelFactory
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponent {

  fun create(detailsModel: GHPRDetailsModel,
             avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory): JComponent {

    val metaPanel = createPanel(detailsModel, avatarIconsProviderFactory).apply {
      border = JBUI.Borders.empty(8)
    }

    val scrollablePanel = ScrollablePanel(VerticalFlowLayout(0, 0)).apply {
      isOpaque = false
      add(metaPanel)
    }
    val actionManager = ActionManager.getInstance()

    return ScrollPaneFactory.createScrollPane(scrollablePanel, true).apply {
      viewport.isOpaque = false
      isOpaque = false
    }.also {
      val actionGroup = actionManager.getAction("Github.PullRequest.Details.Popup") as ActionGroup
      PopupHandler.installPopupHandler(it, actionGroup, ActionPlaces.UNKNOWN, actionManager)
    }
  }

  private fun createPanel(model: GHPRDetailsModel, avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory): JComponent {
    val panel = JPanel(VerticalLayout(UI.scale(8))).apply {
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
    val metadataPanel = GHPRMetadataPanelFactory(model, avatarIconsProviderFactory).create()
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

  private class Controller(private val model: GHPRDetailsModel,
                           private val directionPanel: GHPRDirectionPanel,
                           private val title: JBLabel,
                           private val number: JBLabel) {

    init {
      model.addAndInvokeDetailsChangedListener {
        update()
      }
    }

    private fun update() {
      directionPanel.direction = model.headBranch to model.baseBranch
      title.icon = when (model.state) {
        GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
        GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
        GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
      }
      title.text = model.title
      number.text = " #${model.number}"
    }
  }
}