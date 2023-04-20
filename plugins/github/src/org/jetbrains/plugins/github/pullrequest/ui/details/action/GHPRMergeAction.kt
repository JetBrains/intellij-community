// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.util.NlsActions
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel

internal abstract class GHPRMergeAction(
  actionName: @NlsActions.ActionText String,
  stateModel: GHPRStateModel,
  securityService: GHPRSecurityService
) : GHPRStateChangeAction(actionName, stateModel, securityService) {

  init {
    stateModel.addAndInvokeMergeabilityStateLoadingResultListener(::update)
  }

  override fun computeEnabled(): Boolean {
    val mergeability = stateModel.mergeabilityState
    return super.computeEnabled() &&
           mergeability != null &&
           mergeability.canBeMerged &&
           securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) &&
           !securityService.isMergeForbiddenForProject()
  }
}