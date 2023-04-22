// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import java.awt.event.ActionEvent

internal class GHPRSetMyselfAsReviewerAction(
  stateModel: GHPRStateModel,
  securityService: GHPRSecurityService,
  private val metadataModel: GHPRMetadataModel,
) : GHPRStateChangeAction(CollaborationToolsBundle.message("review.details.action.set.myself.as.reviewer"), stateModel, securityService) {
  override fun actionPerformed(event: ActionEvent) = stateModel.submitTask {
    val newReviewer = securityService.currentUser as GHPullRequestRequestedReviewer
    val newReviewers = metadataModel.reviewers.toMutableList().apply {
      add(newReviewer)
    }
    val delta = CollectionDelta(metadataModel.reviewers, newReviewers)
    metadataModel.adjustReviewers(EmptyProgressIndicator(), delta)
  }

  override fun computeEnabled(): Boolean {
    return super.computeEnabled() &&
           (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || stateModel.viewerDidAuthor)
  }
}