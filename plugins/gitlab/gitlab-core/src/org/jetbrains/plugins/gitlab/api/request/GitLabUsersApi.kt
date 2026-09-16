// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.request
import com.intellij.collaboration.api.sendAndReadBody
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserRestDTO
import org.jetbrains.plugins.gitlab.api.loadValue
import org.jetbrains.plugins.gitlab.api.runQuery
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.awt.Image
import java.net.URI
import javax.imageio.ImageIO

@SinceGitLab("7.0", note = "No exact version")
suspend fun GitLabApi.Rest.getCurrentUser(): GitLabUserRestDTO {
  val uri = server.restApiUri.resolveRelative("user")
  return request(uri).GET().build()
    .loadValue(GitLabApiRequestName.REST_GET_CURRENT_USER)
}

@SinceGitLab("12.5", note = "No exact version")
suspend fun GitLabApi.GraphQL.getCurrentUser(): GitLabUserDTO =
  runQuery<GitLabUserDTO>(GitLabGQLQuery.GET_CURRENT_USER, "currentUser")
  ?: throw IllegalStateException("Unable to load current user")

suspend fun GitLabApi.loadImage(uri: String): Image {
  val request = request(uri).GET().build()
  return sendAndReadBody(request) { _, _ ->
    ImageIO.read(this)
  }
}

suspend fun GitLabApi.loadImage(uri: URI): Image {
  val request = request(uri).GET().build()
  return sendAndReadBody(request) { _, _ ->
    ImageIO.read(this)
  }
}
