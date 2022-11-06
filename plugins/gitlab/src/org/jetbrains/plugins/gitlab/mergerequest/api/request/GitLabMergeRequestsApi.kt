// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortDTO
import java.net.http.HttpResponse

suspend fun GitLabApi.loadMergeRequests(project: GitLabProjectCoordinates,
                                        searchQuery: String): HttpResponse<out List<GitLabMergeRequestShortDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").withQuery(searchQuery)
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.loadMergeRequests(uri: String): HttpResponse<out List<GitLabMergeRequestShortDTO>> {
  val request = request(uri).GET().build()
  return loadJsonList(request)
}