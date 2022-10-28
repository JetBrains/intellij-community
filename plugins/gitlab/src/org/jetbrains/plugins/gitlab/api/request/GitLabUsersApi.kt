// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.page.GraphQLPagesLoader
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.connection.ProjectMembersConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import java.awt.Image

suspend fun GitLabApi.getCurrentUser(server: GitLabServerPath): GitLabUserDTO? {
  val request = gqlQuery(server.gqlApiUri, GitLabGQLQueries.getCurrentUser)
  return loadGQLResponse(request, GitLabUserDTO::class.java, "currentUser").body()
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

suspend fun GitLabApi.loadImage(uri: String): Image {
  val request = request(uri).GET().build()
  return loadImage(request).body()
}