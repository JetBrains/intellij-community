// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.action.GHPRReloadStateAction
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.*
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTitleComponent
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponentFactory {

  fun create(scope: CoroutineScope,
             reviewDetailsVm: GHPRDetailsViewModel,
             securityService: GHPRSecurityService,
             avatarIconsProvider: GHAvatarIconsProvider,
             branchesModel: GHPRBranchesModel,
             detailsModel: GHPRDetailsModel,
             metadataModel: GHPRMetadataModel,
             stateModel: GHPRStateModel): JComponent {
    val title = GHPRTitleComponent.create(scope, reviewDetailsVm)
    val description = GHPRDetailsDescriptionComponentFactory.create(scope, reviewDetailsVm)

    val branches = GHPRDetailsBranchesComponentFactory.create(branchesModel)
    val statusChecks = GHPRStatusChecksComponentFactory.create(scope, reviewDetailsVm, securityService)
    val state = GHPRStatePanel(securityService, stateModel).also {
      detailsModel.addAndInvokeDetailsChangedListener {
        it.select(detailsModel.state, true)
      }
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
      add(statusChecks, CC().growX().gapBottom("$gapBetweenCheckAndActions"))
      add(state, CC().growX().pushX().minHeight("pref"))
    }
  }

  private val indentTop get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 16 else 12)
  private val indentBottom get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 18 else 15)
  private val indentLeft get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 17 else 13)
  private val indentRight get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 13 else 13)

  private val gapBetweenTitleAndDescription get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 8 else 8)
  private val gapBetweenDescriptionAndCommits get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 22 else 18)
  private val gapBetweenCheckAndActions get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 10 else 10)
}