// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState

interface GHPRStateModel {

  val details: GHPullRequestShort
  val mergeabilityState: GHPRMergeabilityState?
  val mergeabilityLoadingError: Throwable?

  var isBusy: Boolean
  var actionError: Throwable?

  fun reloadMergeabilityState()

  fun submitCloseTask()
  fun submitReopenTask()
  fun submitMergeTask()
  fun submitRebaseMergeTask()
  fun submitSquashMergeTask()

  fun addAndInvokeMergeabilityStateLoadingResultListener(listener: () -> Unit)
  fun addAndInvokeBusyStateChangedListener(listener: () -> Unit)
  fun addAndInvokeActionErrorChangedListener(listener: () -> Unit)
}
