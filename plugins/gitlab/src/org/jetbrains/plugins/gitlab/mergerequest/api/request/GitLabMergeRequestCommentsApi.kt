// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.page.ApiPageUtil
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.net.http.HttpResponse

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

suspend fun GitLabApi.changeMergeRequestDiscussionResolve(
  project: GitLabProjectCoordinates,
  discussionId: String,
  resolved: Boolean
): HttpResponse<out GitLabDiscussionDTO?> {
  val parameters = mapOf(
    "discussionId" to discussionId,
    "resolved" to resolved
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.toggleMergeRequestDiscussionResolve, parameters)
  return loadGQLResponse(request, GitLabDiscussionDTO::class.java, "discussionToggleResolve", "discussion")
}