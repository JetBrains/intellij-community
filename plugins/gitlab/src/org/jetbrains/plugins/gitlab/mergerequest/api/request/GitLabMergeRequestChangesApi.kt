// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.http.HttpResponse

suspend fun GitLabApi.Rest.loadMergeRequestDiffs(project: GitLabProjectCoordinates,
                                                 mergeRequest: GitLabMergeRequestId): HttpResponse<out List<GitLabDiffDTO>> {
  val uri = project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mergeRequest.iid)
    .resolveRelative("diffs")
  val request = request(uri).GET().build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_GET_MERGE_REQUEST_DIFF) {
    loadJsonList(request)
  }
}

suspend fun GitLabApi.Rest.loadCommitDiffs(project: GitLabProjectCoordinates,
                                           commitSha: String): HttpResponse<out List<GitLabDiffDTO>> {
  val uri = project.restApiUri
    .resolveRelative("repository")
    .resolveRelative("commits")
    .resolveRelative(commitSha)
    .resolveRelative("diff")
  val request = request(uri).GET().build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_GET_COMMIT_DIFF) {
    loadJsonList(request)
  }
}

suspend fun GitLabApi.Rest.loadCommit(project: GitLabProjectCoordinates,
                                      commitSha: String): HttpResponse<out GitLabCommitRestDTO> {
  val uri = project.restApiUri
    .resolveRelative("repository")
    .resolveRelative("commits")
    .resolveRelative(commitSha)
  val request = request(uri).GET().build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_GET_COMMIT) {
    loadJsonValue(request)
  }
}

