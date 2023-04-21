// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
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
        .emptyBorders()
        .fill()
        .flowY()
        .hideMode(3)
    )).apply {
      isOpaque = false

      add(title, CC().growX().gap(
        left = ReviewDetailsUIUtil.indentLeft,
        right = ReviewDetailsUIUtil.indentRight,
        top = ReviewDetailsUIUtil.indentTop,
        bottom = ReviewDetailsUIUtil.gapBetweenTitleAndDescription))
      add(description, CC().growX().gap(
        left = ReviewDetailsUIUtil.indentLeft,
        right = ReviewDetailsUIUtil.indentRight,
        bottom = ReviewDetailsUIUtil.gapBetweenDescriptionAndCommits))
      add(commitsAndBranches, CC().growX().gap(
        left = ReviewDetailsUIUtil.indentLeft,
        right = ReviewDetailsUIUtil.indentRight,
        bottom = ReviewDetailsUIUtil.gapBetweenCommitsAndCommitInfo))
      add(commitInfo, CC().growX().maxHeight("${ReviewDetailsUIUtil.commitInfoMaxHeight}").gap(
        left = ReviewDetailsUIUtil.indentLeft,
        right = ReviewDetailsUIUtil.indentRight,
        bottom = ReviewDetailsUIUtil.gapBetweenCommitInfoAndCommitsBrowser))
      add(commitFilesBrowserComponent, CC().grow().push())
      add(statusChecks, CC().growX().maxHeight("${ReviewDetailsUIUtil.statusChecksMaxHeight}").gap(
        left = ReviewDetailsUIUtil.indentLeft,
        right = ReviewDetailsUIUtil.indentRight,
        top = ReviewDetailsUIUtil.gapBetweenCommitsBrowserAndStatusChecks,
        bottom = ReviewDetailsUIUtil.gapBetweenCheckAndActions))
      add(state, CC().growX().pushX().minHeight("pref").gap(
        left = ReviewDetailsUIUtil.indentLeft - 2,
        right = ReviewDetailsUIUtil.indentRight,
        bottom = ReviewDetailsUIUtil.indentBottom))

      PopupHandler.installPopupMenu(this, DefaultActionGroup(GHPRReloadDetailsAction()), "GHPRDetailsPopup")
    }
  }
}