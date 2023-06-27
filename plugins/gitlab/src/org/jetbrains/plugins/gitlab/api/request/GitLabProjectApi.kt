// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.util.resolveRelative
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserRestDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpResponse

suspend fun GitLabApi.GraphQL.loadAllProjectLabels(project: GitLabProjectCoordinates): List<GitLabLabelDTO> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters() + mapOf(
      "fullPath" to project.projectPath.fullPath()
    )
    val request = gitLabQuery(project.serverPath, GitLabGQLQuery.GET_PROJECT_LABELS, parameters)
    withErrorStats(project.serverPath, GitLabGQLQuery.GET_PROJECT_LABELS) {
      loadResponse<LabelConnection>(request, "project", "labels").body()
    }
  }.map { it.nodes }.foldToList()

fun getProjectUsersURI(project: GitLabProjectCoordinates) = project.restApiUri.resolveRelative("users")

suspend fun GitLabApi.Rest.getProjectUsers(serverPath: GitLabServerPath, uri: URI): HttpResponse<out List<GitLabUserRestDTO>> {
  val request = request(uri).GET().build()
  return withErrorStats(serverPath, GitLabApiRequestName.REST_GET_PROJECT_USERS) {
    loadJsonList(request)
  }
}

private class LabelConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabLabelDTO>)
  : GraphQLConnectionDTO<GitLabLabelDTO>(pageInfo, nodes)