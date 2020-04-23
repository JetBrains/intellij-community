// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.util.concurrent.CompletableFuture

abstract class GHPRMergeAction(actionName: String,
                               busyStateModel: SingleValueModel<Boolean>,
                               errorHandler: (String) -> Unit,
                               private val mergeabilityModel: SingleValueModel<GHPRMergeabilityState?>)
  : GHPRStateChangeAction(actionName, busyStateModel, errorHandler) {

  final override val errorPrefix = GithubBundle.message("pull.request.merge.error")

  init {
    mergeabilityModel.addValueChangedListener(::update)
  }

  override fun computeEnabled(): Boolean {
    val mergeability = mergeabilityModel.value
    return super.computeEnabled() && mergeability != null && mergeability.canBeMerged
  }


  override fun submitTask(): CompletableFuture<Unit>? {
    return mergeabilityModel.value?.let {
      submitMergeTask(it)
    }
  }

  abstract fun submitMergeTask(mergeability: GHPRMergeabilityState): CompletableFuture<Unit>?
}