// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.StatusText
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsMetadataService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import java.awt.Graphics
import java.awt.event.AdjustmentListener
import javax.swing.BorderFactory
import javax.swing.JPanel


internal class GithubPullRequestDetailsPanel(model: GithubPullRequestDetailsModel,
                                             securityService: GithubPullRequestsSecurityService,
                                             busyStateTracker: GithubPullRequestsBusyStateTracker,
                                             metadataService: GithubPullRequestsMetadataService,
                                             stateService: GithubPullRequestsStateService,
                                             iconProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : JPanel(), ComponentWithEmptyText, Disposable {

  private val emptyText = object : StatusText(this) {
    override fun isStatusVisible() = model.details == null
  }
  private val iconsProvider = iconProviderFactory.create(JBValue.UIInteger("Profile.Icon.Size", 20), this)

  private val metaPanel = GithubPullRequestMetadataPanel(model, securityService, busyStateTracker, metadataService, iconsProvider).apply {
    border = JBUI.Borders.empty(4, 8, 4, 8)
  }
  private val descriptionPanel = GithubPullRequestDescriptionPanel(model).apply {
    border = JBUI.Borders.empty(4, 8, 8, 8)
  }
  private val statePanel = GithubPullRequestStatePanel(model, securityService, busyStateTracker, stateService).apply {
    border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                JBUI.Borders.empty(8))
  }

  init {
    layout = MigLayout(LC().flowY().fill()
                         .gridGap("0", "0")
                         .insets("0", "0", "0", "0"))
    isOpaque = false

    val scrollPane = ScrollPaneFactory.createScrollPane(ScrollablePanel(VerticalFlowLayout(0, 0)).apply {
      add(metaPanel)
      add(descriptionPanel)
      isOpaque = false
    }, true).apply {
      viewport.isOpaque = false
      isOpaque = false
    }
    add(scrollPane, CC().minWidth("0").minHeight("0").growX().growY().growPrioY(0).shrinkPrioY(0))
    add(statePanel, CC().growX().growY().growPrioY(1).shrinkPrioY(1).pushY())

    val verticalScrollBar = scrollPane.verticalScrollBar
    verticalScrollBar.addAdjustmentListener(AdjustmentListener {

      if (verticalScrollBar.maximum - verticalScrollBar.visibleAmount >= 1) {
        statePanel.border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                               JBUI.Borders.empty(8))
      }
      else {
        statePanel.border = JBUI.Borders.empty(8)
      }

    })

    Disposer.register(this, metaPanel)
    Disposer.register(this, descriptionPanel)
    Disposer.register(this, statePanel)
  }

  override fun getEmptyText() = emptyText

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    emptyText.paint(this, g)
  }

  override fun dispose() {}
}