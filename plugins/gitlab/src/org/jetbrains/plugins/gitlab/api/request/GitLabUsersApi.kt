// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserRestDTO
import org.jetbrains.plugins.gitlab.api.gitLabQuery
import java.awt.Image
import java.net.http.HttpResponse

suspend fun GitLabApi.Rest.getCurrentUser(server: GitLabServerPath): HttpResponse<out GitLabUserRestDTO> {
  val uri = server.restApiUri.resolveRelative("user")
  val request = request(uri).GET().build()
  return loadJsonValue(request)
}

suspend fun GitLabApi.GraphQL.getCurrentUser(server: GitLabServerPath): GitLabUserDTO? {
  val request = gitLabQuery(server, GitLabGQLQuery.GET_CURRENT_USER)
  return loadResponse<GitLabUserDTO>(request, "currentUser").body()
}

suspend fun GitLabApi.loadImage(uri: String): Image {
  val request = request(uri).GET().build()
  return loadImage(request).body()
}
