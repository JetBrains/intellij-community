// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJobState
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteConclusion
import org.jetbrains.plugins.github.api.data.GHCommitStatusContextState
import org.jetbrains.plugins.github.api.data.GHRefUpdateRule
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeStateStatus
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeabilityData
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeableState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState.ChecksState

class GHPRMergeabilityStateBuilder(private val headRefOid: String, private val prHtmlUrl: String,
                                   private val mergeabilityData: GHPullRequestMergeabilityData) {

  private var canOverrideAsAdmin = false
  private var requiredContexts = emptyList<String>()
  private var isRestricted = false
  private var requiredApprovingReviewsCount = 0

  fun withRestrictions(currentUserIsAdmin: Boolean, refUpdateRule: GHRefUpdateRule) {
    // TODO: load via PullRequest.viewerCanMergeAsAdmin when we update the min version
    canOverrideAsAdmin = /*baseBranchProtectionRules.enforceAdmins?.enabled == false &&*/currentUserIsAdmin
    requiredContexts = refUpdateRule.requiredStatusCheckContexts.filterNotNull()
    isRestricted = !refUpdateRule.viewerCanPush
    requiredApprovingReviewsCount = refUpdateRule.requiredApprovingReviewCount ?: 0
  }

  fun build(): GHPRMergeabilityState {
    val hasConflicts = when (mergeabilityData.mergeable) {
      GHPullRequestMergeableState.MERGEABLE -> false
      GHPullRequestMergeableState.CONFLICTING -> true
      GHPullRequestMergeableState.UNKNOWN -> null
    }

    val ciJobs = mutableListOf<CodeReviewCIJob>()
    val lastCommit = mergeabilityData.commits.lastOrNull()?.commit
    val contexts = lastCommit?.status?.contexts.orEmpty()
    contexts.forEach { context ->
      val status = CodeReviewCIJob(context.context, context.state.toCiState(), context.isRequired, context.targetUrl)
      ciJobs.add(status)
    }

    val checkSuites = lastCommit?.checkSuites.orEmpty()
    checkSuites.flatMap { checkSuite -> checkSuite.checkRuns }.forEach { checkRun ->
      val status = CodeReviewCIJob(checkRun.name, checkRun.conclusion.toCiState(), checkRun.isRequired, checkRun.url)
      ciJobs.add(status)
    }

    val canBeMerged = when {
      mergeabilityData.mergeStateStatus.canMerge() -> true
      mergeabilityData.mergeStateStatus.adminCanMerge() && canOverrideAsAdmin -> true
      else -> false
    }

    val checksState = when (mergeabilityData.mergeStateStatus) {
      GHPullRequestMergeStateStatus.CLEAN,
      GHPullRequestMergeStateStatus.DIRTY,
      GHPullRequestMergeStateStatus.DRAFT,
      GHPullRequestMergeStateStatus.HAS_HOOKS,
      GHPullRequestMergeStateStatus.UNKNOWN,
      GHPullRequestMergeStateStatus.UNSTABLE -> null
      GHPullRequestMergeStateStatus.BEHIND -> ChecksState.BLOCKING_BEHIND
      GHPullRequestMergeStateStatus.BLOCKED -> {
        if (requiredContexts.isEmpty()
            || contexts
              .filter { it.state == GHCommitStatusContextState.SUCCESS }
              .map { it.context }
              .containsAll(requiredContexts)) {
          null
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
                                 ciJobs,
                                 canBeMerged, mergeabilityData.canBeRebased,
                                 isRestricted, actualRequiredApprovingReviewsCount)
  }

  private fun GHCommitStatusContextState.toCiState(): CodeReviewCIJobState {
    return when (this) {
      GHCommitStatusContextState.ERROR,
      GHCommitStatusContextState.EXPECTED,
      GHCommitStatusContextState.FAILURE -> CodeReviewCIJobState.FAILED
      GHCommitStatusContextState.PENDING -> CodeReviewCIJobState.PENDING
      GHCommitStatusContextState.SUCCESS -> CodeReviewCIJobState.SUCCESS
    }
  }

  private fun GHCommitCheckSuiteConclusion?.toCiState(): CodeReviewCIJobState {
    return when (this) {
      null -> CodeReviewCIJobState.PENDING
      GHCommitCheckSuiteConclusion.ACTION_REQUIRED,
      GHCommitCheckSuiteConclusion.CANCELLED,
      GHCommitCheckSuiteConclusion.NEUTRAL,
      GHCommitCheckSuiteConclusion.STALE,
      GHCommitCheckSuiteConclusion.STARTUP_FAILURE,
      GHCommitCheckSuiteConclusion.TIMED_OUT,
      GHCommitCheckSuiteConclusion.FAILURE -> CodeReviewCIJobState.FAILED
      GHCommitCheckSuiteConclusion.SKIPPED -> CodeReviewCIJobState.SKIPPED
      GHCommitCheckSuiteConclusion.SUCCESS -> CodeReviewCIJobState.SUCCESS
    }
  }
}