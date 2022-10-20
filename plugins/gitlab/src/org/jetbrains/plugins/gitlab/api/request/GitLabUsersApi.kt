// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

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

suspend fun GitLabApi.getProjectMembers(project: GitLabProjectCoordinates): List<GitLabMemberDTO> {
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.getProject, mapOf(
    "fullPath" to project.projectPath.fullPath()
  ))
  val members = loadGQLResponse(request, ProjectMembersConnection::class.java, "project", "projectMembers").body()
  return members?.nodes ?: emptyList()
}

suspend fun GitLabApi.loadImage(uri: String): Image {
  val request = request(uri).GET().build()
  return loadImage(request).body()
}