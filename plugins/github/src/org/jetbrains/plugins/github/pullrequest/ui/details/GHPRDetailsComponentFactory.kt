// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.panels.HorizontalLayout
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.action.GHPRReloadDetailsAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRReloadStateAction
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.*
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTitleComponent
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRDiffController
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponentFactory {

  fun create(
    project: Project,
    scope: CoroutineScope,
    reviewDetailsVm: GHPRDetailsViewModel,
    reviewFlowVm: GHPRReviewFlowViewModel,
    commitsVm: GHPRCommitsViewModel,
    dataProvider: GHPRDataProvider,
    repositoryDataService: GHPRRepositoryDataService,
    securityService: GHPRSecurityService,
    avatarIconsProvider: GHAvatarIconsProvider,
    branchesModel: GHPRBranchesModel,
    metadataModel: GHPRMetadataModel,
    stateModel: GHPRStateModel,
    commitFilesBrowserComponent: JComponent,
    diffBridge: GHPRDiffController
  ): JComponent {
    val title = GHPRTitleComponent.create(scope, reviewDetailsVm)
    val description = GHPRDetailsDescriptionComponentFactory.create(scope, reviewDetailsVm)
    val commitsAndBranches = JPanel(HorizontalLayout(0)).apply {
      isOpaque = false
      add(GHPRDetailsCommitsComponentFactory.create(scope, commitsVm, diffBridge), HorizontalLayout.LEFT)
      add(GHPRDetailsBranchesComponentFactory.create(project, repositoryDataService, branchesModel), HorizontalLayout.RIGHT)
    }
    val commitInfo = GHPRDetailsCommitInfoComponentFactory.create(scope, commitsVm)
    val statusChecks = GHPRStatusChecksComponentFactory.create(scope, reviewDetailsVm, reviewFlowVm, securityService, avatarIconsProvider)
    val state = GHPRStatePanel(
      scope,
      reviewDetailsVm, reviewFlowVm,
      dataProvider, securityService, stateModel, metadataModel,
      avatarIconsProvider
    ).also {
      PopupHandler.installPopupMenu(it, DefaultActionGroup(GHPRReloadStateAction()), "GHPRStatePanelPopup")
    }

    return JPanel(MigLayout(
      LC()
        .insets("$indentTop", "$indentLeft", "$indentBottom", "$indentRight")
        .gridGap("0", "0")
        .fill()
        .flowY()
        .hideMode(3)
    )).apply {
      isOpaque = false

      add(title, CC().growX().gapBottom("$gapBetweenTitleAndDescription"))
      add(description, CC().growX().gapBottom("$gapBetweenDescriptionAndCommits"))
      add(commitsAndBranches, CC().growX().gapBottom("$gapBetweenCommitsAndCommitInfo"))
      add(commitInfo, CC().growX().maxHeight("$commitInfoMaxHeight").gapBottom("$gapBetweenCommitInfoAndCommitsBrowser"))
      add(commitFilesBrowserComponent, CC().grow().push())
      add(statusChecks, CC().growX().gapBottom("$gapBetweenCheckAndActions").maxHeight("$statusChecksMaxHeight"))
      add(state, CC().growX().pushX().minHeight("pref"))

      PopupHandler.installPopupMenu(this, DefaultActionGroup(GHPRReloadDetailsAction()), "GHPRDetailsPopup")
    }
  }

  private val indentTop: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 12, newUI = 16)
  private val indentBottom: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 15, newUI = 18)
  private val indentLeft: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 13, newUI = 17)
  private val indentRight: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 13, newUI = 13)

  private val gapBetweenTitleAndDescription: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 8, newUI = 8)
  private val gapBetweenDescriptionAndCommits: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 18, newUI = 22)
  private val gapBetweenCommitsAndCommitInfo: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 9, newUI = 15)
  private val gapBetweenCommitInfoAndCommitsBrowser: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 12, newUI = 12)
  private val gapBetweenCheckAndActions: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 10, newUI = 10)

  private val commitInfoMaxHeight: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 100, newUI = 100)
  private val statusChecksMaxHeight: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 143, newUI = 143)
}