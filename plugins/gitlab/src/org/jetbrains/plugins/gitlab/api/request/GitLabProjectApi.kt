// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.gitLabQuery

suspend fun GitLabApi.GraphQL.loadAllProjectLabels(project: GitLabProjectCoordinates): List<GitLabLabelDTO> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters() + mapOf(
      "fullPath" to project.projectPath.fullPath()
    )
    val request = gitLabQuery(project.serverPath, GitLabGQLQuery.GET_PROJECT_LABELS, parameters)
    loadResponse<LabelConnection>(request, "project", "labels").body()
  }.map { it.nodes }.foldToList()

suspend fun GitLabApi.GraphQL.getAllProjectMembers(project: GitLabProjectCoordinates): List<GitLabMemberDTO> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters() + mapOf(
      "fullPath" to project.projectPath.fullPath()
    )
    val request = gitLabQuery(project.serverPath, GitLabGQLQuery.GET_PROJECT_MEMBERS, parameters)
    loadResponse<ProjectMembersConnection>(request, "project", "projectMembers").body()
  }.map { it.nodes }.foldToList().filterNotNull()

private class LabelConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabLabelDTO>)
  : GraphQLConnectionDTO<GitLabLabelDTO>(pageInfo, nodes)

private class ProjectMembersConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabMemberDTO?>)
  : GraphQLConnectionDTO<GitLabMemberDTO?>(pageInfo, nodes)