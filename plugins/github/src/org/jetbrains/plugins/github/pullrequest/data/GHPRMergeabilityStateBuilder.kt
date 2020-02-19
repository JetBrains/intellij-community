// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import org.jetbrains.plugins.github.api.data.GHBranchProtectionRules
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteConclusion
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteStatusState
import org.jetbrains.plugins.github.api.data.GHCommitStatusContextState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeStateStatus
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeabilityData
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeableState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState.ChecksState

class GHPRMergeabilityStateBuilder(private val details: GHPullRequest,
                                   private val mergeabilityData: GHPullRequestMergeabilityData) {

  private var baseBranchProtectionRules: GHBranchProtectionRules? = null
  private var isAdmin: Boolean = false

  fun withWriteAccess(baseBranchProtectionRules: GHBranchProtectionRules, isAdmin: Boolean) {
    this.baseBranchProtectionRules = baseBranchProtectionRules
    this.isAdmin = isAdmin
  }

  fun build(): GHPRMergeabilityState {
    val hasConflicts = when (mergeabilityData.mergeable) {
      GHPullRequestMergeableState.MERGEABLE -> false
      GHPullRequestMergeableState.CONFLICTING -> true
      GHPullRequestMergeableState.UNKNOWN -> null
    }

    var failedChecks = 0
    var pendingChecks = 0
    var successfulChecks = 0

    val lastCommit = mergeabilityData.commits.nodes.lastOrNull()?.commit
    val contexts = lastCommit?.status?.contexts.orEmpty()
    for (context in contexts) {
      when (context.state) {
        GHCommitStatusContextState.ERROR,
        GHCommitStatusContextState.FAILURE -> failedChecks++
        GHCommitStatusContextState.EXPECTED,
        GHCommitStatusContextState.PENDING -> pendingChecks++
        GHCommitStatusContextState.SUCCESS -> successfulChecks++
      }
    }

    val checkSuites = lastCommit?.checkSuites?.nodes.orEmpty()
    for (suite in checkSuites) {
      when (suite.status) {
        GHCommitCheckSuiteStatusState.IN_PROGRESS,
        GHCommitCheckSuiteStatusState.QUEUED,
        GHCommitCheckSuiteStatusState.REQUESTED -> pendingChecks++
        GHCommitCheckSuiteStatusState.COMPLETED -> {
          when (suite.conclusion) {
            GHCommitCheckSuiteConclusion.ACTION_REQUIRED -> failedChecks++
            GHCommitCheckSuiteConclusion.CANCELLED -> successfulChecks++
            GHCommitCheckSuiteConclusion.FAILURE -> failedChecks++
            GHCommitCheckSuiteConclusion.NEUTRAL -> successfulChecks++
            GHCommitCheckSuiteConclusion.SKIPPED -> successfulChecks++
            GHCommitCheckSuiteConclusion.STALE -> failedChecks++
            GHCommitCheckSuiteConclusion.SUCCESS -> successfulChecks++
            GHCommitCheckSuiteConclusion.TIMED_OUT -> failedChecks++
            null -> failedChecks++
          }
        }
      }
    }

    val canOverrideAsAdmin = baseBranchProtectionRules?.enforceAdmins?.enabled == false
    val canBeMerged = when {
      mergeabilityData.mergeStateStatus.canMerge() -> true
      mergeabilityData.mergeStateStatus.adminCanMerge() && canOverrideAsAdmin && isAdmin -> true
      else -> false
    }

    val summaryChecksState = getChecksSummaryState(failedChecks, pendingChecks, successfulChecks)
    val checksState = when (mergeabilityData.mergeStateStatus) {
      GHPullRequestMergeStateStatus.CLEAN,
      GHPullRequestMergeStateStatus.DIRTY,
      GHPullRequestMergeStateStatus.DRAFT,
      GHPullRequestMergeStateStatus.HAS_HOOKS,
      GHPullRequestMergeStateStatus.UNKNOWN,
      GHPullRequestMergeStateStatus.UNSTABLE -> summaryChecksState
      GHPullRequestMergeStateStatus.BEHIND -> ChecksState.BLOCKING_BEHIND
      GHPullRequestMergeStateStatus.BLOCKED -> {
        val requiredContexts = baseBranchProtectionRules?.requiredStatusChecks?.contexts.orEmpty()
        if (requiredContexts.isEmpty()
            || contexts
              .filter { it.state == GHCommitStatusContextState.SUCCESS }
              .map { it.context }
              .containsAll(requiredContexts)) {
          summaryChecksState
        }
        else ChecksState.BLOCKING_FAILING
      }
    }

    return GHPRMergeabilityState(details.number, details.headRefOid, details.url,
                                 hasConflicts,
                                 failedChecks, pendingChecks, successfulChecks,
                                 checksState,
                                 canBeMerged, mergeabilityData.canBeRebased)
  }

  private fun getChecksSummaryState(failed: Int, pending: Int, successful: Int): ChecksState {
    return when {
      failed > 0 -> ChecksState.FAILING
      pending > 0 -> ChecksState.PENDING
      successful > 0 -> ChecksState.SUCCESSFUL
      else -> ChecksState.NONE
    }
  }
}