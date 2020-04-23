// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.util.concurrent.CompletableFuture

internal class GHPRRebaseMergeAction(busyStateModel: SingleValueModel<Boolean>,
                                     errorHandler: (String) -> Unit,
                                     private val mergeabilityModel: SingleValueModel<GHPRMergeabilityState?>,
                                     private val stateService: GHPRStateService)
  : GHPRMergeAction(GithubBundle.message("pull.request.merge.rebase.action"), busyStateModel, errorHandler, mergeabilityModel) {

  init {
    update()
  }

  override fun computeEnabled(): Boolean {
    return super.computeEnabled() && mergeabilityModel.value?.canBeRebased == true
  }

  override fun submitMergeTask(mergeability: GHPRMergeabilityState): CompletableFuture<Unit>? =
    stateService.rebaseMerge(EmptyProgressIndicator(), mergeability, mergeability.headRefOid)
}