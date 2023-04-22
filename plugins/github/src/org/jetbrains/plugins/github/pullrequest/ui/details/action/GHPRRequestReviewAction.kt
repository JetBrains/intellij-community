// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import java.awt.event.ActionEvent
import javax.swing.JComponent

internal class GHPRRequestReviewAction(
  stateModel: GHPRStateModel,
  securityService: GHPRSecurityService,
  private val metadataModel: GHPRMetadataModel,
  private val avatarIconsProvider: GHAvatarIconsProvider
) : GHPRStateChangeAction(CollaborationToolsBundle.message("review.details.action.request"), stateModel, securityService) {
  override fun actionPerformed(event: ActionEvent) = stateModel.submitTask {
    val parentComponent = event.source as JComponent
    GHUIUtil.showChooserPopup(
      parentComponent,
      GHUIUtil.SelectionListCellRenderer.PRReviewers(avatarIconsProvider),
      metadataModel.reviewers,
      metadataModel.loadPotentialReviewers()
    ).thenAccept { selectedReviewers ->
      metadataModel.adjustReviewers(EmptyProgressIndicator(), selectedReviewers)
    }
  }

  override fun computeEnabled(): Boolean {
    return super.computeEnabled() &&
           (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || stateModel.viewerDidAuthor)
  }
}