// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabEdition
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.api.GitLabGidData
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabReviewerDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.gitLabQuery
import org.jetbrains.plugins.gitlab.api.projectApiUrl
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.api.withQuery
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestByBranchDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestMetricsDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestRebaseDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.asApiParameter
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Creates a merge request
 *
 * Note: reviewer_ids parameter has different behavior depending on the user's subscription plan
 *  [org.jetbrains.plugins.gitlab.api.dto.GitLabPlan.FREE] -- sets only one reviewer from the list (the last one)
 *  OTHER -- sets all reviewers from the list
 */
@SinceGitLab("14.0", note = "No exact version, but definitely exists in minimal")
suspend fun GitLabApi.Rest.createMergeRequest(
  projectId: String,
  sourceBranch: String,
  targetBranch: String,
  title: String,
  description: String? = null,
  reviewerIds: List<String>? = null,
  assigneeIds: List<String>? = null,
  labels: List<String>? = null,
  squashBeforeMerge: Boolean? = null,
  removeSourceBranch: Boolean? = null,
): HttpResponse<out GitLabMergeRequestShortRestDTO> {
  val uri = projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .withQuery {
      "source_branch" eq sourceBranch
      "target_branch" eq targetBranch
      "title" eq title
      "description" eq description
      "reviewer_ids" eq reviewerIds
      "assignee_ids" eq assigneeIds
      "labels" eq labels
      "squash" eq squashBeforeMerge
      "remove_source_branch" eq removeSourceBranch
    }
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_MERGE_REQUEST) {
    loadJsonValue(request)
  }
}

@SinceGitLab("7.0", note = "?search available since 10.4, ?scope since 9.5")
fun GitLabApi.Rest.getMergeRequestListURI(
  projectId: String,
  searchQuery: String
): URI =
  projectApiUrl(projectId).resolveRelative("merge_requests").withQuery(searchQuery)

@SinceGitLab("12.0")
suspend fun GitLabApi.GraphQL.loadMergeRequest(
  projectPath: GitLabProjectPath,
  mrIid: String
): HttpResponse<out GitLabMergeRequestDTO?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "mergeRequestId" to mrIid
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_MERGE_REQUEST, parameters)
  return withErrorStats(GitLabGQLQuery.GET_MERGE_REQUEST) {
    loadResponse(request, "project", "mergeRequest")
  }
}

@ApiStatus.Internal
@SinceGitLab("14.0", note = "No exact version")
suspend fun GitLabApi.GraphQL.getMergeRequestMetrics(
  projectPath: GitLabProjectPath,
  username: String,
): HttpResponse<out GitLabMergeRequestMetricsDTO?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "username" to username
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_MERGE_REQUEST_METRICS, parameters)
  return withErrorStats(GitLabGQLQuery.GET_MERGE_REQUEST_METRICS) {
    loadResponse(request, "project")
  }
}

@SinceGitLab("13.1")
suspend fun GitLabApi.GraphQL.findMergeRequestsByBranch(
  projectPath: GitLabProjectPath,
  state: GitLabMergeRequestState,
  sourceBranch: String,
  targetBranch: String? = null
): HttpResponse<out GraphQLConnectionDTO<GitLabMergeRequestByBranchDTO>?> {
  val parameters = mutableMapOf(
    "projectId" to projectPath.fullPath(),
    "state" to state.asApiParameter(),
    "sourceBranches" to listOf(sourceBranch),
    "targetBranches" to targetBranch?.let { listOf(it) }
  )
  val request = gitLabQuery(GitLabGQLQuery.FIND_MERGE_REQUESTS, parameters)
  return withErrorStats(GitLabGQLQuery.FIND_MERGE_REQUESTS) {
    loadResponse<MergeRequestsByBranchConnection>(request, "project", "mergeRequests")
  }
}

private class MergeRequestsByBranchConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabMergeRequestByBranchDTO>)
  : GraphQLConnectionDTO<GitLabMergeRequestByBranchDTO>(pageInfo, nodes)

@SinceGitLab("13.2")
fun GitLabApi.Rest.getMergeRequestStateEventsUri(projectId: String, mrIid: String): URI =
  projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("resource_state_events")

@SinceGitLab("11.4", note = "Maybe released in 11.3-rc5")
fun GitLabApi.Rest.getMergeRequestLabelEventsUri(projectId: String, mrIid: String): URI =
  projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("resource_label_events")

@SinceGitLab("13.1")
fun GitLabApi.Rest.getMergeRequestMilestoneEventsUri(projectId: String, mrIid: String): URI =
  projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("resource_milestone_events")

@SinceGitLab("10.6", editions = [GitLabEdition.Enterprise])
@SinceGitLab("13.3", editions = [GitLabEdition.Community], note = "Maybe released in 13.2-rc42 or so")
suspend fun GitLabApi.Rest.mergeRequestApprove(
  projectId: String,
  mrIid: String
): HttpResponse<out Unit> {
  val uri = projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("approve")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_APPROVE_MERGE_REQUEST) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("10.6", editions = [GitLabEdition.Enterprise])
