// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

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
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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
  mergeRequestId: GitLabMergeRequestId
): HttpResponse<out GitLabMergeRequestDTO?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.GET_MERGE_REQUEST, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.GET_MERGE_REQUEST) {
    loadResponse(request, "project", "mergeRequest")
  }
}

fun getMergeRequestStateEventsUri(project: GitLabProjectCoordinates, mr: GitLabMergeRequestId): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mr.iid).resolveRelative("resource_state_events")

fun getMergeRequestLabelEventsUri(project: GitLabProjectCoordinates, mr: GitLabMergeRequestId): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mr.iid).resolveRelative("resource_label_events")

fun getMergeRequestMilestoneEventsUri(project: GitLabProjectCoordinates, mr: GitLabMergeRequestId): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mr.iid).resolveRelative("resource_milestone_events")

suspend fun GitLabApi.Rest.mergeRequestApprove(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId
): HttpResponse<out Unit> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mergeRequestId.iid)
    .resolveRelative("approve")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_APPROVE_MERGE_REQUEST) {
    sendAndAwaitCancellable(request)
  }
}

suspend fun GitLabApi.Rest.mergeRequestUnApprove(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId
): HttpResponse<out Unit> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mergeRequestId.iid)
    .resolveRelative("unapprove")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_UNAPPROVE_MERGE_REQUEST) {
    sendAndAwaitCancellable(request)
  }
}

suspend fun GitLabApi.Rest.mergeRequestRebase(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId
): HttpResponse<out GitLabMergeRequestRebaseDTO> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mergeRequestId.iid)
    .resolveRelative("rebase")
  val request = request(uri).PUT(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_REBASE_MERGE_REQUEST) {
    loadJsonValue<GitLabMergeRequestRebaseDTO>(request)
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestUpdate(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId,
  state: GitLabMergeRequestNewState,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
    "state" to state
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_UPDATE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_UPDATE) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestUpdate")
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestSetReviewers(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId,
  reviewers: List<GitLabUserDTO>
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
    "reviewerUsernames" to reviewers.map { it.username }
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestSetReviewers")
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestAccept(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId,
  commitMessage: String,
  sha: String,
  withSquash: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
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
  mergeRequestId: GitLabMergeRequestId,
  isDraft: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
    "isDraft" to isDraft
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT) {
    loadResponse<GitLabMergeRequestResult>(request, "mergeRequestSetDraft")
  }
}

suspend fun GitLabApi.GraphQL.mergeRequestReviewerRereview(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId,
  reviewer: GitLabReviewerDTO
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
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