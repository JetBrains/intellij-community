// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserRestDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.awt.Image
import java.net.http.HttpResponse

@SinceGitLab("7.0", note = "No exact version")
suspend fun GitLabApi.Rest.getCurrentUser(): HttpResponse<out GitLabUserRestDTO> {
  val uri = server.restApiUri.resolveRelative("user")
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_CURRENT_USER) {
    loadJsonValue(request)
  }
}

@SinceGitLab("12.5", note = "No exact version")
suspend fun GitLabApi.GraphQL.getCurrentUser(): GitLabUserDTO {
  val request = gitLabQuery(GitLabGQLQuery.GET_CURRENT_USER)
  return withErrorStats(GitLabGQLQuery.GET_CURRENT_USER) {
    loadResponse<GitLabUserDTO>(request, "currentUser").body()
    ?: throw IllegalStateException("Unable to load current user")
  }
}

suspend fun GitLabApi.loadImage(uri: String): Image {
  val request = request(uri).GET().build()
  return loadImage(request).body()
}
