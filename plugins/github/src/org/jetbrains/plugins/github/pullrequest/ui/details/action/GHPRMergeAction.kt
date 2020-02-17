// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeableState
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.util.concurrent.CompletableFuture

abstract class GHPRMergeAction(actionName: String,
                               busyStateModel: SingleValueModel<Boolean>,
                               errorHandler: (String) -> Unit,
                               private val detailsModel: SingleValueModel<GHPullRequest?>)
  : GHPRStateChangeAction(actionName, busyStateModel, errorHandler) {

  final override val errorPrefix = "Error occurred while merging pull request:"

  init {
    detailsModel.addValueChangedListener(::update)
  }

  override fun computeEnabled(): Boolean {
    val details = detailsModel.value
    return super.computeEnabled() && details != null && details.mergeable == GHPullRequestMergeableState.MERGEABLE
  }


  override fun submitTask(): CompletableFuture<Unit>? {
    return detailsModel.value?.let {
      submitMergeTask(it)
    }
  }

  abstract fun submitMergeTask(details: GHPullRequest): CompletableFuture<Unit>?
}