// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.util.NlsActions
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStateModel

abstract class GHPRMergeAction(@NlsActions.ActionText actionName: String, stateModel: GHPRStateModel)
  : GHPRStateChangeAction(actionName, stateModel) {

  init {
    stateModel.addAndInvokeMergeabilityStateLoadingResultListener(::update)
  }

  override fun computeEnabled(): Boolean {
    val mergeability = stateModel.mergeabilityState
    return super.computeEnabled() && mergeability != null && mergeability.canBeMerged
  }
}