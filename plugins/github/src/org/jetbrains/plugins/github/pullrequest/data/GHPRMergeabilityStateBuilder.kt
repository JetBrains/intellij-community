// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.util.containers.nullize
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeStateStatus
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeabilityData
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeableState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState.ChecksState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService

class GHPRMergeabilityStateBuilder(private val headRefOid: String, private val prHtmlUrl: String,
                                   private val mergeabilityData: GHPullRequestMergeabilityData) {

  private var canOverrideAsAdmin = false
  private var requiredContexts = emptyList<String>()
  private var isRestricted = false
  private var requiredApprovingReviewsCount = 0

  fun withRestrictions(securityService: GHPRSecurityService, baseBranchProtectionRules: GHBranchProtectionRules) {
    canOverrideAsAdmin = baseBranchProtectionRules.enforceAdmins?.enabled == false &&
                         securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.ADMIN)
    requiredContexts = baseBranchProtectionRules.requiredStatusChecks?.contexts.orEmpty()

    val restrictions = baseBranchProtectionRules.restrictions
    val allowedLogins = restrictions?.users?.map { it.login }.nullize()
    val allowedTeams = restrictions?.teams?.map { it.slug }.nullize()
    isRestricted = (allowedLogins != null && !allowedLogins.contains(securityService.currentUser.login)) ||
                   (allowedTeams != null && !securityService.isUserInAnyTeam(allowedTeams))

    requiredApprovingReviewsCount = baseBranchProtectionRules.requiredPullRequestReviews?.requiredApprovingReviewCount ?: 0
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
        GHCommitCheckSuiteStatusState.PENDING,
        GHCommitCheckSuiteStatusState.WAITING,
        GHCommitCheckSuiteStatusState.REQUESTED -> pendingChecks++
        GHCommitCheckSuiteStatusState.COMPLETED -> {
          when (suite.conclusion) {
            GHCommitCheckSuiteConclusion.ACTION_REQUIRED -> failedChecks++
            GHCommitCheckSuiteConclusion.CANCELLED -> successfulChecks++
            GHCommitCheckSuiteConclusion.FAILURE -> failedChecks++
            GHCommitCheckSuiteConclusion.NEUTRAL -> successfulChecks++
            GHCommitCheckSuiteConclusion.SKIPPED -> successfulChecks++
            GHCommitCheckSuiteConclusion.STALE -> failedChecks++
            GHCommitCheckSuiteConclusion.STARTUP_FAILURE -> failedChecks++
            GHCommitCheckSuiteConclusion.SUCCESS -> successfulChecks++
            GHCommitCheckSuiteConclusion.TIMED_OUT -> failedChecks++
            null -> failedChecks++
          }
        }
      }
    }

    val canBeMerged = when {
      mergeabilityData.mergeStateStatus.canMerge() -> true
      mergeabilityData.mergeStateStatus.adminCanMerge() && canOverrideAsAdmin -> true
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

    val actualRequiredApprovingReviewsCount =
      if (mergeabilityData.mergeStateStatus == GHPullRequestMergeStateStatus.BLOCKED && !isRestricted && checksState != ChecksState.BLOCKING_FAILING)
        requiredApprovingReviewsCount
      else 0

    return GHPRMergeabilityState(headRefOid, prHtmlUrl,
                                 hasConflicts,
                                 failedChecks, pendingChecks, successfulChecks,
                                 checksState,
                                 canBeMerged, mergeabilityData.canBeRebased,
                                 isRestricted, actualRequiredApprovingReviewsCount)
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