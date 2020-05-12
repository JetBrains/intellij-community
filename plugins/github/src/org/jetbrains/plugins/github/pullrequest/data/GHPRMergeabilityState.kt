// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

class GHPRMergeabilityState(val headRefOid: String,
                            val htmlUrl: String,

                            val hasConflicts: Boolean?,

                            val failedChecks: Int,
                            val pendingChecks: Int,
                            val successfulChecks: Int,
                            val checksState: ChecksState,

                            val canBeMerged: Boolean,
                            val canBeRebased: Boolean,

                            val isRestricted: Boolean,
                            val requiredApprovingReviewsCount: Int) {

  enum class ChecksState {
    BLOCKING_BEHIND, BLOCKING_FAILING, FAILING, PENDING, SUCCESSFUL, NONE
  }
}