@SinceGitLab("13.3", editions = [GitLabEdition.Community], note = "Maybe released in 13.2-rc42 or so")
suspend fun GitLabApi.Rest.mergeRequestUnApprove(
  projectId: String,
  mrIid: String
): HttpResponse<out Unit> {
  val uri = projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("unapprove")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_UNAPPROVE_MERGE_REQUEST) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("11.6")
suspend fun GitLabApi.Rest.mergeRequestRebase(
  projectId: String,
  mrIid: String
): HttpResponse<out GitLabMergeRequestRebaseDTO> {
  val uri = projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("rebase")
  val request = request(uri).PUT(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_REBASE_MERGE_REQUEST) {
    loadJsonValue<GitLabMergeRequestRebaseDTO>(request)
  }
}

@SinceGitLab("13.9")
suspend fun GitLabApi.GraphQL.mergeRequestUpdate(
  projectPath: GitLabProjectPath,
  mrIid: String,
  state: GitLabMergeRequestNewState,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "state" to state
  )
  val request = gitLabQuery(GitLabGQLQuery.MERGE_REQUEST_UPDATE, parameters)
  return withErrorStats(GitLabGQLQuery.MERGE_REQUEST_UPDATE) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestUpdate")
  }
}

/**
 * Sets the reviewers in the Merge Request
 *
 * Note: this request has different behavior depending on the user's subscription plan
 *  [org.jetbrains.plugins.gitlab.api.dto.GitLabPlan.FREE] -- sets only one reviewer from the list (the last one)
 *  OTHER -- sets all reviewers from the list
 */
@SinceGitLab("13.8")
suspend fun GitLabApi.Rest.mergeRequestSetReviewers(
  projectId: String,
  mrIid: String,
  reviewers: List<GitLabUserDTO>
): HttpResponse<out Unit> {
  val reviewerIds = reviewers.map { GitLabGidData(it.id).guessRestId() }
  val uri = projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .withQuery {
      "reviewer_ids" eq reviewerIds
    }
  val request = request(uri)
    .PUT(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_PUT_MERGE_REQUEST_REVIEWERS) {
    sendAndAwaitCancellable(request)
  }
}

/**
 * Sets the reviewers in the Merge Request
 *
 * Note: this request has different behavior depending on the user's subscription plan
 *  [org.jetbrains.plugins.gitlab.api.dto.GitLabPlan.FREE] -- sets only one reviewer from the list (the last one)
 *  OTHER -- sets all reviewers from the list
 */
@SinceGitLab("15.3")
suspend fun GitLabApi.GraphQL.mergeRequestSetReviewers(
  projectPath: GitLabProjectPath,
  mrIid: String,
  reviewers: List<GitLabUserDTO>
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "reviewerUsernames" to reviewers.map { it.username }
  )
  val request = gitLabQuery(GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS, parameters)
  return withErrorStats(GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestSetReviewers")
  }
}

@SinceGitLab("13.10")
suspend fun GitLabApi.GraphQL.mergeRequestAccept(
  projectPath: GitLabProjectPath,
  mrIid: String,
  commitMessage: String?,
  sha: String,
  shouldRemoveSourceBranch: Boolean,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "commitMessage" to commitMessage,
    "sha" to sha,
    "withSquash" to false,
    "shouldRemoveSourceBranch" to shouldRemoveSourceBranch
  )
  val request = gitLabQuery(GitLabGQLQuery.MERGE_REQUEST_ACCEPT, parameters)
  return withErrorStats(GitLabGQLQuery.MERGE_REQUEST_ACCEPT) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestAccept")
  }
}

@SinceGitLab("13.10")
suspend fun GitLabApi.GraphQL.mergeRequestAcceptSquash(
  projectPath: GitLabProjectPath,
  mrIid: String,
  commitMessage: String?,
  squashCommitMessage: String?,
  sha: String,
  shouldRemoveSourceBranch: Boolean,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "commitMessage" to commitMessage,
    "squashCommitMessage" to squashCommitMessage,
    "sha" to sha,
    "withSquash" to true,
    "shouldRemoveSourceBranch" to shouldRemoveSourceBranch
  )
  val request = gitLabQuery(GitLabGQLQuery.MERGE_REQUEST_ACCEPT, parameters)
  return withErrorStats(GitLabGQLQuery.MERGE_REQUEST_ACCEPT) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestAccept")
  }
}

@SinceGitLab("13.12")
suspend fun GitLabApi.GraphQL.mergeRequestSetDraft(
  projectPath: GitLabProjectPath,
  mrIid: String,
  isDraft: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "isDraft" to isDraft
  )
  val request = gitLabQuery(GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT, parameters)
  return withErrorStats(GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestSetDraft")
  }
}

@SinceGitLab("13.9")
suspend fun GitLabApi.GraphQL.mergeRequestReviewerRereview(
  projectPath: GitLabProjectPath,
  mrIid: String,
  reviewer: GitLabReviewerDTO
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "userId" to reviewer.id
  )
  val request = gitLabQuery(GitLabGQLQuery.MERGE_REQUEST_REVIEWER_REREVIEW, parameters)
  return withErrorStats(GitLabGQLQuery.MERGE_REQUEST_REVIEWER_REREVIEW) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestReviewerRereview")
  }
}

private class GitLabMergeRequestResult(mergeRequest: GitLabMergeRequestDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>(errors) {
  override val value = mergeRequest
}