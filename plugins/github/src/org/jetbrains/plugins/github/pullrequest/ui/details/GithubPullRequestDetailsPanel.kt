// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.*
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import java.awt.BorderLayout
import java.awt.Graphics
import kotlin.properties.Delegates


internal class GithubPullRequestDetailsPanel(private val stateService: GithubPullRequestsStateService,
                                             iconProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                             private val accountDetails: GithubAuthenticatedUser,
                                             private val repoDetails: GithubRepoDetailed)
  : Wrapper(), ComponentWithEmptyText, Disposable {

  private val emptyText = object : StatusText(this) {
    override fun isStatusVisible() = details == null
  }
  private val iconsProvider = iconProviderFactory.create(JBValue.UIInteger("Profile.Icon.Size", 20), this)

  private val metaPanel = GithubPullRequestMetadataPanel(stateService, iconsProvider).apply {
    border = JBUI.Borders.empty(4, 8, 4, 8)
  }
  private val descriptionPanel = GithubPullRequestDescriptionPanel().apply {
    border = JBUI.Borders.empty(4, 8, 8, 8)
  }
  private val contentPanel = ScrollablePanel(BorderLayout(0, UIUtil.DEFAULT_VGAP)).apply {
    isOpaque = false
    add(metaPanel, BorderLayout.NORTH)
    add(descriptionPanel, BorderLayout.CENTER)
  }

  var details: GithubPullRequestDetailedWithHtml?
    by Delegates.observable<GithubPullRequestDetailedWithHtml?>(null) { _, _, newValue ->
      descriptionPanel.description = newValue?.bodyHtml
      metaPanel.direction = newValue?.let { it.base to it.head }
      metaPanel.state = newValue?.let {
        GithubPullRequestStatePanel.State.create(accountDetails, repoDetails, it, stateService.isBusy(it.number))
      }
      metaPanel.reviewers = newValue?.requestedReviewers
      metaPanel.assignees = newValue?.assignees
      metaPanel.labels = newValue?.labels

      contentPanel.validate()
      contentPanel.isVisible = details != null
    }

  init {
    setContent(ScrollPaneFactory.createScrollPane(contentPanel, true).apply {
      viewport.isOpaque = false
      isOpaque = false
    })
    details = null

    stateService.addPullRequestBusyStateListener(this) {
      if (it == metaPanel.state?.number)
        metaPanel.state = metaPanel.state?.copy(busy = stateService.isBusy(it))
    }

    Disposer.register(this, iconsProvider)
  }

  override fun getEmptyText() = emptyText

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)
    emptyText.paint(this, g)
  }

  override fun dispose() {}
}