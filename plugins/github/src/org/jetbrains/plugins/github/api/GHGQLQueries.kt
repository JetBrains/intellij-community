// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

object GHGQLQueries {
  const val findOrganizationTeams = "findOrganizationTeams"
  const val findRepositoryPermission = "findRepositoryPermission"
  const val commentBody = "commentBody"
  const val updateIssueComment = "updateIssueComment"
  const val deleteIssueComment = "deleteIssueComment"
  const val issueSearch = "issueSearch"
  const val findPullRequest = "findPullRequest"
  const val pullRequestTimeline = "pullRequestTimeline"
  const val pullRequestReviewThreads = "pullRequestReviewThreads"
  const val pullRequestCommits = "pullRequestCommits"
  const val pullRequestMergeabilityData = "findPullRequestMergeability"
  const val createReview = "createReview"
  const val submitReview = "submitReview"
  const val updateReview = "updateReview"
  const val deleteReview = "deleteReview"
  const val pendingReview = "findPendingReview"
  const val addReviewComment = "addReviewComment"
  const val deleteReviewComment = "deleteReviewComment"
  const val updateReviewComment = "updateReviewComment"
  const val resolveReviewThread = "resolveReviewThread"
  const val unresolveReviewThread = "unresolveReviewThread"
}