// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.HttpClientUtil.inflateAndReadWithErrorHandlingAndLogging
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerMetadataDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerVersionDTO
import java.net.http.HttpResponse

private val LOG = logger<GitLabApi>()

/**
 * Checks whether the given server looks like and acts like a GitLab server.
 *
 * Note that this depends on the /projects API endpoint being unauthenticated.
 * It has been this since at least 9.0 and it is still. If GitLab changes the
 * visibility of projects, this function will need to be replaced.
 *
 * Maybe we can combine behavioural checks in the future if this turns out to
 * be insufficient to recognize GitLab server.
 */
@SinceGitLab("9.0", note = "Older, but no need to figure out exactly")
suspend fun GitLabApi.Rest.checkIsGitLabServer(): Boolean {
  val uri = server.restApiUri.resolveRelative("projects?page=1&per_page=1")
  val request = request(uri).GET().build()
  val bodyHandler = inflateAndReadWithErrorHandlingAndLogging(LOG, request) { reader, _ ->
    reader.readText()
  }
  return try {
    sendAndAwaitCancellable(request, bodyHandler)
    true
  }
  catch (e: HttpStatusErrorException) {
    false
  }
}

// should not have statistics to avoid recursion
@SinceGitLab("15.2")
suspend fun GitLabApi.Rest.getServerMetadata(): HttpResponse<out GitLabServerMetadataDTO> {
  val uri = server.restApiUri.resolveRelative("metadata")
  val request = request(uri).GET().build()
  return loadJsonValue(request)
}

@SinceGitLab("8.13", deprecatedIn = "15.5")
suspend fun GitLabApi.Rest.getServerVersion(): HttpResponse<out GitLabServerVersionDTO> {
  val uri = server.restApiUri.resolveRelative("version")
  val request = request(uri).GET().build()
  return loadJsonValue(request)
}

@SinceGitLab("8.13", note = "Enterprise/Community only detectable after 15.6, community is assumed by default")
suspend fun GitLabApi.Rest.getServerMetadataOrVersion(): GitLabServerMetadataDTO =
  try {
    getServerMetadata().body()
  }
  catch (e: Exception) {
    getServerVersion().body().let {
      // TODO: find a way to check CE vs EE
      GitLabServerMetadataDTO(it.version, it.revision, false)
    }
  }
