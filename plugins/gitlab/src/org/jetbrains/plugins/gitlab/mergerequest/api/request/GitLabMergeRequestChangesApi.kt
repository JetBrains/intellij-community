// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import java.net.http.HttpResponse

suspend fun GitLabApi.loadMergeRequestVersions(project: GitLabProjectCoordinates): HttpResponse<out List<GitLabMergeRequestShortRestDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").resolveRelative("versions")
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.loadMergeRequestDiffs(project: GitLabProjectCoordinates): HttpResponse<out List<GitLabMergeRequestShortRestDTO>> {
  val uri = project.restApiUri.resolveRelative("merge_requests").resolveRelative("diffs")
  val request = request(uri).GET().build()
  return loadJsonList(request)
}