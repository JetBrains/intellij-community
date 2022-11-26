// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.page.GraphQLPagesLoader
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabLabelDTO

suspend fun GitLabApi.loadAllProjectLabels(project: GitLabProjectCoordinates): List<GitLabLabelDTO> {
  val pagesLoader = GraphQLPagesLoader<GitLabLabelDTO> { pagination ->
    val parameters = GraphQLPagesLoader.arguments(pagination) + mapOf(
      "fullPath" to project.projectPath.fullPath()
    )
    val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.getProjectLabels, parameters)
    loadGQLResponse(request, LabelConnection::class.java, "project", "labels").body()
  }

  return pagesLoader.loadAll()
}

suspend fun GitLabApi.getAllProjectMembers(project: GitLabProjectCoordinates): List<GitLabMemberDTO> {
  val pagesLoader = GraphQLPagesLoader<GitLabMemberDTO> { pagination ->
    val parameters = GraphQLPagesLoader.arguments(pagination) + mapOf(
      "fullPath" to project.projectPath.fullPath()
    )
    val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.getProjectMembers, parameters)
    loadGQLResponse(request, ProjectMembersConnection::class.java, "project", "projectMembers").body()
  }

  return pagesLoader.loadAll()
}

private class LabelConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabLabelDTO>)
  : GraphQLConnectionDTO<GitLabLabelDTO>(pageInfo, nodes)

private class ProjectMembersConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabMemberDTO>)
  : GraphQLConnectionDTO<GitLabMemberDTO>(pageInfo, nodes)