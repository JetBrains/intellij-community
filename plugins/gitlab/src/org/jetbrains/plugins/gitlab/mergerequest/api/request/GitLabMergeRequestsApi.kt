// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabReviewerDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestRebaseDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestIidDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SinceGitLab("7.0", note = "?search available since 10.4, ?scope since 9.5")
suspend fun GitLabApi.Rest.loadMergeRequests(project: GitLabProjectCoordinates,
                                             searchQuery: String): HttpResponse<out List<GitLabMergeRequestShortRestDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").withQuery(searchQuery)
  val request = request(uri).GET().build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_GET_MERGE_REQUESTS) {
    loadJsonList(request)
  }
}

suspend fun GitLabApi.GraphQL.loadMergeRequest(
  project: GitLabProjectCoordinates,
  mrIid: String
): HttpResponse<out GitLabMergeRequestDTO?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mrIid
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.GET_MERGE_REQUEST, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.GET_MERGE_REQUEST) {
    loadResponse(request, "project", "mergeRequest")
  }
}

suspend fun GitLabApi.GraphQL.findMergeRequestsByBranch(project: GitLabProjectCoordinates, branch: String)
  : HttpResponse<out GraphQLConnectionDTO<GitLabMergeRequestIidDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "branch" to branch
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.FIND_MERGE_REQUESTS, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.FIND_MERGE_REQUESTS) {
    loadResponse<MergeRequestsConnection>(request, "project", "mergeRequests")
  }
}

private class MergeRequestsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabMergeRequestIidDTO>)
  : GraphQLConnectionDTO<GitLabMergeRequestIidDTO>(pageInfo, nodes)

@SinceGitLab("13.2")
fun getMergeRequestStateEventsUri(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mrIid).resolveRelative("resource_state_events")

@SinceGitLab("11.4", note = "Maybe released in 11.3-rc5")
fun getMergeRequestLabelEventsUri(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mrIid).resolveRelative("resource_label_events")

@SinceGitLab("13.1")
fun getMergeRequestMilestoneEventsUri(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mrIid).resolveRelative("resource_milestone_events")

@SinceGitLab("10.6", editions = [GitLabEdition.Enterprise])
@SinceGitLab("13.3", editions = [GitLabEdition.Community], note = "Maybe released in 13.2-rc42 or so")
suspend fun GitLabApi.Rest.mergeRequestApprove(
  project: GitLabProjectCoordinates,
  mrIid: String
): HttpResponse<out Unit> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("approve")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_APPROVE_MERGE_REQUEST) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("10.6", editions = [GitLabEdition.Enterprise])
@SinceGitLab("13.3", editions = [GitLabEdition.Community], note = "Maybe released in 13.2-rc42 or so")
suspend fun GitLabApi.Rest.mergeRequestUnApprove(
  project: GitLabProjectCoordinates,
  mrIid: String
): HttpResponse<out Unit> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("unapprove")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_UNAPPROVE_MERGE_REQUEST) {
    sendAndAwaitCancellable(request)
  }
}

suspend fun GitLabApi.Rest.mergeRequestRebase(
  project: GitLabProjectCoordinates,
  mrIid: String
): HttpResponse<out GitLabMergeRequestRebaseDTO> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("rebase")
  val request = request(uri).PUT(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_REBASE_MERGE_REQUEST) {
    loadJsonValue<GitLabMergeRequestRebaseDTO>(request)
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestUpdate(
  project: GitLabProjectCoordinates,
  mrIid: String,
  state: GitLabMergeRequestNewState,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "state" to state
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_UPDATE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_UPDATE) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestUpdate")
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestSetReviewers(
  project: GitLabProjectCoordinates,
  mrIid: String,
  reviewers: List<GitLabUserDTO>
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "reviewerUsernames" to reviewers.map { it.username }
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestSetReviewers")
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestAccept(
  project: GitLabProjectCoordinates,
  mrIid: String,
  commitMessage: String,
  sha: String,
  withSquash: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "commitMessage" to commitMessage,
    "sha" to sha,
    "withSquash" to withSquash
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_ACCEPT, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_ACCEPT) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestAccept")
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestSetDraft(
  project: GitLabProjectCoordinates,
  mrIid: String,
  isDraft: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "isDraft" to isDraft
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestSetDraft")
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestReviewerRereview(
  project: GitLabProjectCoordinates,
  mrIid: String,
  reviewer: GitLabReviewerDTO
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mrIid,
    "userId" to reviewer.id
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_REVIEWER_REREVIEW, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_REVIEWER_REREVIEW) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestReviewerRereview")
  }
}

private class GitLabMergeRequestResult(mergeRequest: GitLabMergeRequestDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>(errors) {
  override val value = mergeRequest
}