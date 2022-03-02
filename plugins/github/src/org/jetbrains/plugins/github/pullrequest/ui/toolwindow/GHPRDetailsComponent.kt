// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRReloadStateAction
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.*
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTitleComponent
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponent {

  fun create(project: Project,
             securityService: GHPRSecurityService,
             avatarIconsProvider: GHAvatarIconsProvider,
             branchesModel: GHPRBranchesModel,
             detailsModel: GHPRDetailsModel,
             metadataModel: GHPRMetadataModel,
             stateModel: GHPRStateModel): JComponent {
    val actionManager = ActionManager.getInstance()

    val branches = GHPRBranchesPanel.create(branchesModel)
    val title = GHPRTitleComponent.create(detailsModel)
    val description = HtmlEditorPane().apply {
      detailsModel.addAndInvokeDetailsChangedListener {
        setBody(detailsModel.description.convertToHtml(project))
      }
    }
    val timelineLink = ActionLink(GithubBundle.message("pull.request.view.conversations.action")) {
      val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return@ActionLink
      ActionUtil.invokeAction(action, it.source as ActionLink, ActionPlaces.UNKNOWN, null, null)
    }
    val metadata = GHPRMetadataPanelFactory(metadataModel, avatarIconsProvider).create()
    val state = GHPRStatePanel(securityService, stateModel).also {
      detailsModel.addAndInvokeDetailsChangedListener {
        it.select(detailsModel.state, true)
      }
      PopupHandler.installPopupMenu(it, DefaultActionGroup(GHPRReloadStateAction()), "GHPRStatePanelPopup")
    }

    metadata.border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                         JBUI.Borders.empty(8))

    state.border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                      JBUI.Borders.empty(8))

    val detailsSection = JPanel(MigLayout(LC().insets("0", "0", "0", "0")
                                            .gridGap("0", "0")
                                            .fill().flowY())).apply {
      isOpaque = false
      border = JBUI.Borders.empty(8)

      add(branches, CC().gapBottom("${UI.scale(8)}"))
      add(title, CC().gapBottom("${UI.scale(8)}"))
      add(description, CC().grow().push().minHeight("0"))
      add(timelineLink, CC().gapBottom("push"))
    }

    val groupId = "Github.PullRequest.Details.Popup"
    PopupHandler.installPopupMenu(detailsSection, groupId, groupId)
    PopupHandler.installPopupMenu(description, groupId, groupId)
    PopupHandler.installPopupMenu(metadata, groupId, groupId)

    return JPanel(MigLayout(LC().insets("0", "0", "0", "0")
                              .gridGap("0", "0")
                              .fill().flowY())).apply {
      isOpaque = false

      add(detailsSection, CC().grow().push().minHeight("0"))
      add(metadata, CC().growX().pushX())
      add(Wrapper(state).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
      }, CC().growX().pushX().minHeight("pref"))
    }
  }
}