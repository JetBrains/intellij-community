// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerMetadataDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerVersionDTO
import java.net.http.HttpResponse

suspend fun GitLabApi.Rest.checkIsGitLabServer(server: GitLabServerPath): Boolean {
  val uri = server.restApiUri.resolveRelative("metadata")
  val request = request(uri).GET().build()
  val response = sendAndAwaitCancellable(request, HttpResponse.BodyHandlers.discarding())
  return response.headers().map().keys.any {
    it.contains("gitlab", true)
  }
}

// should not have statistics to avoid recursion
suspend fun GitLabApi.Rest.getServerMetadata(server: GitLabServerPath): HttpResponse<out GitLabServerMetadataDTO> {
  val uri = server.restApiUri.resolveRelative("metadata")
  val request = request(uri).GET().build()
  return loadJsonValue(request)
}

suspend fun GitLabApi.Rest.getServerVersion(server: GitLabServerPath): HttpResponse<out GitLabServerVersionDTO> {
  val uri = server.restApiUri.resolveRelative("version")
  val request = request(uri).GET().build()
  return loadJsonValue(request)
}

suspend fun GitLabApi.Rest.getServerMetadataOrVersion(server: GitLabServerPath): GitLabServerMetadataDTO =
  try {
    getServerMetadata(server).body()
  }
  catch (e: Exception) {
    getServerVersion(server).body().let {
      // TODO: find a way to check CE vs EE
      GitLabServerMetadataDTO(it.version, it.revision, false)
    }
  }
