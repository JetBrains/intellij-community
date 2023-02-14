// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestApprovalRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.net.http.HttpRequest
import java.net.http.HttpResponse

suspend fun GitLabApi.loadMergeRequests(project: GitLabProjectCoordinates,
                                        searchQuery: String): HttpResponse<out List<GitLabMergeRequestShortRestDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").withQuery(searchQuery)
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.loadMergeRequest(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId
): HttpResponse<out GitLabMergeRequestDTO?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.getMergeRequest, parameters)
  return loadGQLResponse(request, GitLabMergeRequestDTO::class.java, "project", "mergeRequest")
}

suspend fun GitLabApi.loadMergeRequestStateEvents(project: GitLabProjectCoordinates,
                                                  mr: GitLabMergeRequestId)
  : HttpResponse<out List<GitLabResourceStateEventDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").resolveRelative(mr.iid).resolveRelative("resource_state_events")
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.loadMergeRequestLabelEvents(project: GitLabProjectCoordinates,
                                                  mr: GitLabMergeRequestId)
  : HttpResponse<out List<GitLabResourceLabelEventDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").resolveRelative(mr.iid).resolveRelative("resource_label_events")
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.loadMergeRequestMilestoneEvents(project: GitLabProjectCoordinates,
                                                      mr: GitLabMergeRequestId)
  : HttpResponse<out List<GitLabResourceMilestoneEventDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").resolveRelative(mr.iid).resolveRelative("resource_milestone_events")
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.mergeRequestApprove(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId
): HttpResponse<out GitLabMergeRequestApprovalRestDTO> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mergeRequestId.iid)
    .resolveRelative("approve")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return loadJsonValue(request, GitLabMergeRequestApprovalRestDTO::class.java)
}

suspend fun GitLabApi.mergeRequestUnApprove(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId
): HttpResponse<out GitLabMergeRequestApprovalRestDTO> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mergeRequestId.iid)
    .resolveRelative("unapprove")
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return loadJsonValue(request, GitLabMergeRequestApprovalRestDTO::class.java)
}

suspend fun GitLabApi.mergeRequestUpdate(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId,
  state: GitLabMergeRequestNewState,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
    "state" to state
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.mergeRequestUpdate, parameters)
  return loadGQLResponse(request, GitLabMergeRequestResult::class.java, "mergeRequestUpdate")
}

suspend fun GitLabApi.mergeRequestSetReviewers(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId,
  reviewers: List<GitLabUserDTO>
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
    "reviewerUsernames" to reviewers.map { it.username }
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.mergeRequestSetReviewers, parameters)
  return loadGQLResponse(request, GitLabMergeRequestResult::class.java, "mergeRequestSetReviewers")
}

suspend fun GitLabApi.mergeRequestAccept(
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
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.mergeRequestAccept, parameters)
  return loadGQLResponse(request, GitLabMergeRequestResult::class.java, "mergeRequestAccept")
}

suspend fun GitLabApi.mergeRequestSetDraft(
  project: GitLabProjectCoordinates,
  mergeRequestId: GitLabMergeRequestId,
  isDraft: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid,
    "isDraft" to isDraft
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.mergeRequestSetDraft, parameters)
  return loadGQLResponse(request, GitLabMergeRequestResult::class.java, "mergeRequestSetDraft")
}

private class GitLabMergeRequestResult(mergeRequest: GitLabMergeRequestDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>(errors) {
  override val value = mergeRequest
}