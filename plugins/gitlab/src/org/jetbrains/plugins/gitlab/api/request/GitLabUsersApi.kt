// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import java.awt.Image

suspend fun GitLabApi.getCurrentUser(server: GitLabServerPath): GitLabUserDTO? {
  val request = gqlQuery(server.gqlApiUri, GitLabGQLQueries.getCurrentUser)
  return loadGQLResponse(request, GitLabUserDTO::class.java, "currentUser").body()
}

suspend fun GitLabApi.loadImage(uri: String): Image {
  val request = request(uri).GET().build()
  return loadImage(request).body()
}