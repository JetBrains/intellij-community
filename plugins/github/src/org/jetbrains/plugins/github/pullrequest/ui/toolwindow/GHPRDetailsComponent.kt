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
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDirectionPanel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTitleComponent
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
    val metadataPanel = GHPRMetadataPanelFactory(model, avatarIconsProviderFactory).create()
    val timelineLink = LinkLabel<Any>(GithubBundle.message("pull.request.view.conversations.action"), null) { label, _ ->
      val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return@LinkLabel
      ActionUtil.invokeAction(action, label, ActionPlaces.UNKNOWN, null, null)
    }

    with(panel) {
      add(GHPRTitleComponent.create(model))
      add(directionPanel)
      add(metadataPanel, VerticalLayout.FILL_HORIZONTAL)
      add(timelineLink)
    }

    model.addAndInvokeDetailsChangedListener {
      directionPanel.direction = model.headBranch to model.baseBranch
    }

    return panel
  }
}