// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import java.awt.event.ActionEvent

internal class GHPRPostReviewAction(stateModel: GHPRStateModel, securityService: GHPRSecurityService)
  : GHPRStateChangeAction(GithubBundle.message("pull.request.post.action"), stateModel, securityService) {

  override fun actionPerformed(e: ActionEvent?) = stateModel.submitMarkReadyForReviewTask()

  override fun computeEnabled(): Boolean {
    return super.computeEnabled() &&
           (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || stateModel.viewerDidAuthor)
  }
}