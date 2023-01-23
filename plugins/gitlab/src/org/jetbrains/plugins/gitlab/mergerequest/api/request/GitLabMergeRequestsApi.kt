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
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
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
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mergeRequestId" to mergeRequestId.iid
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.getMergeRequest, parameters)
  return loadGQLResponse(request, GitLabMergeRequestResult::class.java, "project")
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

private class GitLabMergeRequestResult(mergeRequest: GitLabMergeRequestDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>(errors) {
  override val value = mergeRequest
}