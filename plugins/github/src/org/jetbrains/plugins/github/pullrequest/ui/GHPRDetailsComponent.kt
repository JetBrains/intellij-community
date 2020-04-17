// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent

internal object GHPRDetailsComponent {

  fun create(project: Project,
             dataContext: GHPRDataContext,
             detailsModel: SingleValueModel<GHPullRequest?>,
             avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory): JComponent {

    val metaPanel = GHPRMetadataPanel(project, detailsModel,
                                      dataContext.securityService,
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
}