// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob

class GHPRMergeabilityState(val hasConflicts: Boolean?,

                            val ciJobs: List<CodeReviewCIJob>,

                            val canBeMerged: Boolean,
                            val canBeRebased: Boolean,

                            val isRestricted: Boolean,
                            val requiredApprovingReviewsCount: Int) {

  enum class ChecksState {
    BLOCKING_BEHIND, BLOCKING_FAILING, FAILING, PENDING, SUCCESSFUL, NONE
  }
}