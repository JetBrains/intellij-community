// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.PopupHandler
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
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponentFactory {

  fun create(project: Project,
             scope: CoroutineScope,
             reviewDetailsVm: GHPRDetailsViewModel,
             reviewFlowVm: GHPRReviewFlowViewModel,
             dataProvider: GHPRDataProvider,
             repositoryDataService: GHPRRepositoryDataService,
             securityService: GHPRSecurityService,
             avatarIconsProvider: GHAvatarIconsProvider,
             branchesModel: GHPRBranchesModel,
             metadataModel: GHPRMetadataModel,
             stateModel: GHPRStateModel): JComponent {
    val title = GHPRTitleComponent.create(scope, reviewDetailsVm)
    val description = GHPRDetailsDescriptionComponentFactory.create(scope, reviewDetailsVm)
    val branches = GHPRDetailsBranchesComponentFactory.create(project, repositoryDataService, branchesModel)
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
    )).apply {
      isOpaque = false

      add(title, CC().growX().gapBottom("$gapBetweenTitleAndDescription"))
      add(description, CC().growX().gapBottom("$gapBetweenDescriptionAndCommits"))
      add(branches, CC().growY().push())
      add(statusChecks, CC().growX().gapBottom("$gapBetweenCheckAndActions").maxHeight("$statusChecksMaxHeight"))
      add(state, CC().growX().pushX().minHeight("pref"))

      PopupHandler.installPopupMenu(this, DefaultActionGroup(GHPRReloadDetailsAction()), "GHPRDetailsPopup")
    }
  }

  private val indentTop get() = if (ExperimentalUI.isNewUI()) 16 else 12
  private val indentBottom get() = if (ExperimentalUI.isNewUI()) 18 else 15
  private val indentLeft get() = if (ExperimentalUI.isNewUI()) 17 else 13
  private val indentRight get() = if (ExperimentalUI.isNewUI()) 13 else 13

  private val gapBetweenTitleAndDescription get() = if (ExperimentalUI.isNewUI()) 8 else 8
  private val gapBetweenDescriptionAndCommits get() = if (ExperimentalUI.isNewUI()) 22 else 18
  private val gapBetweenCheckAndActions get() = if (ExperimentalUI.isNewUI()) 10 else 10

  private val statusChecksMaxHeight: Int get() = if (ExperimentalUI.isNewUI()) 143 else 143
}