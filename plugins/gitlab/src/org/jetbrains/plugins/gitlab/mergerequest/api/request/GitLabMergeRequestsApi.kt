// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceLabelEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceMilestoneEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceStateEventDTO
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.net.http.HttpResponse

suspend fun GitLabApi.loadMergeRequests(project: GitLabProjectCoordinates,
                                        searchQuery: String): HttpResponse<out List<GitLabMergeRequestShortDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").withQuery(searchQuery)
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.loadAllMergeRequestDiscussions(project: GitLabProjectCoordinates,
                                                     mr: GitLabMergeRequestId): Flow<List<GitLabDiscussionDTO>> =
  ApiPageUtil.createGQLPagesFlow {
    val parameters = it.asParameters() + mapOf(
      "projectId" to project.projectPath.fullPath(),
      "mriid" to mr.iid
    )
    val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.getMergeRequestDiscussions, parameters)
    loadGQLResponse(request, DiscussionConnection::class.java, "project", "mergeRequest", "discussions").body()
  }

private class DiscussionConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabDiscussionDTO>)
  : GraphQLConnectionDTO<GitLabDiscussionDTO>(pageInfo, nodes)

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